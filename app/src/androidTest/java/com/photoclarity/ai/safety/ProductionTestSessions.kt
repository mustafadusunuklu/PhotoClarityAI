package com.photoclarity.ai.safety

import android.content.Context
import androidx.room.withTransaction
import com.photoclarity.ai.core.session.SessionClock
import com.photoclarity.ai.data.local.db.PhotoClarityDatabase
import com.photoclarity.ai.domain.model.*
import com.photoclarity.ai.domain.repository.ScanSessionRepository
import dagger.hilt.android.EntryPointAccessors

/** Only for actual navigation tests; refuse to replace any non-test session. */
object ProductionTestSessions {
    private fun entry(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, Phase3TestEntryPoint::class.java)
    fun state(context: Context) = entry(context).sessions().state.value
    suspend fun seed(context: Context, groups: List<DuplicateGroup>, folder: String) {
        require(folder.startsWith("PhotoClarityAI_Phase1_") || folder.startsWith("PhotoClarityAI_Phase3_"))
        require(groups.flatMap { it.photos }.all { it.displayName.startsWith(folder) })
        val store = entry(context).sessions(); store.initialize()
        check(store.state.value.session == null || store.state.value.session?.id?.startsWith("device-session-") == true) {
            "Navigation test refuses to overwrite a user's session"
        }
        val s = ScanSession("device-session-$folder", SessionClock().now(), scopeKey = "FULL", settings = ScanSettings(selectedFolders = setOf(folder)))
        store.begin(s); store.complete(s.copy(status = SessionStatus.COMPLETED, attempted = groups.flatMap { it.photos }.distinctBy { it.mediaKey }.size,
            discovered = groups.flatMap { it.photos }.distinctBy { it.mediaKey }.size, matched = groups.flatMap { it.photos }.distinctBy { it.mediaKey }.size), groups)
        store.setTrusted(true)
    }
    suspend fun cleanup(context: Context, folder: String) {
        require(folder.startsWith("PhotoClarityAI_Phase1_") || folder.startsWith("PhotoClarityAI_Phase3_"))
        val id = "device-session-$folder"; val entry = entry(context)
        entry.database().withTransaction {
            val db = entry.database().openHelper.writableDatabase
            db.execSQL("DELETE FROM removal_items WHERE requestId IN (SELECT id FROM removal_requests WHERE sessionId = ?)", arrayOf(id))
            db.execSQL("DELETE FROM removal_requests WHERE sessionId = ?", arrayOf(id))
            for (table in listOf("scan_selection", "scan_members", "scan_groups")) db.execSQL("DELETE FROM $table WHERE sessionId = ?", arrayOf(id))
            db.execSQL("DELETE FROM scan_sessions WHERE id = ?", arrayOf(id))
        }
        entry.sessions().initialize()
    }
}
