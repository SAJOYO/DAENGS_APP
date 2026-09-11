package com.daengs.app.place.bookmarks

import com.daengs.app.place.PlaceKey
import com.daengs.app.ui.places.PlaceBrowseFilters
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder

class PlaceBookmarkApiTest {
    private val key = PlaceKey("kcisa", "a/b?한글 &x=1")
    private fun page(saved: Boolean = true) = buildJsonObject {
        put("contract_version", "place-bookmarks-v1"); put("limit", 200); put("total_count", if (saved) 1 else 0)
        put("items", JsonArray(if (saved) listOf(buildJsonObject {
            put("key", key.savedJson()); put("name", "저장 시설"); put("created_at", "2026-09-11T00:00:00Z")
        }) else emptyList()))
    }
    @Test fun `private requests encode full refs and authenticate every verb`() {
        for (method in listOf("GET", "PUT", "DELETE")) withServer(page(method != "DELETE").toString()) { api, captured ->
            runBlocking { if (method == "GET") api.list("access") else api.set("access", key, method == "PUT") }
            assertEquals(method, captured[0]); assertEquals("Bearer access", captured[1])
            if (method == "DELETE") assertEquals("/app/places/bookmarks?source=kcisa&ref=${key.ref}", URLDecoder.decode(captured[2], "UTF-8"))
            if (method == "PUT") assertEquals(key.savedJson(), Json.parseToJsonElement(captured[3]))
        }
    }
    @Test fun `whole list rejects truncated duplicate or changed contract`() {
        for (change in listOf<(MutableMap<String, JsonElement>) -> Unit>(
            { it["total_count"] = JsonPrimitive(2) }, { it["contract_version"] = JsonPrimitive("future") },
            { it["items"] = JsonArray(List(2) { page().getValue("items").jsonArray.single() }); it["total_count"] = JsonPrimitive(2) },
        )) {
            val body = page().toMutableMap().apply(change)
            assertTrue(runCatching { parseSavedPage(JsonObject(body)) }.isFailure)
        }
    }
    @Test fun `missing facilities stay saved and filter echo cannot be ignored`() {
        val filters = PlaceBrowseFilters().allBookmarks().savedQuery(emptyList())
        val body = JsonObject(page() + mapOf("filters" to filters, "hits" to JsonArray(emptyList()),
            "missing_keys" to JsonArray(listOf(key.savedJson())), "distance_available" to JsonPrimitive(false)))
        withServer(body.toString()) { api, captured ->
            val result = runBlocking { api.search("access", filters) }
            assertEquals(setOf(key), result.missing); assertEquals(1, result.page.items.size)
            assertEquals("POST", captured[0]); assertFalse(result.distanceAvailable)
        }
        withServer(JsonObject(body + ("filters" to JsonObject(filters + ("name_query" to JsonPrimitive("ignored"))))).toString()) { api, _ ->
            assertTrue(runCatching { runBlocking { api.search("access", filters) } }.isFailure)
        }
    }
    @Test fun `errors retain public code without private server message`() {
        withServer("""{"detail":{"code":"place_bookmark_limit","private":"secret"}}""", 409) { api, _ ->
            val error = runCatching { runBlocking { api.list("access") } }.exceptionOrNull() as PlaceBookmarkException
            assertEquals(409, error.status); assertEquals("place_bookmark_limit", error.code)
            assertFalse(error.message!!.contains("secret"))
        }
    }
    @Test fun `interpret authenticates and cannot smuggle a write action`() {
        val filters = PlaceBrowseFilters().allBookmarks().savedQuery(emptyList())
        for (action in listOf("search", "save", "remove")) {
            val body = buildJsonObject { put("action", action); put("message", ""); put("filters", filters) }
            withServer(body.toString()) { api, captured ->
                val result = runCatching { runBlocking { api.interpret("access", "카페만", filters) } }
                assertEquals(action == "search", result.isSuccess)
                assertEquals("POST", captured[0]); assertEquals("Bearer access", captured[1])
                assertEquals("/app/places/bookmarks/interpret", captured[2])
                assertEquals(filters, Json.parseToJsonElement(captured[3]).jsonObject["filters"])
            }
        }
    }
    private fun withServer(body: String, status: Int = 200, block: (PlaceBookmarkApi, List<String>) -> Unit) {
        val captured = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            captured += listOf(exchange.requestMethod, exchange.requestHeaders.getFirst("Authorization"), exchange.requestURI.toString(), exchange.requestBody.bufferedReader().readText())
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block(PlaceBookmarkApi { "http://127.0.0.1:${server.address.port}" }, captured) } finally { server.stop(0) }
    }
}
