package com.photoclarity.ai.domain.repository

import com.photoclarity.ai.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface ScanSessionRepository {
    val state: StateFlow<SessionSnapshot>
    suspend fun initialize()
    suspend fun begin(session: ScanSession)
    suspend fun checkpoint(session: ScanSession)
    suspend fun complete(session: ScanSession, groups: List<DuplicateGroup>)
    suspend fun setTrusted(trusted: Boolean)
    suspend fun invalidate(error: SessionError)
    suspend fun select(sessionId: String, keys: Set<String>)
    suspend fun journal(request: RemovalJournal)
    suspend fun reconcile(request: RemovalJournal)
}
