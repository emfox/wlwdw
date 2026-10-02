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

/**
 * The one notification channel, and the two notifications posted on it.
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
