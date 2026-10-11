package org.rpwt.wlwdw.ui.screen

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rpwt.wlwdw.data.model.Report

/**
 * Which report the map is told to draw.
 *
 * This is the one part of the map screen a WebView cannot be asked about: the
 * page renders whatever it is handed, so "the marker is on the newest fix" and
 * "no history is drawn" are decisions made here, in Kotlin, over the report log.
 * Everything downstream -- the projection, the marker, the label -- belongs to
 * the website's JS and is exercised on a device.
 *
 * Two of these are the ones that would actually break: picking the wrong row
 * (the log is newest-first, and reading it in the wrong direction puts the map
 * on yesterday's position), and letting a row without a fix become `0,0`.
 */
class MapPayloadTest {

    private val title = "本机"

    /** A fixed zone, so the expected string does not depend on the test machine. */
    private val zone = ZoneId.of("Asia/Shanghai")

    /**
     * 2026-10-11 17:14:14 in the zone above.
     *
     * The instant matters because the formatted time does not: read in UTC this
     * same value is 09:14:14, which is the string the emulator showed, its
     * clock being UTC. The label is meant to be the reader's local time, so the
     * test pins a zone rather than the machine's.
     */
    private val now = 1791710054000L

    private fun at(lat: Double?, lng: Double?, millis: Long = now) =
        Report(at = millis, latitude = lat, longitude = lng)

    @Test
    fun `the newest report is the one sent`() {
        // Newest first, the order ReportRepository.observeAll() produces.
        val payload = mapPayload(
            reports = listOf(
                at(30.28, 120.135, now),
                at(30.2741, 120.1262, now - 60_000),
                at(31.2304, 121.4737, now - 120_000),
            ),
            title = title,
            zone = zone,
        )

        assertTrue(payload, payload.contains("\"lat\":30.28"))
        assertTrue(payload, payload.contains("\"lng\":120.135"))
        assertFalse("an older fix leaked into the payload: $payload", payload.contains("121.4737"))
    }

    @Test
    fun `a report with no fix is skipped, not sent as zero`() {
        val payload = mapPayload(
            reports = listOf(
                // The newest attempt failed before there was a fix.
                at(null, null, now),
                at(30.2741, 120.1262, now - 60_000),
            ),
            title = title,
            zone = zone,
        )

        assertTrue(payload, payload.contains("\"lat\":30.2741"))
        assertFalse("0,0 is in the Gulf of Guinea: $payload", payload.contains("\"lat\":0.0"))
    }

    @Test
    fun `one point, and no trail`() {
        val payload = mapPayload(
            reports = listOf(at(30.28, 120.135, now), at(30.2741, 120.1262, now - 60_000)),
            title = title,
            zone = zone,
        )

        // The shape is the assertion. A `trail` key here would be drawn as a
        // marker and a time label per point, which is what this build removed.
        assertFalse("the payload still carries history: $payload", payload.contains("trail"))
        assertEquals(1, Regex("\"lat\":").findAll(payload).count())
    }

    @Test
    fun `nothing to draw is a null pin`() {
        assertEquals(EMPTY_MAP_PAYLOAD, mapPayload(emptyList(), title, zone))
        // A log whose every row failed before a fix is the same case.
        assertEquals(EMPTY_MAP_PAYLOAD, mapPayload(listOf(at(null, null)), title, zone))
    }

    @Test
    fun `the label and the time come from the report`() {
        val payload = mapPayload(listOf(at(30.28, 120.135, now)), title, zone)

        // pointInfo() reads these two names; getting them wrong renders a blank
        // label rather than failing.
        assertTrue(payload, payload.contains("\"title\":\"$title\""))
        assertTrue(payload, payload.contains("\"date\":\"2026-10-11 17:14:14\""))
    }
}
