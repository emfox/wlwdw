package org.rpwt.wlwdw.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.data.model.ReportFailure
import org.rpwt.wlwdw.location.Fix
import org.rpwt.wlwdw.tracking.TrackingState
import org.rpwt.wlwdw.ui.component.Callout
import org.rpwt.wlwdw.ui.component.StatusPill
import org.rpwt.wlwdw.ui.component.StatusRing
import org.rpwt.wlwdw.ui.component.StatusTone
import org.rpwt.wlwdw.ui.component.WlwdwCard
import org.rpwt.wlwdw.ui.model.DeviceStatus
import org.rpwt.wlwdw.ui.relativeTime
import org.rpwt.wlwdw.ui.theme.CoordinateTextStyle
import org.rpwt.wlwdw.ui.theme.WlwdwDimens
import org.rpwt.wlwdw.ui.theme.statusColors

/**
 * Tab 1: is the device reporting, and what does the server know about it.
 *
 * The whole screen is a function of [TrackingState] -- the ring's colour and
 * wording, whether a notice appears, which link row is unhappy, what the last
 * report was, and whether the primary button starts or stops tracking. Keeping
 * that in one `when` is the point: the pre-rewrite screen spread the same
 * information over seven diagnostic rows and a numeric error code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    state: TrackingState,
    deviceId: String,
    reportIntervalMinutes: Int,
    onOpenSettings: () -> Unit,
    onToggleTracking: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status
    val tracking = state.running

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.wlwdw_app_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.wlwdw_action_settings),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WlwdwDimens.PagePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (status) {
                DeviceStatus.PermissionMissing -> Callout(
                    title = stringResource(R.string.wlwdw_perm_title),
                    body = stringResource(R.string.wlwdw_perm_body),
                    actionLabel = stringResource(R.string.wlwdw_perm_action),
                    onAction = onRequestPermission,
                    modifier = Modifier.padding(top = 10.dp, bottom = WlwdwDimens.CardGap),
                )

                DeviceStatus.DeviceUnregistered -> Callout(
                    title = stringResource(R.string.wlwdw_unregistered_title),
                    // The id is in the body because the fix is to type it into
                    // the server; without it the notice says "something is
                    // wrong" and offers nowhere to go.
                    body = stringResource(R.string.wlwdw_unregistered_body) + "\n" + deviceId,
                    actionLabel = stringResource(R.string.wlwdw_unregistered_action),
                    onAction = onOpenSettings,
                    modifier = Modifier.padding(top = 10.dp, bottom = WlwdwDimens.CardGap),
                )

                else -> Spacer(Modifier.height(WlwdwDimens.CardGap))
            }

            StatusRing(
                tone = ringTone(status),
                title = stringResource(ringTitle(status)),
                caption = ringCaption(status, reportIntervalMinutes),
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = heroLine(state),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            LocationCard(fix = state.fix, stale = status == DeviceStatus.LocationFailed)

            Spacer(Modifier.height(WlwdwDimens.CardGap))

            WlwdwCard {
                val upload = uploadLink(state)
                val push = pushLink(state)
                LinkRow(
                    label = stringResource(R.string.wlwdw_link_upload),
                    pill = stringResource(upload.textRes),
                    tone = upload.tone,
                )
                Spacer(Modifier.height(9.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(9.dp))
                LinkRow(
                    label = stringResource(R.string.wlwdw_link_push),
                    pill = stringResource(push.textRes),
                    tone = push.tone,
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onToggleTracking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tracking) {
                        MaterialTheme.statusColors.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                Text(
                    text = stringResource(
                        if (tracking) R.string.wlwdw_stop_tracking else R.string.wlwdw_start_tracking
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight(660),
                )
            }

            Spacer(Modifier.height(WlwdwDimens.CardGap))
        }
    }
}

/** Where the device is (or last was). */
@Composable
private fun LocationCard(fix: Fix?, stale: Boolean) {
    WlwdwCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = if (stale || fix == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(17.dp),
            )
            if (fix == null) {
                Text(
                    text = stringResource(R.string.wlwdw_location_unknown),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    // Five decimals is about a metre at these latitudes: more
                    // would be noise dressed up as precision.
                    text = String.format(Locale.US, "%.5f, %.5f", fix.lat, fix.lng),
                    style = CoordinateTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (fix != null) {
            Spacer(Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                fix.accuracyMetres?.let {
                    MetaChip(String.format(Locale.US, "±%.0f m", it))
                }
                MetaChip(fixSourceLabel(fix.provider))
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = relativeTime(fix.atMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (stale) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = stringResource(R.string.wlwdw_location_stale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.statusColors.warn,
            )
        }
    }
}

/** The provider as the phone names it, mapped to something a user reads. */
@Composable
private fun fixSourceLabel(provider: String): String = when (provider) {
    "gps" -> stringResource(R.string.wlwdw_fix_source_gps)
    "network" -> stringResource(R.string.wlwdw_fix_source_network)
    else -> stringResource(R.string.wlwdw_fix_source_other)
}

/** A neutral chip: precision, fix source. Not a status, so not a [StatusPill]. */
@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun LinkRow(label: String, pill: String, tone: StatusTone) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusPill(text = pill, tone = tone)
    }
}

// --- Status matrix ---------------------------------------------------------

private fun ringTone(status: DeviceStatus): StatusTone = when (status) {
    DeviceStatus.Reporting -> StatusTone.Ok
    DeviceStatus.PermissionMissing -> StatusTone.Warn
    DeviceStatus.Retrying -> StatusTone.Warn
    DeviceStatus.Stopped -> StatusTone.Idle
    DeviceStatus.PushOffline -> StatusTone.Warn
    DeviceStatus.LocationFailed -> StatusTone.Error
    DeviceStatus.DeviceUnregistered -> StatusTone.Error
}

@StringRes
private fun ringTitle(status: DeviceStatus): Int = when (status) {
    DeviceStatus.Reporting -> R.string.wlwdw_ring_reporting
    DeviceStatus.PermissionMissing -> R.string.wlwdw_ring_permission
    DeviceStatus.Retrying -> R.string.wlwdw_ring_retrying
    DeviceStatus.Stopped -> R.string.wlwdw_ring_stopped
    DeviceStatus.PushOffline -> R.string.wlwdw_ring_push_offline
    DeviceStatus.LocationFailed -> R.string.wlwdw_ring_no_fix
    DeviceStatus.DeviceUnregistered -> R.string.wlwdw_ring_unregistered
}

@Composable
private fun ringCaption(status: DeviceStatus, reportIntervalMinutes: Int): String = when (status) {
    // The interval is a setting, so this reads it rather than quoting it.
    DeviceStatus.Reporting -> stringResource(R.string.wlwdw_interval_every, reportIntervalMinutes)
    DeviceStatus.PermissionMissing -> stringResource(R.string.wlwdw_ring_permission_caption)
    DeviceStatus.Retrying -> stringResource(R.string.wlwdw_ring_retrying_caption)
    DeviceStatus.Stopped -> stringResource(R.string.wlwdw_ring_stopped_caption)
    DeviceStatus.PushOffline -> stringResource(R.string.wlwdw_ring_push_caption)
    DeviceStatus.LocationFailed -> stringResource(R.string.wlwdw_ring_no_fix_caption)
    DeviceStatus.DeviceUnregistered -> stringResource(R.string.wlwdw_ring_unregistered_caption)
}

/**
 * The line under the ring: when the server last heard from this device.
 *
 * It is the only number that answers "can my family see me", so it counts
 * *successful* reports only. A device whose uploads have been failing for an
 * hour must not show the time of the attempt that failed.
 *
 * "最后上报" only while the ring above says 正在上报, where the two readings of
 * the phrase collapse into one. Anywhere else -- stopped, retrying, no
 * permission -- "成功" is doing real work and is spelled out.
 */
@Composable
private fun heroLine(state: TrackingState): String {
    val last = state.lastSuccessAt ?: return stringResource(R.string.wlwdw_last_never)
    return stringResource(
        if (state.status == DeviceStatus.Reporting) {
            R.string.wlwdw_last_report
        } else {
            R.string.wlwdw_last_success
        },
        relativeTime(last),
    )
}

private data class LinkPresentation(@StringRes val textRes: Int, val tone: StatusTone)

private fun uploadLink(state: TrackingState): LinkPresentation = when {
    !state.running -> LinkPresentation(R.string.wlwdw_link_offline, StatusTone.Idle)
    state.failure is ReportFailure.Upload -> LinkPresentation(R.string.wlwdw_link_failing, StatusTone.Error)
    state.failure is ReportFailure.Refused -> LinkPresentation(R.string.wlwdw_link_refused, StatusTone.Error)
    else -> LinkPresentation(R.string.wlwdw_link_ok, StatusTone.Ok)
}

/**
 * The push link is not wired up yet -- MQTT is the next stage. Reporting it as
 * "online" would be the one thing this screen must not do, so it says what is
 * true instead.
 */
private fun pushLink(state: TrackingState): LinkPresentation = when {
    !state.running -> LinkPresentation(R.string.wlwdw_link_offline, StatusTone.Idle)
    else -> LinkPresentation(R.string.wlwdw_link_pending, StatusTone.Idle)
}
