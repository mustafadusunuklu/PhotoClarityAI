package com.photoclarity.ai.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hash_cache")
data class HashCacheEntity(
    @PrimaryKey
    val photoUri: String,
    val md5Hash: String?,
    val sha256Hash: String?,
    val pHash: Long?,
    val aHash: Long?,
    val dHash: Long?,
    val qualityScore: Float,
    val sharpnessScore: Float,
    val lastModified: Long,   // to invalidate stale cache
    val fileSize: Long,
    val cachedAt: Long = System.currentTimeMillis()
)
