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
}
