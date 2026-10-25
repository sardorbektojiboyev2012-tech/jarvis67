package com.jarvis.agent.instagram

import android.content.Context
import com.jarvis.agent.auth.AuthFailure
import com.jarvis.agent.net.Http
import com.jarvis.agent.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/**
 * "Instagram API with Instagram Login". App secret APK ichida saqlanmaydi, shuning uchun token
 * Meta App Dashboard > Instagram > API setup > "Generate token" orqali olinadi (bu parol emas, rasmiy API tokeni).
 * Token ~60 kun yashaydi va secret'siz yangilanadi (graph.instagram.com/refresh_access_token).
 */
object InstagramAuth {
    const val ACCOUNT = "instagram_main"
    const val API = "https://graph.instagram.com/v21.0"
    private const val DAY = 86_400_000L

    fun isConnected(ctx: Context) = TokenStore(ctx).load(ACCOUNT) != null

    /** @return xato matni yoki null */
    suspend fun connect(ctx: Context, token: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = Http.request("$API/me?fields=user_id,username&access_token=${token.trim()}")
            if (r.code != 200) return@withContext "Token rad etildi: " + (try { JSONObject(r.body).getJSONObject("error").optString("message") } catch (e: Exception) { "HTTP ${r.code}" })
            val o = JSONObject(r.body)
            val uid = o.optString("user_id").ifBlank { o.optString("id") }
            TokenStore(ctx).save(ACCOUNT, JSONObject().put("token", token.trim()).put("userId", uid)
                .put("username", o.optString("username")).put("expiresAt", System.currentTimeMillis() + 59 * DAY).toString())
            null
        } catch (e: IOException) { "Tarmoq xatosi: ${e.message}" }
    }

    fun username(ctx: Context): String? = TokenStore(ctx).load(ACCOUNT)?.let { JSONObject(it).optString("username") }

    /** Instagram'da dasturiy "revoke" yo'q: qurilmadan o'chiramiz; to'liq bekor qilish Instagram sozlamalari > Ilovalar va veb-saytlar. */
    fun disconnect(ctx: Context) = TokenStore(ctx).remove(ACCOUNT)

    suspend fun freshToken(ctx: Context): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val store = TokenStore(ctx)
        val j = store.load(ACCOUNT)?.let { JSONObject(it) }
            ?: return@withContext Result.failure(AuthFailure(true, "Instagram ulanmagan"))
        var token = j.getString("token"); var exp = j.getLong("expiresAt"); val now = System.currentTimeMillis()
        if (exp - now < 7 * DAY) {
            try {
                val r = Http.request("https://graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token&access_token=$token")
                if (r.code == 200) {
                    val o = JSONObject(r.body); token = o.getString("access_token")
                    exp = now + o.getLong("expires_in") * 1000
                    store.save(ACCOUNT, j.put("token", token).put("expiresAt", exp).toString())
                } else if (exp < now) return@withContext Result.failure(AuthFailure(true, "Instagram tokeni eskirgan: qayta ulang"))
            } catch (e: IOException) {
                if (exp < now) return@withContext Result.failure(AuthFailure(false, "Token yangilanmadi (tarmoq)"))
            }
        }
        Result.success(token to j.getString("userId"))
    }
}
