package com.daengs.app.walk.diary

import com.daengs.app.auth.Session
import com.daengs.app.walk.support.behaviorComparisonFixture
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class WalkBehaviorComparisonApiTest {
    @Test fun `authenticated one-shot query returns the shared server fixture`() = runBlocking {
        val fixture = behaviorComparisonFixture()
        val stub = Stub(200, fixture.toString())
        try {
            val query = WalkBehaviorComparisonQuery.parse(fixture.getJSONObject("spec"))
            val view = WalkBehaviorComparisonApi { stub.base }.query("token", query).getOrThrow()
            assertEquals("POST", stub.method)
            assertEquals("Bearer token", stub.authorization)
            assertEquals(query.behavior.behaviorCode, stub.body.getString("behavior_code"))
            assertEquals(query.spatial.petId, stub.body.getJSONObject("walk_selector").getString("pet_id"))
            assertEquals(fixture.getJSONObject("receipt").getString("source_revision"), view.receipt.sourceRevision)
        } finally { stub.stop() }
    }

    @Test fun `undeployed or disabled endpoint stays an explicit preparation error`() = runBlocking {
        for (status in listOf(404, 503)) {
            val stub = Stub(status, "{}")
            try {
                val query = WalkBehaviorComparisonQuery.parse(behaviorComparisonFixture().getJSONObject("spec"))
                val failure = WalkBehaviorComparisonApi { stub.base }.query("token", query).exceptionOrNull() as SpatialDiaryHttpException
                assertEquals(status, failure.statusCode)
                assertTrue(failure.message!!.contains("준비 중"))
            } finally { stub.stop() }
        }
    }

    @Test fun `old owner response is discarded after the account changes`() = runBlocking {
        val view = WalkBehaviorComparison.parse(behaviorComparisonFixture())
        var owner = "owner"
        val failure = runCatching { loadBehaviorComparison(view.query, { owner },
            { Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE) }) { _, _ ->
            owner = "another-owner"; Result.success(view)
        } }.exceptionOrNull()
        assertNotNull(failure)
    }

    @Test fun `query cancellation cannot publish a late successful response`() = runBlocking {
        val view = WalkBehaviorComparison.parse(behaviorComparisonFixture())
        val entered = CompletableDeferred<Unit>()
        var delivered = false
        val job = async {
            loadBehaviorComparison(view.query, { "owner" },
                { Session("owner", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE) }) { _, _ ->
                entered.complete(Unit)
                try { awaitCancellation() } catch (_: kotlinx.coroutines.CancellationException) { Result.success(view) }
            }
            delivered = true
        }
        entered.await(); job.cancelAndJoin()
        assertFalse(delivered)
    }

    private class Stub(status: Int, response: String) {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var method: String? = null
        var authorization: String? = null
        lateinit var body: JSONObject
        init {
            server.createContext("/app/walks/spatial-diary/behavior-comparisons/query") { exchange ->
                method = exchange.requestMethod
                authorization = exchange.requestHeaders.getFirst("Authorization")
                body = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }
        fun stop() = server.stop(0)
    }
}
