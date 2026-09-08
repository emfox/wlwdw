package org.rpwt.wlwdw;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Foreground service that keeps a single MQTT connection (WSS) to the
 * self-hosted EMQX broker and delivers pushed message ids to the app.
 *
 * - client id + username = the device id (app_uuid), password = shared secret
 * - subscribes to the broadcast topic "all" and to its own devid topic
 * - cleanSession=false keeps the subscription on the broker while offline so
 *   messages published during a disconnect are queued by the broker and
 *   re-delivered on reconnect
 * - A HiveMQ blocking client does NOT auto-reconnect by itself, so the worker
 *   loops forever: on any connect/subscribe/receive failure it waits with a
 *   small backoff and reconnects (re-subscribing), surviving flaky mobile
 *   networks / Android doze / roaming. The service only gives up when it is
 *   explicitly stopped (running=false).
 */
public class MqttPushService extends Service {
    private static final String TAG = "MqttPush";

    private volatile boolean running;
    private volatile Mqtt3BlockingClient client;
    private Thread worker;

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, MqttPushService.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationUtils notificationUtils = new NotificationUtils(this);
        Notification notification = notificationUtils
                .getAndroidChannelNotification("GPS1S 推送服务", "正在连接消息服务器…")
                .build();
        startForeground(MqttConfig.SERVICE_NOTIFICATION_ID, notification);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String devid = prefs.getString("app_uuid", "");
        if (devid.isEmpty()) {
            Log.w(TAG, "no app_uuid yet, skipping connect");
            return START_STICKY;
        }

        if (worker == null || !worker.isAlive()) {
            worker = new Thread(() -> runConnectionLoop(devid), "mqtt-push");
            worker.start();
        }
        return START_STICKY;
    }

    private void runConnectionLoop(String devid) {
        int attempt = 0;
        while (running) {
            // Exponential backoff between attempts: 1s, 2s, 4s ... capped at
            // ~60s so a flaky network doesn't hammer the broker but a drop is
            // recovered quickly.
            if (attempt > 0) {
                long delay = Math.min(1000L << Math.min(attempt, 6), 60_000L);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Log.w(TAG, "reconnect attempt #" + attempt + " (devid=" + devid + ")");
            }

            Mqtt3BlockingClient c = null;
            try {
                c = MqttClient.builder()
                        .automaticReconnectWithDefaultConfig()
                        .useMqttVersion3()
                        .identifier(devid)
                        .serverHost(MqttConfig.MQTT_HOST)
                        .serverPort(MqttConfig.MQTT_PORT)
                        .webSocketConfig()
                            .serverPath(MqttConfig.MQTT_PATH)
                            .applyWebSocketConfig()
                        .sslWithDefaultConfig()
                        .simpleAuth()
                            .username(devid)
                            .password(MqttConfig.sharedSecretBytes())
                            .applySimpleAuth()
                        .buildBlocking();

                client = c;
                // cleanSession=false so the broker keeps this device's session
                // (and its queued QoS>0 messages) across short disconnects and
                // redelivers them on reconnect.
                c.connectWith().cleanSession(false).keepAlive(30).send();

                for (String topic : new String[]{MqttConfig.TOPIC_ALL, devid}) {
                    c.subscribeWith()
                            .topicFilter(topic)
                            .qos(MqttQos.AT_LEAST_ONCE)
                            .send();
                }
                Log.i(TAG, "connected & subscribed (devid=" + devid + ")");
                attempt = 0; // successful connection resets the backoff

                Mqtt3BlockingClient.Mqtt3Publishes publishes = c.publishes(MqttGlobalPublishFilter.ALL);
                while (running) {
                    Optional<Mqtt3Publish> publish = publishes.receive(5, TimeUnit.SECONDS);
                    if (publish.isPresent()) {
                        onPush(publish.get());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                Log.e(TAG, "MQTT connection lost, will reconnect (devid=" + devid + ")", e);
            } finally {
                if (c != null) {
                    try {
                        c.disconnect();
                    } catch (Exception ignored) {
                    }
                }
                client = null;
            }
            attempt++;
        }
    }

    private void onPush(Mqtt3Publish publish) {
        String payload = "";
        try {
            Optional<ByteBuffer> buf = publish.getPayload();
            if (buf.isPresent()) {
                ByteBuffer bb = buf.get().duplicate();
                byte[] bytes = new byte[bb.remaining()];
                bb.get(bytes);
                payload = new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            Log.e(TAG, "cannot read push payload", e);
            return;
        }
        MqttMessageHandler.handle(this, payload);
    }

    @Override
    public void onDestroy() {
        running = false;
        Mqtt3BlockingClient c = client;
        client = null;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
