package com.jarvis.agent.work

import android.content.Context
import androidx.work.*
import com.jarvis.agent.data.*
import com.jarvis.agent.security.*
import java.util.concurrent.TimeUnit

class PublishWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    private val dao = JarvisDb.get(ctx).jobs()

    override suspend fun doWork(): Result {
        // Jarayon o'lgan bo'lsa, UPLOADING'da qolgan vazifa yuborilgan-yuborilmaganini bilmaymiz:
        // dublikat post qilmaslik uchun avtomatik qayta yubormaymiz, foydalanuvchi tekshiradi.
        dao.stuckUploading().forEach {
            dao.update(it.copy(state = JobState.AWAITING_APPROVAL, updatedAt = now(),
                lastError = "Yuklash uzildi. Platformada borligini tekshiring, so'ng tasdiqlang."))
            Notifier.notify(applicationContext, it.id, "Tekshiruv kerak", "Vazifa #${it.id} uzilib qolgan")
        }
        var retryLater = false
        for (job in dao.due(now())) {
            val adapter = AdapterRegistry.get(job.platform)
            if (adapter == null) {
                fail(job, "'${job.platform}' uchun adapter hali ulanmagan")
                continue
            }
            val act = when (job.type) { "reply" -> ActionType.COMMENT; "message" -> ActionType.MESSAGE; else -> ActionType.PUBLISH }
            if (!ActionLimiter(applicationContext).tryAcquire(job.platform, act)) {
                dao.update(job.copy(nextAttemptAt = now() + TimeUnit.MINUTES.toMillis(30),
                    lastError = "Xavfsizlik limiti: keyinroq qayta uriniladi", updatedAt = now()))
                retryLater = true
                continue
            }
            dao.update(job.copy(state = JobState.UPLOADING, attempts = job.attempts + 1, updatedAt = now()))
            val cur = dao.get(job.id)!!
            when (val r = try { adapter.publish(cur) } catch (e: Exception) { PublishResult.Retry(e.message ?: "xato") }) {
                is PublishResult.Success -> {
                    dao.update(cur.copy(state = JobState.PUBLISHED, remoteId = r.remoteId, lastError = null, updatedAt = now()))
                    Notifier.notify(applicationContext, cur.id, "Chop etildi", "${cur.platform}: ${r.remoteId}")
                }
                is PublishResult.Fatal -> fail(cur, r.reason)
                is PublishResult.Retry ->
                    if (cur.attempts >= MAX_ATTEMPTS) fail(cur, "Urinishlar tugadi: ${r.reason}")
                    else {
                        val backoff = TimeUnit.MINUTES.toMillis(1L shl cur.attempts.coerceAtMost(8))
                        dao.update(cur.copy(state = JobState.SCHEDULED, lastError = r.reason,
                            nextAttemptAt = now() + backoff, updatedAt = now()))
                        retryLater = true
                    }
            }
        }
        return if (retryLater) Result.retry() else Result.success()
    }

    private suspend fun fail(j: JobEntity, why: String) {
        dao.update(j.copy(state = JobState.FAILED, lastError = why, updatedAt = now()))
        Notifier.notify(applicationContext, j.id, "Vazifa muvaffaqiyatsiz", "#${j.id}: $why")
    }
    private fun now() = System.currentTimeMillis()
    companion object { const val MAX_ATTEMPTS = 5 }
}
