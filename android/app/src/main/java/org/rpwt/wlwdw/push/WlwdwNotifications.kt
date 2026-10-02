package org.rpwt.wlwdw.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.ui.WlwdwActivity
import org.rpwt.wlwdw.ui.model.DeviceStatus

/**
 * The one notification channel, and the notifications posted on it.
 *
 * Every long-lived thing this app does is visible here, because a permanent
 * background process the user cannot see is exactly what the platform's
 * foreground-service rules exist to prevent.
 *
 * Ported from the pre-rewrite `NotificationUtils`. The channel id is unchanged
 * on purpose: it is the key Android files the user's settings under, so keeping
 * it means an update does not silently reset a notification preference someone
 * set. The rest of the channel is the old configuration too -- default
 * importance, light and vibration on, contents hidden on the lock screen.
 *
 * The one deliberate change is the channel's *name*, which the old code left as
 * the literal "ANDROID CHANNEL". It is only shown on a fresh install (Android
 * ignores the name once the channel exists), so nothing regresses.
 */
object WlwdwNotifications {

    const val CHANNEL_ID = "org.rpwt.wlwdw"

    /** The long-lived one, owned by the push service. */
    const val SERVICE_ID = 2001

    /** The tracking loop's, owned by the tracking service. */
    const val TRACKING_ID = 2002

    /** One per arrived message; a newer message replaces the previous notice. */
    const val MESSAGE_ID = 1000

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wlwdw_channel_push),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            enableLights(true)
            enableVibration(true)
            lightColor = Color.GREEN
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        context.notificationManager().createNotificationChannel(channel)
    }

    /**
     * The notification a foreground service must show while it runs.
     *
     * Its text says what the link is doing rather than something reassuring:
     * the one thing a permanent notification must not do is claim the device is
     * reachable when it is not.
     */
    fun serviceNotification(context: Context): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.wlwdw_push_service_title))
            .setContentText(context.getString(R.string.wlwdw_push_connecting))
            .setSmallIcon(ICON)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .build()

    /**
     * The tracking loop's notice, which says what the loop is actually doing.
     *
     * The status is the same matrix the screen renders, so the notification and
     * the ring cannot disagree about whether this device is being tracked. It
     * is the state's own [DeviceStatus] rather than a second reading of the
     * failure, which is how a notice ends up claiming everything is fine while
     * every upload is being refused.
     */
    fun trackingNotification(context: Context, status: DeviceStatus): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.wlwdw_tracking_service_title))
            .setContentText(context.getString(statusText(status)))
            .setSmallIcon(ICON)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .build()

    private fun statusText(status: DeviceStatus): Int = when (status) {
        DeviceStatus.Reporting -> R.string.wlwdw_ring_reporting
        DeviceStatus.PermissionMissing -> R.string.wlwdw_ring_permission
        DeviceStatus.LocationFailed -> R.string.wlwdw_ring_no_fix
        DeviceStatus.DeviceUnregistered -> R.string.wlwdw_ring_unregistered
        DeviceStatus.Retrying -> R.string.wlwdw_ring_retrying
        // The tracking loop never reports this one -- it is about the push
        // link, which has its own notice -- but the pair belongs together when
        // the connection is merely unhappy.
        DeviceStatus.PushOffline -> R.string.wlwdw_ring_retrying
        DeviceStatus.Stopped -> R.string.wlwdw_ring_stopped
    }

    /** A message arrived. Tapping it opens the app, where the inbox is. */
    fun messageNotification(context: Context, content: String): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.wlwdw_push_message_title))
            .setContentText(content)
            .setSmallIcon(ICON)
            // No setDefaults: since the channel exists it is the channel that
            // decides sound and vibration, and a per-notification default is
            // ignored. What the old code set is now configured on the channel
            // above, which is where it has to be to mean anything.
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, WlwdwActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun Context.notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    /**
     * A framework placeholder, as the pre-rewrite code used. A notification's
     * small icon is masked to white, so what belongs here is a monochrome glyph
     * of our own -- until one is drawn, borrowing the platform's is the only
     * option that cannot render as an unreadable blob.
     */
    private const val ICON = android.R.drawable.stat_notify_more
}
