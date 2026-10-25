package com.jarvis.agent.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.jarvis.agent.ui.CallActivity

/** "Jarvis sizga qo'ng'iroq qilyapti": qulflangan ekranda ham chiqadigan, qo'ng'iroqqa o'xshash to'liq ekranli bildirishnoma. */
object CallRinger {
    private const val CH = "jarvis_call"
    const val NOTIF_ID = 777

    fun ring(ctx: Context, message: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Jarvis qo'ng'irog'i", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true); vibrationPattern = longArrayOf(0, 800, 600, 800, 600, 800)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
        })
        val i = Intent(ctx, CallActivity::class.java).putExtra("msg", message).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, (System.nanoTime() % 100000).toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CH).setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("JARVIS qo'ng'iroq qilyapti").setContentText(message.take(80))
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pi, true).setContentIntent(pi).setAutoCancel(true).setTimeoutAfter(40_000).build()
        n.flags = n.flags or Notification.FLAG_INSISTENT   // jiringlash javob berilguncha takrorlanadi
        try { nm.notify(NOTIF_ID, n) } catch (_: SecurityException) {}
        if (Settings.canDrawOverlays(ctx)) try { ctx.startActivity(i) } catch (_: Exception) {}
    }

    fun dismiss(ctx: Context) { ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID) }
}
