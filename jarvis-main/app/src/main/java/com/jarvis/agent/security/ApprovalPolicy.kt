package com.jarvis.agent.security

import android.content.Context

enum class ActionType { PUBLISH, COMMENT, MESSAGE, DELETE, CONNECT_ACCOUNT, PURCHASE }
enum class Decision { ALLOW, REQUIRE_APPROVAL, DENY }
data class PolicyResult(val decision: Decision, val reason: String)

/**
 * Standart: siz sozlagan qoida (ruleId) bilan kelgan oddiy ish avtomatik bajariladi.
 * Yangi akkaunt, to'lov, o'chirish, xabar yuborish va qoidadan tashqari har qanday ish: tasdiq so'raydi.
 */
class ApprovalPolicy(ctx: Context) {
    private val p = ctx.getSharedPreferences("jarvis_policy", Context.MODE_PRIVATE)

    var autoPublish: Boolean get() = p.getBoolean("autoPublish", true); set(v) = p.edit().putBoolean("autoPublish", v).apply()
    var autoComment: Boolean get() = p.getBoolean("autoComment", true); set(v) = p.edit().putBoolean("autoComment", v).apply()
    var autoMessage: Boolean get() = p.getBoolean("autoMessage", false); set(v) = p.edit().putBoolean("autoMessage", v).apply()

    fun decide(action: ActionType, ruleId: String?): PolicyResult = when (action) {
        ActionType.CONNECT_ACCOUNT -> PolicyResult(Decision.REQUIRE_APPROVAL, "Yangi akkaunt ulash doim tasdiq talab qiladi")
        ActionType.PURCHASE -> PolicyResult(Decision.REQUIRE_APPROVAL, "Pullik amal doim tasdiq talab qiladi")
        ActionType.DELETE -> PolicyResult(Decision.REQUIRE_APPROVAL, "O'chirish doim tasdiq talab qiladi")
        else -> when {
            ruleId == null -> PolicyResult(Decision.REQUIRE_APPROVAL, "Belgilangan qoidadan tashqari amal")
            action == ActionType.PUBLISH && !autoPublish -> PolicyResult(Decision.REQUIRE_APPROVAL, "Avto-nashr o'chirilgan")
            action == ActionType.COMMENT && !autoComment -> PolicyResult(Decision.REQUIRE_APPROVAL, "Avto-izoh o'chirilgan")
            action == ActionType.MESSAGE && !autoMessage -> PolicyResult(Decision.REQUIRE_APPROVAL, "Xabar yuborish tasdiq talab qiladi")
            else -> PolicyResult(Decision.ALLOW, "Qoida '$ruleId' bo'yicha ruxsat")
        }
    }
}
