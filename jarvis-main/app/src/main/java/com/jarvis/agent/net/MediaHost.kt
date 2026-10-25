package com.jarvis.agent.net

import android.content.Context
import com.jarvis.agent.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Instagram media uchun ochiq HTTPS URL kerak. Shu uchun fayl o'zingizning backend'ingizga (backend/ papkasi) yuklanadi. */
object MediaHost {
    const val ACCOUNT = "backend"
    fun configured(ctx: Context) = TokenStore(ctx).load(ACCOUNT) != null

    /** @return ochiq URL, yoki null agar backend sozlanmagan. Xatoda IOException. */
    suspend fun upload(ctx: Context, file: File): String? = withContext(Dispatchers.IO) {
        val cfg = JSONObject(TokenStore(ctx).load(ACCOUNT) ?: return@withContext null)
        val base = cfg.getString("baseUrl").trimEnd('/')
        val c = URL("$base/upload/${URLEncoder.encode(file.name, "UTF-8")}").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.connectTimeout = 30_000; c.readTimeout = 180_000
        c.setRequestProperty("x-api-key", cfg.getString("apiKey"))
        c.setFixedLengthStreamingMode(file.length())
        file.inputStream().use { i -> c.outputStream.use { i.copyTo(it, 256 * 1024) } }
        val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (c.responseCode !in 200..299) throw IOException("Backend HTTP ${c.responseCode}: ${text.take(100)}")
        JSONObject(text).getString("url")
    }
}
