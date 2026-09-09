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
    rows.sortedBy { it.id }.forEach {
        val stamp = JSONArray(listOf(it.id, it.revision, it.mutationId, it.dirty, it.syncError))
        // Keep pre-migration v1 analysis stamps byte-for-byte compatible.
        if (it.isV2) stamp.put(it.pinRevision).put(it.pinPayload).put(it.pinDirty)
        put(stamp)
    }
}.toString())

class WalkStoryboardSync(
    private val dao: WalkDao,
    private val owner: () -> String,
    private val capabilities: suspend (String) -> JSONObject = { token ->
        WalkApi.call(token, "/entry-capabilities", "GET", null, parse = ::JSONObject).getOrThrow()
    },
    private val request: suspend (String, String, JSONObject) -> JSONObject = { token, path, body ->
        WalkApi.call(token, path, "POST", body, parse = ::JSONObject).getOrThrow()
    },
) {
    private val mutex = Mutex()
    private suspend fun compatibleRequest(token: String, path: String, body: JSONObject, pins: Boolean): JSONObject {
        val formats = if (pins) listOf(GeoStoryboardBundle.FORMAT_V5) else
            listOf(GeoStoryboardBundle.FORMAT_V4, GeoStoryboardBundle.FORMAT_V3, GeoStoryboardBundle.FORMAT_V2)
        for (format in formats) {
            try { return request(token, path, body.put("bundle_format", format)) }
            catch (e: WalkHttpException) {
                val unsupported = e.statusCode == 422 && runCatching {
                    val errors = JSONArray(e.message)
                    errors.length() == 1 && errors.getJSONObject(0).let {
                        it.getString("type") == "literal_error" &&
                            it.getJSONArray("loc").toString() == JSONArray(listOf("body", "bundle_format")).toString()
                    }
                }.getOrDefault(false)
                if (!unsupported || format == formats.last()) throw e
            }
        }
        error("No compatible storyboard format")
    }
    suspend fun sync(token: String, sessionId: String, walkId: String, refresh: Boolean = false) = mutex.withLock {
        val account = owner()
        // Tokens are opaque (the server uses JWE); ownership comes from the authenticated session.
        if (account.isEmpty() || dao.session(sessionId)?.ownerId != account) return@withLock
        val rows = dao.entries(sessionId)
        if (rows.any { it.dirty || it.pinDirty || it.pendingRequest != null || it.syncError != null ||
                it.pinPayload?.let { pin -> JSONObject(pin).optString("state") == "provisional" } == true })
            throw IOException("행동 기록과 위치 확정을 먼저 동기화해야 해요.")
        val pins = rows.any { it.isV2 && it.payload != null }
        if (pins) {
            val formats = capabilities(token).optJSONArray("storyboard_formats")
            if (owner() != account) return@withLock
            if (formats == null || (0 until formats.length()).none { formats.optString(it) == GeoStoryboardBundle.FORMAT_V5 })
                throw IOException("행동은 저장됐어요. 서버의 새 장면 분석 지원을 기다리고 있어요.")
        }
        val stamp = storyboardEntryStamp(rows)
        val previous = dao.sceneAnalysis(sessionId)
        val pending = WalkSceneAnalysisRow(sessionId, previous?.generation ?: 0, stamp,
            previous?.inputRevision.orEmpty(), "running", null, null)
        if (!dao.acceptSceneAnalysis(pending, account)) return@withLock
        try {
            val expected = JSONObject().apply { rows.forEach { put(it.id, it.revision) } }
            val body = JSONObject()
                .put("expected_entries", expected).put("refresh", refresh)
            if (owner() != account) return@withLock
            val response = compatibleRequest(token, "/$walkId/storyboard", body, pins)
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
                if (pins) require(JSONObject(it).getString("format") == GeoStoryboardBundle.FORMAT_V5)
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
