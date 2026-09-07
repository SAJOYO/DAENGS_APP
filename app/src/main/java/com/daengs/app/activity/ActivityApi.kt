package com.daengs.app.activity

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

interface ActivityClient {
    suspend fun sessionLink(token: String, clientSessionId: String): ActivitySessionLink
    suspend fun walkSummary(token: String, window: ActivityWalkWindow): ActivityWalkSummary
    suspend fun territorySummary(token: String, seasonId: String, petId: String): ActivityTerritorySummary
}

/** Read-only API. 생성하거나 조회를 예약하지 않으며 서버 flag를 바꾸지 않는다. */
class ActivityApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) : ActivityClient {
    override suspend fun sessionLink(token: String, clientSessionId: String): ActivitySessionLink {
        requireActivityUuid(clientSessionId)
        return ActivityJson.session(get(token, "/sessions/$clientSessionId")).also {
            require(it.clientSessionId == clientSessionId) { "Activity session response mismatch" }
        }
    }

    override suspend fun walkSummary(token: String, window: ActivityWalkWindow): ActivityWalkSummary {
        val query = "from_ms=${window.fromMs}&to_ms=${window.toMs}" +
            (window.petId?.let { "&pet_id=$it" } ?: "")
        return ActivityJson.walk(get(token, "/walks/summary?$query")).also {
            require(it.fromMs == window.fromMs && it.toMs == window.toMs && it.petId == window.petId) {
                "Activity window response mismatch"
            }
        }
    }

    override suspend fun territorySummary(token: String, seasonId: String, petId: String): ActivityTerritorySummary {
        require(seasonId.isNotBlank())
        requireActivityUuid(petId)
        val encodedSeason = URLEncoder.encode(seasonId, "UTF-8").replace("+", "%20")
        return ActivityJson.territory(get(token, "/territory/$encodedSeason/pets/$petId")).also {
            require(it.seasonId == seasonId && it.petId == petId) { "Activity territory response mismatch" }
        }
    }

    private suspend fun get(token: String, path: String): String = withContext(Dispatchers.IO) {
        ensureActive()
        require(token.isNotBlank()) { "Activity authentication required" }
        val base = baseUrl().trimEnd('/')
        check(base.isNotBlank()) { "Activity API address is not configured" }
        val connection = URL("$base/app/activity$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            ensureActive()
            if (status !in 200..299) {
                val code = runCatching {
                    val detail = Json.parseToJsonElement(body).jsonObject["detail"] as? JsonObject
                    (detail?.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.content
                }.getOrNull()
                throw ActivityHttpException(status, code)
            }
            body
        } finally { connection.disconnect() }
    }
}

/** 401/404/422/503와 activity_disabled 등을 그대로 전달한다. 본문·토큰은 오류 문구에 넣지 않는다. */
class ActivityHttpException(val statusCode: Int, val code: String?) :
    IllegalStateException("Activity request failed ($statusCode)")
