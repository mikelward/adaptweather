package com.adaptweather.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location as AndroidLocation
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.adaptweather.core.domain.model.Location
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
    suspend fun resolve(): Location? {
        if (!hasPermission()) {
            Log.i(TAG, "Coarse location not granted; not resolving.")
            return null
        }
        val manager = context.getSystemService<LocationManager>() ?: return null

        val cached = lastKnown(manager)
        if (cached != null && cached.isFresh()) {
            return cached.toDomain()
        }

        val live = withTimeoutOrNull(timeoutMillis) { requestSingle(manager) }
        return live?.toDomain() ?: cached?.toDomain()
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): AndroidLocation? = runCatching {
        val provider = if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else if (manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            LocationManager.PASSIVE_PROVIDER
        } else {
            return null
        }
        manager.getLastKnownLocation(provider)
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun requestSingle(manager: LocationManager): AndroidLocation? =
        suspendCancellableCoroutine { cont ->
            val provider = LocationManager.NETWORK_PROVIDER
            if (!manager.isProviderEnabled(provider)) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: AndroidLocation) {
                    if (cont.isActive) {
                        runCatching { manager.removeUpdates(this) }
                        cont.resume(location)
                    }
                }
                override fun onProviderDisabled(p: String) {
                    if (cont.isActive) {
                        runCatching { manager.removeUpdates(this) }
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
            } catch (t: Throwable) {
                cont.resume(null)
            }
            cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
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
