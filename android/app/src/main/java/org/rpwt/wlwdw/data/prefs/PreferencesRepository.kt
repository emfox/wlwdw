package org.rpwt.wlwdw.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * One DataStore for the whole app.
 *
 * The delegate has to be a top-level property or every call would open a second
 * instance over the same file, which DataStore refuses outright.
 */
private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore(name = "wlwdw_prefs")

/**
 * The settings, read as a [Flow] and written one key at a time.
 *
 * Replaces the `SharedPreferences` the pre-rewrite app used: DataStore is
 * transactional and reports its reads as a value that can be collected, so the
 * UI observes a setting instead of polling it and no screen keeps a copy that
 * can fall out of step with what is on disk.
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        /** Marks that first-run seeding has happened; see [ensureSeeded]. */
        val seeded = booleanPreferencesKey("seeded")
        val deviceId = stringPreferencesKey("device_id")
        val consentAccepted = booleanPreferencesKey("consent_accepted")
        val useCustomHost = booleanPreferencesKey("use_custom_host")
        val customHost = stringPreferencesKey("custom_host")
        val reportIntervalMinutes = intPreferencesKey("report_interval_minutes")
    }

    val prefs: Flow<WlwdwPrefs> = context.prefsStore.data
        .catch { cause ->
            // An unreadable or corrupt file must not take the app down. Falling
            // back to the defaults is the safe direction: the worst case is
            // asking for consent again, versus rendering settings the user
            // never chose.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map(::toPrefs)

    /**
     * Give every setting a value, once.
     *
     * Anything the pre-rewrite app left in `SharedPreferences` is imported, so
     * an update keeps the settings the user had made rather than silently
     * resetting them.
     */
    suspend fun ensureSeeded() {
        if (context.prefsStore.data.first()[Keys.seeded] == true) return

        val legacy = LegacyPreferences.read(context)
        context.prefsStore.edit { prefs ->
            // The device id is the one value that has to survive the rewrite. It
            // must keep matching Category.devid on the server, or the device
            // quietly starts reporting under a new identity and disappears from
            // the web admin.
            prefs[Keys.deviceId] = legacy.deviceId?.takeIf { it.isNotEmpty() }
                ?: UUID.randomUUID().toString()
            prefs[Keys.consentAccepted] = legacy.consentAccepted ?: false
            prefs[Keys.useCustomHost] = legacy.useCustomHost ?: false
            prefs[Keys.customHost] = legacy.customHost?.takeIf { it.isNotEmpty() }
                ?: WlwdwPrefs.DEFAULT_HOST
            prefs[Keys.reportIntervalMinutes] = legacy.reportIntervalMinutes
                ?.takeIf { it in WlwdwPrefs.INTERVAL_CHOICES_MINUTES }
                ?: WlwdwPrefs.DEFAULT_INTERVAL_MINUTES
            prefs[Keys.seeded] = true
        }
    }

    suspend fun setConsentAccepted(accepted: Boolean) {
        context.prefsStore.edit { it[Keys.consentAccepted] = accepted }
    }

    suspend fun setUseCustomHost(enabled: Boolean) {
        context.prefsStore.edit { it[Keys.useCustomHost] = enabled }
    }

    /** Ignores an empty host: a blank server address is not a usable setting. */
    suspend fun setCustomHost(host: String) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return
        context.prefsStore.edit { it[Keys.customHost] = trimmed }
    }

    /** Ignores a value outside [WlwdwPrefs.INTERVAL_CHOICES_MINUTES]. */
    suspend fun setReportIntervalMinutes(minutes: Int) {
        if (minutes !in WlwdwPrefs.INTERVAL_CHOICES_MINUTES) return
        context.prefsStore.edit { it[Keys.reportIntervalMinutes] = minutes }
    }

    private fun toPrefs(prefs: Preferences) = WlwdwPrefs(
        deviceId = prefs[Keys.deviceId].orEmpty(),
        consentAccepted = prefs[Keys.consentAccepted] ?: false,
        useCustomHost = prefs[Keys.useCustomHost] ?: false,
        customHost = prefs[Keys.customHost] ?: WlwdwPrefs.DEFAULT_HOST,
        reportIntervalMinutes = prefs[Keys.reportIntervalMinutes]
            ?: WlwdwPrefs.DEFAULT_INTERVAL_MINUTES,
    )
}

/**
 * What the pre-rewrite app left behind, read once by [PreferencesRepository.ensureSeeded].
 *
 * Two files are involved because the old app wrote them two different ways: the
 * settings screen went through `PreferenceManager`, whose default file is
 * `<package>_preferences`, while the consent flag went into its own
 * `share_data` file. Both are addressed by name here rather than through
 * `PreferenceManager`, so the androidx.preference dependency the rewrite
 * dropped does not have to come back for a one-time read.
 */
private object LegacyPreferences {

    private const val SETTINGS_SUFFIX = "_preferences"
    private const val SHARE_DATA = "share_data"

    private const val KEY_DEVICE_ID = "app_uuid"
    private const val KEY_USE_CUSTOM_HOST = "enable_custom_host"
    private const val KEY_CUSTOM_HOST = "custom_host"
    private const val KEY_REPORT_INTERVAL = "sync_frequency"
    private const val KEY_CONSENT = "privacy_status"

    /** A null field means the old app never wrote that setting. */
    data class Legacy(
        val deviceId: String?,
        val consentAccepted: Boolean?,
        val useCustomHost: Boolean?,
        val customHost: String?,
        val reportIntervalMinutes: Int?,
    )

    fun read(context: Context): Legacy {
        val settings = context.getSharedPreferences(
            context.packageName + SETTINGS_SUFFIX,
            Context.MODE_PRIVATE,
        )
        val shareData = context.getSharedPreferences(SHARE_DATA, Context.MODE_PRIVATE)

        return Legacy(
            deviceId = settings.getString(KEY_DEVICE_ID, null),
            // The old app set this to "1" once its privacy notice was accepted.
            consentAccepted = shareData.getString(KEY_CONSENT, null)?.let { it == "1" },
            // contains() first: getBoolean returns false for an absent key, which
            // would be indistinguishable from a deliberate "off".
            useCustomHost = if (settings.contains(KEY_USE_CUSTOM_HOST)) {
                settings.getBoolean(KEY_USE_CUSTOM_HOST, false)
            } else {
                null
            },
            customHost = settings.getString(KEY_CUSTOM_HOST, null),
            // Stored as a string by the old ListPreference.
            reportIntervalMinutes = settings.getString(KEY_REPORT_INTERVAL, null)?.toIntOrNull(),
        )
    }
}
