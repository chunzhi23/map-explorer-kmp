package com.tsungmn.map_explorer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tsungmn.map_explorer.MainActivity
import com.tsungmn.map_explorer.R

/**
 * Notification helper utilities for the foreground location service.
 * - createNotificationChannel: create channel on Android O+
 * - buildNotification: construct ongoing foreground notification with PendingIntent to open app
 */
object NotificationHelper {
    const val NOTIF_ID = 0x1001
    const val CHANNEL_ID = "location_channel"
    const val NOTIF_TITLE = "Tracking location"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background location tracking channel"
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun buildNotification(context: Context): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingIntentFlags
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(NOTIF_TITLE)
            .setContentText("App is collecting location.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}