package com.jarvis.agent.security

import android.content.Context

/** Ommaviy izoh/spamdan himoya: platforma va amal turi bo'yicha oynali limit. */
class ActionLimiter(ctx: Context) {
    private val p = ctx.getSharedPreferences("jarvis_limits", Context.MODE_PRIVATE)

    private fun rule(a: ActionType): Pair<Int, Long> = when (a) {
        ActionType.PUBLISH -> 10 to DAY
        ActionType.COMMENT -> 10 to HOUR
        ActionType.MESSAGE -> 5 to HOUR
        else -> 1 to HOUR
    }

    /** true = ruxsat berildi va hisobga yozildi; false = limitga yetgan, keyinroq urinib ko'ring. */
    fun tryAcquire(platform: String, action: ActionType): Boolean = synchronized(LOCK) {
        val (max, window) = rule(action)
        val k = "$platform:${action.name}"
        val now = System.currentTimeMillis()
        val recent = (p.getString(k, "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.filter { now - it < window }
        if (recent.size >= max) return false
        p.edit().putString(k, (recent + now).joinToString(",")).apply()
        true
    }

    companion object {
        private val LOCK = Any()
        const val HOUR = 3_600_000L; const val DAY = 24 * HOUR
    }
}
