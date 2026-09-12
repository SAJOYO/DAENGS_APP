package com.daengs.app.walk.sync

import com.daengs.app.walk.diary.GeoStoryboardBundle
import com.daengs.app.walk.diary.DIARY_PREPARATION_BUDGET_MS
import com.daengs.app.walk.diary.ServerDiaryBundle
import com.daengs.app.walk.diary.ServerDiaryBoard
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
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val request: suspend (String, String, String, JSONObject?) -> JSONObject = { token, path, method, body ->
        WalkApi.call(token, path, method, body, parse = ::JSONObject).getOrThrow()
    },
) {
    private val mutex = Mutex()

    suspend fun sync(token: String, sessionId: String, walkId: String, refresh: Boolean = false) = mutex.withLock {
        val account = owner()
        val walk = dao.session(sessionId)
        if (account.isBlank() || walk?.ownerId != account || walk.serverWalkId != walkId || walk.endedAtMillis == null) return@withLock
        val publication = dao.diaryPublication(sessionId)
        suspend fun closed() = publication != null && (nowMillis() >= publication.deadlineAtMillis ||
            dao.diaryPublication(sessionId)?.publishedBundle != null)
        if (closed()) return@withLock
        val capabilities = try { request(token, "/storyboard/capabilities", "GET", null) }
        catch (e: WalkHttpException) { if (e.statusCode == 404) null else throw e }
        if (owner() != account) return@withLock
        val formats = capabilities?.getJSONArray("diary_formats")
        val offered = formats?.let { (0 until it.length()).map(it::getString).toSet() }.orEmpty()
        val selectedFormat = when {
            ServerDiaryBoard.FORMAT in offered -> ServerDiaryBoard.FORMAT
            ServerDiaryBundle.FORMAT in offered -> ServerDiaryBundle.FORMAT
            else -> null
        }
        val publicationCapability = capabilities?.optJSONObject("diary_publication")
        val serverBudget = (publicationCapability?.optLong("budget_ms", 10_000) ?: 10_000)
            .coerceIn(0, DIARY_PREPARATION_BUDGET_MS)
        if (selectedFormat == null) {
            if (publication != null) return@withLock
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
                before?.inputRevision.orEmpty(), "running", null, null), account, nowMillis())) return@withLock
        val expected = JSONObject().apply { rows.forEach { put(it.id, it.revision) } }
        val path = "/$walkId/storyboard"
        val query = "$path?bundle_format=$selectedFormat&target_scene_count=${ServerDiaryBundle.TARGET_SCENES}"
        val regenerate = refresh && selectedFormat != ServerDiaryBoard.FORMAT
        suspend fun current() {
            if (owner() != account || dao.session(sessionId)?.ownerId != account ||
                diaryInputStamp(dao.entries(sessionId), dao.photoSync(sessionId), dao.photos(sessionId)) != stamp)
                throw IOException("일기를 만드는 중 기록이 바뀌었어요. 다시 동기화해 주세요.")
        }
        fun validate(response: JSONObject) {
            val allowed = if (selectedFormat == ServerDiaryBoard.FORMAT)
                setOf(ServerDiaryBoard.RESPONSE, ServerDiaryBundle.RESPONSE) else setOf(ServerDiaryBundle.RESPONSE)
            require(response.getString("format") in allowed && response.getString("session_id") == sessionId)
            require(response.getInt("target_scene_count") in 1..50)
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
        suspend fun acceptLegacy(response: JSONObject) {
            current()
            require(response.getString("session_id") == sessionId && response.getString("status") == "ready")
            require(response.getLong("generation") >= 0)
            val revisions = response.getJSONObject("entry_revisions")
            require(revisions.keys().asSequence().toSet() == rows.map { it.id }.toSet() &&
                rows.all { revisions.getInt(it.id) == it.revision })
            val raw = response.getJSONObject("bundle").toString()
            require(GeoStoryboardBundle.parse(raw).sessionId == sessionId)
            check(dao.acceptSceneAnalysis(WalkSceneAnalysisRow(sessionId, response.getLong("generation"), stamp,
                response.getString("input_revision"), "ready", raw, null), account, nowMillis()))
        }
        suspend fun accept(response: JSONObject) {
            if (selectedFormat == ServerDiaryBoard.FORMAT && !response.has("format")) {
                acceptLegacy(response)
                return
            }
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
                if (response.getString("status") == "failed") "일기를 만들지 못했어요. 다시 시도해 주세요." else null), account, nowMillis()))
        }
        suspend fun generate(response: JSONObject): JSONObject? {
            current()
            val responseFormat = if (response.getString("format") == ServerDiaryBoard.RESPONSE)
                ServerDiaryBoard.FORMAT else ServerDiaryBundle.FORMAT
            if (closed()) return null
            if (publication != null && (responseFormat != ServerDiaryBoard.FORMAT || serverBudget == 0L ||
                    publicationCapability?.optString("format") != ServerDiaryBoard.FORMAT)) return null
            val body = JSONObject().put("bundle_format", responseFormat)
                .put("target_scene_count", response.getInt("target_scene_count")).put("expected_entries", expected)
                .put("expected_photo_manifest", response.optJSONObject("photo_manifest") ?: JSONObject.NULL)
                // Reuse a live reservation. Context retries never reset the saved local deadline.
                .put("refresh", regenerate && response.getString("status") != "running")
            if (publication != null) body.put("preparation_budget_ms",
                (publication.deadlineAtMillis - nowMillis()).coerceIn(0, serverBudget))
            return request(token, path, "POST", body)
        }
        try {
            current()
            var response = request(token, query, "GET", null)
            // The server preserves an already stored pre-diary board instead of upgrading it.
            if (selectedFormat == ServerDiaryBoard.FORMAT && !response.has("format")) {
                current()
                require(response.getString("session_id") == sessionId)
                if (response.getString("status") != "ready") {
                    if (publication != null) return@withLock
                    legacy(token, sessionId, walkId, false)
                    return@withLock
                }
                acceptLegacy(response)
                return@withLock
            }
            current(); validate(response)
            if (regenerate || response.getString("status") in setOf("pending", "stale", "failed", "running")) {
                response = generate(response) ?: return@withLock
            }
            accept(response)
            var polls = 0
            while (polls++ < if (publication != null) 10 else 8) {
                val status = response.getString("status")
                if (status != "running" && !(publication != null && status == "pending")) break
                if (closed()) return@withLock
                pause(); current()
                if (closed()) return@withLock
                response = request(token, query, "GET", null)
                accept(response)
                // Context preparation can return pending before an LLM reservation exists.
                // Reading alone cannot start it once the context becomes available.
                if (publication != null && response.getString("status") == "pending") {
                    response = generate(response) ?: return@withLock
                    accept(response)
                }
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
