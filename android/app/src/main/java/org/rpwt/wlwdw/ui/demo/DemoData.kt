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

    /**
     * Gone from here: the message list now comes from the Room store
     * (`data/MessageRepository`), and the unread count from that same list. The
     * inbox is empty until the push pipeline lands, which is what the empty
     * state on that screen is for.
     */

    // --- Settings tab -------------------------------------------------------

    /**
     * The settings screen's own values -- device id, host, reporting interval,
     * version -- are no longer here: they come from DataStore (device id, host,
     * interval) or from the build itself (version). See
     * `data/prefs/PreferencesRepository`.
     */
    const val logCount = "3 条"
}
