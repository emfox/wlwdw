package org.rpwt.wlwdw.ui.component

import androidx.annotation.StringRes
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.push.PushState
import org.rpwt.wlwdw.tracking.TrackingState
import org.rpwt.wlwdw.ui.model.DeviceStatus

/**
 * Everything that renders a status, in one place.
 *
 * Shared rather than repeated because three surfaces describe the same two
 * links -- the status screen's ring and its link rows, the map's floating chip,
 * and the one permanent notification -- and two `when` blocks over one sealed
 * interface are two places to forget the next case. The ring's caption is
 * deliberately not here: it needs the reporting interval, which only the status
 * screen has at hand.
 */

fun statusTone(status: DeviceStatus): StatusTone = when (status) {
    DeviceStatus.Reporting -> StatusTone.Ok
    DeviceStatus.PermissionMissing -> StatusTone.Warn
    DeviceStatus.Retrying -> StatusTone.Warn
    DeviceStatus.Stopped -> StatusTone.Idle
    DeviceStatus.PushOffline -> StatusTone.Warn
    DeviceStatus.LocationFailed -> StatusTone.Error
    DeviceStatus.DeviceUnregistered -> StatusTone.Error
}

/** The pill's word for this status; never a second, differently-worded one. */
@StringRes
fun statusTitle(status: DeviceStatus): Int = when (status) {
    DeviceStatus.Reporting -> R.string.wlwdw_ring_reporting
    DeviceStatus.PermissionMissing -> R.string.wlwdw_ring_permission
    DeviceStatus.Retrying -> R.string.wlwdw_ring_retrying
    DeviceStatus.Stopped -> R.string.wlwdw_ring_stopped
    DeviceStatus.PushOffline -> R.string.wlwdw_ring_push_offline
    DeviceStatus.LocationFailed -> R.string.wlwdw_ring_no_fix
    DeviceStatus.DeviceUnregistered -> R.string.wlwdw_ring_unregistered
}

/**
 * What this device's status is, given both links.
 *
 * [TrackingState.status] is decided by the reporting loop, which knows nothing
 * about MQTT. "Every report is landing but no message can reach me" is the one
 * state that needs both answers, and it is the only reason
 * [DeviceStatus.PushOffline] exists -- so it is composed here, where both are
 * available, rather than pushed down into the engine.
 *
 * The notification reads this too, so the notice and the ring cannot disagree
 * about whether the device is being tracked.
 */
fun ringStatus(state: TrackingState, push: PushState): DeviceStatus =
    if (state.status == DeviceStatus.Reporting && push != PushState.Connected) {
        DeviceStatus.PushOffline
    } else {
        state.status
    }

/** The word and the colour for one link's row on the status screen. */
data class LinkPresentation(@StringRes val textRes: Int, val tone: StatusTone)

/**
 * The push link, from the MQTT client's own state.
 *
 * Independent of the upload link on purpose: the two links break for different
 * reasons and are mended differently, and a device whose reports are all going
 * through but whose inbox is dead must say so rather than looking healthy. The
 * notification uses this same mapping when tracking is off and the push link is
 * the only thing running, so its wording matches the row the user can open.
 */
fun pushLink(push: PushState): LinkPresentation = when (push) {
    PushState.Connected -> LinkPresentation(R.string.wlwdw_link_online, StatusTone.Ok)
    // Between attempts: the loop is always either connecting or waiting to
    // retry, and both are worth saying out loud rather than as "normal".
    PushState.Connecting -> LinkPresentation(R.string.wlwdw_link_reconnecting, StatusTone.Warn)
    PushState.Failed -> LinkPresentation(R.string.wlwdw_link_offline, StatusTone.Error)
    PushState.Idle -> LinkPresentation(R.string.wlwdw_link_offline, StatusTone.Idle)
}

/** What the one permanent notification says: a title and a line of text. */
data class ServiceNotice(@StringRes val titleRes: Int, @StringRes val textRes: Int)

/**
 * The permanent notification's wording.
 *
 * There used to be two notices, one per foreground service. There is now one
 * service and one notice, so it has to pick which of the two stories to tell,
 * and it tells the more interesting one: while tracking is on, the reporting
 * loop is what the user needs a reading of (it is what they turned on, and its
 * failures are actionable); with tracking off the push link is the only thing
 * still running, so it gets the line.
 *
 * [tracking] is the *intent* (`WlwdwPrefs.trackingEnabled`), not whether the
 * loop is running this instant. They differ when the loop has stopped itself on
 * a refusal, and in that case the notice must still say why -- reading the loop
 * would show "已停止", which sends the user looking in the wrong place.
 */
fun serviceNotice(tracking: Boolean, state: TrackingState, push: PushState): ServiceNotice =
    if (tracking) {
        ServiceNotice(R.string.wlwdw_tracking_service_title, statusTitle(ringStatus(state, push)))
    } else {
        ServiceNotice(R.string.wlwdw_push_service_title, pushLink(push).textRes)
    }
