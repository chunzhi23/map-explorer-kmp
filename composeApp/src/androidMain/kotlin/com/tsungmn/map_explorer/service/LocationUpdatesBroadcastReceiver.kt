package com.tsungmn.map_explorer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.LocationResult
import com.tsungmn.map_explorer.data.AppDatabase
import com.tsungmn.map_explorer.data.PointEntity
import com.tsungmn.map_explorer.model.ExplorerAreaManager
import com.tsungmn.map_explorer.model.LocationPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver used as a PendingIntent target for location updates.
 *
 * Runs on a background coroutine and:
 *  - persists raw points to local DB (PointEntity)
 *  - adds the point to ExplorerAreaManager (incremental union)
 *
 * This receiver improves reliability for receiving updates when the app is backgrounded or killed.
 */
class LocationUpdatesBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = LocationResult.extractResult(intent) ?: return

        // Process locations on background dispatcher
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val db = AppDatabase.get(context)
                val dao = db.pointDao()
                for (loc in result.locations) {
                    // Prefer Location.time if provided; otherwise use current time
                    val ts = if (loc.time > 0L) loc.time else System.currentTimeMillis()

                    // 1) Persist raw point into DB
                    dao.insert(
                        PointEntity(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            timestamp = ts
                        )
                    )

                    // 2) Add to ExplorerAreaManager (timestamp provided)
                    try {
                        ExplorerAreaManager.addPoint(
                            LocationPoint(loc.latitude, loc.longitude),
                            timestampMs = ts
                        )
                    } catch (e: Exception) {
                        Log.w("LocationReceiver", "addPoint failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("LocationReceiver", "onReceive error: ${e.message}")
            }
        }
    }
}