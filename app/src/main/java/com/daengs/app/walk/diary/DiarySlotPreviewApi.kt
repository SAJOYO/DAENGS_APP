package com.daengs.app.walk.diary

import com.daengs.app.BuildConfig
import com.daengs.app.auth.Session
import com.daengs.app.walk.WalkSyncState
import com.daengs.app.walk.store.WalkSessionRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.coroutineContext

class DiarySlotPreviewApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) {
    suspend fun generate(token: String, walkId: String): Result<DiarySlotPreview> = withContext(Dispatchers.IO) {
        try {
            require(token.isNotBlank()) { "로그인 후 미리보기를 만들 수 있어요." }
            val address = baseUrl().trim().trimEnd('/')
            check(address.isNotBlank()) { "서버 연결을 확인해 주세요." }
            val id = UUID.fromString(walkId)
            val connection = URL("$address/app/walks/$id/diary-slots/preview").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                // Storage preparation plus the preview writer's own 15-second budget.
                connection.readTimeout = 60_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                val body = JSONObject().put("target_scene_count", 3).put("generate", true)
                    .put("collect_backgrounds", true)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val detail = runCatching { JSONObject(connection.errorStream?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }.orEmpty()).opt("detail") }.getOrNull()
                    throw DiarySlotPreviewException(status, when (status) {
                        401 -> "로그인이 만료됐어요. 다시 로그인해 주세요."
                        403 -> "이 산책의 미리보기를 볼 권한이 없어요."
                        404 -> if (detail == "산책 기록을 찾을 수 없습니다.") "서버에서 이 산책을 찾을 수 없어요."
                            else "이 서버에서는 새 방식 미리보기를 아직 사용할 수 없어요."
                        409 -> "산책 자료가 아직 준비되지 않았어요. 잠시 뒤 다시 시도해 주세요."
                        429 -> "요청이 많아요. 잠시 뒤 다시 시도해 주세요."
                        else -> "미리보기를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요."
                    })
                }
                val result = DiarySlotPreview.parse(JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }))
                coroutineContext.ensureActive()
                Result.success(result)
            } finally { connection.disconnect() }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            Result.failure(failure)
        }
    }
}

class DiarySlotPreviewException(val statusCode: Int, message: String) : IllegalStateException(message)

/** Sync only this finished, owned walk. The preview itself never writes a diary board. */
internal suspend fun loadDiarySlotPreview(
    sessionId: String,
    currentOwner: () -> String?,
    freshSession: suspend () -> Session?,
    readSession: suspend (String) -> WalkSessionRow?,
    sync: suspend (String, String) -> Unit,
    fetch: suspend (String, String) -> Result<DiarySlotPreview>,
): DiarySlotPreview {
    val owner = currentOwner()?.takeIf { it.isNotBlank() }
        ?: error("로그인 후 미리보기를 만들 수 있어요.")
    suspend fun ownedSession(): WalkSessionRow {
        coroutineContext.ensureActive()
        check(currentOwner() == owner) { "로그인 정보를 다시 확인해 주세요." }
        val row = readSession(sessionId)
        coroutineContext.ensureActive()
        check(row != null && row.id == sessionId && row.ownerId == owner) { "현재 계정에서 볼 수 없는 산책이에요." }
        check(row.endedAtMillis != null) { "산책을 마친 뒤 미리보기를 만들 수 있어요." }
        check(currentOwner() == owner) { "로그인 정보를 다시 확인해 주세요." }
        return row
    }
    ownedSession()
    val auth = freshSession() ?: error("로그인 후 미리보기를 만들 수 있어요.")
    check(auth.appUserId == owner) { "로그인 정보를 다시 확인해 주세요." }
    ownedSession()
    sync(auth.accessToken, sessionId)
    val row = ownedSession()
    val remoteId = row.serverWalkId
    check(!remoteId.isNullOrBlank() && row.syncState == WalkSyncState.DERIVED.storedValue) {
        "산책 동기화가 아직 끝나지 않았어요. 잠시 뒤 다시 시도해 주세요."
    }
    val result = fetch(auth.accessToken, remoteId).getOrThrow()
    check(ownedSession().serverWalkId == remoteId) { "산책 자료가 바뀌었어요. 다시 시도해 주세요." }
    check(result.clientSessionId == sessionId) { "요청과 다른 산책이 도착했어요. 다시 시도해 주세요." }
    return result
}
