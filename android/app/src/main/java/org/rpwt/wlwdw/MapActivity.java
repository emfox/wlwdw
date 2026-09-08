package org.rpwt.wlwdw;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;


public class MapActivity extends AppCompatActivity {

	MapView mMapView = null;
	BaiduMap mBaiduMap;
	private SharedPreferences sharedPref;
	private Editor sharedEditor;
	private LatLng centerLatLng;
	private float zoom;
	private MapStatus mMapStatus;
	private LocalBroadcastManager mLocalBroadcastManager;
	private BroadcastReceiver mReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);  
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		sharedEditor = sharedPref.edit();

		// SDK 初始化统一走 Application（幂等、且尊重隐私同意状态），避免重复 initialize。
		((LocationApplication) getApplication()).ensureBaiduSdkInitialized(this);

		setContentView(R.layout.map);
		mMapView = (MapView) findViewById(R.id.bmapView);
		mBaiduMap = mMapView.getMap();
		mBaiduMap.setMyLocationEnabled(true); 
        mBaiduMap.setOnMapStatusChangeListener(new BaiduMap.OnMapStatusChangeListener()
        {

            @Override
            public void onMapStatusChangeFinish(MapStatus arg0)
            {

                    centerLatLng = mBaiduMap.getMapStatus().target;
                    zoom = mBaiduMap.getMapStatus().zoom;
            }

			@Override
			public void onMapStatusChange(MapStatus arg0) {
				// TODO 自动生成的方法存根
				
			}

			@Override
			public void onMapStatusChangeStart(MapStatus arg0) {
				// TODO 自动生成的方法存根
				
			}

			@Override
			public void onMapStatusChangeStart(MapStatus mapStatus, int i) {

			}
		});
        
		//恢复地图中心点和缩放等级
		centerLatLng = new LatLng(getDouble(sharedPref,"CenterLat",29.3),getDouble(sharedPref,"CenterLng",117.5));
		zoom = sharedPref.getFloat("zoom",11);
		mMapStatus = new MapStatus.Builder()
		.target(centerLatLng)
		.zoom(zoom).build();
		MapStatusUpdate mMapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mMapStatus);
		mBaiduMap.setMapStatus(mMapStatusUpdate);

		// 蓝点只画"真实拿到过的定位"：无 fix 则不画（也不显示编造的默认坐标）。
		if (LocationStore.hasFix) {
			applyMyLocation(LocationStore.latitude, LocationStore.longitude, LocationStore.radius);
		}
	}

	private void applyMyLocation(double lat, double lng, float accuracyRadius) {
		MyLocationData locData = new MyLocationData.Builder()
				.accuracy(accuracyRadius)
				// 此处设置开发者获取到的方向信息，顺时针0-360（无罗盘数据，固定值）
				.direction(100)
				.latitude(lat)
				.longitude(lng).build();
		mBaiduMap.setMyLocationData(locData);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// 在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
		mMapView.onDestroy();
		// 只记忆地图视野（中心点/缩放）；蓝点坐标统一由 LocationStore 提供，不再回存。
		sharedEditor.putFloat("zoom", zoom);
		putDouble(sharedEditor,"CenterLat",centerLatLng.latitude);
		putDouble(sharedEditor,"CenterLng",centerLatLng.longitude);
		sharedEditor.apply();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// 在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
		mMapView.onResume();

	}

	@Override
	protected void onPause() {
		super.onPause();
		// 在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
		mMapView.onPause();

	}

	@Override
	protected void onStart() {
		super.onStart();
		
		//设置当前坐标更新 Receiver
		IntentFilter filter = new IntentFilter();    
		filter.addAction("LocationResult");
		mReceiver = new BroadcastReceiver() {    
            @Override    
            public void onReceive(Context context, Intent intent) {    
                if (intent.getAction().equals("LocationResult")) {
                	// 定位源(LocationActivity)每次成功定位都会广播，蓝点实时跟随。
                	applyMyLocation(
                			intent.getExtras().getDouble("BaiduLatitude"),
                			intent.getExtras().getDouble("BaiduLongitude"),
                			intent.getExtras().getFloat("Radius"));
                }   
            }    
        };
        //注册当前坐标更新 Receiver
		mLocalBroadcastManager.registerReceiver(mReceiver, filter);
		


	}

	@Override
	protected void onStop() {
		super.onStop();
		mLocalBroadcastManager.unregisterReceiver(mReceiver); 

	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.map, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
	    switch (item.getItemId()) {
        case R.id.action_back_to_cui:
        	//Intent intent = new Intent(this, LocationActivity.class);
        	//startActivity(intent);
    		finish();
            return true;
        case R.id.action_current_location:
        	jumpCurLocation();
            return true;
        case R.id.action_settings:
    		Intent setting = new Intent(this, SettingsActivity.class);
    		startActivity(setting);
            return true;

        default:
            return super.onOptionsItemSelected(item);
            }
	    }

	public void jumpCurLocation(){
		//跳到当前坐标所在的地图中心点（读单一来源 LocationStore）
		if (!LocationStore.hasFix) {
			Toast.makeText(this, "尚无定位数据", Toast.LENGTH_SHORT).show();
			return;
		}
		centerLatLng = new LatLng(LocationStore.latitude, LocationStore.longitude);
		mMapStatus = new MapStatus.Builder()
		.target(centerLatLng).build();
		MapStatusUpdate mMapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mMapStatus);
		mBaiduMap.setMapStatus(mMapStatusUpdate);
	}
	public Editor putDouble(final Editor edit, final String key, final double value) {
		   return edit.putLong(key, Double.doubleToRawLongBits(value));
		}

	public double getDouble(final SharedPreferences prefs, final String key, final double defaultValue) {
		return Double.longBitsToDouble(prefs.getLong(key, Double.doubleToLongBits(defaultValue)));
		}
	}


