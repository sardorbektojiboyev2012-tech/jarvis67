package com.jarvis.agent

import android.app.Application
import com.jarvis.agent.instagram.InstagramAdapter
import com.jarvis.agent.telegram.TelegramAdapter
import com.jarvis.agent.work.AdapterRegistry
import com.jarvis.agent.work.Scheduler
import com.jarvis.agent.youtube.YouTubeAdapter

class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdapterRegistry.register(YouTubeAdapter(this))
        AdapterRegistry.register(InstagramAdapter(this))
        AdapterRegistry.register(TelegramAdapter(this))
        Scheduler.startPeriodic(this)
    }
}
