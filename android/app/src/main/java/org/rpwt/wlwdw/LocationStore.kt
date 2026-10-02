package org.rpwt.wlwdw

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * 进程内"最近一次成功定位"的唯一事实来源（bd09ll，与百度地图坐标系一致）。
 *
 * 写入方：真正的定位源（目前是 LocationActivity 的定位回调）；地图页等界面只读。
 * 更新时会补发一条本地广播 "LocationResult"（extra：Radius float、
 * BaiduLatitude/BaiduLongitude double），与 MapActivity 的订阅契约一致——
 * 即使地图页当时未打开也只是没人接收，无副作用。
 *
 * 本文件是从 Java 迁到 Kotlin 的第一个（构建侧的 Kotlin 接线见
 * gradle/libs.versions.toml 与 app/build.gradle），刻意保持**对外零变化**，
 * 现有 Java 调用点一个字都不用改。因此：
 *
 * - 字段上的 [JvmField] + [Volatile] 都不是可选项。[JvmField] 让属性生成为
 *   真正的 public static 字段（否则 Java 侧得写 LocationStore.INSTANCE.getHasFix()），
 *   [Volatile] 保留原 Java 版的跨线程可见性。
 * - 方法上的 [JvmStatic] 同理，让 Java 侧维持静态调用 LocationStore.update(...)。
 *
 * 后续若要改造成 Flow（去掉 LocalBroadcastManager，见重构方案的 P1），
 * 注意 MapActivity / LocationActivity 两个订阅方要一起改。
 */
object LocationStore {

    @JvmField
    @Volatile
    var hasFix: Boolean = false

    @JvmField
    @Volatile
    var latitude: Double = 0.0

    @JvmField
    @Volatile
    var longitude: Double = 0.0

    @JvmField
    @Volatile
    var radius: Float = 0f

    @JvmField
    @Volatile
    var updatedAtMillis: Long = 0L

    /** 定位成功回调里调用：写入最新位置并向进程内广播。 */
    @JvmStatic
    fun update(context: Context, lat: Double, lng: Double, accuracyRadius: Float) {
        latitude = lat
        longitude = lng
        radius = accuracyRadius
        updatedAtMillis = System.currentTimeMillis()
        hasFix = true

        val intent = Intent("LocationResult")
        intent.putExtra("Radius", radius)
        intent.putExtra("BaiduLatitude", latitude)
        intent.putExtra("BaiduLongitude", longitude)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}
