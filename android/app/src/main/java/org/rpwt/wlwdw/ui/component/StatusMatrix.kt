package org.rpwt.wlwdw.ui.component

import androidx.annotation.StringRes
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.ui.model.DeviceStatus

/**
 * The two things every screen showing a status has to agree on: the colour of
 * the pill and the word in it.
 *
 * Shared rather than repeated because the map's floating chip and the status
 * screen's ring are describing the same [DeviceStatus], and two `when` blocks
 * over one sealed interface are two places to forget the next case. The caption
 * is deliberately not here: it needs the reporting interval, which only the
 * status screen has at hand.
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
