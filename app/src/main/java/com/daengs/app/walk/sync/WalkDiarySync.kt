package com.daengs.app.walk.sync

import com.daengs.app.walk.diary.GeoStoryboardBundle
import com.daengs.app.walk.diary.ServerDiaryBundle
import com.daengs.app.walk.diary.storyboardHash
import com.daengs.app.walk.store.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Versioned local source stamp. No Room migration and no reclassification of legacy stamps. */
fun diaryInputStamp(entries: List<WalkEntryRow>, photos: WalkPhotoSyncRow?, images: List<WalkPhotoRow>): String =
    "diary:" + storyboardHash(JSONArray().put(storyboardEntryStamp(entries))
        .put(photos?.let { JSONArray(listOf(it.publisherId, it.revision, it.acknowledgedRevision, it.pendingPayload)) })
        .put(JSONArray().apply { images.sortedBy { it.id }.forEach {
            put(JSONArray(listOf(it.id, it.capturedAtMillis, it.locationCapturedAtMillis, it.lat, it.lng, it.accuracyM)))
        } }).toString())

/** Negotiates once per sync; new format never falls back after a generation/read error. */
class WalkDiarySync(
    private val dao: WalkDao,
    private val owner: () -> String,
    private val legacy: suspend (String, String, String, Boolean) -> Unit = { token, id, walk, refresh ->
        WalkStoryboardSync(dao, owner).sync(token, id, walk, refresh)
    },
    private val pause: suspend () -> Unit = { delay(2_000) },
    private val request: suspend (String, String, String, JSONObject?) -> JSONObject = { token, path, method, body ->
        WalkApi.call(token, path, method, body, parse = ::JSONObject).getOrThrow()
    },
) {
    private val mutex = Mutex()

    suspend fun sync(token: String, sessionId: String, walkId: String, refresh: Boolean = false) = mutex.withLock {
        val account = owner()
        val walk = dao.session(sessionId)
        if (account.isBlank() || walk?.ownerId != account || walk.serverWalkId != walkId || walk.endedAtMillis == null) return@withLock
        val capabilities = try { request(token, "/storyboard/capabilities", "GET", null) }
        catch (e: WalkHttpException) { if (e.statusCode == 404) null else throw e }
        if (owner() != account) return@withLock
        val formats = capabilities?.getJSONArray("diary_formats")
        if (formats == null || (0 until formats.length()).none { formats.getString(it) == ServerDiaryBundle.FORMAT }) {
            legacy(token, sessionId, walkId, refresh)
            return@withLock
        }
        val rows = dao.entries(sessionId)
        if (rows.any { it.dirty || it.pinDirty || it.pendingRequest != null || it.syncError != null ||
                it.pinPayload?.let { pin -> JSONObject(pin).optString("state") == "provisional" } == true })
            throw IOException("행동·메모와 위치 확정을 먼저 동기화해 주세요.")
        val photos = dao.photoSync(sessionId)
        if (photos != null && (photos.ownerId != account || photos.pendingPayload != null || photos.revision != photos.acknowledgedRevision))
            throw IOException("사진 동기화를 마친 뒤 일기를 만들 수 있어요.")
        if (photos == null && dao.photos(sessionId).isNotEmpty())
            throw IOException("사진 목록을 먼저 동기화해 주세요.")
        val stamp = diaryInputStamp(rows, photos, dao.photos(sessionId))
        val before = dao.sceneAnalysis(sessionId)
        if (!dao.acceptSceneAnalysis(WalkSceneAnalysisRow(sessionId, before?.generation ?: 0, stamp,
                before?.inputRevision.orEmpty(), "running", null, null), account)) return@withLock
        val expected = JSONObject().apply { rows.forEach { put(it.id, it.revision) } }
        val path = "/$walkId/storyboard"
        val query = "$path?bundle_format=${ServerDiaryBundle.FORMAT}&target_scene_count=${ServerDiaryBundle.TARGET_SCENES}"
        suspend fun current() {
            if (owner() != account || dao.session(sessionId)?.ownerId != account ||
                diaryInputStamp(dao.entries(sessionId), dao.photoSync(sessionId), dao.photos(sessionId)) != stamp)
                throw IOException("일기를 만드는 중 기록이 바뀌었어요. 다시 동기화해 주세요.")
        }
        fun validate(response: JSONObject) {
            require(response.getString("format") == ServerDiaryBundle.RESPONSE && response.getString("session_id") == sessionId)
            require(response.getInt("target_scene_count") == ServerDiaryBundle.TARGET_SCENES)
            require(response.getLong("generation") >= 0)
            require(response.getString("status") in setOf("pending", "running", "ready", "failed", "stale"))
            val remote = response.getJSONObject("entry_revisions")
            require(remote.keys().asSequence().toSet() == rows.map { it.id }.toSet() && rows.all { remote.getInt(it.id) == it.revision })
            val manifest = response.optJSONObject("photo_manifest")
            require(response.getString("photos_status") in setOf("complete", "not_available"))
            if (manifest != null) require(response.getString("photos_status") == "complete" && manifest.getLong("revision") >= 1)
            if (photos != null) require(manifest != null && manifest.getString("publisher_id") == photos.publisherId &&
                manifest.getLong("revision") == photos.acknowledgedRevision) { "사진 목록이 서버와 달라요." }
        }
        suspend fun accept(response: JSONObject) {
            current(); validate(response)
            val ready = response.getString("status") == "ready"
            if (ready) {
                val bundle = GeoStoryboardBundle.parse(response.toString())
                require(bundle.sessionId == sessionId)
                // Every live local entry must still have its own source card.
                require(bundle.scenes.mapNotNull { it.entryReference?.entryId }.toSet() == rows.filter { it.payload != null }.map { it.id }.toSet())
            }
            check(dao.acceptSceneAnalysis(WalkSceneAnalysisRow(sessionId, response.getLong("generation"), stamp,
                response.getString("input_revision"), response.getString("status"),
                if (ready) response.toString() else null,
                if (response.getString("status") == "failed") "일기를 만들지 못했어요. 다시 시도해 주세요." else null), account))
        }
        try {
            current()
            var response = request(token, query, "GET", null)
            current(); validate(response)
            if (refresh || response.getString("status") in setOf("pending", "stale", "failed", "running")) {
                val body = JSONObject().put("bundle_format", ServerDiaryBundle.FORMAT)
                    .put("target_scene_count", ServerDiaryBundle.TARGET_SCENES).put("expected_entries", expected)
                    .put("expected_photo_manifest", response.optJSONObject("photo_manifest") ?: JSONObject.NULL)
                    // A non-refresh POST reuses a live lease, or recovers one abandoned by process death.
                    .put("refresh", refresh && response.getString("status") != "running")
                response = request(token, path, "POST", body)
            }
            accept(response)
            repeat(8) {
                if (response.getString("status") != "running") return@repeat
                pause(); current()
                response = request(token, query, "GET", null)
                accept(response)
            }
            if (response.getString("status") != "ready")
                throw DiaryStillPending()
        } catch (e: Exception) {
            if (e is CancellationException || e is DiaryStillPending) throw e
            if (owner() == account) dao.failSceneAnalysis(sessionId, stamp, "일기를 확인하지 못했어요. 다시 시도해 주세요.")
            throw IOException("일기를 확인하지 못했어요. 기록 동기화 후 다시 시도해 주세요.", e)
        }
    }
}

private class DiaryStillPending : IOException("일기를 준비하고 있어요. 잠시 뒤 다시 확인해 주세요.")
