package org.rpwt.wlwdw.ui.model

/**
 * The status matrix: the states of the device as the user experiences them.
 *
 * The pre-rewrite screen collapsed all of this into a numeric error code
 * (61 / 62 / 161) that told the user nothing and offered no way out. The design
 * keeps only the combinations that actually change what the screen should say,
 * so each one is a named type here and the wording for each lives with the UI.
 *
 * Stage 1 renders these from fixed demo values; the data layer will produce
 * them for real later, and this is where it will report them into.
 */
sealed interface DeviceStatus {

    /** Tracking on, background location granted, both links healthy. */
    data object Reporting : DeviceStatus

    /**
     * Tracking on, but location is granted only while the app is in use, so a
     * screen-off device silently stops reporting.
     */
    data object PermissionMissing : DeviceStatus

    /** Tracking on, upload failing and being retried. */
    data object Retrying : DeviceStatus

    /** Tracking off. Shown plainly, without nagging. */
    data object Stopped : DeviceStatus

    /** Uploads are fine but the push link is down, so messages do not arrive. */
    data object PushOffline : DeviceStatus

    /** No usable fix. The last known one stays on screen, marked as old. */
    data object LocationFailed : DeviceStatus
}

/**
 * Health of one of the two independent links the app depends on: the HTTP
 * upload to the server, and the MQTT push connection. They fail for different
 * reasons and are fixed differently, so they are reported separately.
 */
enum class LinkState { Ok, Warn, Error }
