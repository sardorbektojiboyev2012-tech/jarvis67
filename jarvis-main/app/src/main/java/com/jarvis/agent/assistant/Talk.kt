package com.jarvis.agent.assistant

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Suhbat tarixi (ekrandagi yozishmalar uchun). */
object History {
    val items = MutableStateFlow<List<Pair<Boolean, String>>>(emptyList())
    fun add(fromUser: Boolean, text: String) { items.value = (items.value + (fromUser to text)).takeLast(40) }
}

/** Bitta suhbat: ko'p bosqichli buyruqlar (masalan SMS: kimga? nima deb? yuboraymi?) shu yerda davom etadi. */
class Talk(private val ctx: Context) {
    private var pending: (suspend (String) -> Reply)? = null
    val waiting get() = pending != null

    suspend fun respond(text: String): Reply {
        History.add(true, text)
        val p = pending; pending = null
        val r = if (p != null) p(text) else Commands.handle(ctx, text)
        pending = r.next
        History.add(false, r.text)
        return r
    }
}

/** Ovozli suhbat sikli: tingla -> javob ber -> gapir. */
object Conversation {
    suspend fun say(ctx: Context, t: String) { Voice.speak(ctx, t)?.let { AssistantService.log.value = it } }

    suspend fun hear(ctx: Context): String? {
        for (lang in listOf("uz-UZ", "ru-RU", "en-US")) {
            val h = Listen.once(ctx, lang)
            if (h.text != null) return h.text
            if (h.error != 12 && h.error != 13) return null   // faqat "til qo'llanmaydi" xatosida keyingi tilga o'tamiz
        }
        return null
    }

    suspend fun run(ctx: Context, opening: String?, maxTurns: Int = 4, apologizeIfSilent: Boolean = true) {
        val talk = Talk(ctx)
        if (opening != null) say(ctx, opening)
        var turn = 0
        while (turn++ < maxTurns) {
            val heard = hear(ctx)
            if (heard == null) {
                if (talk.waiting) say(ctx, "Bekor qildim.") else if (turn == 1 && apologizeIfSilent) say(ctx, "Eshitmadim. Chaqirsangiz, shu yerdaman.")
                return
            }
            AssistantService.log.value = "Eshitildi: $heard"
            val r = talk.respond(heard)
            say(ctx, r.text)
            if (r.end) return
        }
    }
}
