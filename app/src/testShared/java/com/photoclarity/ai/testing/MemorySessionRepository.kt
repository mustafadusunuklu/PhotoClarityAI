package com.photoclarity.ai.testing

import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.ScanSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Test-only persistence boundary. Production always uses Room. */
class MemorySessionRepository(groups: List<DuplicateGroup> = emptyList(), initial: SessionSnapshot? = null) : ScanSessionRepository {
    private val seed = ScanSession("test-session", 1, status = SessionStatus.COMPLETED, scopeKey = "FULL", settings = ScanSettings(), attempted = groups.flatMap { it.photos }.distinctBy { it.mediaKey }.size)
    private val _state = MutableStateFlow(initial ?: SessionSnapshot(true, true, seed, groups, history = listOf(seed)))
    override val state = _state.asStateFlow()
    var rejectWrites = false
    val checkpoints = mutableListOf<SessionSnapshot>()
    private fun set(s: SessionSnapshot) { check(!rejectWrites); _state.value = s; checkpoints += s }
    override suspend fun initialize() { _state.value.session?.takeIf { it.status == SessionStatus.RUNNING }?.let { complete(it.copy(status = SessionStatus.INTERRUPTED, error = SessionError.PROCESS_INTERRUPTED), emptyList()) } }
    override suspend fun begin(session: ScanSession) { set(_state.value.copy(ready = true, trusted = true, session = session, groups = emptyList(), selectedKeys = emptySet(), history = listOf(session) + _state.value.history)) }
    override suspend fun checkpoint(session: ScanSession) { if (_state.value.session?.id == session.id && _state.value.session?.status == SessionStatus.RUNNING) set(_state.value.copy(session = session)) }
    override suspend fun complete(session: ScanSession, groups: List<DuplicateGroup>) {
        if (_state.value.session?.id != session.id || _state.value.session?.status != SessionStatus.RUNNING) return
        set(_state.value.copy(session = session, groups = groups, selectedKeys = emptySet(), history = _state.value.history.map { if (it.id == session.id) session else it }))
    }
    override suspend fun setTrusted(trusted: Boolean) { set(_state.value.copy(trusted = trusted)) }
    override suspend fun invalidate(error: SessionError) { set(_state.value.copy(trusted = false, session = _state.value.session?.copy(status = SessionStatus.STALE, error = error), selectedKeys = emptySet())) }
    override suspend fun select(sessionId: String, keys: Set<String>) { if (_state.value.session?.id == sessionId && _state.value.trusted) set(_state.value.copy(selectedKeys = keys.intersect(selectableKeys(_state.value.groups)))) }
    override suspend fun journal(request: RemovalJournal) { set(_state.value.copy(removal = request)) }
    override suspend fun reconcile(request: RemovalJournal) {
        val groups = reconcileGroups(_state.value.groups, request.removedKeys)
        set(_state.value.copy(groups = groups, selectedKeys = (_state.value.selectedKeys - request.removedKeys).intersect(selectableKeys(groups)), removal = request))
    }
}
