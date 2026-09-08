package org.rpwt.wlwdw;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;

/**
 * MQTT push configuration (self-hosted EMQX broker, the emqx service of the
 * wlwdw compose project). Devices authenticate as their devid with a shared
 * secret that the EMQX broker validates by calling back wlwdw's /mqtt/auth
 * endpoint.
 *
 * Keep MQTT_DEVICE_SHARED_SECRET identical to the server-side
 * MQTT_DEVICE_SHARED_SECRET (.env of the wlwdw app).
 */
public final class MqttConfig {
    private MqttConfig() {
    }

    /** EMQX ws listener path (must match the broker / nginx-proxy). */
    public static final String MQTT_PATH = "/mqtt";
    /** Broker port: always 443 (WSS through the shared nginx-proxy). */
    public static final int MQTT_PORT = 443;

    /**
     * Resolve the broker host to connect to. This honours the same
     * "custom server" preference the HTTP endpoints use (enable_custom_host /
     * custom_host in Settings): when disabled, devices use the built-in
     * default (wlwdw.rpwt.org, where nginx-proxy routes /mqtt to EMQX); when
     * enabled, the configured host is used instead. Host must be a bare
     * host[:port?] name without scheme/path - for WSS only a host on the
     * default 443 port is supported, matching the HTTP endpoints' https.
     */
    public static String serverHost(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String host = context.getString(R.string.pref_default_custom_host);
        if (prefs.getBoolean("enable_custom_host", false)) {
            host = prefs.getString("custom_host", host);
        }
        return host;
    }

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
