package com.daengs.app.activity

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.math.BigInteger

internal object ActivityFixtures {
    const val OWNER = "11111111-1111-1111-1111-111111111111"
    const val PET = "22222222-2222-2222-2222-222222222222"
    const val SESSION = "33333333-3333-3333-3333-333333333333"
    const val WALK = "44444444-4444-4444-4444-444444444444"
    const val ANALYSIS = "55555555-5555-5555-5555-555555555555"
    const val SEASON = "2026 fall+서울"
    val window = ActivityWalkWindow(1000, 2000, PET)
    fun session(status: String = "WALK_ONLY") = """{
      "client_session_id":"$SESSION", "walk_id":"$WALK", "game_session_id":null,
      "status":"$status", "conflicts":[], "future_field":true
    }"""
    fun walk(status: String = "PENDING", pet: String = "\"$PET\"") = """{
      "identity":{"generation_id":"dev-activity.v1","statistics_version":"activity-statistics.v1"},
      "expected_versions":{"facts":1,"calculation":2,"receipt":3,"capsule":4},
      "owner_id":"$OWNER", "pet_id":$pet, "from_ms":1000, "to_ms":2000,
      "recorded_walk_count":1,"observed_walk_count":0,"moving_distance_m":null,"moving_s":null,
      "stop_count":null,"stop_s":null,"avg_speed_mps":null,
      "exclusions":[["no_observed_intervals",1]],
      "sources":[{"walk_id":"$WALK","analysis_id":"$ANALYSIS","revision":7}],
      "window_basis":"walk_end","pending_walk_count":1,"status":"$status"
    }"""
    fun territory(status: String = "STALE") = """{
      "season_id":"$SEASON","pet_id":"$PET","status":"$status",
      "source_revision":9,"processed_revision":8,
      "statistics":{"statistics_version":"activity-statistics.v1","generation_id":"dev-activity.v1",
        "coverage_start_ms":1000,"confirmed_through_ms":2000,"acquisition_count":2,"takeover_count":1,
        "held_site_ms":500,"verified_held_site_ms":200,"owned_site_count":1,"peak_owned_site_count":2},
      "score":{"bonus":100,"holding_units":922337203685477580812345,"held_site_ms":600,
        "current_count":1,"scoring_count":1,"peak":2,"claims":2,"takeovers":1,"last_ms":2500},
      "score_as_of_ms":2500,
      "sources":[{"period_id":"$ANALYSIS","site_id":"territory-site:hex-v1:140:1:2",
        "claim_id":null,"game_session_id":null}]
    }"""
}

class ActivityApiTest {
    @Test fun `GET sends bearer auth and validates requested session`() = withStub(ActivityFixtures.session()) { stub ->
        val result = runBlocking { stub.api.sessionLink("access-token", ActivityFixtures.SESSION) }
        assertEquals("GET", stub.method)
        assertEquals("Bearer access-token", stub.authorization)
        assertEquals("/app/activity/sessions/${ActivityFixtures.SESSION}", stub.uri)
        assertEquals(ActivityLinkStatus.WALK_ONLY, result.status)
        assertNull(result.gameSessionId)
    }

    @Test fun `walk query preserves null measurements pending count exclusions and source revision`() =
        withStub(ActivityFixtures.walk()) { stub ->
            val value = runBlocking { stub.api.walkSummary("token", ActivityFixtures.window) }
            assertEquals("/app/activity/walks/summary?from_ms=1000&to_ms=2000&pet_id=${ActivityFixtures.PET}", stub.uri)
            assertEquals(ActivityWalkStatus.PENDING, value.status)
            assertEquals(1L, value.pendingWalkCount)
            assertNull(value.movingDistanceM)
            assertNull(value.avgSpeedMps)
            assertEquals(ActivityExclusion("no_observed_intervals", 1), value.exclusions.single())
            assertEquals(7L, value.sources.single().revision)
            assertEquals(3L, value.expectedVersions.receipt)
        }

    @Test fun `owner query omits optional pet and preserves READY`() =
        withStub(ActivityFixtures.walk("READY", "null").replace("\"pending_walk_count\":1", "\"pending_walk_count\":0")) { stub ->
            val value = runBlocking { stub.api.walkSummary("token", ActivityWalkWindow(1000, 2000)) }
            assertEquals("/app/activity/walks/summary?from_ms=1000&to_ms=2000", stub.uri)
            assertEquals(ActivityWalkStatus.READY, value.status)
            assertNull(value.petId)
        }

    @Test fun `territory encodes season and keeps exact score separate from stale statistics`() =
        withStub(ActivityFixtures.territory()) { stub ->
            val value = runBlocking { stub.api.territorySummary("token", ActivityFixtures.SEASON, ActivityFixtures.PET) }
            assertTrue(stub.uri!!.contains("2026%20fall%2B%EC%84%9C%EC%9A%B8"))
            assertEquals(ActivityTerritoryStatus.STALE, value.status)
            assertEquals(9L, value.sourceRevision)
            assertEquals(8L, value.processedRevision)
            assertEquals(BigInteger("922337203685477580812345"), value.score!!.holdingUnits)
            assertEquals(2000L, value.statistics!!.confirmedThroughMs)
            assertEquals(2500L, value.scoreAsOfMs)
            assertNull(value.sources.single().claimId)
        }

    @Test fun `all territory states permit explicit absent statistics and score`() {
        ActivityTerritoryStatus.entries.forEach { status ->
            val result = ActivityJson.territory("""{"season_id":"fall","pet_id":"${ActivityFixtures.PET}",
              "status":"$status","source_revision":0,"processed_revision":0,
              "statistics":null,"score":null,"score_as_of_ms":null,"sources":[]}""")
            assertEquals(status, result.status)
            assertNull(result.score)
            assertNull(result.statistics)
        }
    }

    @Test fun `all link states preserve conflict codes`() {
        ActivityLinkStatus.entries.forEach { status ->
            val value = ActivityJson.session(ActivityFixtures.session(status.name)
                .replace("\"conflicts\":[]", "\"conflicts\":[\"pet_set_mismatch\"]"))
            assertEquals(status, value.status)
            assertEquals(listOf("pet_set_mismatch"), value.conflicts)
        }
    }

    @Test fun `HTTP failures retain status and code without leaking server body`() {
        listOf(401 to null, 404 to "activity_not_found", 422 to null, 503 to "activity_disabled").forEach { (status, code) ->
            withStub(if (code == null) "private server error" else """{"detail":{"code":"$code"}}""", status) { stub ->
                val error = runCatching { runBlocking { stub.api.sessionLink("secret", ActivityFixtures.SESSION) } }.exceptionOrNull()
                assertTrue(error is ActivityHttpException)
                error as ActivityHttpException
                assertEquals(status, error.statusCode)
                assertEquals(code, error.code)
                assertFalse(error.message!!.contains("private"))
                assertFalse(error.message!!.contains("secret"))
            }
        }
    }

    @Test fun `wrong session window pet or season is rejected`() {
        withStub(ActivityFixtures.session().replace(ActivityFixtures.SESSION, ActivityFixtures.ANALYSIS)) { stub ->
            assertFails { runBlocking { stub.api.sessionLink("token", ActivityFixtures.SESSION) } }
        }
        withStub(ActivityFixtures.walk().replace("\"to_ms\":2000", "\"to_ms\":2001")) { stub ->
            assertFails { runBlocking { stub.api.walkSummary("token", ActivityFixtures.window) } }
        }
        withStub(ActivityFixtures.walk(pet = "null")) { stub ->
            assertFails { runBlocking { stub.api.walkSummary("token", ActivityFixtures.window) } }
        }
        withStub(ActivityFixtures.territory().replace(ActivityFixtures.SEASON, "another")) { stub ->
            assertFails { runBlocking { stub.api.territorySummary("token", ActivityFixtures.SEASON, ActivityFixtures.PET) } }
        }
    }

    @Test fun `missing nullable field unknown status and fractional integer do not become valid results`() {
        assertFails { ActivityJson.walk(ActivityFixtures.walk().replace("\"moving_s\":null,", "")) }
        assertFails { ActivityJson.walk(ActivityFixtures.walk("NEW_STATUS")) }
        assertFails { ActivityJson.walk(ActivityFixtures.walk().replace("\"revision\":7", "\"revision\":7.5")) }
        assertFails { ActivityJson.walk(ActivityFixtures.walk().replace("\"walk_end\"", "\"walk_start\"")) }
    }

    @Test fun `invalid inputs fail before network and bounded windows match server`() {
        assertFails { ActivityWalkWindow(2000, 1000) }
        assertFails { ActivityWalkWindow(0, 367L * 86_400_000) }
        assertFails { ActivityWalkWindow(-1, 1000) }
        assertFails { ActivityWalkWindow(0, 1000, "not-a-uuid") }
        ActivityWalkWindow(0, 366L * 86_400_000)
        withStub(ActivityFixtures.session()) { stub ->
            assertFails { runBlocking { stub.api.sessionLink("", ActivityFixtures.SESSION) } }
            assertFails { runBlocking { stub.api.sessionLink("token", "../other") } }
            assertNull(stub.uri)
        }
    }

    private fun assertFails(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    private fun withStub(body: String, status: Int = 200, block: (Stub) -> Unit) {
        val stub = Stub(body, status)
        try { block(stub) } finally { stub.server.stop(0) }
    }
    private class Stub(body: String, status: Int) {
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val api = ActivityApi { "http://127.0.0.1:${server.address.port}/" }
        var uri: String? = null
        var method: String? = null
        var authorization: String? = null
        init {
            server.createContext("/") { exchange ->
                uri = exchange.requestURI.toASCIIString()
                method = exchange.requestMethod
                authorization = exchange.requestHeaders.getFirst("Authorization")
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }
    }
}
