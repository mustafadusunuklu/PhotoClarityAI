package com.photoclarity.ai.data.local.db

import androidx.room.*
import com.photoclarity.ai.data.local.db.entity.*

@Dao
interface ScanSessionDao {
    // Wall-clock rollback must not make a newly started scan restore an older session.
    @Query("SELECT * FROM scan_sessions ORDER BY rowid DESC LIMIT 50") suspend fun sessions(): List<ScanSessionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun session(row: ScanSessionEntity)
    @Query("SELECT * FROM scan_groups WHERE sessionId = :id ORDER BY position") suspend fun groups(id: String): List<ScanGroupEntity>
    @Query("SELECT * FROM scan_members WHERE sessionId = :id ORDER BY groupId, position") suspend fun members(id: String): List<ScanMemberEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun groups(rows: List<ScanGroupEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun members(rows: List<ScanMemberEntity>)
    @Query("DELETE FROM scan_groups") suspend fun clearGroups()
    @Query("DELETE FROM scan_members") suspend fun clearMembers()
    @Query("DELETE FROM scan_selection") suspend fun clearSelection()
    @Query("SELECT * FROM scan_selection WHERE sessionId = :id") suspend fun selection(id: String): List<ScanSelectionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun selection(rows: List<ScanSelectionEntity>)
    @Query("SELECT * FROM removal_requests ORDER BY createdAt DESC, rowid DESC LIMIT 1") suspend fun request(): RemovalRequestEntity?
    @Query("SELECT * FROM removal_items WHERE requestId = :id ORDER BY position") suspend fun items(id: String): List<RemovalItemEntity>
    @Query("DELETE FROM removal_requests") suspend fun clearRequests()
    @Query("DELETE FROM removal_items") suspend fun clearItems()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun request(row: RemovalRequestEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun items(rows: List<RemovalItemEntity>)
}
