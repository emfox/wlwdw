package org.rpwt.wlwdw;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.preference.PreferenceManager;

import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Processes an incoming MQTT push. The server publishes a lightweight payload
 * {"id": <message row id>} to the device topic; the full body is pulled over
 * HTTPS from /message/{id}/{devid}, then stored in the local SQLite box and
 * surfaced as a notification.
 */
public final class MqttMessageHandler {
    private static final String TAG = "MqttMessage";
    private static final int NOTIFY_ID = 1000;

    private MqttMessageHandler() {
    }

    /** Entry point called from the push service (already on a worker thread). */
    public static void handle(Context context, String payload) {
        final String msgId = extractId(payload);
        if (msgId == null || msgId.isEmpty()) {
            Log.w(TAG, "push payload without a message id: " + payload);
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String appUuid = prefs.getString("app_uuid", "");
        if (appUuid.isEmpty()) {
            Log.w(TAG, "no app_uuid yet, dropping push " + msgId);
            return;
        }

        String customHost = context.getString(R.string.pref_default_custom_host);
        if (prefs.getBoolean("enable_custom_host", false)) {
            customHost = prefs.getString("custom_host", customHost);
        }
        final String url = "https://" + customHost + "/message/" + msgId + "/" + appUuid;

        new Thread(() -> {
            try {
                String result = LocationActivity.readContentFromGet(url);
                JSONObject root = (JSONObject) new JSONTokener(result).nextValue();
                JSONObject body = (JSONObject) new JSONTokener(root.getString("message")).nextValue();
                final String time = body.getString("time");
                final String content = body.getString("content");
                new Handler(Looper.getMainLooper()).post(() -> saveAndNotify(context, time, content));
            } catch (Exception e) {
                Log.e(TAG, "failed to pull message " + msgId, e);
            }
        }).start();
    }

    /** Extracts the message id from a JSON {"id": ...} payload or a bare id string. */
    private static String extractId(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            JSONObject obj = (JSONObject) new JSONTokener(payload).nextValue();
            if (obj.has("id")) {
                return String.valueOf(obj.get("id"));
            }
        } catch (Exception ignored) {
            // payload was a bare id string
        }
        String trimmed = payload.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void saveAndNotify(Context context, String time, String content) {
        MsgdbHelper dbHelper = new MsgdbHelper(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(MsgdbHelper.COLUMN_FROM, "定位服务器");
            values.put(MsgdbHelper.COLUMN_TIME, time);
            values.put(MsgdbHelper.COLUMN_CONTENT, content);
            db.insertOrThrow(MsgdbHelper.TABLE_NAME, null, values);
        } catch (Exception e) {
            Log.e(TAG, "failed to store message", e);
        } finally {
            db.close();
        }

        Intent resultIntent = new Intent(context, MessageActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(LocationActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, NotificationUtils.ANDROID_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("定位服务器发来消息")
                .setContentText(content)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFY_ID, notification);
    }
}
