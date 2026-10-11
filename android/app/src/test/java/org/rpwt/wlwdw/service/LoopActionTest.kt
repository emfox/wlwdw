package org.rpwt.wlwdw.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When the service starts and stops the reporting loop.
 *
 * Worth a test because the wrong answer is invisible in the UI and only shows up in a log
 * on a device: a loop that ended on its own leaves the intent true and nothing running, so
 * a rule that reads "not running, and tracking is wanted" starts it again on every later
 * emission of the settings flow. At boot the push link changes state several times while it
 * connects, which turned one self-stop into four starts inside a second.
 *
 * The rule being encoded: the intent starts the loop **once**, and an explicit start is the
 * only other way in.
 */
class LoopActionTest {

    @Test
    fun `a fresh intent starts the loop`() {
        assertEquals(LoopAction.START, loopAction(wanted = true, armed = false))
    }

    @Test
    fun `further emissions for the same intent start nothing`() {
        // The regression: a loop that stopped itself must not be re-launched by the next
        // unrelated state change (a push reconnect, a reposted notice).
        assertEquals(LoopAction.NONE, loopAction(wanted = true, armed = true))
    }

    @Test
    fun `clearing the intent stops the loop`() {
        assertEquals(LoopAction.STOP, loopAction(wanted = false, armed = true))
    }

    @Test
    fun `an intent that was never armed has nothing to stop`() {
        assertEquals(LoopAction.NONE, loopAction(wanted = false, armed = false))
    }
}
