package com.daengs.app.walk.store

import com.daengs.app.walk.diary.relational.RelationalDiaryResponse
import com.daengs.app.walk.diary.relational.RelationalStatus
import com.daengs.app.walk.sync.diaryInputStamp
import org.json.JSONObject

/** Latest attempt and last publication are separate, so a failed refresh need not erase prose. */
data class RelationalDiaryCache(
    val latest: RelationalDiaryResponse,
    val published: RelationalDiaryResponse?,
    val submissionPending: Boolean = false,
)

/** Dedicated format in the existing JSON column; no legacy board conversion or publication job. */
internal object RelationalDiaryStorage {
    const val STAMP_PREFIX = "diary:relational:"
    private const val CACHE_FORMAT = "walk-relational-diary-cache-v1"

    fun stamp(entries: List<WalkEntryRow>, photos: WalkPhotoSyncRow?, images: List<WalkPhotoRow>): String =
        STAMP_PREFIX + diaryInputStamp(entries, photos, images).removePrefix("diary:")

    suspend fun currentStamp(dao: WalkDao, sessionId: String): String =
        stamp(dao.entries(sessionId), dao.photoSync(sessionId), dao.photos(sessionId))

    /** Durable format choice is made before the first GET, not inferred from a model result. */
    suspend fun select(dao: WalkDao, sessionId: String, walkId: String, ownerId: String): Boolean {
        val walk = dao.session(sessionId) ?: return false
        if (ownerId.isBlank() || walk.ownerId != ownerId || walk.serverWalkId != walkId || walk.endedAtMillis == null) return false
        if (dao.isRelationalDiary(sessionId)) return true
        dao.saveSceneAnalysis(WalkSceneAnalysisRow(sessionId, 0, currentStamp(dao, sessionId), "", "pending", null, null))
        return true
    }

    suspend fun markSubmission(dao: WalkDao, sessionId: String, walkId: String, ownerId: String, stamp: String): Boolean {
        val walk = dao.session(sessionId) ?: return false
        if (ownerId.isBlank() || walk.ownerId != ownerId || walk.serverWalkId != walkId || currentStamp(dao, sessionId) != stamp) return false
        val row = dao.sceneAnalysis(sessionId) ?: return false
        if (!row.entryStamp.startsWith(STAMP_PREFIX) || row.entryStamp != stamp || decode(row, walkId) == null) return false
        dao.saveSceneAnalysis(row.copy(bundle = JSONObject(requireNotNull(row.bundle)).put("submission_pending", true).toString()))
        return true
    }

    fun submissionPending(row: WalkSceneAnalysisRow?): Boolean = row?.takeIf { it.entryStamp.startsWith(STAMP_PREFIX) }
        ?.bundle?.let { runCatching { JSONObject(it).optBoolean("submission_pending", false) }.getOrDefault(false) } ?: false

    /** Called inside the DAO transaction: ownership, revisions and current source are checked atomically. */
    suspend fun accept(dao: WalkDao, raw: String, sessionId: String, walkId: String,
        ownerId: String, expectedStamp: String): Boolean {
        val response = RelationalDiaryResponse.parse(raw)
        require(response.sessionId == sessionId) { "다른 산책의 일기 응답이에요." }
        val walk = dao.session(sessionId) ?: return false
        if (ownerId.isBlank() || walk.ownerId != ownerId || walk.endedAtMillis == null || walk.serverWalkId != walkId) return false
        if (currentStamp(dao, sessionId) != expectedStamp) return false
        if (!matchesSources(dao, response, ownerId)) return false
        val before = dao.sceneAnalysis(sessionId)
        val previous = before?.takeIf { it.entryStamp.startsWith(STAMP_PREFIX) }
        if (previous != null && previous.generation > response.generation) return false
        // A delayed poll must not regress a publication from the same generation/input.
        if (previous?.status == "ready" && previous.generation == response.generation &&
            previous.entryStamp == expectedStamp && response.status in setOf(RelationalStatus.PENDING, RelationalStatus.RUNNING)) return false
        if (previous?.status == "stale" && previous.generation == response.generation &&
            previous.entryStamp == expectedStamp && response.status != RelationalStatus.STALE &&
            previous.inputRevision != response.inputRevision) return false
        val old = previous?.takeIf { it.entryStamp == expectedStamp }?.let { decode(it, walkId) }
        val published = when (response.status) {
            RelationalStatus.READY -> response
            RelationalStatus.STALE -> null
            else -> old?.published?.takeIf { it.inputRevision == response.inputRevision }
        }
        val cache = JSONObject().put("format", CACHE_FORMAT).put("server_walk_id", walkId).put("latest", raw)
            .put("submission_pending", submissionPending(previous) && response.status in setOf(RelationalStatus.PENDING, RelationalStatus.RUNNING))
            .put("published", published?.rawJson ?: JSONObject.NULL).toString()
        dao.saveSceneAnalysis(WalkSceneAnalysisRow(sessionId, response.generation, expectedStamp,
            response.inputRevision, response.status.name.lowercase(java.util.Locale.ROOT), cache,
            response.errorCode, if (published != null) expectedStamp else null))
        return true
    }

    /** Read the saved response only. A changed/deleted record never reappears from the cache. */
    suspend fun read(dao: WalkDao, sessionId: String, ownerId: String): RelationalDiaryCache? {
        val walk = dao.session(sessionId) ?: return null
        return project(dao.sceneAnalysis(sessionId), dao.entries(sessionId), dao.photoSync(sessionId),
            dao.photos(sessionId), walk, ownerId)
    }

    /** Same checks for Room reads and flow projections; never issue a second read while assembling. */
    fun project(row: WalkSceneAnalysisRow?, entries: List<WalkEntryRow>, photos: WalkPhotoSyncRow?,
        images: List<WalkPhotoRow>, walk: WalkSessionRow?, ownerId: String): RelationalDiaryCache? {
        if (walk == null || ownerId.isBlank() || walk.ownerId != ownerId || walk.endedAtMillis == null) return null
        if (row == null || row.sessionId != walk.id || !row.entryStamp.startsWith(STAMP_PREFIX) ||
            row.entryStamp != stamp(entries, photos, images)) return null
        if (entries.any { it.sessionId != walk.id } || images.any { it.sessionId != walk.id || it.ownerId != ownerId }) return null
        val cache = decode(row, walk.serverWalkId ?: return null) ?: return null
        return cache.takeIf { matchesSources(it.latest, ownerId, entries, photos, images) }
    }

    private suspend fun matchesSources(dao: WalkDao, response: RelationalDiaryResponse, ownerId: String): Boolean {
        return matchesSources(response, ownerId, dao.entries(response.sessionId), dao.photoSync(response.sessionId), dao.photos(response.sessionId))
    }

    private fun matchesSources(response: RelationalDiaryResponse, ownerId: String, entries: List<WalkEntryRow>,
        photos: WalkPhotoSyncRow?, images: List<WalkPhotoRow>): Boolean {
        if (entries.any { it.dirty || it.pinDirty || it.pendingRequest != null || it.syncError != null ||
                it.pinPayload?.let { pin -> JSONObject(pin).optString("state") == "provisional" } == true }) return false
        if (entries.associate { it.id to it.revision.toLong() } != response.entryRevisions) return false
        // A restored walk has no local publisher. Its authenticated server manifest belongs
        // to the saved diary, not to a local upload awaiting acknowledgement.
        if (photos == null) return images.isEmpty()
        return photos.ownerId == ownerId && photos.pendingPayload == null && photos.revision == photos.acknowledgedRevision &&
            response.photoManifest?.let { it.publisherId == photos.publisherId && it.revision == photos.acknowledgedRevision } == true
    }

    private fun decode(row: WalkSceneAnalysisRow, walkId: String): RelationalDiaryCache? = runCatching {
        val raw = JSONObject(requireNotNull(row.bundle))
        require(raw.getString("format") == CACHE_FORMAT && raw.getString("server_walk_id") == walkId)
        val latest = RelationalDiaryResponse.parse(raw.getString("latest"))
        val published = if (raw.isNull("published")) null else RelationalDiaryResponse.parse(raw.getString("published"))
        require(latest.sessionId == row.sessionId && latest.generation == row.generation && latest.inputRevision == row.inputRevision)
        require(latest.status.name.lowercase(java.util.Locale.ROOT) == row.status)
        require(published == null || (published.sessionId == row.sessionId && published.status == RelationalStatus.READY &&
            published.generation <= latest.generation && published.inputRevision == latest.inputRevision && row.bundleEntryStamp == row.entryStamp))
        RelationalDiaryCache(latest, published, raw.optBoolean("submission_pending", false))
    }.getOrNull()
}
