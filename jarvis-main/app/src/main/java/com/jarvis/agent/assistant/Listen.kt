package com.jarvis.agent.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class Heard(val text: String?, val error: Int)

/** Google nutq tanish xizmati (internet kerak). Til: uz-UZ. */
object Listen {
    suspend fun once(ctx: Context, lang: String): Heard = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return@withContext Heard(null, -2)
        withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine<Heard> { c ->
                val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(b: Bundle?) {
                        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        sr.destroy(); if (c.isActive) c.resume(Heard(t, -1))
                    }
                    override fun onError(e: Int) { sr.destroy(); if (c.isActive) c.resume(Heard(null, e)) }
                    override fun onReadyForSpeech(p: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(v: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(p: Bundle?) {}
                    override fun onEvent(t: Int, p: Bundle?) {}
                })
                sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
                c.invokeOnCancellation { try { sr.destroy() } catch (_: Exception) {} }
            }
        } ?: Heard(null, -1)
    }
}
