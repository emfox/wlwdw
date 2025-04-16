package com.wlwdw.gps1s;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.LocationClientOption.LocationMode;
import com.baidu.mapapi.model.LatLng;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PollingService extends Service {  
	  
    public static final String ACTION_ONCE = "com.wlwdw.gps1s.PollingOnce";
    public static final String ACTION_START = "com.wlwdw.gps1s.StartPollingService";
    public static final String ACTION_STOP = "com.wlwdw.gps1s.StopPollingService";
    public static boolean isPolling = false;
	public MyLocationListener mMyLocationListener;
	
    private LocationClient mLocationClient; 
    private LocationClientOption mLCoption;
    private SharedPreferences sharedPref;
    private LocalBroadcastManager mLocalBroadcastManager;
    private BDLocation mLocation;
    private LatLng wgs;
    private static boolean should_stop = false;
    
    
    @Override  
    public IBinder onBind(Intent intent) {  
        return null;  
    }  
  
    @Override  
    public void onCreate() {
    	sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    	
    	mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        try {
            mLocationClient = new LocationClient(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mMyLocationListener = new MyLocationListener();
		mLocationClient.registerLocationListener(mMyLocationListener);
        mLCoption = new LocationClientOption();
		setNewLocOption();
        mLocationClient.start();
        
    }  
      
    @Override  
    public int onStartCommand(Intent intent, int flags, int startId) {
    	if(intent.getAction().equals(PollingService.ACTION_STOP)){
    		System.out.println(intent.getAction());
    		isPolling = false;
    		stopSelf();
    		return START_REDELIVER_INTENT;
    	}
    	setNewLocOption();
    	mLocationClient.requestLocation();
    	System.out.println("Polling...");  
    	if(intent.getAction().equals(PollingService.ACTION_ONCE)){
    		System.out.println(intent.getAction());
    		if(!isPolling){
    			should_stop = true;
    		}
    		return START_REDELIVER_INTENT;
    	}
    	isPolling = true;
		return START_REDELIVER_INTENT;
    }  

      
    @Override  
    public void onDestroy() {  
        super.onDestroy();  
        System.out.println("Service:onDestroy");  
        mLocationClient.stop();
    }  
    
    public void setNewLocOption()
    {
        switch(sharedPref.getInt("LocationMode", 1)){
        case 1:
        	mLCoption.setLocationMode(LocationMode.Hight_Accuracy);
        	break;
        case 2:
        	mLCoption.setLocationMode(LocationMode.Battery_Saving);
        	break;
        case 3:
        	mLCoption.setLocationMode(LocationMode.Device_Sensors);
        	break;
        }
        mLCoption.setIsNeedAddress(sharedPref.getBoolean("NeedAddr", false));
        System.out.println(sharedPref.getBoolean("NeedAddr", false));
        mLCoption.setCoorType(sharedPref.getString("CoorType", "bd09ll"));
		mLocationClient.setLocOption(mLCoption);
    }
	/**
	 * 实现实时回调监听
	 */
	public class MyLocationListener implements BDLocationListener {

		@Override
		public void onReceiveLocation(BDLocation location) {
			//Receive Location 
			mLocation = location;
			StringBuffer sb = new StringBuffer(256);

			if (location.getLocType() == BDLocation.TypeGpsLocation){
				sb.append("\n速度 : ");
				sb.append(location.getSpeed());
				sb.append("\n卫星 : ");
				sb.append(location.getSatelliteNumber());
				sb.append("\n方向 : ");
				sb.append(location.getDirection());
				sb.append("\n地址 : ");
				sb.append(location.getAddrStr());

			} else if (location.getLocType() == BDLocation.TypeNetWorkLocation){
				sb.append("\n地址 : ");
				sb.append(location.getAddrStr());
				sb.append("\n移动运营商 : ");
				sb.append(location.getOperators());
			}
			Intent intent = new Intent();
			intent.setAction("LocationResult");
			intent.putExtra("LocationResult", sb.toString());
			intent.putExtra("ErrCode", location.getLocType());
			intent.putExtra("Time", location.getTime());
			intent.putExtra("BaiduLongitude", location.getLongitude());
			intent.putExtra("BaiduLatitude", location.getLatitude());
			wgs = CoordsTrans.bd2wgs(new LatLng(location.getLatitude(),location.getLongitude()));
			intent.putExtra("Longitude", wgs.longitude);
			intent.putExtra("Latitude", wgs.latitude);
			intent.putExtra("Radius", location.getRadius());
			mLocalBroadcastManager.sendBroadcast(intent);
			Log.i("BaiduLocationOutput", sb.toString());

			new Thread(new Runnable(){
			    @Override
			    public void run() {
			        // 拼凑get请求的URL字串，使用URLEncoder.encode对特殊和不可见字符进行编码
			    	String custom_host = "www.wlwdw.com";
			    	if(sharedPref.getBoolean("enable_custom_host",false))
			    		custom_host = sharedPref.getString("custom_host","www.wlwdw.com");
					String appUUID = sharedPref.getString("app_uuid",null);
			    	String GET_URL = "https://" + custom_host  + "/trail/new/" + appUUID + "/"
			    			+ Double.toString(wgs.longitude) + "/" + Double.toString(wgs.latitude);
			    	try {
						readContentFromGet(GET_URL);
					} catch (IOException e) {
						// TODO 自动生成的 catch 块
						e.printStackTrace();
					}
			    }
			}).start();
			if(should_stop){
				should_stop = false;
				stopSelf();
			}
			}
		}

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
            System.out.println(line);
            sb.append(line);
        }
        reader.close();
        // 断开连接
        connection.disconnect();
        return sb.toString();
    }

}  