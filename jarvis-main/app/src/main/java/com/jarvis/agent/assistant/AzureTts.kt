package com.jarvis.agent.assistant

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Azure AI Speech: o'zbek yigit ovozi uz-UZ-SardorNeural. Natija: mp3 fayl. Tarmoq oqimida chaqiring (IO). */
object AzureTts {
    fun configured(ctx: Context) = Secrets.azure(ctx) != null

    fun synth(ctx: Context, text: String, out: File): Boolean {
        val (key, region) = Secrets.azure(ctx) ?: return false
        return try {
            val ssml = "<speak version='1.0' xml:lang='uz-UZ'><voice name='uz-UZ-SardorNeural'>${esc(text)}</voice></speak>"
            val c = URL("https://$region.tts.speech.microsoft.com/cognitiveservices/v1").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("Ocp-Apim-Subscription-Key", key)
            c.setRequestProperty("Content-Type", "application/ssml+xml")
            c.setRequestProperty("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
            c.setRequestProperty("User-Agent", "JARVIS")
            c.outputStream.use { it.write(ssml.toByteArray()) }
            if (c.responseCode != 200) return false
            c.inputStream.use { i -> out.outputStream().use { i.copyTo(it) } }
            out.length() > 0
        } catch (e: Exception) { false }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
