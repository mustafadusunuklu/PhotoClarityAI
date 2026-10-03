package com.photoclarity.ai.ui.results

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.domain.repository.SettingsRepository
import com.photoclarity.ai.ui.scan.ScanResultHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.photoclarity.ai.core.media.PhotoAccessManager
import com.photoclarity.ai.core.media.PhotoAccess
import com.photoclarity.ai.core.media.RemovalBatchPolicy

private fun sizeLabel(bytes: Long): String = when {
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    else -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
}

data class ResultsUiState(
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedPhotoIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val pendingDeleteIntentSender: IntentSender? = null,
    val lastDeletedCount: Int = 0,
    val removalMode: PhotoRepository.RemovalMode = PhotoRepository.RemovalMode.PERMANENT_DELETE,
    val waitingForLegacyWritePermission: Boolean = false,
    val confirmationPhotos: List<Photo>? = null,
    val consentBatch: Int = 0,
    val consentBatchCount: Int = 0
) {
    val selectedPhotoCount get() = selectedPhotoIds.size
    val selectedSizeLabel get() = sizeLabel(groups.flatMap { it.photos }
        .distinctBy { it.id }.filter { it.id in selectedPhotoIds }.sumOf { it.sizeBytes })
    val confirmationSizeLabel get() = sizeLabel(confirmationPhotos.orEmpty().sumOf { it.sizeBytes })
    val selectionLocked get() = isLoading || confirmationPhotos != null
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    val qualityScorer: QualityScorer,
    private val photoAccess: PhotoAccessManager? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultsUiState(
        groups = if (photoAccess?.state?.value?.access == PhotoAccess.DENIED) emptyList() else ScanResultHolder.groups,
        error = ScanResultHolder.error ?: if (photoAccess?.state?.value?.access == PhotoAccess.DENIED)
            "Fotoğraf erişimi yok. İzin vererek yeniden tarayın." else null,
        removalMode = photoRepository.removalMode))
    val uiState = _uiState.asStateFlow()
    private var snapshot: List<Photo>? = null
    private var pending: PhotoRepository.DeleteResult.RequiresPermission? = null
    private val completed = mutableSetOf<Uri>()
    private val failures = mutableSetOf<Uri>()
    private val grantedRetries = mutableSetOf<Uri>()
    private var accessInvalidated = false
    private var accessRevision = ScanResultHolder.accessRevision ?: photoAccess?.state?.value?.revision

    init {
        photoAccess?.let { manager ->
            viewModelScope.launch {
                manager.state.collect { state ->
                    if (state.revision != accessRevision || state.error != null) {
                        accessRevision = state.revision
                        accessInvalidated = true
                        if (!_uiState.value.isLoading) invalidateAccess()
                    }
                }
            }
        }
    }

    private fun invalidateAccess() {
        val message = "Fotoğraf erişimi değişti veya doğrulanamadı. Güncel erişimle yeniden tarayın."
        ScanResultHolder.groups = emptyList()
        ScanResultHolder.error = message
        ScanResultHolder.accessRevision = accessRevision
        _uiState.value = _uiState.value.copy(groups = emptyList(), selectedPhotoIds = emptySet(),
            confirmationPhotos = null, waitingForLegacyWritePermission = false, error = message)
    }

    private fun selectableIds(): Set<Long> {
        val protected = _uiState.value.groups.map { it.recommendedKeepId }.toSet()
        return _uiState.value.groups.filter {
            it.groupType != DuplicateGroup.GroupType.LOW_QUALITY &&
                it.photos.size >= 2 && it.recommendedPhoto != null
        }.flatMap { it.photos }.filter { it.id !in protected }.map { it.id }.toSet()
    }

    fun togglePhotoSelection(photoId: Long, selected: Boolean) {
        if (_uiState.value.selectionLocked) return
        if (selected && photoId !in selectableIds()) {
            _uiState.value = _uiState.value.copy(error = "Korunacak fotoğraf ve tekil kalite önerileri silme için seçilemez.")
            return
        }
        val ids = _uiState.value.selectedPhotoIds
        _uiState.value = _uiState.value.copy(selectedPhotoIds = if (selected) ids + photoId else ids - photoId)
    }

    fun smartSelectAll() {
        if (_uiState.value.selectionLocked) return
        _uiState.value = _uiState.value.copy(selectedPhotoIds = selectableIds())
    }

    fun requestDeleteConfirmation() {
        if (_uiState.value.selectionLocked) return
        val ids = _uiState.value.selectedPhotoIds.intersect(selectableIds())
        val photos = _uiState.value.groups.flatMap { it.photos }.distinctBy { it.id }
            .filter { it.id in ids }.toList()
        if (photos.isNotEmpty()) _uiState.value = _uiState.value.copy(
            confirmationPhotos = photos, error = null, message = null)
    }

    fun dismissDeleteConfirmation() {
        if (!_uiState.value.isLoading) _uiState.value = _uiState.value.copy(confirmationPhotos = null)
    }

    fun onLegacyWritePermissionRequested() {
        if (_uiState.value.confirmationPhotos != null && !_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(waitingForLegacyWritePermission = true)
        }
    }

    fun onLegacyWritePermissionResult(granted: Boolean) {
        if (!_uiState.value.waitingForLegacyWritePermission) return
        _uiState.value = _uiState.value.copy(waitingForLegacyWritePermission = false)
        if (granted) deleteSelectedPhotos() else {
            _uiState.value = _uiState.value.copy(confirmationPhotos = null,
                error = "Silme izni verilmedi; fotoğraflar silinmedi.")
        }
    }

    // Must be preceded by explicit app confirmation; the frozen dialog selection is the transaction.
    fun deleteSelectedPhotos() {
        if (_uiState.value.isLoading) return
        val photos = _uiState.value.confirmationPhotos ?: return
        snapshot = photos.toList()
        completed.clear(); failures.clear(); grantedRetries.clear()
        _uiState.value = _uiState.value.copy(isLoading = true, confirmationPhotos = null,
            lastDeletedCount = 0, error = null, message = null, consentBatch = 0,
            consentBatchCount = if (_uiState.value.removalMode == PhotoRepository.RemovalMode.SYSTEM_TRASH)
                RemovalBatchPolicy.count(photos.size) else 0)
        viewModelScope.launch { runRemoval(photos.map { it.contentUri }) }
    }

    private suspend fun runRemoval(uris: List<Uri>) {
        try {
            when (val result = photoRepository.deletePhotos(uris.toList())) {
                is PhotoRepository.DeleteResult.Success -> {
                    completed.addAll(result.removedUris); failures.addAll(result.failedUris)
                    finish()
                }
                is PhotoRepository.DeleteResult.RequiresPermission -> {
                    completed.addAll(result.removedUris); failures.addAll(result.failedUris)
                    if (result.retryUris.firstOrNull() in grantedRetries) {
                        failures.addAll(result.retryUris)
                        finish("Sistem izni sonrasında silme tamamlanamadı.")
                    } else {
                        pending = result.copy(retryUris = result.retryUris.toList(),
                            trashUris = result.trashUris.toList(), remainingTrashUris = result.remainingTrashUris.toList())
                        _uiState.value = _uiState.value.copy(pendingDeleteIntentSender = result.intentSender,
                            consentBatch = _uiState.value.consentBatch + if (result.retryUris.isEmpty()) 1 else 0)
                    }
                }
                is PhotoRepository.DeleteResult.Error -> finish(result.message)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { finish("İşlem tamamlanamadı. Sonuçları yeniden tarayarak kontrol edin.") }
    }

    fun onDeletePromptLaunched() {
        _uiState.value = _uiState.value.copy(pendingDeleteIntentSender = null)
    }

    fun onDeletePromptFailed() {
        if (snapshot == null) return
        pending = null
        viewModelScope.launch { finish("Sistem onay ekranı açılamadı.") }
    }

    fun onDeleteResult(approved: Boolean) {
        val request = pending ?: return
        pending = null // Repeated callbacks cannot apply a transaction twice.
        _uiState.value = _uiState.value.copy(pendingDeleteIntentSender = null)
        viewModelScope.launch {
            if (!approved) {
                finish("İşlem iptal edildi. Tamamlanmayan fotoğraflar sonuçlarda korundu.")
            } else if (request.retryUris.isNotEmpty()) {
                grantedRetries.add(request.retryUris.first())
                runRemoval(request.retryUris)
            } else {
                try {
                    val batch = request.trashUris.ifEmpty { snapshot.orEmpty().map { it.contentUri } }
                    val verified = photoRepository.verifyTrashedPhotos(batch)
                    completed.addAll(verified.removedUris); failures.addAll(verified.failedUris)
                    if (accessInvalidated) finish("Fotoğraf erişimi değişti. Kalan bölümler işlenmedi; yeniden tarayın.")
                    else if (verified.failedUris.isNotEmpty()) finish("Bu bölümde bazı fotoğraflar doğrulanamadı. Kalan bölümler işlenmedi; yeniden tarayın.")
                    else if (request.remainingTrashUris.isNotEmpty()) runRemoval(request.remainingTrashUris)
                    else finish()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { finish("Çöp kutusuna taşıma doğrulanamadı. Yeniden tarayın.") }
            }
        }
    }

    private suspend fun finish(error: String? = null) {
        val photos = snapshot ?: return
        val removed = photos.filter { it.contentUri in completed }
        val removedIds = removed.map { it.id }.toSet()
        val updated = _uiState.value.groups.mapNotNull { group ->
            val remaining = group.photos.filter { it.id !in removedIds }
            if (group.groupType == DuplicateGroup.GroupType.LOW_QUALITY) {
                group.takeIf { remaining.isNotEmpty() }
            } else if (remaining.size < 2) null else {
                val keeper = remaining.firstOrNull { it.id == group.recommendedKeepId }
                    ?: qualityScorer.selectBest(remaining)
                group.copy(photos = remaining, recommendedKeepId = keeper.id,
                    totalWasteBytes = remaining.filter { it.id != keeper.id }.sumOf { it.sizeBytes })
            }
        }
        val incomplete = photos.size - removed.size
        val status = if (removed.isEmpty()) null else if (_uiState.value.removalMode == PhotoRepository.RemovalMode.SYSTEM_TRASH)
            "${removed.size} fotoğraf cihazın sistem çöp kutusuna taşındı. Geri yüklemeyi sistem veya galeri uygulaması yönetir."
        else "${removed.size} fotoğraf kalıcı olarak silindi."
        snapshot = null; pending = null
        ScanResultHolder.groups = updated
        _uiState.value = _uiState.value.copy(groups = updated,
            selectedPhotoIds = (_uiState.value.selectedPhotoIds - removedIds).intersect(
                updated.flatMap { it.photos }.map { it.id }.toSet()),
            isLoading = true, pendingDeleteIntentSender = null, lastDeletedCount = removed.size,
            message = status, error = error ?: if (incomplete > 0)
                "$incomplete fotoğraf için işlem doğrulanamadı; bu fotoğraflar silinmiş kabul edilmedi." else null)
        // Trashed bytes are still on the device. Do not claim reclaimed space for them.
        if (removed.isNotEmpty() && _uiState.value.removalMode == PhotoRepository.RemovalMode.PERMANENT_DELETE) {
            try { settingsRepository.addCleanedBytes(removed.sumOf { it.sizeBytes }) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "İşlem tamamlandı ancak alan istatistiği kaydedilemedi.")
            }
        }
        _uiState.value = _uiState.value.copy(isLoading = false)
        if (accessInvalidated) invalidateAccess()
    }

    fun getGroupById(groupId: String): DuplicateGroup? = _uiState.value.groups.firstOrNull { it.id == groupId }
}
