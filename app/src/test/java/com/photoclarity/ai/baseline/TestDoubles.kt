package com.photoclarity.ai.baseline

import android.net.Uri
import com.photoclarity.ai.data.local.db.HashCacheDao
import com.photoclarity.ai.data.local.db.entity.HashCacheEntity
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.Mockito

/** No real URI, MediaStore, filesystem, Room or DataStore access. */
internal fun photo(id: Long, size: Long = id * 100): Photo {
    val uri = Mockito.mock(Uri::class.java)
    Mockito.`when`(uri.toString()).thenReturn("content://phase0.synthetic/images/$id")
    return Photo(id, uri, uri, "synthetic-$id.png", "image/png", size,
        100, 100, null, 64, 64, "synthetic", 1, null, null)
}

internal fun group(type: DuplicateGroup.GroupType = DuplicateGroup.GroupType.VISUAL_SIMILAR) =
    DuplicateGroup("group-1", (1L..4L).map { photo(it) }, type, .9f, 1, 900)

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

internal class FakePhotoRepository : PhotoRepository {
    override var removalMode = PhotoRepository.RemovalMode.PERMANENT_DELETE
    var result: PhotoRepository.DeleteResult = PhotoRepository.DeleteResult.Success(emptySet())
    var verified = PhotoRepository.DeleteResult.Success(emptySet())
    val verificationRequests = mutableListOf<List<Uri>>()
    override suspend fun verifyTrashedPhotos(uris: List<Uri>): PhotoRepository.DeleteResult.Success {
        verificationRequests += uris.toList()
        return verified
    }
    val requests = mutableListOf<List<Uri>>()
    override suspend fun deletePhotos(uris: List<Uri>): PhotoRepository.DeleteResult {
        requests += uris.toList()
        return result
    }
    override suspend fun loadAllPhotos(selectedFolders: Set<String>) = emptyList<Photo>()
    override suspend fun loadPhotosFromBucket(bucketId: Long) = emptyList<Photo>()
    override suspend fun getAllBuckets() = emptyMap<Long, String>()
    override suspend fun getStorageInfo() = StorageInfo(100, 50, 0, 0)
    override suspend fun getPhotoCount() = 0
}

internal class FakeSettingsRepository : SettingsRepository {
    val additions = mutableListOf<Long>()
    override fun getScanSettings() = flowOf(ScanSettings())
    override suspend fun saveScanSettings(settings: ScanSettings) = Unit
    override suspend fun getCleanedBytesThisMonth() = additions.sum()
    override suspend fun addCleanedBytes(bytes: Long) { additions += bytes }
    override suspend fun resetMonthlyStats() { additions.clear() }
}

/** Implements only persistence semantics; real PhotoAnalyzer owns all analysis. */
internal class FakeHashCacheDao(entries: List<HashCacheEntity>) : HashCacheDao {
    private val cache = java.util.concurrent.ConcurrentHashMap(entries.associateBy { it.photoUri })
    private val writeCount = java.util.concurrent.atomic.AtomicInteger()
    val writes: Int get() = writeCount.get()
    override suspend fun getValidCache(uri: String, lastModified: Long, fileSize: Long) =
        cache[uri]?.takeIf { it.lastModified == lastModified && it.fileSize == fileSize }
    override suspend fun getByMd5(hash: String) = cache.values.filter { it.md5Hash == hash }
    override suspend fun getBySha256(hash: String) = cache.values.filter { it.sha256Hash == hash }
    override suspend fun insertCache(entity: HashCacheEntity) { writeCount.incrementAndGet(); cache[entity.photoUri] = entity }
    override suspend fun insertAllCache(entities: List<HashCacheEntity>) { entities.forEach { insertCache(it) } }
    override suspend fun deleteByUri(uri: String) { cache.remove(uri) }
    override suspend fun deleteExpired(expiryTime: Long) { cache.entries.removeAll { it.value.cachedAt < expiryTime } }
    override suspend fun count() = cache.size
}
