package com.wlwdw.gps1s;


import android.app.Application;
import android.app.Service;
import android.os.Vibrator;

import com.wlwdw.gps1s.service.LocService;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.common.BaiduMapSDKException;

/**
 * 主Application，所有百度定位SDK的接口说明请参考线上文档：http://developer.baidu.com/map/loc_refer/index.html
 *
 * 百度定位SDK官方网站：http://developer.baidu.com/map/index.php?title=android-locsdk
 * 
 * 直接拷贝com.baidu.location.service包到自己的工程下，简单配置即可获取定位结果，也可以根据demo内容自行封装
 */
public class LocationApplication extends Application {
	public LocService locService;
    public Vibrator mVibrator;
    @Override
    public void onCreate() {
        super.onCreate();

        // 默认本地个性化地图初始化方法
        SDKInitializer.setAgreePrivacy(this, true);
        try {
            SDKInitializer.initialize(this);
        } catch (BaiduMapSDKException e) {
            e.printStackTrace();
        }
        SDKInitializer.setCoordType(CoordType.BD09LL);

        mVibrator =(Vibrator)getApplicationContext().getSystemService(Service.VIBRATOR_SERVICE);
    }
}
