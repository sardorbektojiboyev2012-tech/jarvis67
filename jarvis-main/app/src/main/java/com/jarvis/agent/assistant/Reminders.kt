package com.jarvis.agent.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarvis.agent.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Reminder(val id: Int, val at: Long, val text: String, val daily: Boolean, val briefing: Boolean)

/** Eslatmalar va "Jarvis sizga qo'ng'iroq qiladi": AlarmManager.setAlarmClock (aniq vaqt, maxsus ruxsat shart emas). */
object Reminders {
    private fun p(ctx: Context) = ctx.getSharedPreferences("jarvis_reminders", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Reminder> = try {
        val a = JSONArray(p(ctx).getString("list", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Reminder(o.getInt("id"), o.getLong("at"), o.optString("text"), o.optBoolean("daily"), o.optBoolean("brief")) }
    } catch (e: Exception) { emptyList() }

    private fun save(ctx: Context, l: List<Reminder>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("at", it.at).put("text", it.text).put("daily", it.daily).put("brief", it.briefing)) }
        p(ctx).edit().putString("list", a.toString()).apply()
    }

    fun add(ctx: Context, at: Long, text: String, daily: Boolean = false, briefing: Boolean = false): Reminder {
        val l = all(ctx); val r = Reminder((l.maxOfOrNull { it.id } ?: 0) + 1, at, text, daily, briefing)
        save(ctx, l + r); schedule(ctx, r); return r
    }

    fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val op = PendingIntent.getBroadcast(ctx, r.id, Intent(ctx, ReminderReceiver::class.java).putExtra("id", r.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(r.at, show), op)
    }

    private fun cancel(ctx: Context, id: Int) {
        val op = PendingIntent.getBroadcast(ctx, id, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
        if (op != null) ctx.getSystemService(AlarmManager::class.java).cancel(op)
    }

    fun remove(ctx: Context, id: Int) { cancel(ctx, id); save(ctx, all(ctx).filter { it.id != id }) }
    fun clear(ctx: Context) { all(ctx).forEach { cancel(ctx, it.id) }; save(ctx, emptyList()) }
    fun removeBriefings(ctx: Context) { all(ctx).filter { it.briefing }.forEach { remove(ctx, it.id) } }

    fun advance(ctx: Context, r: Reminder) {
        var at = r.at; val now = System.currentTimeMillis()
        while (at <= now) at += 86_400_000L
        val n = r.copy(at = at); save(ctx, all(ctx).map { if (it.id == r.id) n else it }); schedule(ctx, n)
    }

    fun scheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        all(ctx).forEach { r -> if (r.daily && r.at < now - 3_600_000L) advance(ctx, r) else schedule(ctx, r) }
    }

    fun describe(r: Reminder): String {
        val c = Calendar.getInstance().apply { timeInMillis = r.at }
        val hm = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        return if (r.briefing) "har kuni $hm hisobot" else "$hm ${r.text}"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val r = Reminders.all(ctx).firstOrNull { it.id == i.getIntExtra("id", -1) } ?: return
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (r.daily) Reminders.advance(ctx, r) else Reminders.remove(ctx, r.id)
                CallRinger.ring(ctx, if (r.briefing) Briefing.build(ctx) else r.text)
            } finally { pr.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) { if (i.action == Intent.ACTION_BOOT_COMPLETED) Reminders.scheduleAll(ctx) }
}
