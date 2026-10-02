package org.rpwt.wlwdw.tracking

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.rpwt.wlwdw.data.ReportRepository
import org.rpwt.wlwdw.data.net.WlwdwApi
import org.rpwt.wlwdw.data.prefs.PreferencesRepository
import org.rpwt.wlwdw.location.LocationSource
import org.rpwt.wlwdw.push.WlwdwNotifications

/**
 * Keeps the tracking loop running while the app is not on screen.
 *
 * The loop itself is [ReportingEngine] and is unchanged; what changes is who
 * holds the scope. A ViewModel's scope lives and dies with the screen, so
 * tracking stopped the moment the app was backgrounded far enough for the
 * process to be reclaimed -- while a tracker's entire job is to keep reporting
 * with the screen off. A foreground service is the only construct Android lets
 * do that, and it requires the user be able to see that it is happening, which
 * is what the notification is for.
 *
 * The pre-rewrite app had no such service: only the MQTT link was foregrounded,
 * and the reporting loop lived in the Activity. That is worth stating plainly
 * because it means this is not a port -- screen-off reporting is new behaviour,
 * and the reason it is being added is that "reports while the phone is in a
 * pocket" is the point of the app.
 *
 * The service is `location`-typed rather than `dataSync`: it exists to read the
 * device's position, Android charges the location type for exactly that, and
 * unlike dataSync it has no 6h/24h limit to run into. See the targetSdk note in
 * app/build.gradle.
 */
class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsStore by lazy { PreferencesRepository(applicationContext) }
    private val engine by lazy {
        ReportingEngine(
            sender = WlwdwApi(),
            location = LocationSource(applicationContext),
            reports = ReportRepository(applicationContext),
            // The one state every reader sees: the screen collects the same
            // flow, so "is it reporting" has a single answer rather than one
            // per owner.
            output = liveState,
        )
    }

    private var loop: Job? = null
    private var notice: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The channel has to exist before the notification that keeps this in
        // the foreground is posted, or Android drops it.
        WlwdwNotifications.createChannel(this)
        startForeground(
            WlwdwNotifications.TRACKING_ID,
            WlwdwNotifications.trackingNotification(this, liveState.value.status),
        )

        // startForegroundService can deliver more than one start; a second loop
        // would send every report twice.
        if (loop?.isActive != true) {
            loop = scope.launch {
                engine.run { prefsStore.prefs.first() }
            }
        }
        // Keep the notice honest: a permanent notification that keeps saying
        // "reporting" while the uploads are failing is worse than none.
        if (notice?.isActive != true) {
            notice = scope.launch {
                engine.state.collect { state ->
                    getSystemService(android.app.NotificationManager::class.java).notify(
                        WlwdwNotifications.TRACKING_ID,
                        WlwdwNotifications.trackingNotification(this@TrackingService, state.status),
                    )
                }
            }
        }
        Log.i(TAG, "tracking service started")
        return START_STICKY
    }

    override fun onDestroy() {
        // Cancelling is what ends the loop: the engine's own finally block
        // clears `running`. Wiping the whole state here would also throw away
        // the last fix and the last successful report, which are exactly what
        // the screen shows once tracking has stopped.
        loop?.cancel()
        notice?.cancel()
        scope.cancel()
        // Persisting tracking across a restart is deliberate (START_STICKY), but
        // a service the user stopped must not come back.
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "tracking service stopped")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WlwdwTrack"

        /**
         * The one place tracking state lives, for both the service and the UI.
         *
         * Process-wide rather than owned by the service or by a ViewModel,
         * because neither outlives the other: the screen can be open with the
         * service stopped (showing the last fix and when it last worked), and
         * the service runs with no screen at all.
         */
        private val liveState = MutableStateFlow(TrackingState())

        val state: StateFlow<TrackingState> = liveState.asStateFlow()

        /**
         * Start reporting.
         *
         * Only legal while the app is in the foreground: Android 12+ refuses a
         * background start, which is also why this is called from a tap rather
         * than from a boot receiver.
         */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, TrackingService::class.java))
        }

        /** Stop reporting. The notification goes with it. */
        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }

        /**
         * Re-read the location permissions.
         *
         * Answered here rather than by the engine because the screen asks this
         * before any loop exists: whether the start button is allowed to start
         * anything is a question about the install, not about a running loop.
         */
        fun permissionsChanged(context: Context) {
            val source = LocationSource(context.applicationContext)
            liveState.update {
                it.copy(
                    hasForeground = source.hasForegroundPermission(),
                    hasBackground = source.hasBackgroundPermission(),
                )
            }
        }

        /**
         * Read the log's last accepted report into the state.
         *
         * The loop does this too when it starts, but a cold start that has not
         * started tracking must still be able to say when this device last
         * worked: the answer is in the log, not in this process.
         */
        suspend fun restoreLastSuccess(context: Context) {
            val last = ReportRepository(context.applicationContext).lastAcceptedAt() ?: return
            liveState.update { it.copy(lastSuccessAt = last) }
        }
    }
}
