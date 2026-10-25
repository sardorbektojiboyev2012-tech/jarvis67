package com.jarvis.agent.video

import android.content.Context
import com.jarvis.agent.net.Http
import com.jarvis.agent.security.SpendingGuard
import com.jarvis.agent.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Script(val title: String, val description: String, val tags: List<String>, val scenes: List<String>)

/**
 * PULLIK ixtiyoriy modul: Anthropic API (o'z kalitingiz). SpendingGuard standart 0 sent, ya'ni siz limit
 * belgilamaguningizcha ishlamaydi. Sarf taxminiy hisoblanadi; haqiqiy narxni console.anthropic.com'da tekshiring.
 * Qidiruv yoqilsa, model web_search vositasidan foydalanadi (qo'shimcha to'lov).
 */
object ClaudeScript {
    suspend fun generate(ctx: Context, topic: String, lang: String, seconds: Int, research: Boolean): Result<Script> = withContext(Dispatchers.IO) {
        val key = TokenStore(ctx).load("anthropic_key")?.let { JSONObject(it).optString("key") }?.ifBlank { null }
            ?: return@withContext Result.failure(Exception("Anthropic API kaliti kiritilmagan"))
        if (!SpendingGuard(ctx).tryCharge(if (research) 10 else 3))
            return@withContext Result.failure(Exception("Oylik xarajat limiti 0 yoki tugagan (Sozlamalar)"))
        val prompt = "Write a ~$seconds-second vertical video script about: $topic\nLanguage code: $lang. 5-7 short scenes, each at most 18 words. " +
            "Use ONLY verified facts; if you cannot verify a detail (scores, names, times), leave it out. Never invent results.\n" +
            "Return ONLY JSON: {\"title\":\"\",\"description\":\"\",\"tags\":[],\"scenes\":[]}"
        val body = JSONObject().put("model", "claude-sonnet-5-5").put("max_tokens", 1500)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        if (research) body.put("tools", JSONArray().put(JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 3)))
        try {
            val r = Http.request("https://api.anthropic.com/v1/messages", "POST", json = body.toString(), timeoutMs = 120_000,
                headers = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"))
            if (r.code != 200) return@withContext Result.failure(Exception("Anthropic HTTP ${r.code}: ${r.body.take(200)}"))
            val blocks = JSONObject(r.body).getJSONArray("content")
            val text = (0 until blocks.length()).map { blocks.getJSONObject(it) }.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
            val j = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
            fun arr(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
            Result.success(Script(j.optString("title"), j.optString("description"), arr(j.optJSONArray("tags")), arr(j.optJSONArray("scenes"))))
        } catch (e: Exception) { Result.failure(Exception("Ssenariy olinmadi: ${e.message}")) }
    }
}
