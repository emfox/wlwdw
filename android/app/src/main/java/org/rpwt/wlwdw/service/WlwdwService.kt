package org.rpwt.wlwdw.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.rpwt.wlwdw.data.ReportRepository
import org.rpwt.wlwdw.data.net.WlwdwApi
import org.rpwt.wlwdw.data.prefs.PreferencesRepository
import org.rpwt.wlwdw.location.LocationSource
import org.rpwt.wlwdw.push.PushLink
import org.rpwt.wlwdw.push.PushState
import org.rpwt.wlwdw.push.WlwdwNotifications
import org.rpwt.wlwdw.tracking.ReportingEngine
import org.rpwt.wlwdw.tracking.TrackingState
import org.rpwt.wlwdw.ui.component.ServiceNotice
import org.rpwt.wlwdw.ui.component.serviceNotice

/**
 * The one thing this app runs in the background: the push link, and the
 * reporting loop when the user has asked for it.
 *
 * ## Why one service
 *
 * There used to be two, one per long-lived job, which meant two permanent
 * notifications in the shade. A foreground service shows exactly one
 * notification -- the one passed to `startForeground` -- so one notification
 * cannot be had without one service. This is that service.
 *
 * The two jobs keep their own state, their own failure modes and their own
 * warnings; what they now share is the service that hosts them and the single
 * notice that says the service is alive.
 *
 * ## What the notice says
 *
 * One line has to stand for two jobs, so it tells the more interesting story:
 * while tracking is on it shows the reporting loop's status (the user turned it
 * on, and its failures are actionable); with tracking off it shows the push
 * link, which is then the only thing running. Both wordings come from
 * `ui/component/StatusMatrix.kt`, the same functions the status screen renders,
 * so the notice and the screen cannot disagree. See [serviceNotice].
 *
 * ## Why the service is always up, and what that cost
 *
 * The push link is the reason it runs at all -- a message arriving while the app
 * is closed is the whole point of push -- so the service is not the user's
 * tracking session any more. It is `START_STICKY` and it comes back when the
 * process is killed, which means it can no longer answer "was tracking on?" by
 * observing that it exists: it always exists. That answer is now stored
 * (`WlwdwPrefs.trackingEnabled`), and this class reads it.
 *
 * The consequence to state plainly: **the service runs from the moment consent
 * is given, and stopping tracking no longer stops it.** Stopping tracking stops
 * the loop; the push link and its notice stay. Before the merge, "stop
 * tracking" took the whole service (and the push link with it) down, which was
 * arguably a bug: a stopped tracker stopped receiving messages.
 *
 * ## The foreground service type
 *
 * The manifest declares `location|dataSync` and this class passes a subset at
 * runtime. The documented rules (verified, not remembered -- see the platform
 * reference for `Service.startForeground(int, Notification, int)`):
 *
 * - the type passed must be a **subset** of what the manifest declares, or
 *   `IllegalArgumentException`;
 * - on targetSdk 34+ it must not be `FOREGROUND_SERVICE_TYPE_NONE`, so "just
 *   declare it in the manifest" is not an option;
 * - **calling `startForeground` again is the documented way to change it** --
 *   the same mechanism a fitness app uses to add media playback to a running
 *   location service.
 *
 * `dataSync` is the floor: it is what a running push link is, it needs no
 * runtime permission, and it is always legal to claim. `location` is added on
 * top while tracking is on **and** the app holds a location permission, because
 * the system checks for that permission when the location type is claimed and
 * throws `SecurityException` when it is missing.
 *
 * The type follows the tracking *intent*, not the instantaneous loop state: they
 * differ only when the loop has stopped itself on a refusal, and flapping the
 * type on and off around failures would be churn for nothing. It is the same
 * choice the notice makes, so the two stay in step by construction.
 */
class WlwdwService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsStore by lazy { PreferencesRepository(applicationContext) }
    private val location by lazy { LocationSource(applicationContext) }

    private val engine by lazy {
        ReportingEngine(
            sender = WlwdwApi(),
            location = location,
            reports = ReportRepository(applicationContext),
            // The one state every reader sees: the screen collects the same
            // flow, so "is it reporting" has a single answer rather than one
            // per owner.
            output = liveTracking,
        )
    }

    private val push by lazy { PushLink(applicationContext, scope) }

    private var trackingLoop: Job? = null
    private var watcher: Job? = null

    /**
     * Serialises the loop's start and stop.
     *
     * Both the watcher and an explicit start command can decide the loop should
     * be running, and they run on different threads of [scope]. Without this the
     * two can both see "not running" and both start it, which would send every
     * report twice -- the exact thing [onStartCommand]'s guard was for when the
     * loop had a single caller.
     */
    private val trackingLock = Mutex()

    /** The types last claimed, so the type is only changed when it must be. */
    private var claimedTypes = 0

    /**
     * The notice last posted, so a later start can re-promote with the same
     * answer instead of walking back to the floor.
     *
     * Null until the watcher has read the settings once, which is what the floor
     * is for.
     */
    private var lastNotice: ServiceNotice? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The channel has to exist before the notification that keeps this in
        // the foreground is posted, or Android drops it.
        WlwdwNotifications.createChannel(this)

        // Promoted on *every* start, not only the first: `startForegroundService`
        // is a promise that the service will call `startForeground`, and it is
        // re-armed per call -- skipping it because the service already looks
        // foreground is how an app gets "did not then call
        // Service.startForeground()".
        //
        // What was decided last time is re-used rather than the floor: going
        // back to `dataSync` would narrow a type the service legitimately holds
        // and re-word the notice, only for the watcher to put both back.
        val known = lastNotice
        if (known == null) {
            // Nothing read yet. The floor is used because it needs no permission
            // and no stored setting, so this first promotion cannot fail; the
            // watcher replaces both the type and the wording as soon as it has
            // read what it needs.
            promote(
                types = TYPES_PUSH,
                notice = serviceNotice(tracking = false, liveTracking.value, PushLink.state.value),
            )
        } else {
            promote(types = claimedTypes, notice = known)
        }

        // Idempotent, so a second start (startForegroundService can deliver
        // more than one) does not open a second session under the same id.
        push.start()
        // startForegroundService can deliver more than one start, and the
        // first one already has a watcher.
        if (watcher?.isActive != true) {
            watcher = scope.launch { watch() }
        }
        // An explicit start, which is the only way back from a loop that
        // stopped itself: the stored intent is already true in that case, so
        // writing it again changes nothing and nothing would wake the loop.
        if (intent?.action == ACTION_START_TRACKING) {
            scope.launch { syncTracking() }
        }
        Log.i(TAG, "service started")
        return START_STICKY
    }

    override fun onDestroy() {
        // The loop ends by cancellation: the engine's own finally block clears
        // `running`. Wiping the whole state here would also throw away the last
        // fix and the last successful report, which are exactly what the screen
        // shows once tracking has stopped.
        push.stop()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "service stopped")
        super.onDestroy()
    }

    /**
     * Follow the stored state: run the loop while tracking is asked for, keep the
     * notice and the claimed type honest.
     *
     * One collector over the three things that can change any of it, rather than
     * a callback per event: the settings (the tracking intent and the server),
     * the reporting loop's state, and the push link's. Anything that changes the
     * notice changes one of them.
     */
    private suspend fun watch() {
        combine(
            prefsStore.prefs,
            liveTracking,
            PushLink.state,
        ) { prefs, tracking, push -> Triple(prefs, tracking, push) }
            .collect { (prefs, tracking, push) ->
                syncTracking()
                promote(
                    types = foregroundTypes(
                        trackingRequested = prefs.trackingEnabled && prefs.consentAccepted,
                        hasLocationPermission = location.hasForegroundPermission(),
                    ),
                    notice = serviceNotice(prefs.trackingEnabled, tracking, push),
                )
            }
    }

    /**
     * Start or stop the reporting loop to match the stored intent.
     *
     * Also called directly when the button is pressed, which is the only way to
     * re-arm a loop that stopped itself: the intent is already true then, so the
     * settings flow does not emit and the collector never wakes.
     */
    private suspend fun syncTracking() = trackingLock.withLock {
        val prefs = prefsStore.prefs.first()
        // Consent as well as the switch. The switch can only be turned on from
        // behind the consent gate, so this cannot fail today -- which is exactly
        // why it is written down: "never report without consent" should not
        // depend on the current shape of the navigation graph.
        val wanted = prefs.trackingEnabled && prefs.consentAccepted
        when {
            wanted && trackingLoop?.isActive != true -> {
                Log.i(TAG, "tracking loop starting")
                trackingLoop = scope.launch { engine.run { prefsStore.prefs.first() } }
            }

            !wanted && trackingLoop?.isActive == true -> {
                Log.i(TAG, "tracking loop stopping")
                trackingLoop?.cancel()
            }
        }
    }

    /**
     * Put the service in the foreground with [types], showing [notice].
     *
     * Always a `startForeground` rather than a plain re-post when the types have
     * not changed: the call is also the answer to the promise
     * `startForegroundService` made, so it has to happen on every start. Posting
     * the notification is the same work either way.
     */
    private fun promote(types: Int, notice: ServiceNotice) {
        val notification = WlwdwNotifications.serviceNotification(this, notice.titleRes, notice.textRes)
        lastNotice = notice
        val changed = types != claimedTypes
        try {
            ServiceCompat.startForeground(this, WlwdwNotifications.SERVICE_ID, notification, types)
            claimedTypes = types
            // Logged only on a change, because the type is otherwise invisible:
            // it is on no screen, and it changes in response to a switch the
            // user flipped minutes ago. This is the app's own account of what it
            // is claiming, which is what makes the claim checkable against what
            // the system accepted (dumpsys activity services).
            if (changed) Log.i(TAG, "foreground types 0x${types.toString(16)}")
        } catch (e: Exception) {
            // The location type needs access to location *while in use*, which a
            // (re)start from the background does not have unless
            // ACCESS_BACKGROUND_LOCATION is granted. Falling back rather than
            // propagating: a revoked permission would otherwise be a crash loop
            // at boot. The loop reports the missing permission in its own words,
            // so nothing is hidden by this.
            //
            // The attempt is not remembered as a failure, so the next change of
            // state tries again -- which is what upgrades the type back once the
            // app is in the foreground and the permission chain allows it.
            Log.w(TAG, "could not claim types 0x${types.toString(16)}; falling back to push only", e)
            ServiceCompat.startForeground(this, WlwdwNotifications.SERVICE_ID, notification, TYPES_PUSH)
            claimedTypes = TYPES_PUSH
        }
    }

    companion object {
        private const val TAG = "WlwdwSvc"

        /** The floor: what a running push link is, and needs nothing to claim. */
        private const val TYPES_PUSH = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

        /**
         * Set by [startTracking]; see [onStartCommand] for why the stored intent
         * alone cannot re-arm a stopped loop.
         */
        private const val ACTION_START_TRACKING = "org.rpwt.wlwdw.action.START_TRACKING"

        /**
         * The one place tracking state lives, for both the service and the UI.
         *
         * Process-wide rather than owned by the service or by a ViewModel,
         * because neither outlives the other: the screen can be open with the
         * loop stopped (showing the last fix and when it last worked), and the
         * loop runs with no screen at all.
         */
        private val liveTracking = MutableStateFlow(TrackingState())

        val tracking: StateFlow<TrackingState> = liveTracking.asStateFlow()

        /**
         * What the push link is doing, for the status screen.
         *
         * Handed through rather than re-declared so that the screens have one
         * place to read the app's background state from, even though the link
         * itself belongs to `push/`.
         */
        val push: StateFlow<PushState> = PushLink.state

        /**
         * Bring the service up. Idempotent.
         *
         * Tracking is not touched: whether it runs follows the stored intent, so
         * this is the call for "the app is open, make sure the push link is up".
         */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, WlwdwService::class.java))
        }

        /**
         * Ask for tracking, and bring the service up if it is not already.
         *
         * Stored before the service is started so that a service starting fresh
         * already reads the right answer, and so that a crash between the two
         * leaves the intent -- which the next start of the app will honour --
         * rather than a running service nobody asked for.
         */
        suspend fun startTracking(context: Context) {
            PreferencesRepository(context.applicationContext).setTrackingEnabled(true)
            context.startForegroundService(
                Intent(context, WlwdwService::class.java).setAction(ACTION_START_TRACKING),
            )
        }

        /**
         * Stop the reporting loop, and only that.
         *
         * The service stays up: it is also the push link, and a tracker that is
         * not reporting still has to be reachable. Nothing is sent to the
         * service for the loop to stop -- it is watching the stored intent, so
         * clearing it is the whole instruction.
         */
        suspend fun stopTracking(context: Context) {
            PreferencesRepository(context.applicationContext).setTrackingEnabled(false)
        }

        /**
         * Re-read the location permissions.
         *
         * Answered here rather than by the engine because the screen asks this
         * before any loop exists: whether the start button is allowed to start
         * anything is a question about the install, not about a running loop.
         */
        fun permissionsChanged(context: Context) {
            val source = LocationSource(context.applicationContext)
            liveTracking.update {
                it.copy(
                    hasForeground = source.hasForegroundPermission(),
                    hasBackground = source.hasBackgroundPermission(),
                )
            }
        }

        /**
         * Read the log's last accepted report into the state.
         *
         * The loop does this too when it starts, but a cold start that has not
         * started tracking must still be able to say when this device last
         * worked: the answer is in the log, not in this process.
         */
        suspend fun restoreLastSuccess(context: Context) {
            val last = ReportRepository(context.applicationContext).lastAcceptedAt() ?: return
            liveTracking.update { it.copy(lastSuccessAt = last) }
        }
    }
}

/**
 * The foreground service types to claim, as a bitmask.
 *
 * Extracted from the service so it can be tested: the choice decides whether the
 * app may read location in the background, and getting it wrong is a
 * `SecurityException` on a device rather than a failed assertion -- which is
 * exactly the kind of thing worth pinning in a unit test.
 *
 * `dataSync` always, because the push link always runs. `location` only on top
 * of it, because claiming a type the app cannot satisfy is refused by the
 * system.
 */
internal fun foregroundTypes(trackingRequested: Boolean, hasLocationPermission: Boolean): Int =
    if (trackingRequested && hasLocationPermission) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    } else {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    }
