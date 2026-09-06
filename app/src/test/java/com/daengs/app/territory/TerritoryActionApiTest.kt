package com.daengs.app.territory

import com.daengs.app.location.*
import java.net.ServerSocket
import java.math.BigDecimal
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TerritoryActionApiTest {
    @Test fun `HTTP preserves stored request bytes authorization and seven decimal coordinates`() = runBlocking {
        val request = markBody(WALK, SITE, DOG,
            LocationSample(GeoPoint(37.5123456789, 127.123456789), 2000, 2_000_000_000, 2.5f))
        val parsed = JSONObject(request)
        assertEquals(7, BigDecimal(parsed.get("lat").toString()).scale())
        assertEquals(7, BigDecimal(parsed.get("lng").toString()).scale())
        assertFalse(parsed.has("owner_id"))
        val captured = mutableListOf<Pair<List<String>, String>>()
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val worker = thread {
                repeat(3) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                        val headers = mutableListOf<String>()
                        while (true) { val line = reader.readLine(); if (line.isNullOrEmpty()) break; headers += line }
                        val size = headers.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                        val chars = CharArray(size)
                        var read = 0
                        while (read < size) read += reader.read(chars, read, size - read)
                        captured += headers to String(chars)
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray())
                    }
                }
            }
            val api = TerritoryActionApi { "http://127.0.0.1:${server.localPort}/" }
            api.request("test-token", "PUT", "/claim-sessions/$WALK", sessionStartBody(1000, listOf(DOG)))
            repeat(2) { api.request("test-token", "POST", "/claims", request) }
            worker.join(5000)
        }
        assertEquals("PUT /app/territory/claim-sessions/$WALK HTTP/1.1", captured[0].first.first())
        captured.drop(1).forEach { (headers, body) ->
            assertEquals("POST /app/territory/claims HTTP/1.1", headers.first())
            assertTrue(headers.any { it.equals("Authorization: Bearer test-token", true) })
            assertEquals(request, body)
        }
    }

    @Test fun `HTTP conflict exposes typed code but never body or credentials`() = runBlocking {
        ServerSocket(0).use { server ->
            val worker = thread {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val bytes = """{"detail":{"code":"attempt_identity_conflict","message":"private-body"}}""".toByteArray()
                    socket.getOutputStream().write("HTTP/1.1 409 Conflict\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                }
            }
            val error = runCatching { TerritoryActionApi { "http://127.0.0.1:${server.localPort}" }
                .request("private-token", "GET", "/claim-sessions/$WALK", null) }.exceptionOrNull() as TerritoryActionException
            worker.join(5000)
            assertEquals(409, error.status); assertEquals("attempt_identity_conflict", error.code)
            assertFalse(error.toString().contains("private"))
        }
    }
}
