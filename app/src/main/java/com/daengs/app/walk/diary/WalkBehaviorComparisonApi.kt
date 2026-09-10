package com.daengs.app.walk.diary

import com.daengs.app.BuildConfig
import com.daengs.app.auth.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class WalkBehaviorComparisonApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) {
    suspend fun query(token: String, query: WalkBehaviorComparisonQuery): Result<WalkBehaviorComparison> = withContext(Dispatchers.IO) {
        try {
            require(token.isNotBlank()) { "로그인 후 행동 기록을 볼 수 있어요." }
            val address = baseUrl().trimEnd('/')
            check(address.isNotBlank()) { "서버 연결을 확인해 주세요." }
            val connection = URL("$address/app/walks/spatial-diary/behavior-comparisons/query").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000; connection.readTimeout = 30_000; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.outputStream.use { it.write(query.toJson().toString().toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) {
                    val status = connection.responseCode
                    val detail = runCatching { JSONObject(connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
                        .optJSONObject("detail") }.getOrNull()
                    throw SpatialDiaryHttpException(status, detail?.optString("code"),
                        if (status == 404 || status == 503) "행동 기록 비교를 준비 중이에요. 잠시 뒤 다시 시도해 주세요."
                        else detail?.optString("message")?.takeIf { it.isNotBlank() } ?: "행동 기록을 불러오지 못했어요. ($status)")
                }
                val result = WalkBehaviorComparison.parse(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
                // Blocking I/O may finish after the user switches the dog or behavior.
                coroutineContext.ensureActive()
                require(result.query.toJson().toString() == query.toJson().toString()) { "요청과 다른 행동 기록이 도착했어요." }
                Result.success(result)
            } finally { connection.disconnect() }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            Result.failure(failure)
        }
    }
}

/** Bind the one snapshot request to the account that initiated it. */
internal suspend fun loadBehaviorComparison(query: WalkBehaviorComparisonQuery,
    currentOwner: () -> String?, freshSession: suspend () -> Session?,
    fetch: suspend (String, WalkBehaviorComparisonQuery) -> Result<WalkBehaviorComparison>): WalkBehaviorComparison {
    val owner = currentOwner()
    val session = freshSession() ?: error("로그인 후 행동 기록을 볼 수 있어요.")
    check(owner != null && session.appUserId == owner && currentOwner() == owner)
    val result = fetch(session.accessToken, query).getOrThrow()
    coroutineContext.ensureActive()
    check(currentOwner() == owner) { "로그인 정보를 다시 확인해 주세요." }
    return result
}
