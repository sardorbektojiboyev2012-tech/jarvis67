package com.jarvis.agent.telegram

import android.content.Context
import com.jarvis.agent.data.JobEntity
import com.jarvis.agent.net.Http
import com.jarvis.agent.security.TokenStore
import com.jarvis.agent.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Telegram Bot API (bepul). Payload: {"kind":"video"|"text","filePath","caption","text"}.
 * Bot API rejalashtirilgan yuborishni qo'llamaydi: vaqtni JARVIS navbati boshqaradi.
 * Eslatma: javob yo'qolsa qayta urinish dublikat xabar yuborishi mumkin (chat xabarlari uchun qabul qilingan).
 */
class TelegramAdapter(private val ctx: Context) : PlatformAdapter {
    override val platform = "telegram"

    override suspend fun publish(job: JobEntity): PublishResult = withContext(Dispatchers.IO) {
        val cfg = TokenStore(ctx).load("telegram_main")?.let { JSONObject(it) }
            ?: return@withContext PublishResult.Fatal("Telegram bot ulanmagan")
        val p = try { JSONObject(job.payloadJson) } catch (e: Exception) { return@withContext PublishResult.Fatal("Payload JSON noto'g'ri") }
        val base = "https://api.telegram.org/bot${cfg.getString("botToken")}"
        val chat = cfg.getString("chatId")
        try {
            val resp = if (p.optString("kind", "video") == "text")
                Http.request("$base/sendMessage", "POST", form = mapOf("chat_id" to chat, "text" to p.optString("text")))
            else {
                val f = File(p.optString("filePath"))
                if (!f.isFile) return@withContext PublishResult.Fatal("Video fayl topilmadi")
                sendVideo("$base/sendVideo", chat, p.optString("caption").take(1024), f)
            }
            val o = try { JSONObject(resp.body) } catch (e: Exception) { null }
            val mid = o?.optJSONObject("result")?.optLong("message_id", -1) ?: -1
            when {
                resp.code == 200 && o?.optBoolean("ok") == true && mid > 0 -> PublishResult.Success("tg:$chat/$mid")
                resp.code == 429 || resp.code >= 500 -> PublishResult.Retry("Telegram ${resp.code}: ${o?.optString("description")}")
                else -> PublishResult.Fatal("Telegram ${resp.code}: ${o?.optString("description") ?: resp.body.take(150)}")
            }
        } catch (e: IOException) { PublishResult.Retry("Tarmoq xatosi: ${e.message}") }
    }

    private fun sendVideo(url: String, chat: String, caption: String, f: File): Http.Resp {
        val b = "----jarvis${System.nanoTime()}"
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 30_000; c.readTimeout = 180_000
        c.setChunkedStreamingMode(256 * 1024)
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        c.outputStream.use { out ->
            fun field(n: String, v: String) = out.write("--$b\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n$v\r\n".toByteArray())
            field("chat_id", chat); field("caption", caption); field("supports_streaming", "true")
            out.write("--$b\r\nContent-Disposition: form-data; name=\"video\"; filename=\"${f.name}\"\r\nContent-Type: video/mp4\r\n\r\n".toByteArray())
            f.inputStream().use { it.copyTo(out, 256 * 1024) }
            out.write("\r\n--$b--\r\n".toByteArray())
        }
        val code = c.responseCode
        return Http.Resp(code, (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
    }
}
