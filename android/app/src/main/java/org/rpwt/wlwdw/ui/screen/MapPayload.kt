package org.rpwt.wlwdw.ui.screen

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.rpwt.wlwdw.data.model.Report

/**
 * The map's data, and the rule that picks it.
 *
 * Kept out of `MapScreen` so it can be tested: which report counts as "where
 * this device is" is pure logic over the report log, and it is the one part of
 * the map that a WebView cannot be asked about. The screen keeps the view; this
 * keeps the decision.
 *
 * The field names are the website's on purpose -- `pointInfo()` reads `title`
 * and `updatetime.date` -- so the page needs no translation layer.
 *
 * One field, not two. An earlier version also sent the history as a `trail`,
 * and the map drew a marker and a time label per point, which is what the
 * website does, because there the map answers "where has this unit been". This
 * app's map answers a narrower question, "where is this phone now", and drawing
 * the history as well meant extra overlays on the SDK, extra labels over the
 * map, and a reader who has to work out which pin is the live one. The report
 * log still holds the history; the diagnostics page is where it is read.
 */
@Serializable
internal data class MapPayload(val me: MapPin? = null)

@Serializable
internal data class MapPin(
    val title: String,
    val lat: Double,
    val lng: Double,
    val updatetime: MapTime,
)

@Serializable
internal data class MapTime(val date: String)

/** What the page is sent when there is nothing to draw; its starting value too. */
internal const val EMPTY_MAP_PAYLOAD = """{"me":null}"""

private val json = Json { encodeDefaults = true }

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * Turn the report log into the page's payload: the newest position, nothing else.
 *
 * [reports] is newest-first, so the first row that has coordinates is where this
 * device is now. Rows without coordinates are skipped rather than sent as zeros:
 * an attempt that failed before there was a fix has no position, and `0,0` is in
 * the Gulf of Guinea. That is also the rule the website's `placed()` applies.
 *
 * [zone] is a parameter rather than a read of the default so that a test can
 * pin the formatted time instead of depending on where the machine is.
 */
internal fun mapPayload(
    reports: List<Report>,
    title: String,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val newest = reports.firstOrNull { it.latitude != null && it.longitude != null }
        ?: return EMPTY_MAP_PAYLOAD

    return json.encodeToString(
        MapPayload(
            me = MapPin(
                title = title,
                lat = newest.latitude!!,
                lng = newest.longitude!!,
                updatetime = MapTime(
                    Instant.ofEpochMilli(newest.at).atZone(zone).format(timeFormat),
                ),
            ),
        ),
    )
}
