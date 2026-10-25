package com.jarvis.agent.data

import android.content.Context
import com.jarvis.agent.security.*
import com.jarvis.agent.work.Scheduler
import java.security.MessageDigest

sealed interface Enqueued {
    data class Queued(val id: Long, val state: JobState) : Enqueued
    data object Duplicate : Enqueued
    data class Denied(val reason: String) : Enqueued
}

class JobRepository(private val ctx: Context) {
    private val dao = JarvisDb.get(ctx).jobs()
    private val policy = ApprovalPolicy(ctx)

    fun key(platform: String, type: String, payload: String, scheduledAt: Long): String =
        MessageDigest.getInstance("SHA-256").digest("$platform|$type|$payload|$scheduledAt".toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun enqueue(platform: String, type: String, payload: String, scheduledAt: Long,
                        action: ActionType, ruleId: String?): Enqueued {
        val verdict = policy.decide(action, ruleId)
        if (verdict.decision == Decision.DENY) return Enqueued.Denied(verdict.reason)
        val state = if (verdict.decision == Decision.REQUIRE_APPROVAL) JobState.AWAITING_APPROVAL else JobState.SCHEDULED
        val id = dao.insert(JobEntity(idempotencyKey = key(platform, type, payload, scheduledAt),
            platform = platform, type = type, payloadJson = payload, state = state,
            scheduledAt = scheduledAt, lastError = if (state == JobState.AWAITING_APPROVAL) verdict.reason else null))
        if (id == -1L) return Enqueued.Duplicate
        if (state == JobState.SCHEDULED) Scheduler.kick(ctx)
        return Enqueued.Queued(id, state)
    }

    suspend fun approve(id: Long) {
        val j = dao.get(id) ?: return
        if (j.state != JobState.AWAITING_APPROVAL && j.state != JobState.DRAFTED && j.state != JobState.FAILED) return
        dao.update(j.copy(state = JobState.SCHEDULED, nextAttemptAt = 0, lastError = null, updatedAt = System.currentTimeMillis()))
        Scheduler.kick(ctx)
    }

    fun observeAll() = dao.observeAll()
}
