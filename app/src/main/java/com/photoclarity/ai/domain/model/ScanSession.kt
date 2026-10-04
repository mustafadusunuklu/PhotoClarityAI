package com.photoclarity.ai.domain.model

import com.photoclarity.ai.domain.repository.PhotoRepository

enum class SessionStatus { RUNNING, COMPLETED, CANCELLED, INTERRUPTED, FAILED, STALE }
enum class SessionError { ACCESS_CHANGED, MEDIA_CHANGED, MEDIA_UNREADABLE, STORAGE, ANALYSIS, ANALYSIS_VERSION_CHANGED, BACKGROUND, PROCESS_INTERRUPTED, USER_CANCELLED }

data class ScanSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: SessionStatus = SessionStatus.RUNNING,
    val scopeKey: String,
    val settings: ScanSettings,
    val discovered: Int = 0,
    val attempted: Int = 0,
    val failed: Int = 0,
    val matched: Int = 0,
    val durationMillis: Long = 0,
    val error: SessionError? = null,
    val failedKnown: Boolean = false,
    val analysisVersion: Int = AnalysisVersion.CURRENT
)

enum class RemovalStatus { PREPARED, APPLYING, WAITING_SYSTEM, RECONCILING, FINISHED }
data class RemovalJournal(
    val id: String,
    val sessionId: String,
    val createdAt: Long,
    val mode: PhotoRepository.RemovalMode,
    val photos: List<Photo>,
    val status: RemovalStatus = RemovalStatus.PREPARED,
    val issuedKeys: Set<String> = emptySet(),
    val removedKeys: Set<String> = emptySet(),
    val failedKeys: Set<String> = emptySet(),
    val error: String? = null
)

data class SessionSnapshot(
    val ready: Boolean = false,
    val trusted: Boolean = false,
    val session: ScanSession? = null,
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val history: List<ScanSession> = emptyList(),
    val removal: RemovalJournal? = null,
    val storageError: String? = null
) {
    val visibleGroups get() = if (trusted && session?.status == SessionStatus.COMPLETED) groups else emptyList()
    val errorMessage: String? get() = storageError ?: when (session?.error) {
        SessionError.ACCESS_CHANGED -> "Fotoğraf erişimi değişti veya doğrulanamadı. Güncel erişimle yeniden tarayın."
        SessionError.MEDIA_CHANGED -> "Fotoğraflar değişti veya artık erişilemiyor. Yeniden tarayın."
        SessionError.MEDIA_UNREADABLE -> "Bazı fotoğraflar analiz edilemedi; sonuçlar tüm fotoğrafları kapsamıyor."
        SessionError.BACKGROUND -> "Uygulama arka plana geçti; tarama durduruldu. Yeniden başlatabilirsiniz."
        SessionError.PROCESS_INTERRUPTED -> "Önceki tarama kesildi; tamamlanmış sonuç yok. Yeniden başlatın."
        SessionError.USER_CANCELLED -> "Tarama iptal edildi; tamamlanmış sonuç yok."
        SessionError.STORAGE -> "Tarama verileri kaydedilemedi. Yeniden deneyin."
        SessionError.ANALYSIS -> "Tarama tamamlanamadı. Yeniden deneyin."
        SessionError.ANALYSIS_VERSION_CHANGED -> "Analiz yöntemi güncellendi. Eski sonuçlarla işlem yapmadan yeniden tarayın."
        null -> null
    }
}

val Photo.mediaKey: String get() = contentUri.toString()

/** Global keeper protection also covers malformed/overlapping input groups. */
fun selectableKeys(groups: List<DuplicateGroup>): Set<String> {
    val protected = groups.mapNotNull { it.recommendedPhoto?.mediaKey }.toSet()
    val ambiguousIds = groups.flatMap { it.photos }.groupBy { it.id }
        .filterValues { members -> members.map { it.mediaKey }.distinct().size > 1 }.keys
    return groups.filter { it.groupType != DuplicateGroup.GroupType.LOW_QUALITY &&
        it.photos.size >= 2 && it.recommendedPhoto != null }.flatMap { it.photos }
        .filter { it.mediaKey !in protected && it.id !in ambiguousIds }.map { it.mediaKey }.toSet()
}

fun reconcileGroups(groups: List<DuplicateGroup>, removed: Set<String>): List<DuplicateGroup> = groups.mapNotNull { group ->
    val remaining = group.photos.filter { it.mediaKey !in removed }
    if (group.groupType == DuplicateGroup.GroupType.LOW_QUALITY) group.takeIf { remaining.isNotEmpty() }
    else if (remaining.size < 2) null
    else group.copy(photos = remaining, totalWasteBytes = remaining.filter { it.mediaKey != group.recommendedPhoto?.mediaKey }.sumOf { it.sizeBytes })
}
