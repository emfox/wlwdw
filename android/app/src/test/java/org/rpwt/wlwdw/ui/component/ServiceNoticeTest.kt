package org.rpwt.wlwdw.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rpwt.wlwdw.R
import org.rpwt.wlwdw.data.model.ReportFailure
import org.rpwt.wlwdw.push.PushState
import org.rpwt.wlwdw.tracking.TrackingState

/**
 * What the one permanent notification says.
 *
 * Two notices became one, so this decision -- which of the two jobs the line is
 * about, and which word it uses -- is new, and it is the part a device is bad at
 * reporting: the notice is a single line in the shade that nobody diffs. The
 * cases below are exactly the ones where reading the wrong source would produce
 * a plausible-looking lie.
 */
class ServiceNoticeTest {

    private val reporting = TrackingState(
        running = true,
        hasForeground = true,
        hasBackground = true,
    )

    @Test
    fun `tracking off reports the push link`() {
        assertEquals(
            ServiceNotice(R.string.wlwdw_push_service_title, R.string.wlwdw_link_online),
            serviceNotice(tracking = false, state = TrackingState(), push = PushState.Connected),
        )
    }

    @Test
    fun `tracking on reports the reporting loop`() {
        assertEquals(
            ServiceNotice(R.string.wlwdw_tracking_service_title, R.string.wlwdw_ring_reporting),
            serviceNotice(tracking = true, state = reporting, push = PushState.Connected),
        )
    }

    @Test
    fun `a loop that stopped itself still says why`() {
        // The loop stops on a refusal and leaves the stored intent on. Reading
        // the loop's own state here would say 已停止, which sends the user to the
        // start button instead of to the server where the device has to be
        // registered -- so the notice follows the intent, not the loop.
        val stopped = TrackingState(running = false, failure = ReportFailure.Unregistered)

        assertEquals(
            ServiceNotice(R.string.wlwdw_tracking_service_title, R.string.wlwdw_ring_unregistered),
            serviceNotice(tracking = true, state = stopped, push = PushState.Connected),
        )
    }

    @Test
    fun `reporting with a dead push link says so`() {
        // The one status that needs both answers. The notice composes them the
        // same way the ring does, so a device whose reports are landing but
        // whose inbox is unreachable cannot look healthy in the shade.
        assertEquals(
            ServiceNotice(R.string.wlwdw_tracking_service_title, R.string.wlwdw_ring_push_offline),
            serviceNotice(tracking = true, state = reporting, push = PushState.Connecting),
        )
    }
}
