package org.rpwt.wlwdw.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.rpwt.wlwdw.data.MessageRepository
import org.rpwt.wlwdw.data.ReportRepository
import org.rpwt.wlwdw.data.model.Message
import org.rpwt.wlwdw.data.net.WlwdwApi
import org.rpwt.wlwdw.data.prefs.PreferencesRepository
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs
import org.rpwt.wlwdw.location.LocationSource
import org.rpwt.wlwdw.tracking.ReportingEngine
import org.rpwt.wlwdw.tracking.TrackingState

/**
 * The app's state, as the screens read it.
 *
 * Settings first, then the message store. Null settings mean "not read yet",
 * and that has to stay distinguishable from the defaults: on the first frame
 * the app does not know whether the user has consented, and guessing either way
 * is visible -- guess "yes" and a new user lands in the tracking UI without
 * consenting, guess "no" and a consented user is asked again on every launch.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)
    private val messageStore = MessageRepository(application)
    private val location = LocationSource(application)
    private val engine = ReportingEngine(WlwdwApi(), location, ReportRepository(application))

    /** The job running [ReportingEngine.run]; null when tracking is off. */
    private var trackingJob: Job? = null

    init {
        // Read the report log before the first frame, so the status screen can
        // say when it last worked even though this process has never reported
        // anything itself.
        viewModelScope.launch { engine.restore() }
    }

    val settings: StateFlow<WlwdwPrefs?> = flow {
        // Seed before the first emission, so the id is already there the first
        // time a screen shows it rather than appearing a frame later.
        prefs.ensureSeeded()
        emitAll(prefs.prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The inbox.
     *
     * There is deliberately no separate "unread count" flow: the badge and the
     * list would then be two queries that can disagree for a frame, and the
     * badge exists to summarise the list. It is counted from this one, in the
     * composable that draws both.
     */
    val messages: StateFlow<List<Message>> = messageStore.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What the tracking screen shows.
     *
     * The engine owns this rather than the ViewModel mirroring it into a second
     * StateFlow: there is exactly one thing that knows whether a report went
     * out, and it is the thing that sent it.
     */
    val tracking: StateFlow<TrackingState> = engine.state

    fun startTracking() {
        if (trackingJob?.isActive == true) return
        // Read the permissions before the first tick so the ring shows the
        // right wording immediately rather than after a fix attempt.
        engine.onPermissionsChanged()
        trackingJob = viewModelScope.launch {
            // Read the settings fresh on every tick: a change to the interval or
            // the server is then picked up by the loop that is already running.
            engine.run { settings.filterNotNull().first() }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        // The loop's own finally block is what clears `running`. Wiping the
        // whole state here would also throw away the last fix and the time of
        // the last successful report, which are exactly what the screen shows
        // once tracking has stopped.
    }

    /** Called when the app may have just been granted (or lost) location. */
    fun refreshPermissions() = engine.onPermissionsChanged()

    fun acceptConsent() = viewModelScope.launch { prefs.setConsentAccepted(true) }

    fun setUseCustomHost(enabled: Boolean) = viewModelScope.launch {
        prefs.setUseCustomHost(enabled)
    }

    fun setCustomHost(host: String) = viewModelScope.launch { prefs.setCustomHost(host) }

    fun setReportIntervalMinutes(minutes: Int) = viewModelScope.launch {
        prefs.setReportIntervalMinutes(minutes)
    }

    fun markAllRead() = viewModelScope.launch { messageStore.markAllRead() }
}
