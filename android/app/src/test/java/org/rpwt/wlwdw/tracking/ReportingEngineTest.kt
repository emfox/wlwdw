package org.rpwt.wlwdw.tracking

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.rpwt.wlwdw.data.ReportLog
import org.rpwt.wlwdw.data.model.Report
import org.rpwt.wlwdw.data.model.ReportFailure
import org.rpwt.wlwdw.data.net.ReportOutcome
import org.rpwt.wlwdw.data.net.ReportSender
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs
import org.rpwt.wlwdw.location.Fix
import org.rpwt.wlwdw.location.LocationProvider
import org.rpwt.wlwdw.ui.model.DeviceStatus

/**
 * The tracking loop, with no device, no database and no HTTP server.
 *
 * These are the decisions that are expensive to get wrong and impossible to see
 * on screen: a deny has to stop the loop, an upload failure must not, and
 * stopping must not wipe the answer to "when did it last work". The virtual
 * clock is what makes a loop that sleeps for two minutes between ticks a
 * millisecond-long test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportingEngineTest {

    @Test
    fun `a tick sends the fix to the configured host with the device id`() = runTest {
        val sender = FakeSender(ReportOutcome.Accepted)
        val log = FakeLog()
        val engine = ReportingEngine(sender, FakeLocation(), log, now = { NOW })

        val job = launch { engine.run { PREFS } }
        testScheduler.runCurrent()
        val state = engine.state.value
        job.cancelAndJoin()

        // The first attempt happens at once rather than one interval in: a
        // tracker that stayed quiet for its first two minutes would look
        // broken exactly when the user is watching it.
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(1, sender.calls)
        assertEquals("10.0.2.2:8080", sender.host)
        assertEquals(DEVICE_ID, sender.devid)
        assertEquals(LAT, sender.lat!!, 0.0)
        assertEquals(LNG, sender.lng!!, 0.0)

        assertEquals(NOW, state.lastAttemptAt)
        assertEquals(NOW, state.lastSuccessAt)
        assertNull(state.failure)
        assertEquals(FIX, state.fix)
        assertEquals(DeviceStatus.Reporting, state.status)

        val row = log.rows.single()
        assertEquals(NOW, row.at)
        assertEquals(LAT, row.latitude!!, 0.0)
        assertEquals(LNG, row.longitude!!, 0.0)
        assertEquals(5.0f, row.accuracyMetres!!, 0.0f)
        assertEquals("gps", row.provider)
        assertNull(row.failure)
    }

    /**
     * The one failure that retrying cannot fix.
     *
     * A device the server does not know gets the same answer every two minutes
     * forever, so the loop gives up after the first one -- and the state says
     * "not registered" rather than "stopped", because the thing to change is on
     * the server, not on the phone.
     */
    @Test
    fun `a deny stops the loop instead of retrying forever`() = runTest {
        val sender = FakeSender(ReportOutcome.DeviceUnknown)
        val log = FakeLog()
        val engine = ReportingEngine(sender, FakeLocation(), log, now = { NOW })

        // A stop condition as well as the assertion. `advanceUntilIdle` runs
        // until the scheduler is empty, and a loop that keeps rescheduling
        // itself never empties it: without this belt the test would spin the
        // virtual clock for ever instead of failing on the count below.
        var job: Job? = null
        sender.afterEach = { if (it == 2) job?.cancel() }
        job = launch { engine.run { PREFS } }
        testScheduler.advanceUntilIdle()
        job.join()

        assertEquals(1, sender.calls)
        assertEquals(ReportFailure.Unregistered, log.rows.single().failure)
        val state = engine.state.value
        assertEquals(ReportFailure.Unregistered, state.failure)
        assertEquals(DeviceStatus.DeviceUnregistered, state.status)
        assertFalse(state.running)
        // The fix is still on screen: the screen is not empty, it is unhelpful
        // in one specific way, and the coordinates are what the user reads out
        // when registering the device.
        assertEquals(FIX, state.fix)
    }

    @Test
    fun `an upload failure is retried on the next tick`() = runTest {
        val sender = FakeSender(ReportOutcome.Unreachable("no route to host"))
        val log = FakeLog()
        val engine = ReportingEngine(sender, FakeLocation(), log, now = { NOW })

        // Nothing here stops the loop -- that is the point of the test -- so
        // the test stops it, the same way the ViewModel does.
        var job: Job? = null
        sender.afterEach = { if (it == 2) job?.cancel() }
        job = launch { engine.run { PREFS } }
        testScheduler.advanceUntilIdle()

        assertEquals(2, sender.calls)
        assertEquals(
            listOf(
                ReportFailure.Upload("no route to host"),
                ReportFailure.Upload("no route to host"),
            ),
            log.rows.map { it.failure },
        )
        // However many attempts failed, this is still not a successful report.
        assertNull(engine.state.value.lastSuccessAt)
    }

    @Test
    fun `without permission nothing is read and nothing is sent`() = runTest {
        val sender = FakeSender(ReportOutcome.Accepted)
        val location = FakeLocation(foreground = false)
        val log = FakeLog()
        val engine = ReportingEngine(sender, location, log, now = { NOW })

        val job = launch { engine.run { PREFS } }
        testScheduler.runCurrent()
        val state = engine.state.value
        job.cancelAndJoin()

        assertEquals(0, sender.calls)
        assertEquals(0, location.reads)
        assertEquals(DeviceStatus.PermissionMissing, state.status)
        assertEquals(ReportFailure.Permission, log.rows.single().failure)
        // Nothing to send means nothing in the row, not a pair of zeros that
        // would put the device at 0,0 off the coast of Africa.
        assertNull(log.rows.single().latitude)
        assertNull(log.rows.single().longitude)
    }

    @Test
    fun `no fix is logged without coordinates`() = runTest {
        val log = FakeLog()
        val engine = ReportingEngine(FakeSender(ReportOutcome.Accepted), FakeLocation(fix = null), log, now = { NOW })

        val job = launch { engine.run { PREFS } }
        testScheduler.runCurrent()
        val state = engine.state.value
        job.cancelAndJoin()

        assertEquals(DeviceStatus.LocationFailed, state.status)
        assertEquals(ReportFailure.NoFix, log.rows.single().failure)
        assertNull(log.rows.single().latitude)
        // The previous fix is kept on screen rather than blanked; here there
        // never was one, so the screen shows "no location yet".
        assertNull(state.fix)
    }

    @Test
    fun `stopping keeps the last success and the last fix`() = runTest {
        val engine = ReportingEngine(FakeSender(ReportOutcome.Accepted), FakeLocation(), FakeLog(), now = { NOW })

        val job = launch { engine.run { PREFS } }
        testScheduler.runCurrent()
        job.cancelAndJoin()

        val state = engine.state.value
        assertFalse(state.running)
        assertEquals(DeviceStatus.Stopped, state.status)
        assertEquals(NOW, state.lastSuccessAt)
        assertEquals(FIX, state.fix)
    }

    @Test
    fun `the last success comes out of the log, not out of this process`() = runTest {
        val reportedAt = NOW - 47 * 60_000L

        val restored = engineWith(log = FakeLog().apply { lastAccepted = reportedAt })
        restored.restore()
        assertEquals(reportedAt, restored.state.value.lastSuccessAt)

        // Never having reported is a real answer and has to stay distinct from
        // not having looked yet, or a fresh install is told it reported.
        val fresh = engineWith(log = FakeLog())
        fresh.restore()
        assertNull(fresh.state.value.lastSuccessAt)
    }

    /**
     * The matrix, in the order it is written.
     *
     * Two of these are the ones worth pinning: "not registered" outranks
     * "stopped" (the loop really has stopped, but telling the user that would
     * send them looking at the wrong thing), and a missing permission outranks
     * every transient failure, because no amount of waiting fixes it.
     */
    @Test
    fun `the status matrix picks the most specific state`() {
        assertEquals(
            DeviceStatus.DeviceUnregistered,
            TrackingState(running = false, failure = ReportFailure.Unregistered).status,
        )
        assertEquals(DeviceStatus.Stopped, TrackingState(running = false).status)
        assertEquals(
            DeviceStatus.PermissionMissing,
            TrackingState(running = true, hasForeground = true, hasBackground = false).status,
        )
        assertEquals(
            DeviceStatus.PermissionMissing,
            TrackingState(running = true, hasForeground = false, hasBackground = true).status,
        )
        assertEquals(
            DeviceStatus.LocationFailed,
            running(failure = ReportFailure.NoFix).status,
        )
        assertEquals(
            DeviceStatus.Retrying,
            running(failure = ReportFailure.Upload("timeout")).status,
        )
        assertEquals(
            DeviceStatus.Retrying,
            running(failure = ReportFailure.Refused("坐标超出范围")).status,
        )
        assertEquals(DeviceStatus.Reporting, running().status)
    }

    private fun running(failure: ReportFailure? = null) = TrackingState(
        running = true,
        hasForeground = true,
        hasBackground = true,
        failure = failure,
    )

    private fun engineWith(log: ReportLog, sender: ReportSender = FakeSender(ReportOutcome.Accepted)) =
        ReportingEngine(sender, FakeLocation(), log, now = { NOW })
}

private const val NOW = 1_700_000_000_000L
private const val DEVICE_ID = "b14fcbbb-d2c3-48fd-b72d-513689310f42"
private const val LAT = 30.27410
private const val LNG = 120.12625

private val FIX = Fix(
    lat = LAT,
    lng = LNG,
    accuracyMetres = 5.0f,
    provider = "gps",
    atMillis = NOW - 1_000L,
)

/** A custom host, so that the test can tell the setting was read and not the default. */
private val PREFS = WlwdwPrefs(
    deviceId = DEVICE_ID,
    consentAccepted = true,
    useCustomHost = true,
    customHost = "10.0.2.2:8080",
    reportIntervalMinutes = 2,
)

/** The server, reduced to the one answer it gives. */
private class FakeSender(private val outcome: ReportOutcome) : ReportSender {

    var calls = 0
        private set

    /** Runs after the nth report, so a test can stop a loop that never stops. */
    var afterEach: (Int) -> Unit = {}

    var host: String? = null
        private set
    var devid: String? = null
        private set
    var lat: Double? = null
        private set
    var lng: Double? = null
        private set

    override suspend fun report(host: String, devid: String, lat: Double, lng: Double): ReportOutcome {
        calls++
        this.host = host
        this.devid = devid
        this.lat = lat
        this.lng = lng
        afterEach(calls)
        return outcome
    }
}

/** The platform's location service, reduced to the answers the loop asks for. */
private class FakeLocation(
    private val foreground: Boolean = true,
    private val background: Boolean = true,
    private val fix: Fix? = FIX,
) : LocationProvider {

    var reads = 0
        private set

    override fun hasForegroundPermission() = foreground

    override fun hasBackgroundPermission() = background

    override suspend fun current(): Fix? {
        reads++
        return fix
    }
}

/** The report log, in memory. */
private class FakeLog : ReportLog {

    val rows = mutableListOf<Report>()

    var lastAccepted: Long? = null

    override suspend fun record(report: Report) {
        rows += report
    }

    override suspend fun lastAcceptedAt(): Long? = lastAccepted
}
