package com.daengs.app.place

import com.daengs.app.auth.Session
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

interface FacilityRepository {
    suspend fun discover(owner: String, query: FacilityQuery): FacilityResponse
    suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction): FacilityResponse
}

interface FacilityClient {
    suspend fun discover(token: String, query: FacilityQuery): FacilityResponse
    suspend fun act(token: String, previous: FacilityResponse, action: FacilityAction): FacilityResponse
}

class AuthenticatedFacilityRepository(
    private val client: FacilityClient,
    private val freshSession: suspend () -> Session?,
    private val currentSession: () -> Session?,
) : FacilityRepository {
    override suspend fun discover(owner: String, query: FacilityQuery) = authenticated(owner) { client.discover(it, query) }
    override suspend fun act(owner: String, previous: FacilityResponse, action: FacilityAction) =
        authenticated(owner) { client.act(it, previous, action) }

    private suspend fun authenticated(owner: String, operation: suspend (String) -> FacilityResponse): FacilityResponse {
        val session = freshSession() ?: throw FacilityException(401)
        fun requireCurrent() {
            if (session.appUserId != owner || currentSession()?.appUserId != owner ||
                currentSession()?.refreshToken != session.refreshToken) throw FacilityException(401)
        }
        requireCurrent()
        val response = operation(session.accessToken)
        requireCurrent()
        return response
    }
}

class FacilityApi(private val baseUrl: () -> String) : FacilityClient {
    override suspend fun discover(token: String, query: FacilityQuery) =
        exchange(token, "", query.toJson()).toFacilityResponse(query)

    override suspend fun act(token: String, previous: FacilityResponse, action: FacilityAction): FacilityResponse {
        require(previous.canChoose(action.choice))
        return exchange(token, "/actions", action.toJson()).toFacilityResponse(previous.request, action)
    }

    private suspend fun exchange(token: String, suffix: String, payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        if (baseUrl().isBlank()) throw FacilityException(0)
        val connection = (URL(baseUrl().trimEnd('/') + "/app/places/discovery" + suffix).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 45_000
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val status = connection.responseCode
            if (status !in 200..299) throw FacilityException(status)
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 512 * 1024) { "Facility response too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } finally { connection.disconnect() }
    }
}

class FacilityException(val status: Int) : IllegalStateException("Facility request failed ($status)")

fun Throwable.facilityMessage(): String = when (this) {
    is FacilityException -> when (status) {
        0 -> "AI 검색 서버 주소가 설정되지 않았어요."
        401, 403 -> "AI 조건 검색은 로그인 후 사용할 수 있어요."
        409 -> "검색 상태가 바뀌었어요. 문장으로 다시 검색해 주세요."
        410 -> "검색이 만료됐어요. 문장으로 다시 검색해 주세요."
        422 -> "현재 검색에서 선택할 수 없는 조건이에요. 다시 검색해 주세요."
        429 -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
        504 -> "AI 검색 시간이 초과됐어요. 다시 시도해 주세요."
        else -> "AI 검색 서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요."
    }
    is java.io.IOException -> "연결을 확인하고 다시 시도해 주세요."
    else -> "AI 검색 결과를 확인하지 못했어요. 다시 검색해 주세요."
}
