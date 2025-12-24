package com.tsungmn.map_explorer.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.tsungmn.map_explorer.data.AppDatabase
import com.tsungmn.map_explorer.model.ExplorerAreaManager
import com.tsungmn.map_explorer.model.LocationPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground Service that keeps location tracking alive.
 *
 * Responsibilities:
 * - Create and manage LocationManager.
 * - Load previously persisted geometry on start.
 * - Optionally rebuild geometry from DB on first run.
 * - Periodically autosave ExplorerAreaManager geometry to disk to survive process death.
 */
class LocationService : Service() {
    private lateinit var locationManager: LocationManager

    // service-scoped coroutine for background tasks (rebuild, autosave)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var autosaveJob: Job? = null
    private val AUTOSAVE_INTERVAL_MS = 30_000L // autosave every 30 seconds

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        locationManager = LocationManager(this)

        // 1) Try to load persisted geometry from file
        try {
            ExplorerAreaManager.loadFromFile(filesDir)
        } catch (e: Exception) {
            // ignore; fall back to DB rebuild if needed
        }

        // 2) If no geometry exists, rebuild from DB in background
        serviceScope.launch {
            val currentArea = ExplorerAreaManager.exploredAreaMeters()
            if (currentArea <= 0.0) {
                try {
                    val db = AppDatabase.get(applicationContext)
                    val dao = db.pointDao()
                    val rows = dao.getAll()
                    if (rows.isNotEmpty()) {
                        // Convert DB rows to LocationPoint
                        val pts = rows.map { pe -> LocationPoint(pe.latitude, pe.longitude) }
                        // Sample if extremely large to avoid huge memory usage
                        val sampled = if (pts.size > 20_000) {
                            val step = pts.size / 20_000
                            pts.filterIndexed { idx, _ -> idx % step == 0 }
                        } else pts

                        // Rebuild geometry from points (runs on Dispatchers.Default)
                        ExplorerAreaManager.rebuildFromPoints(sampled)
                    }
                } catch (e: Exception) {
                    // ignore or log
                }
            }
        }

        // 3) Start periodic autosave
        autosaveJob = serviceScope.launch {
            while (isActive) {
                try {
                    ExplorerAreaManager.saveToFile(filesDir)
                } catch (e: Exception) {
                    // ignore or log
                }
                delay(AUTOSAVE_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationHelper.NOTIF_ID, NotificationHelper.buildNotification(this))
        locationManager.startIfPermitted()
        return START_STICKY
    }

    override fun onDestroy() {
        // final save (blocking) before shutdown
        try {
            runBlocking {
                try {
                    ExplorerAreaManager.saveToFile(filesDir)
                } catch (e: Exception) {
                    // ignore
                }
            }
        } catch (_: Exception) {
        }

        autosaveJob?.cancel()
        serviceScope.cancel()
        locationManager.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}