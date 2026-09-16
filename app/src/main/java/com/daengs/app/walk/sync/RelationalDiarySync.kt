package com.daengs.app.walk.sync

import com.daengs.app.walk.diary.relational.RelationalDiaryResponse
import com.daengs.app.walk.diary.relational.RelationalStatus
import com.daengs.app.walk.store.WalkDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.IOException

internal const val RELATIONAL_READ_TIMEOUT_MS = 240_000
internal const val RELATIONAL_POLL_INTERVAL_MS = 5_000L
private const val RELATIONAL_TARGET_SCENES = 3 // Intermediate goal; original records are never capped.

/** Network selection only. Other walk requests retain their normal 30-second timeout. */
internal suspend fun diaryHttpRequest(token: String, path: String, method: String, body: JSONObject?): JSONObject {
    val relational = path.contains("bundle_format=${RelationalDiaryResponse.FORMAT}") ||
        body?.optString("bundle_format") == RelationalDiaryResponse.FORMAT
    return WalkApi.call(token, path, method, body,
        readTimeoutMillis = if (relational && method == "POST") RELATIONAL_READ_TIMEOUT_MS else 30_000,
        maxResponseBytes = if (relational) 4_000_000 else null, parse = ::JSONObject).getOrThrow()
}

/** One explicit submission at most. Recovery/polling never invokes an old writer or a second POST. */
internal class RelationalDiarySync(
    private val dao: WalkDao,
    private val owner: () -> String,
    private val request: suspend (String, String, String, JSONObject?) -> JSONObject,
    private val pause: suspend () -> Unit,
    private val now: () -> Long,
) {
    suspend fun sync(token: String, sessionId: String, walkId: String, refresh: Boolean) {
        val account = owner()
        if (!dao.selectRelationalDiary(sessionId, walkId, account)) return
        val stamp = dao.relationalDiaryInputStamp(sessionId)
        val recovering = dao.sceneAnalysis(sessionId)?.entryStamp == stamp && dao.relationalSubmissionPending(sessionId)
        val path = "/$walkId/storyboard"
        val query = "$path?bundle_format=${RelationalDiaryResponse.FORMAT}&target_scene_count=$RELATIONAL_TARGET_SCENES"
        val started = now()
        suspend fun current() {
            currentCoroutineContext().ensureActive()
            if (owner() != account || dao.session(sessionId)?.let { it.ownerId == account && it.serverWalkId == walkId } != true)
                throw CancellationException("산책 계정이 변경되었어요.")
            check(dao.relationalDiaryInputStamp(sessionId) == stamp) { "기록이 바뀌었어요. 동기화 후 다시 확인해 주세요." }
        }
        suspend fun accept(raw: JSONObject): RelationalDiaryResponse {
            current()
            val response = RelationalDiaryResponse.parse(raw.toString())
            check(dao.acceptRelationalDiary(response.rawJson, sessionId, walkId, account, stamp)) {
                "최신 기록과 일기 응답이 달라요. 동기화 후 다시 확인해 주세요."
            }
            return response
        }
        suspend fun read(): RelationalDiaryResponse {
            current()
            return accept(request(token, query, "GET", null))
        }
        // Always GET before deciding whether a write is necessary, including process restart.
        var response = read()
        val submit = when (response.status) {
            RelationalStatus.RUNNING -> false
            RelationalStatus.READY -> refresh && !recovering
            RelationalStatus.FAILED -> refresh
            RelationalStatus.PENDING -> !recovering || refresh
            RelationalStatus.STALE -> true
        }
        if (submit) {
            current()
            val body = JSONObject().put("bundle_format", RelationalDiaryResponse.FORMAT)
                .put("target_scene_count", response.targetSceneCount)
                .put("expected_entries", JSONObject(response.entryRevisions))
                .put("expected_photo_manifest", response.photoManifest?.let {
                    JSONObject().put("publisher_id", it.publisherId).put("revision", it.revision)
                } ?: JSONObject.NULL)
                .put("refresh", refresh && response.status == RelationalStatus.READY)
            check(dao.markRelationalSubmission(sessionId, walkId, account, stamp))
            // Save intent BEFORE HTTP. A lost response does not imply generation failed on the server.
            val posted = try { request(token, path, "POST", body) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!e.mayHaveSubmittedDiary()) throw e // Includes 429: stop this run immediately.
                null
            }
            response = if (posted != null) accept(posted) else read()
        }
        var polls = 0
        while (response.status == RelationalStatus.RUNNING && polls++ < 48 && now() - started < RELATIONAL_READ_TIMEOUT_MS) {
            pause()
            response = read()
        }
        when (response.status) {
            RelationalStatus.READY -> Unit
            RelationalStatus.FAILED -> throw IllegalStateException("일기를 만들지 못했어요. 다시 생성할 수 있어요.")
            RelationalStatus.STALE -> throw IllegalStateException("기록이 바뀌었어요. 다시 동기화해 주세요.")
            RelationalStatus.PENDING -> throw IllegalStateException("생성 요청의 완료를 확인하지 못했어요. 다시 생성해 주세요.")
            RelationalStatus.RUNNING -> throw RelationalDiaryStillRunning()
        }
    }
}

internal class RelationalDiaryStillRunning : IOException("일기를 만들고 있어요. 잠시 뒤 다시 확인해 주세요.")

private fun Throwable.mayHaveSubmittedDiary(): Boolean {
    var value: Throwable? = this
    while (value != null) {
        if (value is WalkHttpException) return value.statusCode == 408 || value.statusCode >= 500
        if (value is IOException) return true
        value = value.cause
    }
    return false
}
