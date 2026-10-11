package org.rpwt.wlwdw.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.annotation.StringRes
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.ui.WlwdwActivity

/**
 * The one notification channel, and the notifications posted on it.
 *
 * Every long-lived thing this app does is visible here, because a permanent
 * background process the user cannot see is exactly what the platform's
 * foreground-service rules exist to prevent. There is one permanent notice for
 * the one foreground service (see `service.WlwdwService`) and a transient one
 * per message that arrives.
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

    /**
     * The permanent notice, for the one foreground service.
     *
     * One id, because there is one service and a foreground service shows one
     * notification: this is the number the notice is filed under, so re-posting
     * it updates the notice rather than stacking a second one. It is the id the
     * pre-rewrite app used for its MQTT service and the one the rewrite used for
     * the same thing, so the notice an upgrade leaves in the shade is the one
     * this build keeps updating rather than an orphan nobody can dismiss.
     */
    const val SERVICE_ID = 2001

    /** One per arrived message; a newer message replaces the previous notice. */
    const val MESSAGE_ID = 1000

    /**
     * What a notification asks the app to open, when it asks for anything.
     *
     * The value is read by `WlwdwApp`; the name is the contract between it and
     * this file. It is a string rather than a navigation type on purpose --
     * nothing in push/ should have to know what the graph looks like.
     */
    const val EXTRA_OPEN = "org.rpwt.wlwdw.OPEN"

    /** Open the inbox: what a message notification is for. */
    const val OPEN_MESSAGES = "messages"

    /**
     * PendingIntent request codes.
     *
     * Two of them because request code is part of what identifies a
     * PendingIntent and extras are not: two intents with the same code would
     * be treated as the same pending intent, and the second one's extras would
     * overwrite the first's.
     */
    private const val REQUEST_SERVICE = 0
    private const val REQUEST_MESSAGE = 1

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
     * The wording is chosen by the caller rather than decided here: it is the
     * status matrix, shared with the screens (`serviceNotice` in
     * `ui/component/StatusMatrix.kt`), and the one thing a permanent
     * notification must not do is claim the device is reachable, or reporting,
     * when it is not. That is why this takes the two strings instead of
     * inventing something reassuring of its own.
     */
    fun serviceNotification(
        context: Context,
        @StringRes titleRes: Int,
        @StringRes textRes: Int,
    ): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes))
            .setSmallIcon(ICON)
            .setOngoing(true)
            .setContentIntent(openApp(context, REQUEST_SERVICE))
            .build()

    /**
     * A message arrived. Tapping it opens the inbox, not just the app.
     *
     * The distinction is the whole point of a message notification: after the
     * "message from the location server" title the user wants the message, and
     * landing them on the status ring means they have to find the inbox tab.
     */
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
            .setContentIntent(openApp(context, REQUEST_MESSAGE, OPEN_MESSAGES))
            .build()

    /**
     * The tap target of a notification.
     *
     * [FLAG_ACTIVITY_SINGLE_TOP] is what makes the difference between "the app
     * opens" and "the app opens and then goes where it was asked to": without
     * it a tap while the app is already on screen starts a second copy of it
     * and `onNewIntent` is never called, so the extra is never read.
     */
    private fun openApp(context: Context, requestCode: Int, open: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, WlwdwActivity::class.java).apply {
                if (open != null) putExtra(EXTRA_OPEN, open)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
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
