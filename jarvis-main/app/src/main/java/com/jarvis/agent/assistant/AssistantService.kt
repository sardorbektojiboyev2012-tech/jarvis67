package com.jarvis.agent.assistant

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jarvis.agent.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Har doim ishlaydigan xizmat: "Jarvis" so'zini kutadi -> salom beradi -> buyruqni tinglaydi -> bajaradi. */
class AssistantService : Service() {
    companion object {
        val running = MutableStateFlow(false)
        val log = MutableStateFlow("")
        private const val ACTION_STOP = "jarvis.stop"
        private const val CH = "jarvis_listen"
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, AssistantService::class.java))
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, AssistantService::class.java)) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PorcupineManager? = null
    @Volatile private var busy = false
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        goForeground()
        if (wake == null) startWake()
        return START_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Jarvis tinglash", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, AssistantService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CH).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS tinglayapti").setContentText("«Jarvis» deb chaqiring")
            .setContentIntent(open).addAction(0, "To'xtatish", stop).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
    }

    private fun startWake() {
        val key = Secrets.picovoice(this)
        if (key == null) { log.value = "Picovoice AccessKey kiritilmagan"; stopSelf(); return }
        try {
            wake = PorcupineManager.Builder().setAccessKey(key).setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(0.7f).build(this, PorcupineManagerCallback { onWake() })
            wake?.start()
            wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:listen").apply { acquire() }
            running.value = true; log.value = "Tinglayapti"
        } catch (e: Exception) { log.value = "Uyg'otish so'zi xatosi: ${e.message}"; stopSelf() }
    }

    private fun onWake() {
        if (busy) return
        busy = true
        scope.launch {
            try { wake?.stop(); delay(300); session() }
            catch (e: Exception) { log.value = "Xato: ${e.message}" }
            finally { busy = false; try { wake?.start() } catch (_: Exception) {} }
        }
    }

    private suspend fun say(t: String) { Voice.speak(this, t)?.let { log.value = it } }

    private suspend fun hear(): String? {
        for (lang in listOf("uz-UZ", "ru-RU", "en-US")) {
            val h = Listen.once(this, lang)
            if (h.text != null) return h.text
            if (h.error != 12 && h.error != 13) return null   // faqat "til qo'llanmaydi" xatosida keyingi tilga o'tamiz
        }
        return null
    }

    private suspend fun session() {
        say("Salom, qanday yordam bera olaman?")
        var turn = 0
        while (turn++ < 4) {
            val heard = hear()
            if (heard == null) { if (turn == 1) say("Eshitmadim. Chaqirsangiz, shu yerdaman."); return }
            log.value = "Eshitildi: $heard"
            var r = Commands.handle(this, heard)
            val confirm = r.confirm
            if (confirm != null) {
                say(r.text)
                val ans = hear()
                r = if (ans != null && Commands.isYes(ans)) confirm() else Reply("Bekor qildim.")
            }
            say(r.text)
            if (r.end) return
        }
    }

    override fun onDestroy() {
        scope.cancel()
        try { wake?.stop(); wake?.delete() } catch (_: Exception) {}
        wake = null
        try { wl?.release() } catch (_: Exception) {}
        running.value = false
        super.onDestroy()
    }
}
