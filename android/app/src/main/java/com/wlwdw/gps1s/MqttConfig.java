package com.wlwdw.gps1s;

import java.nio.charset.StandardCharsets;

/**
 * MQTT push configuration (self-hosted EMQX broker, the emqx service of the
 * wlwdw compose project). Devices authenticate as their devid with a shared
 * secret that the EMQX broker validates by calling back wlwdw's /mqtt/auth
 * endpoint.
 *
 * Keep MQTT_DEVICE_SHARED_SECRET identical to the server-side
 * MQTT_DEVICE_SHARED_SECRET (.env / .env.local of the wlwdw app).
 */
public final class MqttConfig {
    private MqttConfig() {
    }

    /** Broker host: Android connects over WSS (port 443, shared nginx-proxy). */
    public static final String MQTT_HOST = "mqtt.rpwt.org";
    public static final int MQTT_PORT = 443;
    /** EMQX ws listener path (must match the broker / nginx-proxy). */
    public static final String MQTT_PATH = "/mqtt";

    /**
     * Shared secret that devices present to the broker, which the broker
     * validates via wlwdw's /mqtt/auth callback. It is injected at build time
     * from the untracked android/gradle-local.properties (falls back to the
     * placeholder below for fresh checkouts), so a real secret is never
     * committed. It only gates MQTT access (message bodies are still served
     * through /message/{id}/{devid} which checks the devid), so treat it as
     * transport-level protection, not a strong device credential.
     */
    public static final String MQTT_DEVICE_SHARED_SECRET =
            BuildConfig.MQTT_DEVICE_SHARED_SECRET;

    /** Broadcast topic every device subscribes to (server may address all). */
    public static final String TOPIC_ALL = "all";

    /** Foreground notification id of the push service. */
    public static final int SERVICE_NOTIFICATION_ID = 2001;

    public static byte[] sharedSecretBytes() {
        return MQTT_DEVICE_SHARED_SECRET.getBytes(StandardCharsets.UTF_8);
    }
}
