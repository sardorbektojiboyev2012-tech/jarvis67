package com.jarvis.agent.security

import android.content.Context
import java.util.Calendar

/** Pullik API'lar uchun oylik limit (sent hisobida). Standart 0: pul sarflash butunlay o'chirilgan. */
class SpendingGuard(ctx: Context) {
    private val p = ctx.getSharedPreferences("jarvis_spend", Context.MODE_PRIVATE)
    var monthlyLimitCents: Int get() = p.getInt("limit", 0); set(v) = p.edit().putInt("limit", v).apply()

    private fun month(): String = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.MONTH)}" }
    fun spentCents(): Int = p.getInt("spent_${month()}", 0)

    fun tryCharge(cents: Int): Boolean = synchronized(this) {
        if (spentCents() + cents > monthlyLimitCents) return false
        p.edit().putInt("spent_${month()}", spentCents() + cents).apply(); true
    }
}
