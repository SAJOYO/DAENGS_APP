package com.daengs.app.place

import com.daengs.app.auth.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

fun JsonObject.filterCriteria(): PlaceFilterCriteria {
    fun atom(value: JsonElement): PlaceFilterAtom = value.jsonObject.let {
        PlaceFilterAtom(it.getValue("id").jsonPrimitive.content, it.getValue("capability").jsonPrimitive.content,
            it.getValue("op").jsonPrimitive.content, it.getValue("value"))
    }
    val hard = getValue("hard").jsonObject
    return PlaceFilterCriteria(
        getValue("candidate_kinds").jsonArray.map { PlaceKind.fromWire(it.jsonPrimitive.content) },
        hard.getValue("all").jsonArray.map(::atom),
        hard.getValue("any").jsonArray.map { branch -> branch.jsonObject.let {
            PlaceFilterBranch(it.getValue("id").jsonPrimitive.content, it.getValue("all").jsonArray.map(::atom))
        } },
        getValue("preferences").jsonArray.map { PlaceFilterPreference(atom(it), it.jsonObject.getValue("scope_kinds").jsonArray.map { k -> PlaceKind.fromWire(k.jsonPrimitive.content) }) },
        getValue("unknown_policy") == JsonPrimitive("separate"),
    ).also { require(it.validationMessage() == null) }
}

data class PlaceFilterEditQuery(val text: String, val base: PlaceFilterRequest, val id: String = UUID.randomUUID().toString()) {
    fun toJson() = buildJsonObject { put("query", text); put("base_revision", base.revision); put("base_state", base.state); put("client_request_id", id) }
}

data class PlaceFilterEditAction(val previous: PlaceFilterEditResponse, val confirm: Boolean, val id: String = UUID.randomUUID().toString()) {
    fun toJson() = buildJsonObject {
        put("client_request_id", id); put("search_id", previous.document.getValue("search_id")); put("expected_revision", previous.document.getValue("revision"))
        put("current_state", previous.query.base.state); put("confirm_changes", confirm)
    }
}

data class PlaceFilterEditResponse(val document: JsonObject, val query: PlaceFilterEditQuery, val action: PlaceFilterEditAction? = null) {
    val compiled = document.getValue("compiled").jsonObject
    val proposed = compiled["proposed_state"]?.takeUnless { it == JsonNull }?.jsonObject
    val needsConfirmation = compiled.getValue("requires_confirmation").jsonPrimitive.boolean
    val issues = compiled.getValue("issues").jsonArray.map { it.jsonObject.getValue("message").jsonPrimitive.content }
    val proposal = compiled.getValue("proposal").jsonObject
    val result: PlaceFilterResponse?
    init {
        require(document["contract_version"] == JsonPrimitive("place-filter-edit-v1"))
        UUID.fromString(document.getValue("search_id").jsonPrimitive.content)
        require(sameFilterJson(document.getValue("request"), query.toJson()))
        require(sameFilterJson(compiled.getValue("base_state"), query.base.state))
        val status = compiled.getValue("status").jsonPrimitive.content
        require(status in listOf("ready", "needs_resolution"))
        require(status != "ready" || proposed != null && !needsConfirmation)
        require(!needsConfirmation || proposed != null)
        require(status == "ready" || proposed == null || needsConfirmation)
        // Evidence uses Unicode code points; validate before rendering, never use UTF-16 offsets.
        (proposal.getValue("edits").jsonArray + proposal.getValue("unresolved").jsonArray).forEach {
            val evidence = it.jsonObject.getValue("evidence").jsonObject
            val start = evidence.getValue("start").jsonPrimitive.int
            val end = evidence.getValue("end").jsonPrimitive.int
            require(start >= 0 && end > start && end <= query.text.codePointCount(0, query.text.length))
            require(query.text.substring(query.text.offsetByCodePoints(0, start), query.text.offsetByCodePoints(0, end)) == evidence.getValue("quote").jsonPrimitive.content)
        }
        proposed?.let { next ->
            listOf("contract_version", "spatial", "dogs", "unknown_policy", "result_policy").forEach { key ->
                require(sameFilterJson(next.getValue(key), query.base.state.getValue(key)))
            }
            next.filterCriteria()
        }
        if (action == null) {
            require(document["revision"]?.jsonPrimitive?.long == 1L)
            require(document["result"] == JsonNull && document["action_request"] == JsonNull)
            result = null
        } else {
            require(document["search_id"] == action.previous.document["search_id"])
            require(document.getValue("revision").jsonPrimitive.long == action.previous.document.getValue("revision").jsonPrimitive.long + 1)
            require(sameFilterJson(compiled, action.previous.compiled))
            require(sameFilterJson(document.getValue("action_request"), action.toJson()))
            require(proposed != null)
            val criteria = proposed.filterCriteria()
            val request = query.base.request.copy(kinds = criteria.kinds, preferParking = criteria.preferences.isNotEmpty(), nameQuery = proposed.getValue("name_query").jsonPrimitive.content)
            result = PlaceFilterResponse(document.getValue("result").jsonObject, PlaceFilterRequest(request, criteria, query.base.revision + 1, action.id))
        }
    }
}

interface PlaceFilterEditRepository {
    suspend fun propose(owner: String, query: PlaceFilterEditQuery): PlaceFilterEditResponse
    suspend fun apply(owner: String, action: PlaceFilterEditAction): PlaceFilterEditResponse
}

class AuthenticatedPlaceFilterEditRepository(
    private val baseUrl: () -> String,
    private val freshSession: suspend () -> Session?,
    private val currentSession: () -> Session?,
) : PlaceFilterEditRepository {
    override suspend fun propose(owner: String, query: PlaceFilterEditQuery) = PlaceFilterEditResponse(exchange(owner, "", query.toJson()), query)
    override suspend fun apply(owner: String, action: PlaceFilterEditAction) = PlaceFilterEditResponse(exchange(owner, "/actions", action.toJson()), action.previous.query, action)

    private suspend fun exchange(owner: String, suffix: String, payload: JsonObject): JsonObject {
        val session = freshSession() ?: throw FacilityException(401)
        fun requireOwner() { if (session.appUserId != owner || currentSession()?.appUserId != owner || currentSession()?.refreshToken != session.refreshToken) throw FacilityException(401) }
        requireOwner()
        val response = withContext(Dispatchers.IO) {
            if (baseUrl().isBlank()) throw FacilityException(0)
            val connection = (URL(baseUrl().trimEnd('/') + "/app/places/filter-edits" + suffix).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 45_000; doOutput = true; instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
                if (connection.responseCode !in 200..299) throw FacilityException(connection.responseCode)
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 512 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            } finally { connection.disconnect() }
        }
        requireOwner()
        return response
    }
}
