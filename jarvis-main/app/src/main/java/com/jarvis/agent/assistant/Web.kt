package com.jarvis.agent.assistant

import com.jarvis.agent.net.Http
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bepul ochiq API'lar: Open-Meteo (ob-havo, kalitsiz) va O'zbekiston Markaziy banki (valyuta kursi). Blokirovka qiladi: IO oqimida chaqiring. */
object Web {
    private fun get(url: String): String? = try { Http.request(url, timeoutMs = 8_000).let { if (it.code == 200) it.body else null } } catch (e: Exception) { null }

    private fun wmo(c: Int) = when (c) {
        0 -> "osmon ochiq"; 1 -> "asosan ochiq"; 2 -> "qisman bulutli"; 3 -> "bulutli"
        45, 48 -> "tuman"; in 51..57 -> "mayda yomg'ir"; in 61..67 -> "yomg'ir"; in 71..77 -> "qor"
        in 80..82 -> "jala"; 85, 86 -> "qor yog'moqda"; in 95..99 -> "momaqaldiroq"; else -> "noaniq"
    }

    fun weather(city: String): String {
        val g = get("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city, "UTF-8")}&count=1&language=en")
            ?: return "Ob-havoni olib bo'lmadi. Internetni tekshiring."
        val res = JSONObject(g).optJSONArray("results") ?: return "«$city» shahrini topolmadim."
        val c = res.getJSONObject(0)
        val w = get("https://api.open-meteo.com/v1/forecast?latitude=${c.getDouble("latitude")}&longitude=${c.getDouble("longitude")}" +
            "&current=temperature_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1")
            ?: return "Ob-havoni olib bo'lmadi."
        return try {
            val j = JSONObject(w); val cur = j.getJSONObject("current"); val d = j.getJSONObject("daily")
            "${c.getString("name")}. Hozir ${cur.getDouble("temperature_2m").roundToInt()} daraja, ${wmo(cur.getInt("weather_code"))}. " +
                "Bugun eng yuqori ${d.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()}, eng past ${d.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()} daraja. " +
                "Yog'ingarchilik ehtimoli ${d.getJSONArray("precipitation_probability_max").optInt(0)} foiz. Shamol soatiga ${cur.getDouble("wind_speed_10m").roundToInt()} kilometr."
        } catch (e: Exception) { "Ob-havo ma'lumotini o'qib bo'lmadi." }
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%,.0f", v).replace(',', ' ')

    fun rate(code: String): String {
        val body = get("https://cbu.uz/uz/arkhiv-kursov-valyut/json/") ?: return "Valyuta kursini olib bo'lmadi. Internetni tekshiring."
        return try {
            val a = JSONArray(body)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                if (o.optString("Ccy") == code) {
                    val diff = o.optString("Diff").toDoubleOrNull() ?: 0.0
                    return "Markaziy bank kursi: ${o.optString("Nominal")} ${o.optString("CcyNm_UZ", code)} ${fmt(o.getString("Rate").toDouble())} so'm. " +
                        "Kechagiga nisbatan ${fmt(abs(diff))} so'm ${if (diff >= 0) "oshgan" else "kamaygan"}."
                }
            }
            "$code valyutasi topilmadi."
        } catch (e: Exception) { "Kurs ma'lumotini o'qib bo'lmadi." }
    }
}
