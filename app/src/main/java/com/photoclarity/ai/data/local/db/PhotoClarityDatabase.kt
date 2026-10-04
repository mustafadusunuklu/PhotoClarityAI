package com.photoclarity.ai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.photoclarity.ai.data.local.db.entity.HashCacheEntity
import com.photoclarity.ai.data.local.db.entity.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HashCacheEntity::class, ScanSessionEntity::class, ScanGroupEntity::class, ScanMemberEntity::class,
        ScanSelectionEntity::class, RemovalRequestEntity::class, RemovalItemEntity::class],
    version = 3,
    exportSchema = true
)
abstract class PhotoClarityDatabase : RoomDatabase() {
    abstract fun hashCacheDao(): HashCacheDao
    abstract fun scanSessionDao(): ScanSessionDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hash_cache ADD COLUMN algorithmVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE hash_cache ADD COLUMN generationModified INTEGER")
                db.execSQL("ALTER TABLE hash_cache ADD COLUMN mediaStoreVersion TEXT")
                db.execSQL("ALTER TABLE hash_cache ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE hash_cache ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE hash_cache ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE hash_cache ADD COLUMN mimeType TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS scan_sessions (id TEXT NOT NULL PRIMARY KEY, startedAt INTEGER NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS scan_groups (sessionId TEXT NOT NULL, groupId TEXT NOT NULL, position INTEGER NOT NULL, type TEXT NOT NULL, similarity REAL NOT NULL, keeperId INTEGER NOT NULL, wasteBytes INTEGER NOT NULL, PRIMARY KEY(sessionId, groupId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS scan_members (sessionId TEXT NOT NULL, groupId TEXT NOT NULL, mediaKey TEXT NOT NULL, position INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(sessionId, groupId, mediaKey))")
                db.execSQL("CREATE TABLE IF NOT EXISTS scan_selection (sessionId TEXT NOT NULL, mediaKey TEXT NOT NULL, PRIMARY KEY(sessionId, mediaKey))")
                db.execSQL("CREATE TABLE IF NOT EXISTS removal_requests (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, createdAt INTEGER NOT NULL, mode TEXT NOT NULL, status TEXT NOT NULL, error TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS removal_items (requestId TEXT NOT NULL, mediaKey TEXT NOT NULL, position INTEGER NOT NULL, payload TEXT NOT NULL, issued INTEGER NOT NULL, removed INTEGER NOT NULL, failed INTEGER NOT NULL, PRIMARY KEY(requestId, mediaKey))")
            }
        }
    }
}
