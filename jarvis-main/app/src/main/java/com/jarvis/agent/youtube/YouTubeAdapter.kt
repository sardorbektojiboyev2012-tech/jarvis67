package com.jarvis.agent.youtube

import android.content.Context
import com.jarvis.agent.auth.AuthFailure
import com.jarvis.agent.auth.GoogleAuth
import com.jarvis.agent.data.JobEntity
import com.jarvis.agent.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * YouTube Data API v3, resumable upload. Payload:
 * {"filePath","title","description","tags":[],"privacy":"private|unlisted|public","publishAt":"2026-10-20T15:00:00Z"?,"categoryId":"22"?}
 * PUBLISHED faqat API video ID qaytarsa. Uzilgan yuklash sessiyadan davom ettiriladi (dublikat video bo'lmaydi).
 */
class YouTubeAdapter(private val ctx: Context) : PlatformAdapter {
    override val platform = "youtube"
    private val sessions = ctx.getSharedPreferences("yt_sessions", Context.MODE_PRIVATE)
    private class YtFail(val r: PublishResult) : Exception()
    private data class Q(val code: Int, val offset: Long, val body: String)

    override suspend fun publish(job: JobEntity): PublishResult = withContext(Dispatchers.IO) {
        val p = try { JSONObject(job.payloadJson) } catch (e: Exception) { return@withContext PublishResult.Fatal("Payload JSON noto'g'ri") }
        val file = File(p.optString("filePath"))
        if (!file.isFile) return@withContext PublishResult.Fatal("Video fayl topilmadi: ${file.name}")
        val tr = GoogleAuth.freshAccessToken(ctx)
        val token = tr.getOrNull() ?: run {
            val e = tr.exceptionOrNull() as? AuthFailure
            return@withContext if (e?.fatal == true) PublishResult.Fatal(e.message ?: "Auth xatosi") else PublishResult.Retry(e?.message ?: "Auth xatosi")
        }
        try { upload(job, p, file, token) }
        catch (e: YtFail) { e.r }
        catch (e: IOException) { PublishResult.Retry("Tarmoq xatosi: ${e.message}") }
        finally { UploadProgress.clear(job.id) }
    }

    private fun upload(job: JobEntity, p: JSONObject, file: File, token: String): PublishResult {
        val total = file.length()
        val key = "s_${job.idempotencyKey}"
        var session = sessions.getString(key, null)
        var offset = 0L
        if (session != null) {
            val q = query(session, total)
            when {
                q.code == 200 || q.code == 201 -> return finish(job, p, token, q.body, key)
                q.code == 308 -> offset = q.offset
                q.code in 400..499 && q.code != 429 && q.code != 408 -> { sessions.edit().remove(key).apply(); session = null }
                else -> return PublishResult.Retry("Sessiya holati: HTTP ${q.code}")
            }
        }
        if (session == null) {
            session = startSession(token, p, total)
            sessions.edit().putString(key, session).apply()
        }
        val c = open(session, "PUT")
        c.doOutput = true
        c.setFixedLengthStreamingMode(total - offset)
        c.setRequestProperty("Content-Type", "video/*")
        c.setRequestProperty("Content-Range", "bytes $offset-${total - 1}/$total")
        c.readTimeout = 180_000
        FileInputStream(file).use { fin ->
            fin.channel.position(offset)
            c.outputStream.use { out ->
                val buf = ByteArray(256 * 1024); var sent = offset
                while (true) {
                    val n = fin.read(buf); if (n < 0) break
                    out.write(buf, 0, n); sent += n
                    UploadProgress.set(job.id, (sent * 100 / total).toInt())
                }
            }
        }
        val code = c.responseCode
        val body = readBody(c)
        return if (code == 200 || code == 201) finish(job, p, token, body, key) else throw YtFail(classify(code, body))
    }

    private fun startSession(token: String, p: JSONObject, total: Long): String {
        val publishAt = p.optString("publishAt", "")
        val privacy = if (publishAt.isNotBlank()) "private" else p.optString("privacy", "private")
        val meta = JSONObject()
            .put("snippet", JSONObject()
                .put("title", p.optString("title", "Untitled").take(100))
                .put("description", p.optString("description", "").take(5000))
                .put("tags", p.optJSONArray("tags") ?: JSONArray())
                .put("categoryId", p.optString("categoryId", "22")))
            .put("status", JSONObject().put("privacyStatus", privacy)
                .put("selfDeclaredMadeForKids", p.optBoolean("madeForKids", false))
                .apply { if (publishAt.isNotBlank()) put("publishAt", publishAt) })
        val c = open("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status", "POST")
        c.doOutput = true
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        c.setRequestProperty("X-Upload-Content-Length", total.toString())
        c.setRequestProperty("X-Upload-Content-Type", "video/*")
        c.outputStream.use { it.write(meta.toString().toByteArray()) }
        val code = c.responseCode
        if (code != 200) throw YtFail(classify(code, readBody(c)))
        return c.getHeaderField("Location") ?: throw YtFail(PublishResult.Retry("Sessiya URL kelmadi"))
    }

    private fun query(session: String, total: Long): Q {
        val c = open(session, "PUT")
        c.doOutput = true; c.setFixedLengthStreamingMode(0)
        c.setRequestProperty("Content-Range", "bytes */$total")
        c.outputStream.close()
        val code = c.responseCode
        val off = c.getHeaderField("Range")?.substringAfter('-')?.toLongOrNull()?.plus(1) ?: 0L
        return Q(code, off, readBody(c))
    }

    private fun finish(job: JobEntity, p: JSONObject, token: String, body: String, key: String): PublishResult {
        val id = try { JSONObject(body).optString("id") } catch (e: Exception) { "" }
        if (id.isBlank()) throw YtFail(PublishResult.Fatal("API javobida video ID yo'q. YouTube Studio'da qo'lda tekshiring"))
        sessions.edit().remove(key).apply()
        // Haqiqiy holatni so'rab tekshiramiz (masalan, tasdiqlanmagan loyiha videoni private qilib qo'yishi mumkin)
        verify(token, id)?.let { (privacy, upload) ->
            val wanted = if (p.optString("publishAt").isNotBlank()) "private" else p.optString("privacy", "private")
            if (privacy != wanted)
                Notifier.notify(ctx, job.id, "YouTube cheklovi", "Video $privacy holatida (so'ralgan: $wanted). Loyiha auditdan o'tmagan bo'lishi mumkin.")
            if (upload != "uploaded" && upload != "processed")
                Notifier.notify(ctx, job.id, "YouTube holati", "uploadStatus=$upload (id $id)")
        }
        return PublishResult.Success(id)
    }

    private fun verify(token: String, id: String): Pair<String, String>? = try {
        val c = open("https://www.googleapis.com/youtube/v3/videos?part=status&id=$id", "GET")
        c.setRequestProperty("Authorization", "Bearer $token")
        if (c.responseCode != 200) null else {
            val st = JSONObject(readBody(c)).getJSONArray("items").getJSONObject(0).getJSONObject("status")
            st.optString("privacyStatus") to st.optString("uploadStatus")
        }
    } catch (e: Exception) { null }

    private fun classify(code: Int, body: String): PublishResult {
        val err = try { JSONObject(body).getJSONObject("error") } catch (e: Exception) { null }
        val reason = err?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason") ?: ""
        val msg = err?.optString("message") ?: body.take(200)
        return when {
            code == 401 -> PublishResult.Retry("401: token rad etildi, yangilab qayta uriniladi")
            code == 403 && reason in QUOTA -> PublishResult.Fatal("YouTube kvotasi/limiti tugagan ($reason). Kvota Tinch okeani vaqti bilan yarim tunda yangilanadi; keyin qayta urining.")
            code == 403 -> PublishResult.Fatal("Ruxsat yo'q ($reason): $msg")
            code == 408 || code == 429 || code >= 500 -> PublishResult.Retry("HTTP $code $reason")
            else -> PublishResult.Fatal("HTTP $code $reason: $msg")
        }
    }

    private fun open(url: String, method: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method; instanceFollowRedirects = false; connectTimeout = 30_000; readTimeout = 60_000
    }
    private fun readBody(c: HttpURLConnection): String =
        (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""

    private companion object { val QUOTA = setOf("quotaExceeded", "uploadLimitExceeded", "rateLimitExceeded", "dailyLimitExceeded") }
}
