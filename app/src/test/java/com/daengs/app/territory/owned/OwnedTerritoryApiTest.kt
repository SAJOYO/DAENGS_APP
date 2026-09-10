package com.daengs.app.territory.owned

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

internal const val OWNED_PET = "00000000-0000-0000-0000-000000000001"
internal const val OWNED_SITE = "territory-site:hex-v1:140:324:777"
internal fun ownedJson(petId: String? = null) = """{
  "status":"READY","season_id":"first","server_now_ms":1800000000000,"pet_id":${petId?.let { "\"$it\"" } ?: "null"},
  "total_count":2,"next_cursor":"next+/=","items":[{
  "site_id":"$OWNED_SITE","version":2,"pet_id":"$OWNED_PET","pet_name":"두부","pet_breed":"dog_bichon_frise",
  "certification":"VERIFIED","occupied_at":"2027-01-15T08:00:00Z","expires_at":"2027-01-18T08:00:00Z",
  "location":{"lat":37.5,"lng":127.0},"location_status":"AVAILABLE"}]}"""

class OwnedTerritoryApiTest {
    @Test fun `GET encodes cursor and sends only member filter and bearer`() {
        withServer(ownedJson(OWNED_PET)) { api, request ->
            val page = runBlocking { api.fetch("access", OWNED_PET, "next+/=") }
            assertEquals(listOf("GET", "Bearer access", "/app/territory/my-sites?limit=50&pet_id=$OWNED_PET&cursor=next%2B%2F%3D"), request)
            assertEquals(2, page.totalCount)
            assertEquals("두부", page.items.single().petName)
            assertEquals(37.5, page.items.single().point!!.latitude, 0.0)
            assertEquals("next+/=", page.nextCursor)
        }
    }

    @Test fun `all pets omits filter and missing location still counts as owned`() {
        val body = JSONObject(ownedJson()).apply {
            getJSONArray("items").getJSONObject(0).put("location", JSONObject.NULL).put("location_status", "NOT_FOUND")
        }.toString()
        withServer(body) { api, request ->
            val page = runBlocking { api.fetch("access", null, null) }
            assertEquals("/app/territory/my-sites?limit=50", request.last())
            assertNull(page.items.single().point)
            assertEquals(2, page.totalCount)
        }
    }

    @Test fun `no season and ready empty are distinct`() {
        val body = JSONObject(ownedJson()).put("items", org.json.JSONArray()).put("total_count", 0).put("next_cursor", JSONObject.NULL)
        assertEquals("first", parseOwnedTerritories(body.toString(), null).seasonId)
        body.put("status", "NO_ACTIVE_SEASON").put("season_id", JSONObject.NULL)
        assertNull(parseOwnedTerritories(body.toString(), null).seasonId)
    }

    @Test fun `wrong filter malformed shape duplicate IDs and unknown state fail`() {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.remove("next_cursor") }, { it.put("status", "UNKNOWN") }, { it.put("total_count", 1.5) },
            { it.put("pet_id", OWNED_PET) }, { it.getJSONArray("items").put(it.getJSONArray("items").getJSONObject(0)) },
            { it.getJSONArray("items").getJSONObject(0).getJSONObject("location").put("lat", 91) },
            { it.getJSONArray("items").getJSONObject(0).put("location_status", "NOT_FOUND") },
        )
        changes.forEach { change ->
            val root = JSONObject(ownedJson()); change(root)
            assertTrue(runCatching { parseOwnedTerritories(root.toString(), null) }.isFailure)
        }
    }

    @Test fun `HTTP codes are retained without private server text`() {
        listOf(401 to "expired", 409 to "season_changed", 503 to "activity_disabled", 503 to "territory_sites_unavailable").forEach { (status, code) ->
            withServer("""{"detail":{"code":"$code","private":"secret"}}""", status) { api, _ ->
                val error = runCatching { runBlocking { api.fetch("token", null, null) } }.exceptionOrNull() as OwnedTerritoryHttpException
                assertEquals(status, error.status); assertEquals(code, error.code)
                assertFalse(error.message!!.contains("secret"))
            }
        }
    }

    @Test fun `oversized responses fail`() = withServer(" ".repeat(256001)) { api, _ ->
        assertTrue(runCatching { runBlocking { api.fetch("token", null, null) } }.isFailure)
    }

    private fun withServer(body: String, status: Int = 200, block: (OwnedTerritoryApi, List<String>) -> Unit) {
        val request = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            request += exchange.requestMethod
            request += exchange.requestHeaders.getFirst("Authorization")
            request += exchange.requestURI.toString()
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block(OwnedTerritoryApi { "http://127.0.0.1:${server.address.port}" }, request) }
        finally { server.stop(0) }
    }
}
