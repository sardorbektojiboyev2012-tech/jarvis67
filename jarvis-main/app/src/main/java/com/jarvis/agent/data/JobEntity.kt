package com.jarvis.agent.data

import androidx.room.*

@Entity(tableName = "jobs", indices = [Index(value = ["idempotencyKey"], unique = true)])
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val idempotencyKey: String,      // bir xil kontent+platforma+vaqt = bitta vazifa
    val platform: String,            // "youtube", "instagram", ...
    val type: String,                // "video", "photo", "reply", ...
    val payloadJson: String,
    val state: JobState = JobState.DRAFTED,
    val scheduledAt: Long = 0,       // epoch ms
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val lastError: String? = null,
    val remoteId: String? = null,    // platforma qaytargan haqiqiy ID
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
