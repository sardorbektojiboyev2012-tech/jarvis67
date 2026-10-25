package com.jarvis.agent.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(job: JobEntity): Long // -1 = dublikat
    @Update suspend fun update(job: JobEntity)
    @Query("SELECT * FROM jobs WHERE id = :id") suspend fun get(id: Long): JobEntity?
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC") fun observeAll(): Flow<List<JobEntity>>
    @Query("""SELECT * FROM jobs WHERE state IN ('APPROVED','SCHEDULED')
              AND scheduledAt <= :now AND nextAttemptAt <= :now ORDER BY scheduledAt""")
    suspend fun due(now: Long): List<JobEntity>
    @Query("SELECT * FROM jobs WHERE state = 'UPLOADING'") suspend fun stuckUploading(): List<JobEntity>
}
