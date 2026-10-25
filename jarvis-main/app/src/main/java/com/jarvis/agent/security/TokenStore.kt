package com.jarvis.agent.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** OAuth tokenlar Android Keystore'dagi AES-256-GCM kalit bilan shifrlanadi. Parollar umuman saqlanmaydi. */
class TokenStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("jarvis_tokens", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return g.generateKey()
    }

    fun save(accountId: String, secretJson: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(secretJson.toByteArray())
        prefs.edit().putString(accountId, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    fun load(accountId: String): String? {
        val raw = prefs.getString(accountId, null) ?: return null
        return try {
            val b = Base64.decode(raw, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
                .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12))) }
            String(c.doFinal(b, 12, b.size - 12))
        } catch (e: Exception) { null }   // buzilgan/kalit yo'qolgan bo'lsa: qayta ulanish kerak
    }

    /** Foydalanuvchi ruxsatni bekor qilganda: tokenni qurilmadan o'chirish. */
    fun remove(accountId: String) = prefs.edit().remove(accountId).apply()
    fun accounts(): Set<String> = prefs.all.keys

    private companion object { const val ALIAS = "jarvis_token_key" }
}
