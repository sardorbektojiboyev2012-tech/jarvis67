package com.jarvis.agent.video

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import kotlin.math.max
import kotlin.math.min

object SlideRenderer {
    fun size(aspect: String): Pair<Int, Int> = when (aspect) { "16:9" -> 1920 to 1080; "1:1" -> 1080 to 1080; else -> 1080 to 1920 }

    private val palette = listOf(0xFF0F2027 to 0xFF2C5364, 0xFF1D976C to 0xFF093028, 0xFF283E51 to 0xFF4B79A1,
        0xFF42275A to 0xFF734B6D, 0xFF000428 to 0xFF004E92, 0xFF134E5E to 0xFF71B280)

    fun slide(w: Int, h: Int, text: String, idx: Int, bg: File?): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val src = bg?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
        if (src != null) {
            val s = max(w / src.width.toFloat(), h / src.height.toFloat())
            val dw = src.width * s; val dh = src.height * s
            c.drawBitmap(src, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawColor(0x55000000)
            src.recycle()
        } else {
            val (a, b) = palette[idx % palette.size]
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a.toInt(), b.toInt(), Shader.TileMode.CLAMP) })
        }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = min(w, h) * 0.075f; typeface = Typeface.DEFAULT_BOLD; setShadowLayer(8f, 0f, 4f, Color.BLACK)
        }
        val tw = (w * 0.84f).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, tw).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        val top = (h - layout.height) / 2f
        c.drawRoundRect(RectF((w - tw) / 2f - 30, top - 30, (w + tw) / 2f + 30, top + layout.height + 30), 36f, 36f,
            Paint().apply { color = 0x88000000.toInt() })
        c.save(); c.translate((w - tw) / 2f, top); layout.draw(c); c.restore()
        return bmp
    }

    fun savePng(b: Bitmap, f: File) { f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    fun saveJpg(b: Bitmap, f: File) { f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
}
