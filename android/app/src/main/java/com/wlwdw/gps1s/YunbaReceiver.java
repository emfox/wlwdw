package com.wlwdw.gps1s;

import java.io.IOException;
//import java.text.SimpleDateFormat;


import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.wlwdw.gps1s.MsgdbHelper;

import io.yunba.android.manager.YunBaManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import android.telephony.TelephonyManager;


public class YunbaReceiver extends BroadcastReceiver {

	private final static int mId = 1000;
    private SharedPreferences sharedPref;
	private MsgdbHelper dbHelper = null;
	private SQLiteDatabase msgdb = null; 
	public Context YunbaContext;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		 if (YunBaManager.MESSAGE_RECEIVED_ACTION.equals(intent.getAction())) {
			 YunbaContext = context;
			sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
			
			final String topic = intent.getStringExtra(YunBaManager.MQTT_TOPIC);
			final String msgid = intent.getStringExtra(YunBaManager.MQTT_MSG);
			
			final Handler handler = new Handler() {
		         // 在Handler中获取消息，重写handleMessage()方法
		         @Override
		         public void handleMessage(Message result) {            
		             // 判断消息码是否为1
		             if(result.what==1){
		            	String time = result.getData().getString("time");
		                String msg = result.getData().getString("content");
		     			//保存消息到数据库
		     	        //获取数据库
		     	        dbHelper = new MsgdbHelper(YunbaContext);
		     	        msgdb = dbHelper.getReadableDatabase();

		     	        //插入数据
		     	        try {
		     	            ContentValues values = new ContentValues();
		     	            //SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		     	            //String curDate= sDateFormat.format(new java.util.Date());
		     	            values.put(MsgdbHelper.COLUMN_FROM, "定位服务器");
		     	            values.put(MsgdbHelper.COLUMN_TIME, time);
		     	            values.put(MsgdbHelper.COLUMN_CONTENT, msg);
		     	            msgdb.insertOrThrow(MsgdbHelper.TABLE_NAME, null, values);
		     	        } catch (SQLException e) {
		     	            e.printStackTrace();
		     	        }
		     			
		     	       msgdb.close();
		     		     // send msg to notification
		     			
		     			NotificationCompat.Builder mBuilder =
		     			        new NotificationCompat.Builder(YunbaContext, topic)
		     			        .setSmallIcon(android.R.drawable.ic_dialog_info)
		     			        .setContentTitle("定位服务器发来消息")
		     			        .setContentText(msg)
		     			        //.setVibrate(new long[]{100,500,100,500})
		     			        .setDefaults(Notification.DEFAULT_ALL) // default vibrate sound and light
		     			        .setAutoCancel(true);
		     			// Creates an explicit intent for an Activity in your app
		     			Intent resultIntent = new Intent(YunbaContext, MessageActivity.class);

		     			// The stack builder object will contain an artificial back stack for the
		     			// started Activity.
		     			// This ensures that navigating backward from the Activity leads out of
		     			// your application to the Home screen.
		     			TaskStackBuilder stackBuilder = TaskStackBuilder.create(YunbaContext);
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
		     			    (NotificationManager) YunbaContext.getSystemService(Context.NOTIFICATION_SERVICE);
		     			// mId allows you to update the notification later on.
		     			mNotificationManager.notify(mId, mBuilder.build());
		             }
		         }
		     };
		     
			new Thread(new Runnable(){
			    @Override
			    public void run() {
			        // 拼凑get请求的URL字串，使用URLEncoder.encode对特殊和不可见字符进行编码
			    	String custom_host = "www.wlwdw.com";
			    	if(sharedPref.getBoolean("enable_custom_host",false))
			    		custom_host = sharedPref.getString("custom_host","www.wlwdw.com");
					String appUUID = sharedPref.getString("app_uuid","");
					String GET_URL = "https://" + custom_host  + "/message/" + msgid + "/" + appUUID;
			    	try {
						String url_result = LocationActivity.readContentFromGet(GET_URL);
						JSONObject result1 = (JSONObject)new JSONTokener(url_result).nextValue();
						JSONObject result2 = (JSONObject)new JSONTokener(result1.getString("message")).nextValue();
						Message notify_main = Message.obtain();
						Bundle bundle = new Bundle();
						bundle.putString("time", result2.getString("time"));
						bundle.putString("content", result2.getString("content"));
						notify_main.setData(bundle);
						notify_main.what = 1;
						handler.sendMessage(notify_main);
					} catch (IOException | JSONException e) {
						e.printStackTrace();
					}
			    	
			    	
			    }
			}).start();

		     
		}

	}


}
