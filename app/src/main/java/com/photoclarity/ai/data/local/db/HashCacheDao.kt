package com.photoclarity.ai.data.local.db

import androidx.room.*
import com.photoclarity.ai.data.local.db.entity.HashCacheEntity

@Dao
interface HashCacheDao {

    @Query("SELECT * FROM hash_cache WHERE photoUri = :uri AND lastModified = :lastModified AND fileSize = :fileSize LIMIT 1")
    suspend fun getValidCache(uri: String, lastModified: Long, fileSize: Long): HashCacheEntity?

    @Query("SELECT * FROM hash_cache WHERE md5Hash = :hash")
    suspend fun getByMd5(hash: String): List<HashCacheEntity>

    @Query("SELECT * FROM hash_cache WHERE sha256Hash = :hash")
    suspend fun getBySha256(hash: String): List<HashCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: HashCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCache(entities: List<HashCacheEntity>)

    @Query("DELETE FROM hash_cache WHERE photoUri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM hash_cache WHERE cachedAt < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long)

    @Query("SELECT COUNT(*) FROM hash_cache")
    suspend fun count(): Int
}
