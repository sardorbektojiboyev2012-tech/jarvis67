package com.jarvis.agent.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import com.jarvis.agent.assistant.AzureTts
import androidx.media3.transformer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class VideoSpec(val title: String, val description: String, val tags: List<String>, val scenes: List<String>,
                     val aspect: String = "9:16", val lang: String = "en", val images: List<File> = emptyList())
data class VideoResult(val video: File, val thumb: File, val warnings: List<String>)

/**
 * Bepul, to'liq lokal quvur: matn slaydlari (Canvas) + qurilma TTS ovozi + Media3 Transformer (H.264 MP4).
 * TTS ovozi bo'lmasa: har slayd 3 soniya, ovozsiz (zaxira). Generativ AI video bu yerda YO'Q (alohida adapter kerak).
 * Ilova ochiq bo'lganda ishlaydi (Transformer asosiy oqimda ishlaydi).
 */
object VideoPipeline {
    suspend fun create(ctx: Context, spec: VideoSpec, progress: (String) -> Unit): VideoResult {
        require(spec.scenes.isNotEmpty()) { "Ssenariy bo'sh" }
        val dir = File(ctx.filesDir, "media").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val (w, h) = SlideRenderer.size(spec.aspect)
        val warnings = mutableListOf<String>()

        progress("Slaydlar chizilmoqda...")
        val slides = withContext(Dispatchers.Default) {
            spec.scenes.mapIndexed { i, t ->
                File(dir, "s_${stamp}_$i.png").also { f ->
                    val b = SlideRenderer.slide(w, h, t, i, spec.images.getOrNull(i)); SlideRenderer.savePng(b, f); b.recycle()
                }
            }
        }

        progress("Ovoz yaratilmoqda...")
        var audio = emptyList<File>()
        var durs = List(spec.scenes.size) { 3_000_000L }
        val azure = spec.lang.startsWith("uz") && AzureTts.configured(ctx)
        val tts = Tts(ctx)
        if (azure || tts.init(Locale.forLanguageTag(spec.lang))) {
            val wavs = mutableListOf<File>(); val d = mutableListOf<Long>(); var ok = true
            for ((i, t) in spec.scenes.withIndex()) {
                val f = File(dir, "a_${stamp}_$i.${if (azure) "mp3" else "wav"}")
                val done = if (azure) withContext(Dispatchers.IO) { AzureTts.synth(ctx, t, f) } else tts.synth(t, f)
                if (!done) { ok = false; break }
                val us = durationUs(f); if (us <= 0) { ok = false; break }
                wavs += f; d += us
            }
            if (ok) { audio = wavs; durs = d } else warnings += "TTS ishlamadi: video ovozsiz yaratildi"
        } else warnings += "Qurilmada '${spec.lang}' TTS ovozi yo'q: video ovozsiz. Telefon sozlamalari > TTS dan ovoz paketini o'rnating."
        tts.shutdown()

        progress("Video yig'ilmoqda...")
        val out = File(dir, "video_$stamp.mp4")
        export(ctx, slides, durs, audio, out)

        progress("Thumbnail yaratilmoqda...")
        val thumb = File(dir, "thumb_$stamp.jpg")
        withContext(Dispatchers.Default) { val b = SlideRenderer.slide(1280, 720, spec.title, 0, spec.images.firstOrNull()); SlideRenderer.saveJpg(b, thumb); b.recycle() }

        (slides + audio).forEach { it.delete() }
        return VideoResult(out, thumb, warnings)
    }

    private fun durationUs(f: File): Long {
        val r = MediaMetadataRetriever()
        return try { r.setDataSource(f.path); (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000 }
        catch (e: Exception) { 0L } finally { r.release() }
    }

    private suspend fun export(ctx: Context, slides: List<File>, durs: List<Long>, audio: List<File>, out: File) =
        withContext(Dispatchers.Main) {   // Transformer Looper'li oqimni talab qiladi
            suspendCancellableCoroutine<Unit> { cont ->
                val vItems = slides.mapIndexed { i, f ->
                    EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(f))).setDurationUs(durs[i]).setFrameRate(30).build()
                }
                val seqs = mutableListOf(EditedMediaItemSequence(vItems))
                if (audio.isNotEmpty()) seqs += EditedMediaItemSequence(audio.map {
                    EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it))).setRemoveVideo(true).build()
                })
                val t = Transformer.Builder(ctx).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(Unit) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                }).build()
                t.start(Composition.Builder(seqs).build(), out.absolutePath)
                cont.invokeOnCancellation { t.cancel() }
            }
        }
}
