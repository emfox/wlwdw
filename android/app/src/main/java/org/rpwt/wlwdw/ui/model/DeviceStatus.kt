package org.rpwt.wlwdw.ui.model

/**
 * The status matrix: the states of the device as the user experiences them.
 *
 * The pre-rewrite screen collapsed all of this into a numeric error code
 * (61 / 62 / 161) that told the user nothing and offered no way out. The design
 * keeps only the combinations that actually change what the screen should say,
 * so each one is a named type here and the wording for each lives with the UI.
 *
 * Produced for real by `tracking/ReportingEngine`, which is the only thing that
 * decides which of these is current.
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

    /**
     * The server does not know this device id.
     *
     * Not one of the six the design enumerated, and it earns its place: it is
     * the state a fresh install starts in, and the only one the user fixes on
     * the server rather than on the phone. Without it the screen said
     * "reporting" while the server was answering `{"result":"deny"}` to every
     * report -- the failure mode that made wiring this up worth doing.
     */
    data object DeviceUnregistered : DeviceStatus
}

/**
 * Health of one of the two independent links the app depends on: the HTTP
 * upload to the server, and the MQTT push connection. They fail for different
 * reasons and are fixed differently, so they are reported separately.
 */
enum class LinkState { Ok, Warn, Error }
