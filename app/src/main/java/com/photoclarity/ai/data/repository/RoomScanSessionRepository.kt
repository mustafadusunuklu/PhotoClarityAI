package com.photoclarity.ai.data.repository

import androidx.room.withTransaction
import com.photoclarity.ai.core.session.SessionClock
import com.photoclarity.ai.data.local.db.PhotoClarityDatabase
import com.photoclarity.ai.data.local.db.SessionCodec
import com.photoclarity.ai.data.local.db.entity.*
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomScanSessionRepository @Inject constructor(private val db: PhotoClarityDatabase, private val clock: SessionClock) : ScanSessionRepository {
    private val dao get() = db.scanSessionDao()
    private val lock = Mutex()
    private val _state = MutableStateFlow(SessionSnapshot())
    override val state = _state.asStateFlow()

    private suspend fun write(reloadFindings: Boolean = true, trustedAfterCommit: Boolean? = null,
                              update: (SessionSnapshot) -> SessionSnapshot = { it }, block: suspend () -> Unit) = lock.withLock {
        try {
            db.withTransaction { block() }
            if (reloadFindings) reload(trustedAfterCommit) else _state.value = update(_state.value).copy(storageError = null)
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _state.value = _state.value.copy(trusted = false, storageError = "Tarama verileri okunamadı veya kaydedilemedi. İşlem yapılmadı.")
            throw e
        }
    }

    override suspend fun initialize() = write {
        if (_state.value.ready) return@write
        dao.sessions().map { SessionCodec.session(it.payload) }.filter { it.status == SessionStatus.RUNNING }.forEach {
            save(it.copy(status = SessionStatus.INTERRUPTED, endedAt = clock.now(), error = SessionError.PROCESS_INTERRUPTED))
        }
    }
    private suspend fun save(s: ScanSession) = dao.session(ScanSessionEntity(s.id, s.startedAt, SessionCodec.session(s)))
    override suspend fun begin(session: ScanSession) = write(trustedAfterCommit = true) {
        check(dao.request()?.status.let { it == null || it == RemovalStatus.FINISHED.name })
        dao.clearSelection(); dao.clearMembers(); dao.clearGroups(); save(session)
    }
    override suspend fun checkpoint(session: ScanSession) = write {
        if (_state.value.session?.id == session.id && _state.value.session?.status == SessionStatus.RUNNING) save(session)
    }
    override suspend fun complete(session: ScanSession, groups: List<DuplicateGroup>) = write {
        if (_state.value.session?.id != session.id || _state.value.session?.status != SessionStatus.RUNNING) return@write
        save(session); replaceGroups(session.id, groups); dao.clearSelection()
    }
    private suspend fun replaceGroups(id: String, groups: List<DuplicateGroup>) {
        dao.clearGroups(); dao.clearMembers()
        dao.groups(groups.mapIndexed { index, g -> ScanGroupEntity(id, g.id, index, g.groupType.name, g.similarityScore, g.recommendedKeepId, g.totalWasteBytes) })
        dao.members(groups.flatMap { g -> g.photos.mapIndexed { index, p -> ScanMemberEntity(id, g.id, p.mediaKey, index, SessionCodec.photo(p)) } })
    }
    override suspend fun setTrusted(trusted: Boolean) { lock.withLock { _state.value = _state.value.copy(trusted = trusted) } }
    override suspend fun invalidate(error: SessionError) = write(trustedAfterCommit = false) {
        _state.value.session?.let { save(it.copy(status = SessionStatus.STALE, error = error, endedAt = it.endedAt ?: clock.now())) }
        dao.clearSelection()
    }
    override suspend fun select(sessionId: String, keys: Set<String>) {
        var selected: Set<String>? = null
        write(reloadFindings = false, update = { s -> selected?.let { s.copy(selectedKeys = it) } ?: s }) {
            val snapshot = _state.value
            if (snapshot.session?.id != sessionId || !snapshot.trusted || snapshot.session.status != SessionStatus.COMPLETED ||
                snapshot.removal?.status.let { it != null && it != RemovalStatus.FINISHED }) return@write
            selected = keys.intersect(selectableKeys(snapshot.groups))
            dao.clearSelection()
            dao.selection(checkNotNull(selected).map { ScanSelectionEntity(sessionId, it) })
        }
    }
    private suspend fun saveJournal(r: RemovalJournal) {
        val previous = _state.value.removal?.takeIf { it.id == r.id }
        if (previous == null) {
            dao.clearRequests(); dao.clearItems()
            dao.items(r.photos.mapIndexed { index, p -> RemovalItemEntity(r.id, p.mediaKey, index, SessionCodec.photo(p),
                p.mediaKey in r.issuedKeys, p.mediaKey in r.removedKeys, p.mediaKey in r.failedKeys) })
        } else {
            // Frozen payload is immutable. Only batch flags change after an external effect.
            check(previous.sessionId == r.sessionId && previous.mode == r.mode && previous.photos.size == r.photos.size)
            check(previous.photos === r.photos || previous.photos.zip(r.photos).all { (old, fresh) ->
                old == fresh || SessionCodec.photo(old) == SessionCodec.photo(fresh)
            })
            check(r.removedKeys.containsAll(previous.removedKeys) && r.failedKeys.containsAll(previous.failedKeys))
            if (previous.issuedKeys != r.issuedKeys) {
                dao.clearIssued(r.id)
                r.issuedKeys.chunked(AnalysisVersion.CACHE_BATCH).forEach { dao.markIssued(r.id, it) }
            }
            (r.removedKeys - previous.removedKeys).chunked(AnalysisVersion.CACHE_BATCH).forEach { dao.markRemoved(r.id, it) }
            (r.failedKeys - previous.failedKeys).chunked(AnalysisVersion.CACHE_BATCH).forEach { dao.markFailed(r.id, it) }
        }
        dao.request(RemovalRequestEntity(r.id, r.sessionId, r.createdAt, r.mode.name, r.status.name, r.error))
    }
    override suspend fun journal(request: RemovalJournal) = write(reloadFindings = false, update = { it.copy(removal = request) }) {
        val current = _state.value.removal
        check(current == null || current.id == request.id || current.status == RemovalStatus.FINISHED)
        saveJournal(request)
    }
    override suspend fun reconcile(request: RemovalJournal) = write {
        check(_state.value.removal?.id == request.id)
        if (_state.value.session?.id == request.sessionId) {
            val groups = reconcileGroups(_state.value.groups, request.removedKeys)
            replaceGroups(request.sessionId, groups)
            val selection = (_state.value.selectedKeys - request.removedKeys).intersect(selectableKeys(groups))
            dao.clearSelection(); dao.selection(selection.map { ScanSelectionEntity(request.sessionId, it) })
        }
        saveJournal(request)
    }
    private suspend fun reload(trustedAfterCommit: Boolean? = null) {
        val snapshot = db.withTransaction {
            val sessions = dao.sessions().map { SessionCodec.session(it.payload) }
            val session = sessions.firstOrNull()
            val id = session?.id
            val members = id?.let { dao.members(it) }.orEmpty().groupBy { it.groupId }
            val groups = id?.let { dao.groups(it) }.orEmpty().map { row ->
                val photos = members[row.groupId].orEmpty().map { SessionCodec.photo(it.payload) }
                DuplicateGroup(row.groupId, photos, DuplicateGroup.GroupType.valueOf(row.type), row.similarity, row.keeperId, row.wasteBytes)
            }
            val request = dao.request()?.let { row ->
                val items = dao.items(row.id)
                RemovalJournal(row.id, row.sessionId, row.createdAt, PhotoRepository.RemovalMode.valueOf(row.mode), items.map { SessionCodec.photo(it.payload) },
                    RemovalStatus.valueOf(row.status), items.filter { it.issued }.map { it.mediaKey }.toSet(),
                    items.filter { it.removed }.map { it.mediaKey }.toSet(), items.filter { it.failed }.map { it.mediaKey }.toSet(), row.error)
            }
            SessionSnapshot(true, trustedAfterCommit ?: _state.value.trusted, session, groups,
                id?.let { dao.selection(it).map { it.mediaKey }.toSet() }.orEmpty(), sessions, request)
        }
        _state.value = snapshot
    }
}
