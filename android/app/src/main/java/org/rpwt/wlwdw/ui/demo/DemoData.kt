package org.rpwt.wlwdw.ui.demo

import org.rpwt.wlwdw.ui.model.DeviceStatus
import org.rpwt.wlwdw.ui.model.LinkState

/**
 * Fixed values for the stage 1 prototype.
 *
 * Everything here is invented. Stage 1 is the interface only, deliberately, so
 * that the layout can be reviewed without the data layer being able to hide
 * problems behind it. The data layer replaces this file; nothing else reads it,
 * and no other file should start to.
 */
object DemoData {

    // --- Status tab ---------------------------------------------------------

    /** The state the status screen renders. Change this to see the others. */
    val status: DeviceStatus = DeviceStatus.Reporting

    const val lastReport = "刚刚（12:04:31）"
    const val lastSuccessfulReport = "47 分钟前"

    /** Shown when tracking is off, so nothing is being sent at all. */
    const val lastReportStopped = "3 天前"

    const val address = "浙江省杭州市西湖区文三路 90 号"
    const val coordinates = "30.274105, 120.126254"
    const val accuracy = "±14 m"
    const val fixSource = "GPS"

    /**
     * Kept on screen when there is no fresh fix, so the user sees where the
     * device last was rather than an empty card.
     */
    const val staleAddress = "浙江省杭州市余杭区文一西路 969 号"
    const val staleCoordinates = "30.281340, 119.987210"
    const val staleAccuracy = "±120 m"
    const val staleFixSource = "网络定位"

    val uploadState: LinkState = LinkState.Ok
    val pushState: LinkState = LinkState.Ok

    // --- Map tab ------------------------------------------------------------

    const val mapFixAge = "刚刚 · ±14 m"

    data class Anchor(val name: String, val time: String)

    val anchors = listOf(
        Anchor("小明的手机", "12:01"),
        Anchor("家里", "11:47"),
    )

    // --- Messages tab -------------------------------------------------------

    data class Message(
        val sender: String,
        val body: String,
        val time: String,
        val unread: Boolean,
        val fromServer: Boolean,
    )

    val messagesToday = listOf(
        Message("妈妈", "到楼下了，下来帮我拿一下东西", "刚刚", unread = true, fromServer = false),
        Message("服务器", "设备定位已恢复上报", "12:04", unread = true, fromServer = true),
        Message("爸爸", "今天不用等我吃饭", "11:20", unread = false, fromServer = false),
    )

    val messagesYesterday = listOf(
        Message("服务器", "上报持续失败 3 次，请检查网络", "21:33", unread = false, fromServer = true),
    )

    /**
     * Derived, never a literal: the badge and the unread rows have to agree, and
     * a hand-kept count is the one thing that silently drifts out of step with
     * the list it summarises.
     */
    val unreadCount = (messagesToday + messagesYesterday).count { it.unread }

    // --- Settings tab -------------------------------------------------------

    const val deviceId = "3f2a9c14-8b7e-4d21-9c33-0a7f5b2e8d10"
    const val host = "wlwdw.rpwt.org"
    const val reportInterval = "每 5 分钟"
    const val version = "2.0.0 (1)"
    const val logCount = "3 条"
}
