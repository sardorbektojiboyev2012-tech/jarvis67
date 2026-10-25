package com.jarvis.agent.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.agent.BuildConfig
import com.jarvis.agent.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AuthFailure(val fatal: Boolean, msg: String) : Exception(msg)

/** Google OAuth 2.0 (PKCE, AppAuth). Parol so'ralmaydi; faqat brauzerdagi rasmiy Google kirish oynasi. */
object GoogleAuth {
    const val ACCOUNT = "youtube_main"
    private val REDIRECT = Uri.parse("com.jarvis.agent:/oauth2redirect")
    private const val SCOPES = "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly"
    private val cfg = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"), Uri.parse("https://oauth2.googleapis.com/token"))

    fun isConfigured() = BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()

    fun authIntent(ctx: Context): Intent {
        val req = AuthorizationRequest.Builder(cfg, BuildConfig.GOOGLE_CLIENT_ID, ResponseTypeValues.CODE, REDIRECT)
            .setScope(SCOPES).build()
        return AuthorizationService(ctx).getAuthorizationRequestIntent(req)
    }

    /** @return xato matni yoki null (muvaffaqiyat) */
    suspend fun handleResult(ctx: Context, data: Intent): String? {
        val resp = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (resp == null) return ex?.errorDescription ?: "Ulash bekor qilindi"
        val state = AuthState(resp, ex)
        val svc = AuthorizationService(ctx)
        return suspendCoroutine { c ->
            svc.performTokenRequest(resp.createTokenExchangeRequest()) { tr, e ->
                state.update(tr, e)
                if (tr == null) c.resume(e?.errorDescription ?: "Token olinmadi")
                else { TokenStore(ctx).save(ACCOUNT, state.jsonSerializeString()); c.resume(null) }
                svc.dispose()
            }
        }
    }

    fun isConnected(ctx: Context) = TokenStore(ctx).load(ACCOUNT) != null

    suspend fun freshAccessToken(ctx: Context): Result<String> {
        val json = TokenStore(ctx).load(ACCOUNT)
            ?: return Result.failure(AuthFailure(true, "YouTube ulanmagan"))
        val state = AuthState.jsonDeserialize(json)
        val svc = AuthorizationService(ctx)
        return suspendCoroutine { c ->
            state.performActionWithFreshTokens(svc) { at, _, ex ->
                if (at == null) {
                    val fatal = ex?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR
                    c.resume(Result.failure(AuthFailure(fatal,
                        if (fatal) "Ruxsat bekor bo'lgan yoki muddati tugagan: YouTube'ni qayta ulang" else "Token yangilanmadi (tarmoq?)")))
                } else {
                    TokenStore(ctx).save(ACCOUNT, state.jsonSerializeString())
                    c.resume(Result.success(at))
                }
                svc.dispose()
            }
        }
    }

    /** Ruxsatni bekor qilish: Google'da revoke (imkon bo'lsa) va qurilmadan o'chirish. */
    suspend fun disconnect(ctx: Context) = withContext(Dispatchers.IO) {
        val store = TokenStore(ctx)
        try {
            val st = AuthState.jsonDeserialize(store.load(ACCOUNT) ?: "")
            val t = st.refreshToken ?: st.accessToken
            if (t != null) (URL("https://oauth2.googleapis.com/revoke?token=$t").openConnection() as HttpURLConnection)
                .apply { requestMethod = "POST"; connectTimeout = 15000; readTimeout = 15000 }.responseCode
        } catch (_: Exception) { /* oflayn bo'lsa ham qurilmadan o'chiramiz */ }
        store.remove(ACCOUNT)
    }
}
