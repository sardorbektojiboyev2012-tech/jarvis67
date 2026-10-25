package com.jarvis.agent.video

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Qurilmaning o'z TTS dvigateli (bepul, oflayn ovoz paketi o'rnatilgan bo'lsa internetsiz ishlaydi). */
class Tts(private val ctx: Context) {
    private var tts: TextToSpeech? = null

    suspend fun init(locale: Locale): Boolean = suspendCoroutine { c ->
        var done = false
        tts = TextToSpeech(ctx) { status ->
            if (done) return@TextToSpeech
            done = true
            val t = tts
            if (status != TextToSpeech.SUCCESS || t == null) c.resume(false)
            else {
                val r = t.setLanguage(locale)
                c.resume(r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED)
            }
        }
    }

    suspend fun synth(text: String, out: File): Boolean = suspendCancellableCoroutine { c ->
        val t = tts ?: run { c.resume(false); return@suspendCancellableCoroutine }
        val id = "u${System.nanoTime()}"
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (c.isActive) c.resume(true) }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { if (c.isActive) c.resume(false) }
        })
        if (t.synthesizeToFile(text, null, out, id) != TextToSpeech.SUCCESS && c.isActive) c.resume(false)
    }

    fun shutdown() { tts?.shutdown(); tts = null }
}
