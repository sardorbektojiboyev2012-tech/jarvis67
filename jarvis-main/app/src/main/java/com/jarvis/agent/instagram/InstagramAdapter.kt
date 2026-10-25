package com.jarvis.agent.instagram

import android.content.Context
import com.jarvis.agent.auth.AuthFailure
import com.jarvis.agent.data.JobEntity
import com.jarvis.agent.net.Http
import com.jarvis.agent.net.MediaHost
import com.jarvis.agent.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Payload: {"mediaType":"REELS"|"IMAGE","videoUrl"|"imageUrl"|"filePath","caption"}
 * Oqim: container yaratish -> status_code=FINISHED kutish -> media_publish.
 * Instagram Login API media'ni OCHIQ URL'dan oladi, shuning uchun filePath bo'lsa backend'ga yuklanadi (MediaHost).
 * media_publish yuborilgandan keyin natija noma'lum qolsa, avtomatik qayta yuborilmaydi (dublikat post bo'lmasligi uchun).
 */
class InstagramAdapter(private val ctx: Context) : PlatformAdapter {
    override val platform = "instagram"
    private val st = ctx.getSharedPreferences("ig_state", Context.MODE_PRIVATE)
    private class IgFail(val r: PublishResult) : Exception()

    override suspend fun publish(job: JobEntity): PublishResult = withContext(Dispatchers.IO) {
        val k = job.idempotencyKey
        if (st.contains("p_$k")) return@withContext PublishResult.Fatal(
            "Nashr so'rovi yuborilgan, lekin natija noma'lum. Instagram'da tekshiring; post bor bo'lsa qayta tasdiqlamang.")
        val p = try { JSONObject(job.payloadJson) } catch (e: Exception) { return@withContext PublishResult.Fatal("Payload JSON noto'g'ri") }
        val tr = InstagramAuth.freshToken(ctx)
        val (token, uid) = tr.getOrNull() ?: run {
            val e = tr.exceptionOrNull() as? AuthFailure
            return@withContext if (e?.fatal == true) PublishResult.Fatal(e.message ?: "Auth") else PublishResult.Retry(e?.message ?: "Auth")
        }
        try { run(job, k, p, token, uid) }
        catch (e: IgFail) { e.r }
        catch (e: IOException) { PublishResult.Retry("Tarmoq xatosi: ${e.message}") }
    }

    private suspend fun run(job: JobEntity, k: String, p: JSONObject, token: String, uid: String): PublishResult {
        val isVideo = p.optString("mediaType", "REELS") != "IMAGE"
        var url = st.getString("u_$k", null)
        if (url == null) {
            url = p.optString(if (isVideo) "videoUrl" else "imageUrl", "").ifBlank { null }
            if (url == null) {
                val f = File(p.optString("filePath"))
                if (!f.isFile) throw IgFail(PublishResult.Fatal("Media fayl yoki URL yo'q"))
                url = MediaHost.upload(ctx, f) ?: throw IgFail(PublishResult.Fatal(
                    "Instagram media uchun ochiq HTTPS URL talab qiladi. Backend (MediaHost) ni sozlang yoki videoUrl bering."))
            }
            st.edit().putString("u_$k", url).apply()
        }
        var cid = st.getString("c_$k", null)
        if (cid == null) {
            val form = mutableMapOf("access_token" to token, "caption" to p.optString("caption", "").take(2200))
            if (isVideo) { form["media_type"] = "REELS"; form["video_url"] = url; form["share_to_feed"] = "true" } else form["image_url"] = url
            val r = Http.request("${InstagramAuth.API}/$uid/media", "POST", form = form)
            if (r.code !in 200..299) throw IgFail(classify(r))
            cid = JSONObject(r.body).optString("id").ifBlank { throw IgFail(PublishResult.Fatal("Container ID kelmadi")) }
            st.edit().putString("c_$k", cid).apply()
        }
        var ready = false; var tries = 0
        while (!ready && tries++ < 60) {
            val s = Http.request("${InstagramAuth.API}/$cid?fields=status_code&access_token=$token")
            if (s.code in 200..299) when (JSONObject(s.body).optString("status_code")) {
                "FINISHED" -> ready = true
                "ERROR" -> { st.edit().remove("c_$k").apply(); throw IgFail(PublishResult.Fatal("Instagram media'ni rad etdi: ${s.body.take(200)}")) }
                "EXPIRED" -> { st.edit().remove("c_$k").apply(); throw IgFail(PublishResult.Retry("Container muddati tugagan, qaytadan yaratiladi")) }
            } else if (s.code in 400..499 && s.code != 429) throw IgFail(classify(s))
            if (!ready) delay(5000)
        }
        if (!ready) return PublishResult.Retry("Instagram media'ni hali qayta ishlamoqda")

        st.edit().putString("p_$k", "1").apply()   // shu nuqtadan boshlab natija noma'lum bo'lishi mumkin
        val r = try { Http.request("${InstagramAuth.API}/$uid/media_publish", "POST", form = mapOf("creation_id" to cid, "access_token" to token)) }
        catch (e: IOException) { throw IgFail(PublishResult.Fatal("Tarmoq uzildi: nashr natijasi noma'lum. Instagram'da tekshiring.")) }
        if (r.code !in 200..299) { st.edit().remove("p_$k").apply(); throw IgFail(classify(r)) }
        val id = JSONObject(r.body).optString("id")
        if (id.isBlank()) throw IgFail(PublishResult.Fatal("media_publish javobida ID yo'q. Instagram'da tekshiring."))
        st.edit().remove("p_$k").remove("c_$k").remove("u_$k").apply()
        try {
            val pl = Http.request("${InstagramAuth.API}/$id?fields=permalink&access_token=$token")
            if (pl.code == 200) Notifier.notify(ctx, job.id, "Instagram: chop etildi", JSONObject(pl.body).optString("permalink"))
        } catch (_: Exception) {}
        return PublishResult.Success(id)
    }

    private fun classify(r: Http.Resp): PublishResult {
        val e = try { JSONObject(r.body).getJSONObject("error") } catch (x: Exception) { null }
        val code = e?.optInt("code") ?: 0
        val msg = e?.optString("message") ?: r.body.take(200)
        return when {
            code == 190 || code == 102 -> PublishResult.Fatal("Token yaroqsiz (190): Instagram'ni qayta ulang. $msg")
            code in setOf(4, 17, 32, 613, 368) || r.code == 429 -> PublishResult.Retry("Rate limit ($code): $msg")
            code in setOf(1, 2) || r.code >= 500 -> PublishResult.Retry("Vaqtincha xato ($code): $msg")
            code == 10 || code in 200..299 -> PublishResult.Fatal("Ruxsat yo'q ($code): $msg. Akkaunt Business/Creator va instagram_business_content_publish ruxsati kerak.")
            else -> PublishResult.Fatal("HTTP ${r.code}, kod $code: $msg")
        }
    }
}
