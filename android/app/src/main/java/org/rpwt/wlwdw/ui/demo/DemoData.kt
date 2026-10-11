package org.rpwt.wlwdw.ui.demo

/**
 * Fixed values for what is still a prototype.
 *
 * Everything here is invented. What has been deleted from this file is as
 * informative as what is left: the status matrix, the last-report times and the
 * coordinates all come from `tracking/ReportingEngine` now, the message list
 * and the settings from the data layer, and the settings screen's log row from
 * the report log. Only the map, which has no tile source yet, still stands in.
 */
object DemoData {

    // --- Map tab ------------------------------------------------------------

    /** The last fix's age and precision, as the map's bottom sheet shows it. */
    const val mapFixAge = "刚刚 · ±14 m"

    const val address = "浙江省杭州市西湖区文三路 90 号"

    data class Anchor(val name: String, val time: String)

    val anchors = listOf(
        Anchor("小明的手机", "12:01"),
        Anchor("家里", "11:47"),
    )
}
