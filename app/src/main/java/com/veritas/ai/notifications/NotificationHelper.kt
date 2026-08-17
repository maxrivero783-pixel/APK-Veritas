package com.veritas.ai.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.veritas.ai.MainActivity

/**
 * Pure Android notification helper. No FCM, no Google services.
 * Creates system notifications for the polling-based push system.
 */
object NotificationHelper {

    const val CHANNEL_ID = "veritas_notifications"
    const val CHANNEL_NAME = "Veritas AI"
    const val CHANNEL_DESC = "Alertas OSINT, resultados async y notificaciones del sistema"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        context: Context,
        title: String,
        body: String,
        deepLink: String? = null,
        notificationId: Int = System.currentTimeMillis().toInt()
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            deepLink?.let { putExtra("deep_link", it) }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(context, notificationId, intent, flags)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()
    }

    fun showNotification(context: Context, title: String, body: String, deepLink: String? = null) {
        createNotificationChannels(context)
        val id = System.currentTimeMillis().toInt()
        val notification = buildNotification(context, title, body, deepLink, id)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }
}