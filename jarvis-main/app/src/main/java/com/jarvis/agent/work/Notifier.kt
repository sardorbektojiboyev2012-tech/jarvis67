package com.jarvis.agent.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object Notifier {
    private const val CH = "jarvis_jobs"
    fun notify(c: Context, id: Long, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "JARVIS vazifalari", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(c, CH).setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).build()
        try { nm.notify(id.toInt(), n) } catch (_: SecurityException) { /* bildirishnoma ruxsati yo'q */ }
    }
}
