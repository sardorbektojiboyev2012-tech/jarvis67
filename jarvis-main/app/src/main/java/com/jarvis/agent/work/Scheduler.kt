package com.jarvis.agent.work

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object Scheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Yangi vazifa qo'shilganda: internet bo'lishi bilan ishga tushadi (oflayn bo'lsa kutadi). */
    fun kick(c: Context) = WorkManager.getInstance(c).enqueueUniqueWork(
        "publish-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
        OneTimeWorkRequestBuilder<PublishWorker>().setConstraints(online).build())

    /** Davriy tekshiruv (Android minimum 15 daqiqa; aniq vaqt kafolatlanmaydi, Doze/batareya tejash ta'sir qiladi). */
    fun startPeriodic(c: Context) = WorkManager.getInstance(c).enqueueUniquePeriodicWork(
        "publish-periodic", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<PublishWorker>(15, TimeUnit.MINUTES).setConstraints(online).build())
}
