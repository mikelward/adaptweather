package app.clothescast.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location as AndroidLocation
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import app.clothescast.diag.DiagLog
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.clothescast.core.domain.model.Location
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Resolves the device's current coarse location using [LocationManager.NETWORK_PROVIDER]
 * — cell-tower + WiFi-based positioning, no GPS hardware fix needed, low battery cost.
 *
 * Strategy:
 * 1. If we don't have ACCESS_COARSE_LOCATION granted (foreground or background as
 *    appropriate), return null without trying.
 * 2. Try `getLastKnownLocation` first; if it's recent (< [maxAgeMillis]) we use it.
 * 3. Otherwise request a single update with a [timeoutMillis] cap so the Worker can
 *    fall back to the user's settings location quickly when the device is offline or
 *    the network provider isn't available.
 *
 * Returns null on every failure path; callers are expected to fall back to a
 * configured or default location.
 */
class LocationResolver(
    private val context: Context,
    private val maxAgeMillis: Long = 60 * 60 * 1000L,
    private val timeoutMillis: Long = 10_000L,
) {
    /**
     * Returns the device's current location or null on any failure path.
     *
     * This is called from the daily WorkManager job — which has no surrounding
     * try/catch — so it must be the firewall: any exception thrown by a
     * `LocationManager` API (`SecurityException` from a missing background
     * grant, `IllegalArgumentException` from a non-existent provider on
     * unusual devices, etc.) is caught and logged here rather than crashing
     * the worker. Callers can rely on `resolve()` never throwing.
     */
    suspend fun resolve(): Location? = try {
        resolveInner()
    } catch (t: Throwable) {
        DiagLog.w(TAG, "Unexpected ${t.javaClass.simpleName} from resolve(); returning null.", t)
        null
    }

    private suspend fun resolveInner(): Location? {
        if (!hasCoarsePermission()) {
            DiagLog.i(TAG, "Coarse location not granted; not resolving.")
            return null
        }
        // The Worker runs in the background, so a missing ACCESS_BACKGROUND_LOCATION
        // grant will make the OS throw SecurityException from any location call —
        // log it up front so the cause is obvious in DiagLog instead of looking
        // like a generic "location returned null".
        if (!hasBackgroundPermission()) {
            DiagLog.w(TAG, "Background location not granted; calls from the worker will fail.")
        }
        val manager = context.getSystemService<LocationManager>()
        if (manager == null) {
            DiagLog.w(TAG, "LocationManager unavailable on this device.")
            return null
        }

        val cached = lastKnown(manager)
        if (cached != null && cached.isFresh()) {
            return cached.toDomain()
        }

        val live = withTimeoutOrNull(timeoutMillis) { requestSingle(manager) }
        if (live == null && cached == null) {
            DiagLog.w(TAG, "No live or cached location available within ${timeoutMillis}ms.")
        }
        return live?.toDomain() ?: cached?.toDomain()
    }

    private fun hasCoarsePermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): AndroidLocation? = try {
        val provider = if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else if (manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            DiagLog.i(TAG, "NETWORK provider disabled; falling back to PASSIVE for last-known.")
            LocationManager.PASSIVE_PROVIDER
        } else {
            DiagLog.i(TAG, "Neither NETWORK nor PASSIVE provider enabled; no cached location.")
            null
        }
        if (provider == null) null else manager.getLastKnownLocation(provider)
    } catch (t: SecurityException) {
        DiagLog.w(TAG, "SecurityException reading last-known location (background grant?).", t)
        null
    } catch (t: Throwable) {
        DiagLog.w(TAG, "Failed to read last-known location: ${t.javaClass.simpleName}", t)
        null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingle(manager: LocationManager): AndroidLocation? =
        suspendCancellableCoroutine { cont ->
            val provider = LocationManager.NETWORK_PROVIDER
            // isProviderEnabled is documented to throw IllegalArgumentException
            // on devices that don't know the provider name. resolve() catches
            // the resulting throw at the outer level, but resuming with null
            // here keeps the contract local and avoids cancelling the
            // coroutine via exception.
            val providerEnabled = try {
                manager.isProviderEnabled(provider)
            } catch (t: Throwable) {
                DiagLog.w(TAG, "isProviderEnabled threw ${t.javaClass.simpleName}; treating as disabled.", t)
                false
            }
            if (!providerEnabled) {
                DiagLog.i(TAG, "NETWORK provider disabled; skipping single-update request.")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: AndroidLocation) {
                    if (cont.isActive) {
                        safeRemoveUpdates(manager, this)
                        cont.resume(location)
                    }
                }
                override fun onProviderDisabled(p: String) {
                    if (cont.isActive) {
                        DiagLog.i(TAG, "NETWORK provider disabled mid-request.")
                        safeRemoveUpdates(manager, this)
                        cont.resume(null)
                    }
                }
                @Deprecated("Required override; never called on API 33+ but keep for older devices.")
                override fun onStatusChanged(p: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
            }
            try {
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (t: SecurityException) {
                DiagLog.w(TAG, "SecurityException requesting location update (background grant?).", t)
                cont.resume(null)
            } catch (t: Throwable) {
                DiagLog.w(TAG, "Failed to request location update: ${t.javaClass.simpleName}", t)
                cont.resume(null)
            }
            cont.invokeOnCancellation { safeRemoveUpdates(manager, listener) }
        }

    private fun safeRemoveUpdates(manager: LocationManager, listener: LocationListener) {
        try {
            manager.removeUpdates(listener)
        } catch (t: Throwable) {
            DiagLog.v(TAG, "removeUpdates threw ${t.javaClass.simpleName}; ignoring.", t)
        }
    }

    private fun AndroidLocation.isFresh(): Boolean =
        System.currentTimeMillis() - time < maxAgeMillis

    private fun AndroidLocation.toDomain(): Location = Location(
        latitude = latitude,
        longitude = longitude,
        displayName = "Device location",
    )

    companion object {
        private const val TAG = "LocationResolver"
    }
}
