package com.wlwdw.gps1s;

import java.text.SimpleDateFormat;

import com.wlwdw.gps1s.MsgdbHelper;

import io.yunba.android.manager.YunBaManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;


public class YunbaReceiver extends BroadcastReceiver {

	private final static int mId = 1000;
	
	private MsgdbHelper dbHelper = null;
	private SQLiteDatabase msgdb = null; 
	
	@Override
	public void onReceive(Context context, Intent intent) {
		 if (YunBaManager.MESSAGE_RECEIVED_ACTION.equals(intent.getAction())) {
			
			String topic = intent.getStringExtra(YunBaManager.MQTT_TOPIC);
			String msg = intent.getStringExtra(YunBaManager.MQTT_MSG);
			
			
			StringBuilder showMsg = new StringBuilder();
			showMsg.append("Received message from server: ").append(YunBaManager.MQTT_TOPIC)
					.append(" = ").append(topic).append(" ")
					.append(YunBaManager.MQTT_MSG).append(" = ").append(msg);
			System.out.println(showMsg);

			//保存消息到数据库
	        //获取数据库
	        dbHelper = new MsgdbHelper(context);
	        msgdb = dbHelper.getReadableDatabase();

	        //插入数据
	        try {
	            ContentValues values = new ContentValues();
	            SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	            String curDate= sDateFormat.format(new java.util.Date());
	            values.put(MsgdbHelper.COLUMN_FROM, "定位服务器");
	            values.put(MsgdbHelper.COLUMN_TIME, curDate);
	            values.put(MsgdbHelper.COLUMN_CONTENT, msg);
	            msgdb.insertOrThrow(MsgdbHelper.TABLE_NAME, null, values);
	        } catch (SQLException e) {
	            e.printStackTrace();
	        }
			
		     // send msg to notification
			
			NotificationCompat.Builder mBuilder =
			        new NotificationCompat.Builder(context)
			        .setSmallIcon(android.R.drawable.ic_dialog_info)
			        .setContentTitle("定位服务器发来消息")
			        .setContentText(msg)
			        .setVibrate(new long[]{100,500,100,500})
			        .setAutoCancel(true);
			// Creates an explicit intent for an Activity in your app
			Intent resultIntent = new Intent(context, MessageActivity.class);

			// The stack builder object will contain an artificial back stack for the
			// started Activity.
			// This ensures that navigating backward from the Activity leads out of
			// your application to the Home screen.
			TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
			// Adds the back stack for the Intent (but not the Intent itself)
			stackBuilder.addParentStack(LocationActivity.class);
			// Adds the Intent that starts the Activity to the top of the stack
			stackBuilder.addNextIntent(resultIntent);
			PendingIntent resultPendingIntent =
			        stackBuilder.getPendingIntent(
			            0,
			            PendingIntent.FLAG_UPDATE_CURRENT
			        );
			mBuilder.setContentIntent(resultPendingIntent);
			NotificationManager mNotificationManager =
			    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			// mId allows you to update the notification later on.
			mNotificationManager.notify(mId, mBuilder.build());
		     
		}

	}

}
