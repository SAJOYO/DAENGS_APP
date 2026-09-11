package com.daengs.app.walk.records

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class WalkRecordSheetsApiTest {
    @Test fun `authenticated batch sends only client ids and distinguishes pending from unavailable`() = runBlocking {
        val body = """{"schema_version":1,"items":[
            {"client_session_id":"$LOCAL","walk_id":"$REMOTE","status":"pending","analysis_id":null,"sheet_fingerprint":null,"sheet":null},
            {"client_session_id":"$MISSING","walk_id":null,"status":"unavailable","analysis_id":null,"sheet_fingerprint":null,"sheet":null}
        ]}"""
        val stub = Stub(200, body)
        try {
            val result = WalkRecordSheetsApi { stub.base }.query("sample-token", linkedMapOf(LOCAL to REMOTE, MISSING to REMOTE_MISSING)).getOrThrow()
            assertEquals("POST", stub.method)
            assertEquals("Bearer sample-token", stub.authorization)
            assertEquals(setOf("client_session_ids"), stub.body.keys().asSequence().toSet())
            val sent = stub.body.getJSONArray("client_session_ids")
            assertEquals(listOf(LOCAL, MISSING), (0 until sent.length()).map { sent.getString(it) })
            assertEquals(WalkTraceState.ANALYSIS_PENDING, result.getValue(LOCAL).state)
            assertEquals(WalkTraceState.NOT_UPLOADED, result.getValue(MISSING).state)
        } finally { stub.stop() }
    }

    @Test fun `absent unauthorized and malformed endpoints cannot become empty successful sheets`() = runBlocking {
        for ((status, body) in listOf(404 to "{}", 401 to "{}", 200 to "{}")) {
            val stub = Stub(status, body)
            try {
                assertTrue(WalkRecordSheetsApi { stub.base }.query("sample-token", mapOf(LOCAL to REMOTE)).isFailure)
            } finally { stub.stop() }
        }
    }

    private class Stub(status: Int, response: String) {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base get() = "http://127.0.0.1:${server.address.port}"
        var method: String? = null
        var authorization: String? = null
        lateinit var body: JSONObject
        init {
            server.createContext("/app/walks/spatial-diary/sheets/query") { exchange ->
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

    private companion object {
        const val LOCAL = "00000000-0000-0000-0000-000000000001"
        const val REMOTE = "00000000-0000-0000-0000-000000000002"
        const val MISSING = "00000000-0000-0000-0000-000000000003"
        const val REMOTE_MISSING = "00000000-0000-0000-0000-000000000004"
    }
}
