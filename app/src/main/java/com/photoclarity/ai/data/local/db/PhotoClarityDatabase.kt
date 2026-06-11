package com.photoclarity.ai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.photoclarity.ai.data.local.db.entity.HashCacheEntity

@Database(
    entities = [HashCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PhotoClarityDatabase : RoomDatabase() {
    abstract fun hashCacheDao(): HashCacheDao
}
