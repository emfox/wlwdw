package org.rpwt.wlwdw.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.rpwt.wlwdw.data.MessageRepository
import org.rpwt.wlwdw.data.model.Message
import org.rpwt.wlwdw.data.prefs.PreferencesRepository
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs

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
