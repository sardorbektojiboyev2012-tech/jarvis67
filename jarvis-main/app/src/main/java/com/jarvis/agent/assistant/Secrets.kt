package com.jarvis.agent.assistant

import android.content.Context
import com.jarvis.agent.security.TokenStore
import org.json.JSONObject

object Secrets {
    fun picovoice(ctx: Context): String? =
        TokenStore(ctx).load("picovoice_key")?.let { JSONObject(it).optString("key") }?.ifBlank { null }

    /** (kalit, region) yoki null */
    fun azure(ctx: Context): Pair<String, String>? {
        val j = TokenStore(ctx).load("azure_tts")?.let { JSONObject(it) } ?: return null
        val k = j.optString("key"); val r = j.optString("region")
        return if (k.isBlank() || r.isBlank()) null else k to r
    }
}
