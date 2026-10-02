package org.rpwt.wlwdw.tracking

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import org.rpwt.wlwdw.data.ReportRepository
import org.rpwt.wlwdw.data.model.Report
import org.rpwt.wlwdw.data.model.ReportFailure
import org.rpwt.wlwdw.data.net.ReportOutcome
import org.rpwt.wlwdw.data.net.WlwdwApi
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs
import org.rpwt.wlwdw.location.Fix
import org.rpwt.wlwdw.location.LocationSource
import org.rpwt.wlwdw.ui.model.DeviceStatus

/**
 * What the tracking screen shows, as one value.
 *
 * [hasForeground]/[hasBackground] are part of the state rather than read by the
 * screen, because the ring's wording depends on them: "reporting" and "will
 * stop as soon as the screen goes off" are different things to tell the user,
 * and only one of them is a problem.
 *
 * [lastSuccessAt] is read back from the report log when the app opens, so it is
 * the answer to "when did this last work" and not "when did it last work since
 * the app was opened".
 */
data class TrackingState(
    val running: Boolean = false,
    val hasForeground: Boolean = false,
    val hasBackground: Boolean = false,
    val fix: Fix? = null,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val failure: ReportFailure? = null,
) {
    /**
     * The status matrix entry this state is in.
     *
     * Order matters. [ReportFailure.Unregistered] first because it survives the
     * loop stopping -- the fix is not to retry but to register the device, and a
     * screen reading "已停止" would send the user looking in the wrong place.
     */
    val status: DeviceStatus
        get() = when {
            failure is ReportFailure.Unregistered -> DeviceStatus.DeviceUnregistered
            !running -> DeviceStatus.Stopped
            !hasForeground || !hasBackground -> DeviceStatus.PermissionMissing
            failure is ReportFailure.NoFix -> DeviceStatus.LocationFailed
            failure != null -> DeviceStatus.Retrying
            else -> DeviceStatus.Reporting
        }
}

/**
 * The tracking loop: fix, upload, log, wait, repeat.
 *
 * This is a plain coroutine rather than a service so that the whole thing can
 * be reasoned about and tested without an Android lifecycle. Moving it into a
 * foreground service later is then a matter of who owns the scope, not of
 * rewriting the logic -- and the manifest already declares the permission that
 * service will need.
 *
 * The loop is driven by the settings it reads each tick, so changing the
 * interval or the server takes effect on the next report instead of needing a
 * restart.
 */
class ReportingEngine(
    private val api: WlwdwApi,
    private val location: LocationSource,
    private val reports: ReportRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    /** Called when the app learns the permission changed, so the ring can react at once. */
    fun onPermissionsChanged() {
        _state.update {
            it.copy(
                hasForeground = location.hasForegroundPermission(),
                hasBackground = location.hasBackgroundPermission(),
            )
        }
    }

    /**
     * Read the log's last accepted report into the state.
     *
     * Kept separate from [run] because the answer to "when did this last work"
     * does not change when the app is restarted, and a cold start that says
     * "还没有成功上报" while the log holds a report from two minutes ago is
     * simply wrong. Called once when the app opens, and again when the loop
     * starts.
     */
    suspend fun restore() {
        val last = reports.lastAcceptedAt() ?: return
        _state.update { it.copy(lastSuccessAt = last) }
    }

    /**
     * Report until cancelled.
     *
     * [prefs] is read fresh on every tick rather than captured once, so a
     * settings change is picked up by the running loop.
     */
    suspend fun run(prefs: suspend () -> WlwdwPrefs) {
        restore()
        // Only the failure is cleared: it is a statement about the attempt that
        // is about to happen, while the last fix and the last success are facts
        // that survive both a restart and a stop.
        _state.update { it.copy(running = true, failure = null) }
        try {
            while (currentCoroutineContext().isActive) {
                val settings = prefs()
                val outcome = tick(settings)
                if (outcome == Outcome.STOP) break
                delay(settings.reportIntervalMinutes * 60_000L)
            }
        } finally {
            // Also runs on cancellation, which is the normal way this ends.
            _state.update { it.copy(running = false) }
        }
    }

    private enum class Outcome { CONTINUE, STOP }

    private suspend fun tick(settings: WlwdwPrefs): Outcome {
        // One instant for the whole attempt: the row that gets logged and the
        // time the screen shows have to be the same fact.
        val at = now()
        val foreground = location.hasForegroundPermission()
        val background = location.hasBackgroundPermission()
        _state.update {
            it.copy(
                hasForeground = foreground,
                hasBackground = background,
                lastAttemptAt = at,
            )
        }

        if (!foreground) {
            return failed(ReportFailure.Permission, null, at)
        }

        val fix = location.current()
        if (fix == null) {
            // The previous fix stays on screen, marked old by the UI.
            return failed(ReportFailure.NoFix, null, at)
        }
        _state.update { it.copy(fix = fix) }

        return when (val result = api.report(settings.serverHost, settings.deviceId, fix.lat, fix.lng)) {
            ReportOutcome.Accepted -> {
                _state.update { it.copy(lastSuccessAt = at, failure = null) }
                reports.record(attempt(at, fix, null))
                Outcome.CONTINUE
            }

            // Stop: every further attempt gets the same answer, and a device
            // that is not registered is a configuration problem, not a
            // transient one.
            ReportOutcome.DeviceUnknown -> failed(ReportFailure.Unregistered, fix, at, Outcome.STOP)

            is ReportOutcome.Rejected -> failed(ReportFailure.Refused(result.message), fix, at)

            is ReportOutcome.Unreachable -> failed(ReportFailure.Upload(result.reason), fix, at)
        }
    }

    /**
     * Put [failure] on screen and in the log, which are two different audiences
     * for the same fact and would otherwise drift apart.
     */
    private suspend fun failed(
        failure: ReportFailure,
        fix: Fix?,
        at: Long,
        outcome: Outcome = Outcome.CONTINUE,
    ): Outcome {
        _state.update { it.copy(failure = failure) }
        reports.record(attempt(at, fix, failure))
        return outcome
    }

    private fun attempt(at: Long, fix: Fix?, failure: ReportFailure?) = Report(
        at = at,
        latitude = fix?.lat,
        longitude = fix?.lng,
        accuracyMetres = fix?.accuracyMetres,
        provider = fix?.provider,
        failure = failure,
    )
}
