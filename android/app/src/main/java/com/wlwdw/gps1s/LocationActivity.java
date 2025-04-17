package com.wlwdw.gps1s;


import io.yunba.android.manager.YunBaManager;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.model.LatLng;
import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.Poi;
import com.baidu.location.PoiRegion;
import com.wlwdw.gps1s.service.LocService;

import android.app.Notification;
import android.app.PendingIntent;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;

import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class LocationActivity extends AppCompatActivity {
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
	private NotificationUtils mNotificationUtils;
	private Notification notification;
	private String errString;
	private Integer interval=2; 
	private Integer tempMode = 1;
	private String tempcoor="bd09ll";
	private CheckBox checkGeoLocation;

	private LocService locService;
	private boolean isLocPolling;
	private LatLng wgs;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		sharedEditor = sharedPref.edit();
		if(sharedPref.getString("app_uuid", "").isEmpty()) {
			sharedEditor.putString("app_uuid",UUID.randomUUID().toString());
		}
		//startBlackService();
		setContentView(R.layout.location);
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

		//设置后台定位
		//android8.0及以上使用NotificationUtils
		if (Build.VERSION.SDK_INT >= 26) {
			mNotificationUtils = new NotificationUtils(this);
			Notification.Builder builder2 = mNotificationUtils.getAndroidChannelNotification
					("适配android 8限制后台定位功能", "正在后台定位");
			notification = builder2.build();
		} else {
			//获取一个Notification构造器
			Notification.Builder builder = new Notification.Builder(LocationActivity.this);
			Intent nfIntent = new Intent(LocationActivity.this, LocationActivity.class);

			builder.setContentIntent(PendingIntent.
							getActivity(LocationActivity.this, 0, nfIntent, PendingIntent.FLAG_IMMUTABLE)) // 设置PendingIntent
					.setContentTitle("适配android 8限制后台定位功能") // 设置下拉列表里的标题
					.setSmallIcon(R.drawable.ic_launcher) // 设置状态栏内的小图标
					.setContentText("正在后台定位") // 设置上下文内容
					.setWhen(System.currentTimeMillis()); // 设置该通知发生的时间

			notification = builder.build(); // 获取构建好的Notification
		}
		notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音

		locService = ((LocationApplication) getApplication()).locService;
		LocationClientOption mOption = locService.getOption();
		mOption.setIsNeedAddress(sharedPref.getBoolean("NeedAddr", false));
		mOption.setCoorType(sharedPref.getString("CoorType", "bd09ll"));
		//FIXME: set ANY option cause API error with code 162.
		//LocService.setLocationOption(mOption);
		boolean isRegSuccess = locService.registerListener(mListener);

		checkGeoLocation.setChecked(sharedPref.getBoolean("NeedAddr", false));
		startLocation.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!isLocPolling) {
					locService.getClient().enableLocInForeground(1, notification);
					locService.start();
					startLocation.setText(getString(R.string.stoplocation));
					isLocPolling = true;
				} else {
					locService.getClient().disableLocInForeground(true);
					locService.stop();
					startLocation.setText(getString(R.string.startlocation));
					isLocPolling = false;
				}
			}
		});

		onceLocate.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(isLocPolling){
					locService.requestLocation();
				}else {
					LocationClientOption mOption = locService.getOption();
					mOption.setOnceLocation(true);
					//FIXME: set ANY option cause API error with code 162.
					//LocService.setLocationOption(mOption);
					locService.start();
				}
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
	protected void onDestroy(){
        super.onDestroy();
		locService.unregisterListener(mListener);
		if(isLocPolling) {
			locService.getClient().disableLocInForeground(true);
			locService.stop();
		}


    }
	@Override
	protected void onStart() {
		super.onStart();

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
	    	case R.id.action_switch_message:
	    		Intent message = new Intent(this, MessageActivity.class);
	    		startActivity(message);
	    		//finish();
	            return true;
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
		
		String appUUID = sharedPref.getString("app_uuid","");
		YunBaManager.subscribe(getApplicationContext(), new String[]{"all", appUUID}, listener);
	}

	private final BDAbstractLocationListener mListener = new BDAbstractLocationListener() {

		/**
		 * 定位请求回调函数
		 * @param location 定位结果
		 */
		@Override
		public void onReceiveLocation(BDLocation location) {

			if (null != location && location.getLocType() != BDLocation.TypeServerError) {
				int tag = 1;
				StringBuffer sb = new StringBuffer(256);
				sb.append("\nlocType description : ");// *****对应的定位类型说明*****
				sb.append(location.getLocTypeDescription());
				sb.append("\naddr : ");// 地址信息
				sb.append(location.getAddrStr());
				sb.append("\nStreetNumber : ");// 获取街道号码
				sb.append(location.getStreetNumber());
				sb.append("\nUserIndoorState: ");// *****返回用户室内外判断结果*****
				sb.append(location.getUserIndoorState());
				sb.append("\nDirection(not all devices have value): ");
				sb.append(location.getDirection());// 方向
				sb.append("\nlocationdescribe: ");
				sb.append(location.getLocationDescribe());// 位置语义化信息
				sb.append("\nPoi: ");// POI信息
				if (location.getPoiList() != null && !location.getPoiList().isEmpty()) {
					for (int i = 0; i < location.getPoiList().size(); i++) {
						Poi poi = (Poi) location.getPoiList().get(i);
						sb.append("poiName:");
						sb.append(poi.getName() + ", ");
						sb.append("poiTag:");
						sb.append(poi.getTags() + "\n");
					}
				}
				if (location.getPoiRegion() != null) {
					sb.append("PoiRegion: ");// 返回定位位置相对poi的位置关系，仅在开发者设置需要POI信息时才会返回，在网络不通或无法获取时有可能返回null
					PoiRegion poiRegion = location.getPoiRegion();
					sb.append("DerectionDesc:"); // 获取POIREGION的位置关系，ex:"内"
					sb.append(poiRegion.getDerectionDesc() + "; ");
					sb.append("Name:"); // 获取POIREGION的名字字符串
					sb.append(poiRegion.getName() + "; ");
					sb.append("Tags:"); // 获取POIREGION的类型
					sb.append(poiRegion.getTags() + "; ");
					sb.append("\nSDK版本: ");
				}
				sb.append(locService.getSDKVersion()); // 获取SDK版本
				int permission = checkPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION);
				sb.append("\npermsission: " + permission);
				if (location.getLocType() == BDLocation.TypeGpsLocation) {// GPS定位结果
					sb.append("\nspeed : ");
					sb.append(location.getSpeed());// 速度 单位：km/h
					sb.append("\nsatellite : ");
					sb.append(location.getSatelliteNumber());// 卫星数目
					sb.append("\nheight : ");
					sb.append(location.getAltitude());// 海拔高度 单位：米
					sb.append("\ngps status : ");
					sb.append(location.getGpsAccuracyStatus());// *****gps质量判断*****
					sb.append("\ndescribe : ");
					sb.append("gps定位成功");
				} else if (location.getLocType() == BDLocation.TypeNetWorkLocation) {// 网络定位结果
					// 运营商信息
					if (location.hasAltitude()) {// *****如果有海拔高度*****
						sb.append("\nheight : ");
						sb.append(location.getAltitude());// 单位：米
					}
					sb.append("\noperationers : ");// 运营商信息
					sb.append(location.getOperators());
					sb.append("\ndescribe : ");
					sb.append("网络定位成功");
				} else if (location.getLocType() == BDLocation.TypeOffLineLocation) {// 离线定位结果
					sb.append("\ndescribe : ");
					sb.append("离线定位成功，离线定位结果也是有效的");
				} else if (location.getLocType() == BDLocation.TypeServerError) {
					sb.append("\ndescribe : ");
					sb.append("服务端网络定位失败，可以反馈IMEI号和大体定位时间到loc-bugs@baidu.com，会有人追查原因");
				} else if (location.getLocType() == BDLocation.TypeNetWorkException) {
					sb.append("\ndescribe : ");
					sb.append("网络不同导致定位失败，请检查网络是否通畅");
				} else if (location.getLocType() == BDLocation.TypeCriteriaException) {
					sb.append("\ndescribe : ");
					sb.append("无法获取有效定位依据导致定位失败，一般是由于手机的原因，处于飞行模式下一般会造成这种结果，可以试着重启手机");
				}
				System.out.println(sb.toString());

				LabelTime.setText(location.getTime());
				switch(location.getLocType()){
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
						errString = "失败。错误代码" + location.getLocType();
						break;
				}
				LabelErrcode.setText(errString);
				wgs = CoordsTrans.bd2wgs(new LatLng(location.getLatitude(),location.getLongitude()));
				double lng = wgs.longitude;
				double lat = wgs.latitude;
				LabelLatLng.setText( lng + "," + lat );
				double gauss[] = CoordsTrans.ToGaussProj(lng, lat);
				LabelGauss.setText(Math.round(gauss[0]) + "," + Math.round(gauss[1]));
				LabelRadius.setText(Float.toString(location.getRadius()));
				LocResult.setText(sb.toString());

				new Thread(new Runnable(){
					@Override
					public void run() {
						// 拼凑get请求的URL字串，使用URLEncoder.encode对特殊和不可见字符进行编码
						String custom_host = getString(R.string.pref_default_custom_host);
						if(sharedPref.getBoolean("enable_custom_host",false))
							custom_host = sharedPref.getString("custom_host",custom_host);
						String appUUID = sharedPref.getString("app_uuid",null);
						String GET_URL = "https://" + custom_host  + "/trail/new/" + appUUID + "/"
								+ Double.toString(wgs.longitude) + "/" + Double.toString(wgs.latitude);
						try {
							String r = readContentFromGet(GET_URL);
							System.out.println(r);
						} catch (IOException e) {
							// TODO 自动生成的 catch 块
							e.printStackTrace();
						}
					}
				}).start();
			}
		}

		@Override
		public void onConnectHotSpotMessage(String s, int i) {
			super.onConnectHotSpotMessage(s, i);
		}

		/**
		 * 回调定位诊断信息，开发者可以根据相关信息解决定位遇到的一些问题
		 * @param locType 当前定位类型
		 * @param diagnosticType 诊断类型（1~9）
		 * @param diagnosticMessage 具体的诊断信息释义
		 */
		@Override
		public void onLocDiagnosticMessage(int locType, int diagnosticType, String diagnosticMessage) {
			super.onLocDiagnosticMessage(locType, diagnosticType, diagnosticMessage);
			int tag = 2;
			StringBuffer sb = new StringBuffer(256);
			sb.append("诊断结果: ");
			if (locType == BDLocation.TypeNetWorkLocation) {
				if (diagnosticType == 1) {
					sb.append("网络定位成功，没有开启GPS，建议打开GPS会更好");
					sb.append("\n" + diagnosticMessage);
				} else if (diagnosticType == 2) {
					sb.append("网络定位成功，没有开启Wi-Fi，建议打开Wi-Fi会更好");
					sb.append("\n" + diagnosticMessage);
				}
			} else if (locType == BDLocation.TypeOffLineLocationFail) {
				if (diagnosticType == 3) {
					sb.append("定位失败，请您检查您的网络状态");
					sb.append("\n" + diagnosticMessage);
				}
			} else if (locType == BDLocation.TypeCriteriaException) {
				if (diagnosticType == 4) {
					sb.append("定位失败，无法获取任何有效定位依据");
					sb.append("\n" + diagnosticMessage);
				} else if (diagnosticType == 5) {
					sb.append("定位失败，无法获取有效定位依据，请检查运营商网络或者Wi-Fi网络是否正常开启，尝试重新请求定位");
					sb.append(diagnosticMessage);
				} else if (diagnosticType == 6) {
					sb.append("定位失败，无法获取有效定位依据，请尝试插入一张sim卡或打开Wi-Fi重试");
					sb.append("\n" + diagnosticMessage);
				} else if (diagnosticType == 7) {
					sb.append("定位失败，飞行模式下无法获取有效定位依据，请关闭飞行模式重试");
					sb.append("\n" + diagnosticMessage);
				} else if (diagnosticType == 9) {
					sb.append("定位失败，无法获取任何有效定位依据");
					sb.append("\n" + diagnosticMessage);
				}
			} else if (locType == BDLocation.TypeServerError) {
				if (diagnosticType == 8) {
					sb.append("定位失败，请确认您定位的开关打开状态，是否赋予APP定位权限");
					sb.append("\n" + diagnosticMessage);
				}
			}
			System.out.println(sb.toString());
		}
	};
	public static String readContentFromGet(String getURL) throws IOException {
		URL getUrl = new URL(getURL);
		// 根据拼凑的URL，打开连接，URL.openConnection函数会根据URL的类型，
		// 返回不同的URLConnection子类的对象，这里URL是一个http，因此实际返回的是HttpURLConnection
		HttpURLConnection connection = (HttpURLConnection) getUrl
				.openConnection();
		// 进行连接，但是实际上get request要在下一句的connection.getInputStream()函数中才会真正发到
		// 服务器
		connection.connect();
		// 取得输入流，并使用Reader读取
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				connection.getInputStream()));
		StringBuffer sb = new StringBuffer(256);
		String line;
		while ((line = reader.readLine()) != null) {
			sb.append(line);
		}
		reader.close();
		// 断开连接
		connection.disconnect();
		return sb.toString();
	}
	public static int checkPermission(Context context, String permission) {

		boolean allowedByPermission = true;
		try {
			int result = context.checkPermission(permission, android.os.Process.myPid(), android.os.Process.myUid());
			allowedByPermission = (result == PackageManager.PERMISSION_GRANTED);
		} catch (Exception e) {
			allowedByPermission = true;
		}
		if (!allowedByPermission) {
			return 0;
		} else {
			return 1;
		}
	}
}
