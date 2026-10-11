package org.rpwt.wlwdw.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.rpwt.wlwdw.data.prefs.PreferencesRepository
import org.rpwt.wlwdw.service.WlwdwService

/**
 * Starts reporting again after the phone reboots, if the user asked for that.
 *
 * Off unless the setting is on, and the setting is off when it is absent. A
 * tracker that resumes sending its owner's position after every reboot without
 * having been asked to is making a decision about somebody else's phone.
 *
 * ## Why this can work at all
 *
 * Android 12 stopped apps in the background from starting foreground services,
 * with a list of exemptions -- and `ACTION_BOOT_COMPLETED` is on it. That alone
 * is not enough here, because the service is `location`-typed, and a
 * `location` foreground service needs a *while-in-use* permission, which an app
 * in the background does not have. The while-in-use section has its own
 * exemption list, and a boot receiver is **not** on it.
 *
 * The way through is the documented exception for this exact case: a
 * `location`-typed service started by an app that holds
 * `ACCESS_BACKGROUND_LOCATION` may access location even while the app runs in
 * the background. So the one hard prerequisite is that permission, and this
 * receiver checks for it rather than assuming -- starting without it throws
 * `SecurityException`, which at boot would be a crash loop.
 *
 * Everything is also wrapped: a platform rule that changes under us should cost
 * one skipped boot, not a phone that cannot start the app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // The receiver is torn down as soon as onReceive returns, so the work
        // has to be pinned to it. goAsync() buys the ~10 seconds a DataStore
        // read and a service start need; the coroutine runs off the main
        // thread, which is what keeps this from being a strict-mode violation.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (val decision = decide(appContext)) {
                    Decision.Start -> {
                        Log.i(TAG, "starting tracking after boot")
                        // This also records the intent, so the service that comes
                        // up reads the same answer it would have from the switch.
                        // One consequence of the merge, worth stating: starting
                        // the service also starts the push link, so a device with
                        // "start on boot" on is now reachable for messages from
                        // boot as well. That is a change from the two-service
                        // build, where only tracking came back.
                        WlwdwService.startTracking(appContext)
                    }
                    is Decision.Skip -> Log.i(TAG, "not starting after boot: ${decision.why}")
                }
            } catch (e: Exception) {
                // SecurityException / ForegroundServiceStartNotAllowedException
                // both land here. Neither is worth a crash at boot.
                Log.w(TAG, "could not start tracking after boot", e)
            } finally {
                pending.finish()
            }
        }
    }

    private sealed interface Decision {
        data object Start : Decision
        data class Skip(val why: String) : Decision
    }

    private suspend fun decide(context: Context): Decision {
        val prefs = PreferencesRepository(context).prefs.first()

        // Consent first, and separately from the switch. The switch can only be
        // turned on from the settings screen, which is behind the consent gate,
        // so this cannot fail today -- which is exactly why it is worth writing
        // down: "never report without consent" should not depend on the current
        // shape of the navigation graph.
        if (!prefs.consentAccepted) return Decision.Skip("consent was never given")

        if (!prefs.startOnBoot) return Decision.Skip("the setting is off")

        // The prerequisite for a location foreground service created while the
        // app is in the background; see the class comment.
        if (!has(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            return Decision.Skip("no background location permission")
        }
        if (!has(context, android.Manifest.permission.ACCESS_FINE_LOCATION) &&
            !has(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            return Decision.Skip("no location permission")
        }
        return Decision.Start
    }

    private fun has(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "WlwdwBoot"
    }
}
