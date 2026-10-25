package com.jarvis.agent.work

import com.jarvis.agent.data.JobEntity

sealed interface PublishResult {
    data class Success(val remoteId: String) : PublishResult   // faqat API haqiqiy ID qaytarsa
    data class Retry(val reason: String) : PublishResult       // vaqtincha xato (tarmoq, 429, 5xx)
    data class Fatal(val reason: String) : PublishResult       // ruxsat/format xatosi: qayta urinmaymiz
}

interface PlatformAdapter {
    val platform: String
    suspend fun publish(job: JobEntity): PublishResult
}

object AdapterRegistry {
    private val map = mutableMapOf<String, PlatformAdapter>()
    fun register(a: PlatformAdapter) { map[a.platform] = a }
    fun get(platform: String) = map[platform]
}
