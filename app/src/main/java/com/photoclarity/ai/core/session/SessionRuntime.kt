package com.photoclarity.ai.core.session

import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SessionClock @Inject constructor() {
    open fun now(): Long = System.currentTimeMillis()
    open fun elapsed(): Long = System.nanoTime() / 1_000_000
    open fun monthKey(at: Long): Int = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()).let { it.year * 100 + it.monthValue - 1 }
    open fun newId(): String = UUID.randomUUID().toString()
}

class SessionDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val compute: CoroutineDispatcher = Dispatchers.Default
)

@Singleton
class OperationGate @Inject constructor() {
    private var owner: String? = null
    @Synchronized fun acquire(id: String): Boolean { if (owner != null) return false; owner = id; return true }
    @Synchronized fun release(id: String) { if (owner == id) owner = null }
    @Synchronized fun busy(): Boolean = owner != null
}

class SessionScope(val scope: CoroutineScope)
