package com.photoclarity.ai.core.analysis

import com.photoclarity.ai.data.local.db.entity.HashCacheEntity
import com.photoclarity.ai.domain.model.AnalysisVersion
import com.photoclarity.ai.domain.model.Photo

object HashCachePolicy {
    fun valid(row: HashCacheEntity, photo: Photo): Boolean =
        row.photoUri == photo.contentUri.toString() && row.algorithmVersion == AnalysisVersion.CURRENT &&
        row.lastModified == photo.dateModified && row.fileSize == photo.sizeBytes &&
        row.generationModified == photo.generationModified && row.mediaStoreVersion == photo.mediaStoreVersion &&
        row.dateAdded == photo.dateAdded && row.width == photo.width && row.height == photo.height && row.mimeType == photo.mimeType

    fun entry(photo: Photo, now: Long): HashCacheEntity = HashCacheEntity(
        photo.contentUri.toString(), photo.md5Hash, photo.sha256Hash, photo.pHash, photo.aHash, photo.dHash,
        photo.qualityScore, photo.sharpnessScore, photo.dateModified, photo.sizeBytes, now, AnalysisVersion.CURRENT,
        photo.generationModified, photo.mediaStoreVersion, photo.dateAdded, photo.width, photo.height, photo.mimeType)
}
