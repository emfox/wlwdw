package org.rpwt.wlwdw.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import org.rpwt.wlwdw.data.model.Report
import org.rpwt.wlwdw.data.prefs.PreferencesRepository
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs
import org.rpwt.wlwdw.push.PushState
import org.rpwt.wlwdw.service.WlwdwService
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
    private val reportLog = ReportRepository(application)

    init {
        // Read the report log before the first frame, so the status screen can
        // say when it last worked even though this process has never reported
        // anything itself. The answer lives in storage, so asking only works
        // before the loop starts -- afterwards the loop owns it.
        viewModelScope.launch { WlwdwService.restoreLastSuccess(application) }
        // The background service starts as soon as the device has consented and
        // so has an identity to authenticate as, not when a screen first wants a
        // message: a message that arrives while the app is closed is the entire
        // point. It carries the reporting loop too, which resumes by itself if
        // the stored intent says tracking was on -- the process being killed is
        // not the user stopping anything.
        //
        // Read from the store directly rather than [settings]: this init block
        // runs before the properties declared below it exist, and viewModelScope
        // dispatches immediately, so the first body ran while `settings` was
        // still null. That was a crash on launch, not a late start.
        viewModelScope.launch {
            prefs.prefs.first { it.consentAccepted }
            WlwdwService.start(application)
        }
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
     * The report log, for the diagnostics page and for the settings row that
     * counts it.
     *
     * One flow for both, as with [messages]: the row's number is then a count
     * of the list behind the page it opens, and the two cannot disagree.
     *
     * Read from storage rather than mirrored off [tracking], which is the
     * reason this screen exists at all: the state holds the last attempt, and
     * the question "did last night work" is asked the morning after.
     */
    val reports: StateFlow<List<Report>> = reportLog.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What the tracking screen shows.
     *
     * The foreground service owns this rather than the ViewModel: the loop has
     * to outlive this screen, so the thing that runs it is the thing that knows
     * whether a report went out. The screen and the permanent notification both
     * read this one flow, which is why they cannot disagree.
     */
    val tracking: StateFlow<TrackingState> = WlwdwService.tracking

    /**
     * What the MQTT link is doing, straight from the component that owns it.
     *
     * Not mirrored into a second flow: there is one thing in the process that
     * knows whether the broker can reach this device, and it is the thing
     * holding the connection. It is read through the service so a screen has one
     * place to look for the app's background state.
     */
    val push: StateFlow<PushState> = WlwdwService.push

    fun startTracking() {
        viewModelScope.launch {
            // Read the permissions before the loop's first tick so the ring shows
            // the right wording immediately rather than after a fix attempt.
            WlwdwService.permissionsChanged(getApplication())
            WlwdwService.startTracking(getApplication())
        }
    }

    fun stopTracking() {
        // Stops the loop and nothing else: the service stays up for the push
        // link, and the loop's own teardown clears `running`. Wiping the whole
        // state here would also throw away the last fix and the time of the last
        // successful report, which are exactly what the screen shows once
        // tracking has stopped.
        viewModelScope.launch { WlwdwService.stopTracking(getApplication()) }
    }

    /** Called when the app may have just been granted (or lost) location. */
    fun refreshPermissions() = WlwdwService.permissionsChanged(getApplication())

    fun acceptConsent() = viewModelScope.launch { prefs.setConsentAccepted(true) }

    fun setUseCustomHost(enabled: Boolean) = viewModelScope.launch {
        prefs.setUseCustomHost(enabled)
    }

    fun setCustomHost(host: String) = viewModelScope.launch { prefs.setCustomHost(host) }

    fun setReportIntervalMinutes(minutes: Int) = viewModelScope.launch {
        prefs.setReportIntervalMinutes(minutes)
    }

    fun setStartOnBoot(enabled: Boolean) = viewModelScope.launch { prefs.setStartOnBoot(enabled) }

    fun markAllRead() = viewModelScope.launch { messageStore.markAllRead() }
}
