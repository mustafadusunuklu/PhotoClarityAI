package com.photoclarity.ai.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(@PrimaryKey val id: String, val startedAt: Long, val payload: String)

@Entity(tableName = "scan_groups", primaryKeys = ["sessionId", "groupId"])
data class ScanGroupEntity(val sessionId: String, val groupId: String, val position: Int,
    val type: String, val similarity: Float, val keeperId: Long, val wasteBytes: Long)

@Entity(tableName = "scan_members", primaryKeys = ["sessionId", "groupId", "mediaKey"])
data class ScanMemberEntity(val sessionId: String, val groupId: String, val mediaKey: String, val position: Int, val payload: String)

@Entity(tableName = "scan_selection", primaryKeys = ["sessionId", "mediaKey"])
data class ScanSelectionEntity(val sessionId: String, val mediaKey: String)

@Entity(tableName = "removal_requests")
data class RemovalRequestEntity(@PrimaryKey val id: String, val sessionId: String, val createdAt: Long,
    val mode: String, val status: String, val error: String?)

@Entity(tableName = "removal_items", primaryKeys = ["requestId", "mediaKey"])
data class RemovalItemEntity(val requestId: String, val mediaKey: String, val position: Int,
    val payload: String, val issued: Boolean, val removed: Boolean, val failed: Boolean)
