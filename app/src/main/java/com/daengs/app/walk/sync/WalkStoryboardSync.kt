package com.daengs.app.walk.sync

import com.daengs.app.walk.diary.GeoStoryboardBundle
import com.daengs.app.walk.diary.storyboardHash
import com.daengs.app.walk.store.WalkDao
import com.daengs.app.walk.store.WalkEntryRow
import com.daengs.app.walk.store.WalkSceneAnalysisRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

fun storyboardEntryStamp(rows: List<WalkEntryRow>): String = storyboardHash(JSONArray().apply {
    rows.sortedBy { it.id }.forEach { put(JSONArray(listOf(it.id, it.revision, it.mutationId, it.dirty, it.syncError))) }
}.toString())

class WalkStoryboardSync(
    private val dao: WalkDao,
    private val owner: () -> String,
    private val request: suspend (String, String, JSONObject) -> JSONObject = { token, path, body ->
        WalkApi.call(token, path, "POST", body, ::JSONObject).getOrThrow()
    },
) {
    private val mutex = Mutex()
    suspend fun sync(token: String, sessionId: String, walkId: String, refresh: Boolean = false) = mutex.withLock {
        val account = owner()
        val tokenOwner = runCatching { JSONObject(String(java.util.Base64.getUrlDecoder()
            .decode(token.split('.')[1]))).getString("sub") }.getOrNull()
        if (account.isEmpty() || tokenOwner != account || dao.session(sessionId)?.ownerId != account) return@withLock
        val rows = dao.entries(sessionId)
        if (rows.any { it.dirty || it.syncError != null }) throw IOException("행동 기록 동기화를 먼저 완료해야 해요.")
        val stamp = storyboardEntryStamp(rows)
        val previous = dao.sceneAnalysis(sessionId)
        val pending = WalkSceneAnalysisRow(sessionId, previous?.generation ?: 0, stamp,
            previous?.inputRevision.orEmpty(), "running", null, null)
        if (!dao.acceptSceneAnalysis(pending, account)) return@withLock
        try {
            val expected = JSONObject().apply { rows.forEach { put(it.id, it.revision) } }
            val body = JSONObject()
                .put("expected_entries", expected).put("refresh", refresh)
                .put("bundle_format", GeoStoryboardBundle.FORMAT_V3)
            val response = try { request(token, "/$walkId/storyboard", body) }
            catch (e: WalkHttpException) {
                // Only an old server's format enum rejection is safe to retry with v2.
                val oldFormat = e.statusCode == 422 && runCatching {
                    val errors = JSONArray(e.message)
                    errors.length() == 1 && errors.getJSONObject(0).let {
                        it.getString("type") == "literal_error" &&
                            it.getJSONArray("loc").toString() == JSONArray(listOf("body", "bundle_format")).toString()
                    }
                }.getOrDefault(false)
                if (!oldFormat) throw e
                request(token, "/$walkId/storyboard", body.put("bundle_format", GeoStoryboardBundle.FORMAT_V2))
            }
            require(response.getString("session_id") == sessionId)
            val remoteEntries = response.getJSONObject("entry_revisions")
            require(remoteEntries.keys().asSequence().toSet() == rows.map { it.id }.toSet() &&
                rows.all { remoteEntries.getInt(it.id) == it.revision }) { "분석 중 기록이 변경됐어요." }
            val status = response.getString("status")
            require(status in setOf("pending", "running", "ready", "failed", "stale"))
            val generation = response.getLong("generation").also { require(it >= 0) }
            val payload = if (status == "ready") response.getJSONObject("bundle").toString().also {
                val parsed = GeoStoryboardBundle.parse(it)
                require(!parsed.synthetic && parsed.sessionId == sessionId)
            } else null
            if (owner() != account) return@withLock
            val accepted = dao.acceptSceneAnalysis(WalkSceneAnalysisRow(sessionId, generation, stamp,
                response.getString("input_revision"), status, payload,
                if (status == "failed") "분석하지 못했어요. 다시 시도해 주세요." else null), account)
            if (!accepted || status != "ready") throw IOException("분석을 다시 확인해야 해요.")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (owner() == account) dao.failSceneAnalysis(sessionId, stamp, "분석하지 못했어요. 다시 시도해 주세요.")
            if (e is WalkHttpException && e.statusCode !in setOf(409, 425)) throw e
            throw IOException("스토리보드 분석을 다시 시도합니다.", e)
        }
    }
}
