package org.rpwt.wlwdw.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One position fix, in WGS-84.
 *
 * WGS-84 unconditionally: this is the platform's own coordinate system and the
 * one the server stores, so there is nothing to convert and no `CoorType`
 * setting to get wrong. That is the whole point of having dropped the Baidu SDK
 * -- the pre-rewrite app had to convert BD-09 back to WGS-84 before reporting.
 */
data class Fix(
    val lat: Double,
    val lng: Double,
    /** Horizontal accuracy in metres; null when the provider does not report one. */
    val accuracyMetres: Float?,
    /** `gps` / `network` / `passive`, as Android names them. */
    val provider: String,
    /** When the fix was taken, on this device's clock. */
    val atMillis: Long,
)

/**
 * Where the device is, from the platform's location service.
 *
 * Deliberately not FusedLocationProviderClient: that would pull in Google Play
 * Services, which is both a privacy cost for a tracker and a hard dependency
 * that does not exist on AOSP builds (including the emulator this is tested
 * on). `LocationManagerCompat` is enough for "where am I, every few minutes".
 */
class LocationSource(private val context: Context) {

    private val manager: LocationManager?
        get() = context.getSystemService(LocationManager::class.java)

    /** Granted while the app is in use. */
    fun hasForegroundPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * Granted to run with the screen off.
     *
     * Always true below Android 10, where the permission did not exist and
     * being granted location at all was enough.
     */
    fun hasBackgroundPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /**
     * The best fix available now, or null if none can be had.
     *
     * Providers are tried in order rather than asking one: `gps` is the only
     * source that is both present on every device and a true WGS-84 fix, but it
     * can take a while indoors, and `network` answers immediately with a coarse
     * one. A report with a 2 km fix beats no report. The last resort is the
     * last known position, which the caller can see is old from [Fix.atMillis].
     */
    suspend fun current(): Fix? {
        val manager = manager ?: return null
        for (provider in PROVIDER_ORDER) {
            if (!isEnabled(manager, provider)) continue
            val fix = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { requestOnce(manager, provider) }
            if (fix != null) return fix
        }
        return lastKnown(manager)
    }

    private suspend fun requestOnce(manager: LocationManager, provider: String): Fix? =
        suspendCancellableCoroutine { continuation ->
            val signal = android.os.CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location?.let(::toFix))
                }
            } catch (e: SecurityException) {
                // Permission was revoked between the check and the call.
                if (continuation.isActive) continuation.resume(null)
            } catch (e: IllegalArgumentException) {
                // Provider not on this device after all.
                if (continuation.isActive) continuation.resume(null)
            }
        }

    /** The newest fix any provider is still holding, however old it is. */
    private fun lastKnown(manager: LocationManager): Fix? =
        PROVIDER_ORDER
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let(::toFix)

    private fun isEnabled(manager: LocationManager, provider: String): Boolean =
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun toFix(location: Location) = Fix(
        lat = location.latitude,
        lng = location.longitude,
        accuracyMetres = if (location.hasAccuracy()) location.accuracy else null,
        provider = location.provider.orEmpty(),
        atMillis = location.time,
    )

    private companion object {
        /** GPS first: see the note on [current]. */
        val PROVIDER_ORDER = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )

        /** Per-provider budget. The interval between reports is minutes, so this is cheap. */
        const val PROVIDER_TIMEOUT_MS = 20_000L
    }
}
