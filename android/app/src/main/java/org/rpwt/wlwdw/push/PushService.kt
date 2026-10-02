package org.rpwt.wlwdw.push

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rpwt.wlwdw.BuildConfig
import org.rpwt.wlwdw.data.MessageRepository
import org.rpwt.wlwdw.data.net.WlwdwApi
import org.rpwt.wlwdw.data.prefs.PreferencesRepository

/**
 * Keeps one MQTT connection to the broker and delivers what arrives.
 *
 * A port of the pre-rewrite `MqttPushService`, and deliberately so: every
 * number in here (port 443, path `/mqtt`, MQTT 3, `cleanSession(false)`,
 * keep-alive 30, QoS 1, the backoff) was arrived at against real devices, real
 * doze and real radios. Re-deriving them would cost weeks and buy nothing, so
 * the behaviour is copied and only the plumbing changed:
 *
 * - a `Thread` with a `volatile boolean` becomes a coroutine on [Dispatchers.IO],
 *   which is cancelled instead of having to be polled;
 * - the blocking HiveMQ client is still the blocking one, because its reconnect
 *   is manual either way -- `automaticReconnect` would race this loop over the
 *   same client id and the two would kick each other off the broker.
 */
class PushService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsStore by lazy { PreferencesRepository(applicationContext) }
    private val messages by lazy { MessageRepository(applicationContext) }
    private val api = WlwdwApi()

    private var loop: Job? = null

    /** The live client, if a session is open. Only touched from the loop. */
    private var client: Mqtt3BlockingClient? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The channel has to exist before the notification that keeps this
        // service in the foreground is posted, or Android drops it.
        WlwdwNotifications.createChannel(this)
        startForeground(WlwdwNotifications.SERVICE_ID, WlwdwNotifications.serviceNotification(this))

        // startForegroundService can deliver more than one start; reconnecting
        // a second time would give the broker two clients with one id.
        if (loop?.isActive != true) {
            loop = scope.launch { connectLoop() }
        }
        // Restarted after the process is killed, which for a push link is the
        // normal way it comes back rather than an error to recover from.
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        // The loop disconnects in its own finally block, but cancelling does not
        // wait for that to run, and a kill between attempts has no open session
        // to clean up -- so this is the backstop, not the main path.
        client?.let { runCatching { it.disconnect() } }
        client = null
        report(PushState.Idle)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Connect, receive, reconnect, forever.
     *
     * Ends only by cancellation, which is what [onDestroy] does.
     */
    private suspend fun connectLoop() {
        prefsStore.ensureSeeded()
        val settings = prefsStore.prefs.first()
        val devid = settings.deviceId
        if (devid.isBlank()) {
            // Nothing to authenticate as; the broker would refuse every attempt.
            // This is the one case that is a dead end rather than a retry.
            Log.w(TAG, "no device id yet, not connecting")
            report(PushState.Failed)
            return
        }
        val endpoint = brokerEndpoint(settings)

        var attempt = 0
        while (currentCoroutineContext().isActive) {
            if (attempt > 0) {
                val wait = backoffMillis(attempt)
                Log.i(TAG, "reconnect #$attempt in ${wait}ms (devid=$devid)")
                report(PushState.Connecting)
                delay(wait)
            } else {
                report(PushState.Connecting)
            }

            val connected = try {
                connectOnce(devid, endpoint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "push link failed (devid=$devid)", e)
                false
            }
            // What the backoff counts is consecutive failures, not attempts in
            // total: a link that worked before dropping is worth retrying soon.
            attempt = if (connected) 0 else attempt + 1
        }
    }

    /**
     * One session: connect, subscribe, then receive until it ends.
     *
     * True once the subscriptions are in place, whether or not the session later
     * drops -- that is what tells the caller the backoff can be reset.
     */
    private suspend fun connectOnce(devid: String, endpoint: BrokerEndpoint): Boolean {
        val mqtt = buildClient(devid, endpoint)
        client = mqtt
        return try {
            mqtt.connectWith()
                // The broker keeps this device's session while it is away and
                // queues what was published, so a short drop loses nothing.
                .cleanSession(false)
                .keepAlive(KEEP_ALIVE_SECONDS)
                .send()
            for (topic in listOf(BrokerEndpoint.TOPIC_ALL, devid)) {
                mqtt.subscribeWith().topicFilter(topic).qos(MqttQos.AT_LEAST_ONCE).send()
            }
            Log.i(TAG, "connected and subscribed (devid=$devid)")
            report(PushState.Connected)
            receiveLoop(mqtt)
            true
        } finally {
            // Disconnecting cleanly is what starts the broker-side queue: for a
            // non-clean session it does not begin until the keep-alive expires.
            withContext(NonCancellable) { runCatching { mqtt.disconnect() } }
            client = null
        }
    }

    private suspend fun receiveLoop(mqtt: Mqtt3BlockingClient) {
        val publishes = mqtt.publishes(MqttGlobalPublishFilter.ALL)
        while (currentCoroutineContext().isActive) {
            val publish = runCatching {
                publishes.receive(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS).orElse(null)
            }.getOrNull()

            if (publish != null) {
                onPush(publish)
            } else if (!mqtt.state.isConnected) {
                // The old loop expected receive() to throw once the link died.
                // It does not have to: with nothing to deliver it can sit there
                // timing out on a connection that is already gone, which would
                // leave the device subscribed to nothing and never retrying.
                Log.w(TAG, "connection lost while waiting for a push")
                return
            }
        }
    }

    private fun buildClient(devid: String, endpoint: BrokerEndpoint): Mqtt3BlockingClient {
        val websocket = MqttClient.builder()
            .useMqttVersion3()
            .identifier(devid)
            .serverHost(endpoint.host)
            .serverPort(endpoint.port)
            .webSocketConfig()
            .serverPath(endpoint.path)
            .applyWebSocketConfig()
        val transport = if (endpoint.tls) websocket.sslWithDefaultConfig() else websocket
        return transport
            .simpleAuth()
            .username(devid)
            .password(BuildConfig.MQTT_DEVICE_SHARED_SECRET.toByteArray(StandardCharsets.UTF_8))
            .applySimpleAuth()
            .buildBlocking()
    }

    private suspend fun onPush(publish: Mqtt3Publish) {
        val payload = readPayload(publish) ?: return
        val id = PushPayload.messageId(payload)
        if (id == null) {
            Log.w(TAG, "push without a message id: $payload")
            return
        }
        deliver(id)
    }

    private fun readPayload(publish: Mqtt3Publish): String? {
        val buffer = publish.payload.orElse(null) ?: return null
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    /**
     * Pull the body the push pointed at, store it, and show it.
     *
     * The push is only a pointer: the text stays on the server until the device
     * asks for it with its own id, which is what stops one device from reading
     * another's mail if a push is ever misdelivered.
     */
    private suspend fun deliver(id: String) {
        val settings = prefsStore.prefs.first()
        val pulled = api.pullMessage(settings.serverHost, settings.deviceId, id)
        if (pulled == null) {
            Log.w(TAG, "no message body for id $id")
            return
        }
        messages.storeFromServer(content = pulled.content, serverTime = pulled.serverTime)
        notify(pulled.content)
    }

    private fun notify(content: String) {
        // Without the notification permission (Android 13+) the system throws
        // here. The message is already stored by then, so losing the notice is
        // survivable -- crashing the service that carries every future one is
        // not.
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(WlwdwNotifications.MESSAGE_ID, WlwdwNotifications.messageNotification(this, content))
        }
    }

    private fun report(next: PushState) {
        linkState.value = next
    }

    companion object {
        private const val TAG = "WlwdwPush"

        /** Seconds between pings, and the value the old service used. */
        private const val KEEP_ALIVE_SECONDS = 30

        /** How long a wait for a push idles before the link is re-checked. */
        private const val RECEIVE_TIMEOUT_SECONDS = 5L

        private val linkState = MutableStateFlow(PushState.Idle)

        /**
         * What the push link is doing, for the status screen.
         *
         * A process-wide value rather than something bound or injected: the
         * service is the only writer and the UI the only reader, and they are
         * in the same process -- which is why the pre-rewrite app could get by
         * with no state at all and just a notification nobody could query.
         */
        val state: StateFlow<PushState> = linkState.asStateFlow()

        /**
         * Start the link.
         *
         * Only legal while the app is in the foreground: Android 12+ refuses to
         * start a foreground service from the background, which is why this is
         * called from the UI and not from a receiver.
         */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, PushService::class.java))
        }

        /**
         * Exponential backoff between attempts: 1s, 2s, 4s ... capped at a
         * minute, so a flaky radio recovers quickly without hammering the
         * broker through an outage that lasts longer than that.
         */
        private fun backoffMillis(attempt: Int): Long =
            (1_000L shl attempt.coerceAtMost(6)).coerceAtMost(60_000L)
    }
}
