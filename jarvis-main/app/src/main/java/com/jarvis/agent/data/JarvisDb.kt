package com.jarvis.agent.data

import android.content.Context
import androidx.room.*

class Converters {
    @TypeConverter fun toState(v: String) = JobState.valueOf(v)
    @TypeConverter fun fromState(s: JobState) = s.name
}

@Database(entities = [JobEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class JarvisDb : RoomDatabase() {
    abstract fun jobs(): JobDao
    companion object {
        @Volatile private var inst: JarvisDb? = null
        fun get(c: Context): JarvisDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, JarvisDb::class.java, "jarvis.db").build().also { inst = it }
        }
    }
}
