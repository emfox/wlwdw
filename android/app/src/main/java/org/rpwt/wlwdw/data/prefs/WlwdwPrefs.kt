package org.rpwt.wlwdw.data.prefs

/**
 * Everything the user can set, as one immutable snapshot.
 *
 * One type rather than a Flow per setting: the screens read their values from a
 * single emission, so they can never render a half-updated mixture (the host
 * row showing the new value while the switch that governs it still shows the
 * old one).
 *
 * These are the settings the pre-rewrite `PreferenceScreen` carried -- device
 * id, custom host, reporting interval -- minus the two that only ever existed
 * to configure the Baidu SDK (`NeedAddr`, `CoorType`). Location now comes from
 * the platform and is WGS-84 unconditionally, so there is nothing left to
 * configure about it.
 */
data class WlwdwPrefs(
    /** This install's tracking device id; has to match `Category.devid` server-side. */
    val deviceId: String,
    /** Whether the first-run page has been answered. */
    val consentAccepted: Boolean,
    /** Whether to use [customHost] instead of the built-in default. */
    val useCustomHost: Boolean,
    /** The host the user configured; only read when [useCustomHost] is set. */
    val customHost: String,
    /** Reporting interval in minutes. */
    val reportIntervalMinutes: Int,
    /**
     * Whether to start reporting again after the phone reboots.
     *
     * Off unless the user turns it on. A tracker that silently starts sending
     * its owner's position after every reboot is a decision about someone
     * else's phone, and the person holding it should have made that decision
     * deliberately.
     */
    val startOnBoot: Boolean,
    /**
     * Whether tracking is supposed to be running.
     *
     * Not a screen setting: it is what the big button on the status screen
     * writes, and it exists so that "should this device be reporting" survives
     * the death of the process holding the loop. One foreground service now
     * carries both the push link and the reporting loop and is always running,
     * so a `START_STICKY` restart can no longer read the answer off the fact
     * that it came back -- that used to work only because a service existed
     * solely while tracking ran. Stored, the answer is the same after a
     * restart, after a reboot (see `BootReceiver`) and after the user stops
     * it, which is the case that matters: a service the user stopped must stay
     * stopped.
     *
     * Distinct from `TrackingState.running`, which is the loop's own account of
     * itself. They differ legitimately: the loop stops itself on a refusal and
     * leaves the intent set, so the screen can offer to start it again.
     */
    val trackingEnabled: Boolean,
) {
    /**
     * Where the server is, as the network layer needs it: `host[:port]`, no
     * scheme. Both the HTTP calls and the MQTT broker are reached here.
     */
    val serverHost: String
        get() = if (useCustomHost) customHost else DEFAULT_HOST

    companion object {
        /** The built-in server, used unless the user overrides it. */
        const val DEFAULT_HOST = "wlwdw.rpwt.org"

        /**
         * Two minutes, matching the default the pre-rewrite app shipped
         * (`pref_general.xml`). This is a behaviour-preserving choice, not a
         * re-decision: changing it is a product call, not a rewrite detail.
         */
        const val DEFAULT_INTERVAL_MINUTES = 2

        /** The six intervals the old ListPreference offered, in order. */
        val INTERVAL_CHOICES_MINUTES = listOf(1, 2, 3, 5, 10, 30)
    }
}
