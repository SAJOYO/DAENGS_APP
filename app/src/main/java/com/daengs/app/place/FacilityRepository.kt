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
    is FacilityException -> FacilityResponsePolicy.failure(status)
    is java.io.IOException -> "연결을 확인하고 다시 시도해 주세요."
    else -> FacilityResponsePolicy.UNKNOWN
}
