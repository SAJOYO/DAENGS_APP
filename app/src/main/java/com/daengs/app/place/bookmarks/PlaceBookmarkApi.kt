package com.daengs.app.place.bookmarks

import com.daengs.app.BuildConfig
import com.daengs.app.place.*
import com.daengs.app.ui.places.PlaceBrowseFilters
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

data class SavedPlace(val key: PlaceKey, val name: String, val createdAt: Instant)
data class SavedPlacePage(val items: List<SavedPlace>, val limit: Int)
data class SavedPlaceResults(val page: SavedPlacePage, val hits: List<PlaceSearchHit>,
    val missing: Set<PlaceKey>, val distanceAvailable: Boolean)

data class SavedSearchPlan(val action: String, val message: String, val filters: JsonObject?,
    val searchFilters: JsonObject? = null)

internal fun PlaceKey.savedJson() = buildJsonObject { put("source", source); put("ref", ref) }
internal fun JsonObject.savedKey() = PlaceKey(getValue("source").jsonPrimitive.content, getValue("ref").jsonPrimitive.content)
internal fun PlaceBrowseFilters.savedQuery(dogs: List<PlaceDogSnapshot>) = buildJsonObject {
    put("lat", origin?.latitude?.let(::JsonPrimitive) ?: JsonNull)
    put("lng", origin?.longitude?.let(::JsonPrimitive) ?: JsonNull)
    put("radius_m", radiusMeters?.takeIf { origin != null }?.let(::JsonPrimitive) ?: JsonNull)
    put("kinds", JsonArray(kinds.map { JsonPrimitive(it.wire) }))
    put("name_query", name.trim()); put("parking", parkingFirst)
    put("hard", requiredConditions ?: buildJsonObject { put("all", JsonArray(emptyList())); put("any", JsonArray(emptyList())) })
    put("dogs", JsonArray(dogs.map { it.toJson() }))
}

internal fun parseSavedPage(body: JsonObject): SavedPlacePage {
    require(body.getValue("contract_version").jsonPrimitive.content == "place-bookmarks-v1")
    val limit = body.getValue("limit").jsonPrimitive.int.also { require(it in 1..200) }
    val items = body.getValue("items").jsonArray.map { value -> value.jsonObject.let {
        SavedPlace(it.getValue("key").jsonObject.savedKey(), it.getValue("name").jsonPrimitive.content,
            Instant.parse(it.getValue("created_at").jsonPrimitive.content))
    } }
    require(items.size == body.getValue("total_count").jsonPrimitive.int && items.size <= limit)
    require(items.map { it.key }.toSet().size == items.size)
    return SavedPlacePage(items, limit)
}

interface PlaceBookmarkClient {
    suspend fun list(token: String): SavedPlacePage
    suspend fun set(token: String, key: PlaceKey, saved: Boolean): SavedPlacePage
    suspend fun search(token: String, filters: JsonObject): SavedPlaceResults
    suspend fun interpret(token: String, query: String, filters: JsonObject): SavedSearchPlan =
        throw PlaceBookmarkException(503, "saved_conversation_unavailable")
}
class PlaceBookmarkException(val status: Int, val code: String?) : IllegalStateException("Place bookmarks failed ($status)")

class PlaceBookmarkApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) : PlaceBookmarkClient {
    override suspend fun interpret(token: String, query: String, filters: JsonObject): SavedSearchPlan {
        val body = request(token, "POST", "/interpret", buildJsonObject {
            put("query", query); put("filters", filters); put("search_policy", "v1")
        })
        val action = body.getValue("action").jsonPrimitive.content
        require(action in setOf("search", "clarify", "explain", "return_search", "search_places"))
        val candidate = body["filters"]?.takeUnless { it == JsonNull }?.jsonObject
        require((action == "search") == (candidate != null))
        val search = body["search_filters"]?.takeUnless { it == JsonNull }?.jsonObject
        require((action == "search_places") == (search != null))
        return SavedSearchPlan(action, body.getValue("message").jsonPrimitive.content, candidate, search)
    }
    override suspend fun list(token: String) = parseSavedPage(request(token, "GET"))
    override suspend fun set(token: String, key: PlaceKey, saved: Boolean): SavedPlacePage {
        val encode: (String) -> String = { URLEncoder.encode(it, Charsets.UTF_8.name()) }
        val page = parseSavedPage(if (saved) request(token, "PUT", body = key.savedJson())
            else request(token, "DELETE", "?source=${encode(key.source)}&ref=${encode(key.ref)}"))
        require(page.items.any { it.key == key } == saved)
        return page
    }
    override suspend fun search(token: String, filters: JsonObject): SavedPlaceResults {
        val body = request(token, "POST", "/search", buildJsonObject { put("filters", filters) })
        val echoed = body.getValue("filters").jsonObject
        // Dog snapshots omit unknown optional values; the server expands them to null.
        fun normalized(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.filterValues { it != JsonNull }.mapValues { normalized(it.value) })
            is JsonArray -> JsonArray(value.map(::normalized))
            else -> value
        }
        require(normalized(filters) == normalized(echoed)) { "Bookmark filters were not applied" }
        val page = parseSavedPage(body)
        val keys = page.items.map { it.key }.toSet()
        val hits = body.getValue("hits").jsonArray.map { it.jsonObject.toPlaceSearchHit() }
        val missing = body.getValue("missing_keys").jsonArray.map { it.jsonObject.savedKey() }
        require(hits.map { it.place.key }.toSet().size == hits.size && missing.toSet().size == missing.size)
        require(hits.all { it.place.key in keys } && missing.all { it in keys })
        require(hits.none { it.place.key in missing })
        val distance = body.getValue("distance_available").jsonPrimitive.boolean
        require(distance == (filters["lat"] != JsonNull))
        return SavedPlaceResults(page, hits, missing.toSet(), distance)
    }

    private suspend fun request(token: String, method: String, suffix: String = "", body: JsonObject? = null): JsonObject = withContext(Dispatchers.IO) {
        ensureActive()
        val connection = URL("${baseUrl().trimEnd('/')}/app/places/bookmarks$suffix").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000; connection.readTimeout = if (suffix == "/interpret") 45_000 else 20_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val status = connection.responseCode
            val text = (if (status == 200) connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(8192); val result = StringBuilder()
                while (true) {
                    ensureActive(); val n = reader.read(buffer); if (n < 0) break
                    require(result.length + n <= 4_000_000)
                    result.append(buffer, 0, n)
                }; result.toString()
            }.orEmpty()
            ensureActive()
            if (status != 200) throw PlaceBookmarkException(status, runCatching {
                Json.parseToJsonElement(text).jsonObject["detail"]?.jsonObject?.get("code")?.jsonPrimitive?.content
            }.getOrNull())
            Json.parseToJsonElement(text).jsonObject
        } finally { connection.disconnect() }
    }
}
