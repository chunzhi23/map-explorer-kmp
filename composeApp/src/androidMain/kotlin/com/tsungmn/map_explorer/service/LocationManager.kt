package com.tsungmn.map_explorer.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.tsungmn.map_explorer.data.LocationRepository
import com.tsungmn.map_explorer.model.ExplorerAreaManager
import com.tsungmn.map_explorer.model.LocationPoint
import com.tsungmn.map_explorer.model.MovementState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * LocationManager
 *
 * - Coordinates location updates from FusedLocationProviderClient.
 * - Maintains a small in-process LocationCallback for UI responsiveness.
 * - Also registers a PendingIntent->BroadcastReceiver to improve background reliability.
 * - Computes buffer radius from speed categories and forwards points to ExplorerAreaManager.
 * - Switches LocationRequest profiles when movement state changes.
 *
 * All heavy geometry work runs on Dispatchers.Default via the internal coroutine scope.
 */
class LocationManager(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Background coroutine scope used for non-UI work (buffer computation, ExplorerAreaManager calls)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var isRequestingUpdates = false

    // Current movement state (used to avoid redundant request changes)
    private var currentRequestState: MovementState? = null

    private val movementDetector = MovementDetector()
    private val switchLock = Any()

    // In-process callback: provides quick updates to the app while service is alive
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleLocation(location)
        }
    }

    // PendingIntent used to register a BroadcastReceiver for background delivery
    private var pendingIntentForBroadcast: PendingIntent? = null

    /**
     * Start requesting locations if we have permission.
     * This method chooses a default request (via LocationRequestFactory).
     */
    fun startIfPermitted() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "startIfPermitted: missing location permission — not starting")
            return
        }
        startWithRequest(LocationRequestFactory.defaultRequest())
    }

    /**
     * Register both the in-process callback and a PendingIntent receiver.
     * Using both increases reliability when the app is backgrounded or killed.
     */
    private fun startWithRequest(request: LocationRequest) {
        if (!hasLocationPermission()) return
        if (isRequestingUpdates) return

        try {
            // In-process callback for fast UI updates
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())

            // Also register a PendingIntent to a BroadcastReceiver so updates can arrive while app is backgrounded
            val intent = Intent(context, LocationUpdatesBroadcastReceiver::class.java)
            val piFlags =
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            pendingIntentForBroadcast = PendingIntent.getBroadcast(context, 0, intent, piFlags)
            fusedClient.requestLocationUpdates(request, pendingIntentForBroadcast!!)

            isRequestingUpdates = true
        } catch (se: SecurityException) {
            Log.w(TAG, "startWithRequest: SecurityException: ${se.message}")
        } catch (e: Exception) {
            Log.w(TAG, "startWithRequest: failed: ${e.message}")
        }
    }

    /**
     * Stop receiving location updates and clean up.
     */
    fun stop() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
            pendingIntentForBroadcast?.let {
                fusedClient.removeLocationUpdates(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "stop: removeLocationUpdates failed: ${e.message}")
        } finally {
            isRequestingUpdates = false
            currentRequestState = null
            // Note: we keep the coroutine scope alive so ExplorerAreaManager autosave / other work may continue if needed.
        }
    }

    /**
     * Handle a new Location instance:
     * - filter by accuracy,
     * - update MovementDetector and possibly switch the request profile,
     * - update the UI repository (LocationRepository),
     * - compute buffer radius and forward to ExplorerAreaManager on background scope.
     */
    private fun handleLocation(location: Location) {
        // Ignore low-accuracy fixes
        if (location.accuracy > 40f) return

        // Movement detection may suggest switching request parameters (walking, biking, driving, etc.)
        val newState = movementDetector.onLocation(location)
        if (newState != null) switchRequestForState(newState)

        // Update UI-facing in-memory repository so Compose can react quickly
        LocationRepository.update(LocationPoint(location.latitude, location.longitude))

        // Offload heavier work to background scope:
        // - compute buffer radius (discrete categories)
        // - forward to ExplorerAreaManager (which performs JTS union)
        scope.launch {
            try {
                val bufferMeters = computeBufferFromSpeed(location.speed)
                // Use Location.time if available; otherwise use current time
                val ts = if (location.time > 0) location.time else System.currentTimeMillis()
                ExplorerAreaManager.addPoint(
                    LocationPoint(location.latitude, location.longitude),
                    bufferMeters,
                    ts
                )
            } catch (e: Exception) {
                Log.w(TAG, "addPoint failed: ${e.message}")
            }
        }
    }

    /**
     * Map measured speed (m/s) to a discrete buffer radius (meters).
     * This is a "digital" (bucketed) mapping, not a continuous function.
     *
     * Buckets (km/h):
     *  - < 6    -> WALK/STOP  (large radius to emphasize explored area)
     *  - < 25   -> BIKE
     *  - < 70   -> ROAD (city/national)
     *  - < 130  -> HIGHWAY
     *  - >=130  -> TRAIN / very fast
     *
     * Returns a safe radius (>= 3.0 m).
     */
    private fun computeBufferFromSpeed(speed: Float): Double {
        val safeSpeed = if (speed.isFinite() && speed >= 0f) speed else 0f
        val kmh = safeSpeed * 3.6f

        val radius = when {
            kmh < 6.0f -> 40.0   // walk / stopped
            kmh < 25.0f -> 28.0  // bike
            kmh < 70.0f -> 18.0  // road
            kmh < 130.0f -> 12.0 // highway
            else -> 8.0          // train / very fast
        }

        return max(3.0, radius)
    }

    /**
     * Switch LocationRequest profile when movement state changes.
     * We remove previous updates then request new ones with the LocationRequestFactory for the new state.
     */
    private fun switchRequestForState(state: MovementState) {
        synchronized(switchLock) {
            // If already in desired state, nothing to do
            if (isRequestingUpdates && currentRequestState == state) return
            if (!hasLocationPermission()) return

            try {
                fusedClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                Log.w(TAG, "switch: remove failed: ${e.message}")
            } finally {
                isRequestingUpdates = false
                currentRequestState = null
            }

            val request = LocationRequestFactory.requestForState(state)
            try {
                fusedClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
                isRequestingUpdates = true
                currentRequestState = state
            } catch (se: SecurityException) {
                Log.w(TAG, "switch: missing permission during request: ${se.message}")
                isRequestingUpdates = false
                currentRequestState = null
            } catch (e: Exception) {
                Log.w(TAG, "switch: request failed: ${e.message}")
                isRequestingUpdates = false
                currentRequestState = null
            }
        }
    }

    /**
     * Check whether the app has at least one of fine/coarse location permissions.
     */
    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        private const val TAG = "LocationManager"
    }
}