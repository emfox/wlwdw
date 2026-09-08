package org.rpwt.wlwdw;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * 进程内"最近一次成功定位"的唯一事实来源（bd09ll，与百度地图坐标系一致）。
 *
 * 写入方：真正的定位源（目前是 LocationActivity 的定位回调）；地图页等界面只读。
 * 更新时会补发一条本地广播 "LocationResult"（extra：Radius float、
 * BaiduLatitude/BaiduLongitude double），与 MapActivity 的订阅契约一致——
 * 即使地图页当时未打开也只是没人接收，无副作用。
 */
public final class LocationStore {

    private LocationStore() {
    }

    public static volatile boolean hasFix;
    public static volatile double latitude;
    public static volatile double longitude;
    public static volatile float radius;
    public static volatile long updatedAtMillis;

    /** 定位成功回调里调用：写入最新位置并向进程内广播。 */
    public static void update(Context context, double lat, double lng, float accuracyRadius) {
        latitude = lat;
        longitude = lng;
        radius = accuracyRadius;
        updatedAtMillis = System.currentTimeMillis();
        hasFix = true;

        Intent intent = new Intent("LocationResult");
        intent.putExtra("Radius", radius);
        intent.putExtra("BaiduLatitude", latitude);
        intent.putExtra("BaiduLongitude", longitude);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
