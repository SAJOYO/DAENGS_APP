package com.daengs.app.territory.bookmarks

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

internal const val BOOKMARK_SITE = "territory-site:hex-v1:140:324:777"
internal fun bookmarkJson() = """{"total_count":1,"limit":20,"items":[{"site_id":"$BOOKMARK_SITE",
    "created_at":"2026-09-10T03:00:00Z","location":{"lat":37.5,"lng":127.0},"location_status":"AVAILABLE"}]}"""
internal fun mutationJson(saved: Boolean) = """{"site_id":"$BOOKMARK_SITE","is_bookmarked":$saved,
    "created_at":${if (saved) "\"2026-09-10T03:00:00Z\"" else "null"},"total_count":${if(saved) 1 else 0},"limit":20}"""

class TerritoryBookmarkApiTest {
    @Test fun `GET PUT DELETE use member bearer and no request body`() {
        val methods = listOf("GET", "PUT", "DELETE")
        methods.forEach { method ->
            withServer(if (method == "GET") bookmarkJson() else mutationJson(method == "PUT")) { api, request ->
                runBlocking { if (method == "GET") api.list("access") else api.set("access", BOOKMARK_SITE, method == "PUT") }
                assertEquals(listOf(method, "Bearer access", "/app/territory/bookmarks" +
                    if (method == "GET") "" else "/$BOOKMARK_SITE", ""), request)
            }
        }
    }
    @Test fun `missing and temporarily unavailable locations remain saved`() {
        listOf("NOT_FOUND", "UNAVAILABLE").forEach { status ->
            val json = JSONObject(bookmarkJson())
            json.getJSONArray("items").getJSONObject(0).put("location", JSONObject.NULL).put("location_status", status)
            val page = parseBookmarkList(json.toString())
            assertEquals(1, page.totalCount); assertNull(page.items.single().point)
            assertEquals(status, page.items.single().locationStatus.name)
        }
    }
    @Test fun `malformed list never becomes empty or loses a bookmark`() {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.remove("items") }, { it.put("total_count", 0) }, { it.put("total_count", 1.5) },
            { it.put("limit", 0) }, { it.getJSONArray("items").put(it.getJSONArray("items").getJSONObject(0)); it.put("total_count", 2) },
            { it.getJSONArray("items").getJSONObject(0).put("location_status", "NEW") },
            { it.getJSONArray("items").getJSONObject(0).put("location_status", "NOT_FOUND") },
            { it.getJSONArray("items").getJSONObject(0).getJSONObject("location").put("lat", 91) },
            { it.getJSONArray("items").getJSONObject(0).put("site_id", "../other") },
        )
        changes.forEach { change ->
            val json = JSONObject(bookmarkJson()); change(json)
            assertTrue(runCatching { parseBookmarkList(json.toString()) }.isFailure)
        }
    }
    @Test fun `mutations must match requested site and saved state`() {
        assertTrue(runCatching { parseBookmarkMutation(mutationJson(true), BOOKMARK_SITE, false) }.isFailure)
        assertTrue(runCatching { parseBookmarkMutation(mutationJson(true), BOOKMARK_SITE + "1", true) }.isFailure)
        assertTrue(runCatching { parseBookmarkMutation(mutationJson(true).replace("\"total_count\":1", "\"total_count\":0"), BOOKMARK_SITE, true) }.isFailure)
    }
    @Test fun `errors preserve status code and server limit without private text`() {
        listOf(401 to null, 404 to "territory_site_not_found", 409 to "bookmark_limit_reached", 503 to "territory_sites_unavailable").forEach { (status, code) ->
            withServer("""{"detail":{"code":"$code","limit":20,"private":"secret"}}""", status) { api, _ ->
                val error = runCatching { runBlocking { api.list("access") } }.exceptionOrNull() as BookmarkHttpException
                assertEquals(status, error.status); assertEquals(20, error.limit); assertFalse(error.message!!.contains("secret"))
            }
        }
        withServer("""{"detail":"Not Found"}""", 404) { api, _ ->
            val error = runCatching { runBlocking { api.list("access") } }.exceptionOrNull() as BookmarkHttpException
            assertNull(error.code)
        }
    }
    @Test fun `oversized responses and invalid path inputs fail`() {
        withServer(" ".repeat(256001)) { api, request ->
            assertTrue(runCatching { runBlocking { api.set("access", "../bad", true) } }.isFailure)
            assertTrue(request.isEmpty())
            assertTrue(runCatching { runBlocking { api.list("access") } }.isFailure)
        }
    }
    private fun withServer(body: String, status: Int = 200, block: (TerritoryBookmarkApi, List<String>) -> Unit) {
        val request = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            request += listOf(exchange.requestMethod, exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestURI.toString(), exchange.requestBody.bufferedReader().readText())
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block(TerritoryBookmarkApi { "http://127.0.0.1:${server.address.port}" }, request) }
        finally { server.stop(0) }
    }
}
