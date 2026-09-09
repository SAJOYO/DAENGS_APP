package com.daengs.app.place

import com.daengs.app.auth.Session
import com.daengs.app.location.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class ConversationResult(
    val sessionId: String,
    val revision: Int,
    val requestId: String,
    val filters: JsonObject,
    val search: PlaceSearchResponse?,
    val selected: PlaceKey?,
    val order: List<PlaceKey>,
    val answer: String?,
    val receipt: JsonObject,
) {
    val kinds get() = filters.getValue("candidate_kinds").jsonArray.map { PlaceKind.fromWire(it.jsonPrimitive.content) }
    val origin get() = filters.getValue("spatial").jsonObject.let {
        GeoPoint(it.getValue("lat").jsonPrimitive.double, it.getValue("lng").jsonPrimitive.double)
    }
    val radius get() = filters.getValue("spatial").jsonObject.getValue("radius_m").jsonPrimitive.int
    val nameQuery get() = filters.getValue("name_query").jsonPrimitive.content
    val parkingFirst get() = filters.getValue("preferences").jsonArray.isNotEmpty()
    val matches get() = receipt.getValue("result_matches_filters").jsonPrimitive.boolean
}

data class ConversationUiState(
    val busy: Boolean = false,
    val result: ConversationResult? = null,
    val error: String? = null,
    val selected: PlaceKey? = null,
)

fun JsonObject.toConversationResult(): ConversationResult {
    require(getValue("contract_version").jsonPrimitive.content == "facility-conversation-v1")
    fun JsonElement.key() = jsonObject.let {
        PlaceKey(it.getValue("source").jsonPrimitive.content, it.getValue("ref").jsonPrimitive.content)
    }
    val search = this["search"]?.takeUnless { it is JsonNull }?.jsonObject?.toPlaceSearchResponse()
    val selected = this["selected"]?.takeUnless { it is JsonNull }?.key()
    val order = getValue("display_order").jsonArray.map { it.key() }
    val keys = search?.groups?.flatMap { it.results }?.map { it.place.key }.orEmpty().toSet()
    require(order.distinct().size == order.size && order.all { it in keys })
    require(selected == null || selected in keys)
    return ConversationResult(
        getValue("session_id").jsonPrimitive.content, getValue("revision").jsonPrimitive.int,
        getValue("client_request_id").jsonPrimitive.content, getValue("filters").jsonObject,
        search, selected, order,
        this["answer"]?.takeUnless { it is JsonNull }?.jsonObject?.get("text")?.jsonPrimitive?.content,
        getValue("receipt").jsonObject,
    )
}

fun interface ConversationClient {
    suspend fun exchange(token: String, payload: JsonObject): JsonObject
}

class ConversationApi(private val baseUrl: () -> String) : ConversationClient {
    override suspend fun exchange(token: String, payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl().trimEnd('/') + "/app/places/conversation").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; instanceFollowRedirects = false
            connectTimeout = 10_000; readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            if (connection.responseCode !in 200..299) throw FacilityException(connection.responseCode)
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= 1024 * 1024)
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } finally { connection.disconnect() }
    }
}

/** Normal and AI searches share one saved state. The client never invents result facts. */
class FacilityConversationRepository(
    private val client: ConversationClient,
    private val fallback: PlaceSearchRepository,
    private val freshSession: suspend () -> Session?,
    private val currentSession: () -> Session?,
) : PlaceSearchRepository {
    private val mutable = MutableStateFlow(ConversationUiState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var owner: String? = null

    fun cancelPending() {
        generation++
        mutable.value = mutable.value.copy(busy = false)
    }

    fun invalidate() {
        generation++
        mutable.value = ConversationUiState()
        owner = null
    }

    fun select(key: PlaceKey) {
        if (key in mutable.value.result?.order.orEmpty()) mutable.value = mutable.value.copy(selected = key)
    }

    override suspend fun search(request: PlaceSearchRequest): PlaceSearchResponse {
        val session = freshSession()
        if (session == null || request.dogSize != null || request.dogWeightKg != null || request.dogAgeYears != null) {
            invalidate()
            return fallback.search(request)
        }
        val result = run(session, "manual", request.toJson())
        return requireNotNull(result.search).also { it.requireDogEcho(request) }
    }

    suspend fun overview(requests: List<PlaceSearchRequest>): PlaceSearchResponse {
        invalidate()
        return searchPlaceBatches(fallback, requests)
    }

    suspend fun chat(query: String, visibleOrder: List<PlaceKey>) {
        val session = freshSession() ?: throw FacilityException(401)
        require(mutable.value.result != null) { "먼저 카테고리를 선택해 주변 장소를 검색해 주세요." }
        run(session, "chat", query = query, visibleOrder = visibleOrder)
    }

    private suspend fun run(
        session: Session, mode: String, manual: JsonObject? = null,
        query: String = "", visibleOrder: List<PlaceKey> = emptyList(),
    ): ConversationResult {
        if (owner != null && owner != session.appUserId) invalidate()
        owner = session.appUserId
        val before = mutable.value.result
        val mine = ++generation
        val requestId = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("client_request_id", requestId); put("mode", mode); put("query", query)
            if (before != null) {
                put("session_id", before.sessionId); put("expected_revision", before.revision)
            }
            manual?.let { put("manual", it) }
            if (mode == "chat") mutable.value.selected?.let { key ->
                put("visible_selected", buildJsonObject { put("source", key.source); put("ref", key.ref) })
            }
            put("visible_order", buildJsonArray {
                visibleOrder.take(120).forEach { key -> add(buildJsonObject {
                    put("source", key.source); put("ref", key.ref)
                }) }
            })
        }
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            val result = client.exchange(session.accessToken, payload).toConversationResult()
            val live = currentSession()
            if (mine != generation || live?.appUserId != session.appUserId || live.refreshToken != session.refreshToken) {
                throw CancellationException("Obsolete facility response")
            }
            require(result.requestId == requestId)
            require(before == null || (result.sessionId == before.sessionId && result.revision == before.revision + 1))
            mutable.value = ConversationUiState(result = result, selected = result.selected)
            return result
        } catch (error: Exception) {
            if (mine == generation) mutable.value = mutable.value.copy(
                busy = false, error = if (error is CancellationException) null else error.facilityMessage(),
            )
            throw error
        }
    }
}
