package com.jarvis.agent.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/** Jarvis ovozi: Azure (o'zbek yigit) bo'lsa shu, bo'lmasa telefonning o'z ovozi (aksent yomonroq). */
object Voice {
    /** @return ogohlantirish matni yoki null */
    suspend fun speak(ctx: Context, text: String): String? {
        val azure = AzureTts.configured(ctx)
        if (azure) {
            val f = File(ctx.cacheDir, "say_${System.nanoTime()}.mp3")
            val ok = withContext(Dispatchers.IO) { AzureTts.synth(ctx, text, f) }
            if (ok) { play(f); f.delete(); return null }
            f.delete()
        }
        speakLocal(ctx, text)
        return if (azure) "Azure ovozi ishlamadi (kalit/region/internet?), telefon ovozi ishlatildi" else null
    }

    private suspend fun play(f: File) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { c ->
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setDataSource(f.path)
                mp.setOnCompletionListener { it.release(); if (c.isActive) c.resume(Unit) }
                mp.setOnErrorListener { m, _, _ -> m.release(); if (c.isActive) c.resume(Unit); true }
                mp.prepare(); mp.start()
            } catch (e: Exception) { mp.release(); if (c.isActive) c.resume(Unit) }
            c.invokeOnCancellation { try { mp.release() } catch (_: Exception) {} }
        }
    }

    private suspend fun speakLocal(ctx: Context, text: String) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { c ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(ctx) { st ->
                val t = tts
                if (st != TextToSpeech.SUCCESS || t == null) { if (c.isActive) c.resume(Unit); return@TextToSpeech }
                val loc = listOf(Locale("uz"), Locale("tr"), Locale.getDefault())
                    .firstOrNull { t.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: Locale.getDefault()
                t.language = loc; t.setPitch(0.85f)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { t.shutdown(); if (c.isActive) c.resume(Unit) }
                    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { t.shutdown(); if (c.isActive) c.resume(Unit) }
                })
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
            }
            c.invokeOnCancellation { tts?.shutdown() }
        }
    }
}
