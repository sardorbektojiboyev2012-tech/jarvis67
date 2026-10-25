
package com.jarvis.agent.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.jarvis.agent.data.*
import com.jarvis.agent.security.ActionType
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.Calendar

data class Reply(
    val text: String,
    val end: Boolean = false,
    val confirm: (suspend () -> Reply)? = null
)

object Commands {
    private val aliases = mapOf(
        "yutub" to "youtube",
        "yutyub" to "youtube",
        "vatsap" to "whatsapp",
        "vots ap" to "whatsapp",
        "instagramni" to "instagram"
    )

    private val months = listOf(
        "yanvar", "fevral", "mart", "aprel", "may", "iyun",
        "iyul", "avgust", "sentyabr", "oktyabr", "noyabr", "dekabr"
    )

    private val days = listOf(
        "yakshanba", "dushanba", "seshanba", "chorshanba",
        "payshanba", "juma", "shanba"
    )

    fun norm(s: String) = s.lowercase()
        .replace('ʻ', '\'')
        .replace('ʼ', '\'')
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('`', '\'')
        .trim()

    fun isYes(s: String) = norm(s).split(Regex("\\s+")).any {
        it in setOf("ha", "xop", "xo'p", "mayli", "ok", "okay", "albatta", "yes", "да")
    }

    suspend fun handle(ctx: Context, raw: String): Reply {
        var t = norm(raw)
        aliases.forEach { (k, v) -> t = t.replace(k, v) }

        fun has(vararg w: String) = w.any { t.contains(it) }

        return when {
            has("rahmat", "to'xta", "toxta", "bo'ldi", "yetarli", "xayr") ->
                Reply("Marhamat. Chaqirsangiz, shu yerdaman.", end = true)

            has("salom") ->
                Reply("Va alaykum assalom! Sizga qanday yordam bera olaman?")

            has("soat") && has("necha", "nechi") -> {
                val c = Calendar.getInstance()
                Reply("Hozir soat ${c.get(Calendar.HOUR_OF_DAY)} va ${c.get(Calendar.MINUTE)} daqiqa.")
            }

            has("sana", "nechanchi") -> {
                val c = Calendar.getInstance()
                Reply(
                    "Bugun ${c.get(Calendar.DAY_OF_MONTH)} " +
                        "${months[c.get(Calendar.MONTH)]}, ${c.get(Calendar.YEAR)} yil, " +
                        "${days[c.get(Calendar.DAY_OF_WEEK) - 1]}."
                )
            }

            has("hisobot", "holat") -> report(ctx, has("telegram"))

            has("fonar", "chiroq") ->
                torch(ctx, !has("o'chir", "ochir", "yoping", "o'ch"))

            has("budilnik", "signal", "uyg'ot", "uygot") ->
                alarm(ctx, t)

            has("qo'ng'iroq", "qongiroq", "qo'ngiroq", "telefon qil", "chaqir") ->
                call(ctx, t)

            has("och", "ochib") ->
                openApp(ctx, t) ?: Reply("Bunday ilovani topolmadim.")

            has("yordam", "nima qila", "imkon") ->
                Reply("Men vaqt va sanani aytaman, hisobot beraman, fonarni yoqaman, budilnik qo'yaman, qo'ng'iroq qilaman va ilovalarni ochaman.")

            else -> Reply("Tushunmadim. Yana bir bor aytib ko'ring.")
        }
    }

    private suspend fun report(ctx: Context, toTelegram: Boolean): Reply {
        val jobs = JarvisDb.get(ctx).jobs().observeAll().first()

        fun n(s: JobState) = jobs.count { it.state == s }

        val queued = n(JobState.SCHEDULED) +
            n(JobState.APPROVED) +
            n(JobState.UPLOADING)

        val bat = ctx.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val text = "Hisobot. Telefon quvvati $bat foiz. Navbatda $queued ta, " +
            "tasdiq kutayotgan ${n(JobState.AWAITING_APPROVAL)} ta, " +
            "chop etilgan ${n(JobState.PUBLISHED)} ta, xato ${n(JobState.FAILED)} ta."

        if (!toTelegram) return Reply(text)

        val r = JobRepository(ctx).enqueue(
            "telegram",
            "message",
            JSONObject()
                .put("kind", "text")
                .put("text", text)
                .toString(),
            System.currentTimeMillis(),
            ActionType.PUBLISH,
            "voice-report"
        )

        return Reply(
            text + when (r) {
                is Enqueued.Queued ->
                    if (r.state == JobState.SCHEDULED)
                        " Telegramga yuborish navbatga qo'yildi."
                    else
                        " Telegramga yuborish uchun ilovada tasdiqlang."

                else -> " Telegramga yuborib bo'lmadi."
            }
        )
    }

    private fun torch(ctx: Context, on: Boolean): Reply = try {
        val cm = ctx.getSystemService(CameraManager::class.java)

        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        if (id == null) {
            Reply("Fonar topilmadi.")
        } else {
            cm.setTorchMode(id, on)
            Reply(if (on) "Fonar yoqildi." else "Fonar o'chirildi.")
        }
    } catch (e: Exception) {
        Reply("Fonarni boshqarib bo'lmadi.")
    }

    private fun needOverlay() =
        Reply("Buning uchun ilovada «Boshqa ilovalar ustida ko'rsatish» ruxsatini bering.")

    private fun alarm(ctx: Context, t: String): Reply {
        val m = Regex("(\\d{1,2})\\D+(\\d{1,2})").find(t)

        val h = m?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(\\d{1,2})").find(t)?.groupValues?.get(1)?.toIntOrNull()

        val min = m?.groupValues?.get(2)?.toIntOrNull() ?: 0

        if (h == null || h !in 0..23 || min !in 0..59) {
            return Reply("Soatni tushunmadim. Masalan: budilnik 7 30.")
        }

        if (!Settings.canDrawOverlays(ctx)) return needOverlay()

        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, min)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            ctx.startActivity(i)
            Reply("Budilnik $h soat $min daqiqaga so'rab qo'yildi.")
        } catch (e: Exception) {
            Reply("Budilnik qo'ya olmadim.")
        }
    }

    private val callStop = setOf(
        "qo'ng'iroq", "qongiroq", "qo'ngiroq", "qil", "qiling",
        "qilib", "ber", "bering", "telefon", "chaqir",
        "chaqiring", "iltimos", "menga"
    )

    private fun call(ctx: Context, t: String): Reply {
        if (
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Reply("Qo'ng'iroq ruxsati berilmagan. Ilovada ruxsatlarni bering.")
        }

        val digits = Regex("\\+?[0-9][0-9 \\-]{6,}")
            .find(t)?.value?.filter { it.isDigit() || it == '+' }

        if (digits != null) {
            return Reply(
                "$digits raqamiga qo'ng'iroq qilaymi? «Ha» deng.",
                confirm = { doCall(ctx, digits, digits) }
            )
        }

        val name = t.split(" ")
            .filter { it.isNotBlank() && it !in callStop }
            .joinToString(" ")
            .trim()

        if (name.isEmpty()) return Reply("Kimga qo'ng'iroq qilay?")

        val found = try {
            listOf(
                name,
                name.removeSuffix("ga"),
                name.removeSuffix("ka"),
                name.removeSuffix("qa"),
                name.removeSuffix("ni")
            )
                .filter { it.length >= 2 }
                .distinct()
                .firstNotNullOfOrNull { findContact(ctx, it) }
        } catch (e: SecurityException) {
            return Reply("Kontaktlarga ruxsat berilmagan. Ilovada ruxsatlarni bering.")
        } ?: return Reply("Kontaktlarda «$name» topilmadi.")

        return Reply(
            "${found.first}ga qo'ng'iroq qilaymi? «Ha» deng.",
            confirm = { doCall(ctx, found.second, found.first) }
        )
    }

    // Kontaktlarni qidirish: Phone klassining haqiqiy konstantalaridan foydalanadi.
    private fun findContact(ctx: Context, name: String): Pair<String, String>? {
        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val displayName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val phoneNumber = ContactsContract.CommonDataKinds.Phone.NUMBER

        ctx.contentResolver.query(
            phoneUri,
            arrayOf(displayName, phoneNumber),
            "$displayName LIKE ?",
            arrayOf("%$name%"),
            "$displayName ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contactName = cursor.getString(0) ?: return null
                val number = cursor.getString(1) ?: return null
                return contactName to number
            }
        }

        return null
    }

    private fun doCall(ctx: Context, number: String, label: String): Reply {
        if (!Settings.canDrawOverlays(ctx)) return needOverlay()

        return try {
            ctx.startActivity(
                Intent(
                    Intent.ACTION_CALL,
                    Uri.parse("tel:" + Uri.encode(number))
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Reply("$label ga qo'ng'iroq qilyapman.")
        } catch (e: Exception) {
            Reply("Qo'ng'iroq qila olmadim.")
        }
    }

    private fun openApp(ctx: Context, t: String): Reply? {
        val pm = ctx.packageManager

        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        )

        val best = apps
            .map { it to norm(it.loadLabel(pm).toString()) }
            .filter { it.second.length >= 3 && t.contains(it.second) }
            .maxByOrNull { it.second.length }
            ?: return null

        val li = pm.getLaunchIntentForPackage(
            best.first.activityInfo.packageName
        ) ?: return null

        if (!Settings.canDrawOverlays(ctx)) return needOverlay()

        return try {
            ctx.startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Reply("${best.second} ochilyapti.")
        } catch (e: Exception) {
            Reply("Ochib bo'lmadi.")
        }
    }
}
