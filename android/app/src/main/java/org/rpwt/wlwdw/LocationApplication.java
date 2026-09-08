package org.rpwt.wlwdw;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.common.BaiduMapSDKException;

import org.rpwt.wlwdw.service.LocService;
import org.rpwt.wlwdw.service.Utils;

/**
 * 主Application。百度地图/定位 SDK 涉及隐私合规：所有 SDK 接口必须先取得
 * 用户同意（MainActivity 的隐私弹窗，结果存于 SP_PRIVACY_STATUS）再调用，
 * 因此这里不再无条件初始化，而是只在"已同意"记录存在时惰性初始化。
 */
public class LocationApplication extends Application {
	public LocService locService;
	private volatile boolean baiduSdkInitialized = false;

	@Override
	public void onCreate() {
		super.onCreate();
		// 已同意(SP_PRIVACY_STATUS=1)时初始化；未选择/拒绝时不初始化，
		// 直到 MainActivity 隐私弹窗同意后再由 ensureBaiduSdkInitialized() 补上。
		ensureBaiduSdkInitialized(this);
	}

	/**
	 * 幂等初始化百度地图 SDK。可从任意入口调用（普通 Activity 启动、进程被杀后
	 * 经推送通知/START_STICKY 重建等）：已初始化直接返回；未取得隐私同意则跳过。
	 */
	public synchronized void ensureBaiduSdkInitialized(Context context) {
		if (baiduSdkInitialized) {
			return;
		}
		if (!Utils.getString(context, Utils.SP_PRIVACY_STATUS).equals("1")) {
			Log.w("LocationApp", "Baidu SDK not initialized: user has not agreed to the privacy policy");
			return;
		}
		SDKInitializer.setAgreePrivacy(context, true);
		try {
			SDKInitializer.initialize(context.getApplicationContext());
		} catch (BaiduMapSDKException e) {
			e.printStackTrace();
		}
		SDKInitializer.setCoordType(CoordType.BD09LL);
		baiduSdkInitialized = true;
	}
}
