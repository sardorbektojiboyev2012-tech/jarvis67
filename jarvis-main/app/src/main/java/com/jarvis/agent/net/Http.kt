package com.jarvis.agent.net

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    data class Resp(val code: Int, val body: String)

    fun request(url: String, method: String = "GET", form: Map<String, String>? = null,
                headers: Map<String, String> = emptyMap(), json: String? = null, timeoutMs: Int = 60_000): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 30_000; c.readTimeout = timeoutMs; c.instanceFollowRedirects = false
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        val body: ByteArray? = when {
            form != null -> { c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }.toByteArray() }
            json != null -> { c.setRequestProperty("Content-Type", "application/json"); json.toByteArray() }
            else -> null
        }
        if (body != null) { c.doOutput = true; c.setFixedLengthStreamingMode(body.size); c.outputStream.use { it.write(body) } }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return Resp(code, text)
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
