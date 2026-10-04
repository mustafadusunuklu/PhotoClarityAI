package com.photoclarity.ai.data.local.db

import android.net.Uri
import com.photoclarity.ai.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned metadata only. No photo bytes, GPS, bitmap, grant or IntentSender. */
object SessionCodec {
    fun photo(photo: Photo): String = JSONObject().apply {
        put("v", 1); put("id", photo.id); put("uri", photo.mediaKey); put("name", photo.displayName)
        put("mime", photo.mimeType); put("size", photo.sizeBytes); put("added", photo.dateAdded)
        put("modified", photo.dateModified); put("taken", photo.dateTaken ?: JSONObject.NULL)
        put("width", photo.width); put("height", photo.height); put("bucket", photo.bucketName)
        put("generation", photo.generationModified ?: JSONObject.NULL); put("mediaStoreVersion", photo.mediaStoreVersion ?: JSONObject.NULL)
        put("bucketId", photo.bucketId); put("quality", photo.qualityScore.toDouble()); put("sharpness", photo.sharpnessScore.toDouble())
    }.toString()

    fun photo(payload: String): Photo = JSONObject(payload).let {
        require(it.getInt("v") == 1)
        val uri = Uri.parse(it.getString("uri"))
        Photo(it.getLong("id"), uri, uri, it.getString("name"), it.getString("mime"), it.getLong("size"),
            it.getLong("added"), it.getLong("modified"), if (it.isNull("taken")) null else it.getLong("taken"),
            it.getInt("width"), it.getInt("height"), it.getString("bucket"), it.getLong("bucketId"), null, null,
            qualityScore = it.getDouble("quality").toFloat(), sharpnessScore = it.getDouble("sharpness").toFloat(),
            generationModified = if (it.isNull("generation")) null else it.getLong("generation"),
            mediaStoreVersion = if (it.isNull("mediaStoreVersion")) null else it.getString("mediaStoreVersion"))
    }

    fun session(session: ScanSession): String = JSONObject().apply {
        put("v", 1); put("id", session.id); put("started", session.startedAt); put("ended", session.endedAt ?: JSONObject.NULL)
        put("status", session.status.name); put("scope", session.scopeKey); put("discovered", session.discovered)
        put("attempted", session.attempted); put("failed", session.failed); put("matched", session.matched)
        put("failedKnown", session.failedKnown)
        put("duration", session.durationMillis); put("error", session.error?.name ?: JSONObject.NULL)
        val s = session.settings
        put("settings", JSONObject().apply {
            put("algorithm", s.hashAlgorithm.name); put("exact", s.exactMatchEnabled); put("visual", s.visualSimilarityEnabled)
            put("threshold", s.similarityThreshold.toDouble()); put("sameFolder", s.includeSameFolderPhotos)
            put("metadata", s.useMetadata); put("gps", s.useGpsMetadata); put("smart", s.smartSelectionEnabled)
            put("burst", s.detectBurstShots); put("low", s.detectLowQuality); put("minSize", s.minFileSizeBytes)
            put("folders", JSONArray(s.selectedFolders.sorted()))
        })
    }.toString()

    fun session(payload: String): ScanSession = JSONObject(payload).let { o ->
        require(o.getInt("v") == 1)
        val s = o.getJSONObject("settings"); val folders = s.getJSONArray("folders")
        ScanSession(o.getString("id"), o.getLong("started"), if (o.isNull("ended")) null else o.getLong("ended"),
            SessionStatus.valueOf(o.getString("status")), o.getString("scope"),
            ScanSettings(HashAlgorithm.valueOf(s.getString("algorithm")), s.getBoolean("exact"), s.getBoolean("visual"),
                s.getDouble("threshold").toFloat(), s.getBoolean("sameFolder"), s.getBoolean("metadata"), s.getBoolean("gps"),
                s.getBoolean("smart"), s.getBoolean("burst"), s.getBoolean("low"),
                (0 until folders.length()).map { folders.getString(it) }.toSet(), s.getLong("minSize")),
            o.getInt("discovered"), o.getInt("attempted"), o.getInt("failed"), o.getInt("matched"), o.getLong("duration"),
            if (o.isNull("error")) null else SessionError.valueOf(o.getString("error")), o.optBoolean("failedKnown", false))
    }
}
