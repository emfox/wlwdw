package org.rpwt.wlwdw.tracking

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import org.rpwt.wlwdw.data.net.ReportOutcome
import org.rpwt.wlwdw.data.net.WlwdwApi
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs
import org.rpwt.wlwdw.location.Fix
import org.rpwt.wlwdw.location.LocationSource
import org.rpwt.wlwdw.ui.model.DeviceStatus

/** Why the last attempt did not put a position on the server. */
sealed interface ReportFailure {

    /** Location is not granted, so there is nothing to report. */
    data object Permission : ReportFailure

    /** Granted, but no provider produced a position. */
    data object NoFix : ReportFailure

    /** The upload did not get through. Retried on the next tick. */
    data class Upload(val reason: String) : ReportFailure

    /** The server refused the request itself: a bug or a bad coordinate. */
    data class Refused(val message: String?) : ReportFailure
}

/**
 * What the tracking screen shows, as one value.
 *
 * [hasForeground]/[hasBackground] are part of the state rather than read by the
 * screen, because the ring's wording depends on them: "reporting" and "will
 * stop as soon as the screen goes off" are different things to tell the user,
 * and only one of them is a problem.
 */
data class TrackingState(
    val running: Boolean = false,
    val hasForeground: Boolean = false,
    val hasBackground: Boolean = false,
    val fix: Fix? = null,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val failure: ReportFailure? = null,
    /** The server answered `deny`: this device id is not registered there. */
    val unregistered: Boolean = false,
) {
    /**
     * The status matrix entry this state is in.
     *
     * Order matters. [unregistered] first because it survives the loop stopping
     * -- the fix is not to retry but to register the device, and a screen
     * reading "已停止" would send the user looking in the wrong place.
     */
    val status: DeviceStatus
        get() = when {
            unregistered -> DeviceStatus.DeviceUnregistered
            !running -> DeviceStatus.Stopped
            !hasForeground || !hasBackground -> DeviceStatus.PermissionMissing
            failure is ReportFailure.NoFix -> DeviceStatus.LocationFailed
            failure != null -> DeviceStatus.Retrying
            else -> DeviceStatus.Reporting
        }
}

/**
 * The tracking loop: fix, upload, wait, repeat.
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
     * Report until cancelled.
     *
     * [prefs] is read fresh on every tick rather than captured once, so a
     * settings change is picked up by the running loop.
     */
    suspend fun run(prefs: suspend () -> WlwdwPrefs) {
        _state.value = TrackingState(running = true)
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
        val foreground = location.hasForegroundPermission()
        val background = location.hasBackgroundPermission()
        _state.update {
            it.copy(
                hasForeground = foreground,
                hasBackground = background,
                lastAttemptAt = now(),
            )
        }

        if (!foreground) {
            _state.update { it.copy(failure = ReportFailure.Permission) }
            return Outcome.CONTINUE
        }

        val fix = location.current()
        if (fix == null) {
            // The previous fix stays on screen, marked old by the UI.
            _state.update { it.copy(failure = ReportFailure.NoFix) }
            return Outcome.CONTINUE
        }
        _state.update { it.copy(fix = fix) }

        return when (val result = api.report(settings.serverHost, settings.deviceId, fix.lat, fix.lng)) {
            ReportOutcome.Accepted -> {
                _state.update {
                    it.copy(lastSuccessAt = now(), failure = null, unregistered = false)
                }
                Outcome.CONTINUE
            }

            ReportOutcome.DeviceUnknown -> {
                // Stop: every further attempt gets the same answer, and a device
                // that is not registered is a configuration problem, not a
                // transient one.
                _state.update { it.copy(unregistered = true, failure = null) }
                Outcome.STOP
            }

            is ReportOutcome.Rejected -> {
                _state.update { it.copy(failure = ReportFailure.Refused(result.message)) }
                Outcome.CONTINUE
            }

            is ReportOutcome.Unreachable -> {
                _state.update { it.copy(failure = ReportFailure.Upload(result.reason)) }
                Outcome.CONTINUE
            }
        }
    }
}
