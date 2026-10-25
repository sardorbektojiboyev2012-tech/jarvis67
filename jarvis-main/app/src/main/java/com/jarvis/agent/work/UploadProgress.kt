package com.jarvis.agent.work

import kotlinx.coroutines.flow.*

object UploadProgress {
    private val _s = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val state: StateFlow<Map<Long, Int>> = _s
    fun set(id: Long, pct: Int) { _s.value = _s.value + (id to pct) }
    fun clear(id: Long) { _s.value = _s.value - id }
}
