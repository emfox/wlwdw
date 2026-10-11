package org.rpwt.wlwdw.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a failure looks like once it is written down.
 *
 * The codes are in the `report` table on devices that are already running this
 * build, so they are a contract with the app's own past: renaming a Kotlin case
 * is free, and changing one of these strings orphans every row written before
 * it. That is the whole reason [ReportFailure.code] is not an enum name.
 */
class ReportFailureTest {

    @Test
    fun `every failure has a stable code`() {
        assertEquals("permission", ReportFailure.Permission.code)
        assertEquals("nofix", ReportFailure.NoFix.code)
        assertEquals("unregistered", ReportFailure.Unregistered.code)
        assertEquals("refused", ReportFailure.Refused("坐标超出范围").code)
        assertEquals("upload", ReportFailure.Upload("timeout").code)
    }

    @Test
    fun `only the two failures that carry text keep a detail`() {
        assertEquals("坐标超出范围", ReportFailure.Refused("坐标超出范围").detail)
        assertEquals("timeout", ReportFailure.Upload("timeout").detail)
        assertNull(ReportFailure.Permission.detail)
        assertNull(ReportFailure.NoFix.detail)
        assertNull(ReportFailure.Unregistered.detail)
    }

    /** The server is not obliged to send a message, and losing the code would
     *  turn a refusal into an unexplained blank. */
    @Test
    fun `a refusal with no message is still a refusal`() {
        assertEquals("refused", ReportFailure.Refused(null).code)
        assertNull(ReportFailure.Refused(null).detail)
    }

    /**
     * Everything that can be written down can be read back.
     *
     * This is what keeps [ReportFailure.of] and [ReportFailure.code] in step.
     * The codes are already on disk, so the failure mode is asymmetric: adding
     * a case is free and forgetting to teach `of` about it turns every one of
     * its rows into "unknown" the next time they are read.
     */
    @Test
    fun `every failure survives a round trip through the log`() {
        val written = listOf(
            ReportFailure.Permission,
            ReportFailure.NoFix,
            ReportFailure.Unregistered,
            ReportFailure.Refused("坐标超出范围"),
            ReportFailure.Refused(null),
            ReportFailure.Upload("timeout"),
        )
        written.forEach { failure ->
            assertEquals(failure, ReportFailure.of(failure.code, failure.detail))
        }
    }

    /**
     * A code this build does not know reads as a failure, never as success.
     *
     * Null on a `Report` means the server accepted the position, so anything
     * this build cannot place has to come back as a failure that says so:
     * showing a stranding failure as a delivered position is worse than
     * showing nothing.
     */
    @Test
    fun `an unrecognised code is a failure, not an accepted report`() {
        val failure = ReportFailure.of("rate-limited", "慢一点")
        assertEquals(ReportFailure.Unrecognized("rate-limited", "慢一点"), failure)
        assertEquals("rate-limited", failure.code)
        assertEquals("慢一点", failure.detail)
    }

    /** The `detail` column is nullable, and a row that lost its reason still
     *  has to read back as the kind of failure it was. */
    @Test
    fun `a stored row with no detail keeps its kind`() {
        assertEquals(ReportFailure.Upload(""), ReportFailure.of("upload", null))
        assertEquals(ReportFailure.Refused(null), ReportFailure.of("refused", null))
    }
}
