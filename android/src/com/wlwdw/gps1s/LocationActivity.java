package com.wlwdw.gps1s;


import io.yunba.android.manager.YunBaManager;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;

public class LocationActivity extends ActionBarActivity{
	private SharedPreferences sharedPref;
	private Editor sharedEditor;
	private TextView LabelTime;
	private TextView LabelErrcode;
	private TextView LabelLatLng;
	private TextView LabelGauss;
	private TextView LabelRadius;
	private TextView LocResult;
	private TextView ModeInfor;
	private Button startLocation;
	private Button onceLocate;
	private RadioGroup selectMode,selectCoordinates;
	//默认2分钟定位间隔。 注意，该间隔与百度API的定位间隔span无关。
	//采用 alarmManager 实现轮询，更好支持系统休眠时也能唤醒并定位，故不使用API提供的定时功能
	private String errString;
	private Integer interval=2; 
	private Integer tempMode = 1;
	private String tempcoor="bd09ll";
	private CheckBox checkGeoLocation;

	private LocalBroadcastManager mLocalBroadcastManager;
	private BroadcastReceiver mReceiver;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startBlackService();
		setContentView(R.layout.location);
		mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);  
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		sharedEditor = sharedPref.edit();
		LabelTime = (TextView)findViewById(R.id.LabelTime);
		LabelErrcode = (TextView)findViewById(R.id.LabelErrcode);
		LabelLatLng = (TextView)findViewById(R.id.LabelLatLng);
		LabelGauss = (TextView)findViewById(R.id.LabelGauss);
		LabelRadius = (TextView)findViewById(R.id.LabelRadius);
		LocResult = (TextView)findViewById(R.id.LocResult);
		ModeInfor= (TextView)findViewById(R.id.modeinfor);
		ModeInfor.setText(getString(R.string.hight_accuracy_desc));
		 checkGeoLocation = (CheckBox)findViewById(R.id.geolocation);
		startLocation = (Button)findViewById(R.id.addfence);
		onceLocate = (Button)findViewById(R.id.oncelocate);
		if(PollingService.isPolling)
				startLocation.setText(getString(R.string.stoplocation));
		startLocation.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				InitLocation();
				
				if(!PollingService.isPolling){

					System.out.println("Start polling service...");  
			        PollingUtils.startPollingService(LocationActivity.this, interval*60, PollingService.class);
			        PollingService.isPolling = true;
			        startLocation.setText(getString(R.string.stoplocation));
				}else{

					System.out.println("Stop polling service...");  
			        PollingUtils.stopPollingService(LocationActivity.this, PollingService.class);
			        PollingService.isPolling = false;
					startLocation.setText(getString(R.string.startlocation));
				}
				
				
			}
		});
		onceLocate.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				InitLocation();
				System.out.println("Start polling once");  
				PollingUtils.PollingOnce(LocationActivity.this, PollingService.class);
				
			}
		});
		selectMode = (RadioGroup)findViewById(R.id.selectMode);
		selectCoordinates= (RadioGroup)findViewById(R.id.selectCoordinates);
		selectMode.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				// TODO Auto-generated method stub
				String ModeInformation = null;
				switch (checkedId) {
				case R.id.radio_hight:
					tempMode = 1;
					ModeInformation = getString(R.string.hight_accuracy_desc);
					break;
				case R.id.radio_low:
					tempMode = 2;
					ModeInformation = getString(R.string.saving_battery_desc);
					break;
				case R.id.radio_device:
					tempMode = 3;
					ModeInformation = getString(R.string.device_sensor_desc);
					break;
				default:
					break;
				}
				ModeInfor.setText(ModeInformation);
			}
		});
		ModeInfor.setText(getString(R.string.hight_accuracy_desc)); //隐藏了设置，故直接显示相关说明
		selectCoordinates.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				// TODO Auto-generated method stub
				switch (checkedId) {
				case R.id.radio_gcj02:
					tempcoor="gcj02";
					break;
				case R.id.radio_bd09ll:
					tempcoor="bd09ll";
					break;
				case R.id.radio_bd09:
					tempcoor="bd09";
					break;
				default:
					break;
				}
			}
		});
	}
	@Override
	protected void onStart() {
		super.onStart();
		checkGeoLocation.setChecked(sharedPref.getBoolean("NeedAddr", false));
		IntentFilter filter = new IntentFilter();    
		filter.addAction("LocationResult");
		mReceiver = new BroadcastReceiver() {    
            @Override    
            public void onReceive(Context context, Intent intent) {    
                if (intent.getAction().equals("LocationResult")) {
                	LabelTime.setText(intent.getExtras().getString("Time"));
                	switch(intent.getExtras().getInt("ErrCode")){
                	case 61:
                		errString = "成功。通过GPS定位";
                		break;
                	case 161:
                		errString = "成功。通过基站或WIFI定位";
                		break;
                	case 63:
                		errString = "定准失败。网络异常";
                	case 68:
                		errString = "成功。通过缓存获取定位信息";
                		break;
                	default:
                		errString = "失败。错误代码" + intent.getExtras().getInt("ErrCode");
                		break;
                	}
                	LabelErrcode.setText(errString);
                	double lng = intent.getExtras().getDouble("Longitude");
                	double lat = intent.getExtras().getDouble("Latitude");
                	LabelLatLng.setText( lng + "," + lat);
                	double gauss[] = CoordsTrans.ToGaussProj(lng, lat);
                	LabelGauss.setText(Math.round(gauss[0]) + "," + Math.round(gauss[1]));
                	LabelRadius.setText(Float.toString(intent.getExtras().getFloat("Radius")));
                	LocResult.setText(intent.getExtras().getString("LocationResult"));

                }   
            }    
        };    
		mLocalBroadcastManager.registerReceiver(mReceiver, filter);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.location, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle presses on the action bar items
	    switch (item.getItemId()) {
	        case R.id.action_switch_map:
	    		Intent intent = new Intent(this, MapActivity.class);
	    		startActivity(intent);
	    		//finish();
	            return true;
	        case R.id.action_settings:
	    		Intent setting = new Intent(this, SettingsActivity.class);
	    		startActivity(setting);
	            return true;

	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		mLocalBroadcastManager.unregisterReceiver(mReceiver);  
	}

	private void InitLocation(){
		
		sharedEditor.putInt("LocationMode", tempMode);
		sharedEditor.putString("CoorType", tempcoor);
				
		interval = Integer.parseInt(sharedPref.getString("sync_frequency", "2"));

		sharedEditor.putBoolean("NeedAddr", checkGeoLocation.isChecked());
		sharedEditor.apply();
	}
	
	private void startBlackService() {
		YunBaManager.start(getApplicationContext());
		
		IMqttActionListener listener = new IMqttActionListener() {
			
			@Override
			public void onSuccess(IMqttToken asyncActionToken) {
				Log.d("Yunba", "Subscribe succeeded");
			}
			
			@Override
			public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
				String msg =  "Subscribe failed : " + exception.getMessage();
				Log.d("Yunba", msg);
			}
		};
		
		String myDeviceId = ((TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
		YunBaManager.subscribe(getApplicationContext(), new String[]{"all", myDeviceId}, listener);
	}
}
