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
import org.rpwt.wlwdw.data.prefs.PreferencesRepository
import org.rpwt.wlwdw.data.prefs.WlwdwPrefs

/**
 * The settings, as state the UI can read.
 *
 * Null means "not read yet", and it has to stay distinguishable from the
 * defaults: on the first frame the app does not know whether the user has
 * consented, and guessing either way is visible -- guess "yes" and a new user
 * lands in the tracking UI without consenting, guess "no" and a consented user
 * is asked again on every launch.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)

    val settings: StateFlow<WlwdwPrefs?> = flow {
        // Seed before the first emission, so the id is already there the first
        // time a screen shows it rather than appearing a frame later.
        prefs.ensureSeeded()
        emitAll(prefs.prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun acceptConsent() = viewModelScope.launch { prefs.setConsentAccepted(true) }

    fun setUseCustomHost(enabled: Boolean) = viewModelScope.launch {
        prefs.setUseCustomHost(enabled)
    }

    fun setCustomHost(host: String) = viewModelScope.launch { prefs.setCustomHost(host) }

    fun setReportIntervalMinutes(minutes: Int) = viewModelScope.launch {
        prefs.setReportIntervalMinutes(minutes)
    }
}
