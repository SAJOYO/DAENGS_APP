package com.daengs.app.territory

import java.net.ServerSocket
import java.net.URLDecoder
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TerritoryOccupancyApiTest {
    private val id = "territory-site:hex-v1:140:1:-2"
    private val body = """[{"site_id":"$id","version":2,"occupancy":{"owner_pet_id":"00000000-0000-0000-0000-000000000001","owner_pet_name":"두부","is_mine":false,"certification":"VERIFIED","occupied_at":"2026-09-05T12:00:00Z"}}]"""

    @Test fun `real HTTP sends only GET bearer and encoded site IDs then parses response`() = runBlocking {
        val headers = mutableListOf<String>()
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val worker = thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        headers += line
                    }
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(bytes); output.flush()
                }
            }
            val result = TerritoryOccupancyApi { "http://127.0.0.1:${server.localPort}/" }.fetch("test-token", listOf(id))
            worker.join(5000)
            assertEquals("GET /app/territory/occupancies?site_ids=$id HTTP/1.1", URLDecoder.decode(headers.first(), "UTF-8"))
            assertTrue(headers.any { it.equals("Authorization: Bearer test-token", true) })
            assertEquals("두부", result.single().occupancy!!.ownerPetName)
            assertFalse(result.single().occupancy!!.isMine)
        }
    }

    @Test fun `missing duplicate or foreign results and malformed certification fail closed`() {
        val bad = listOf("[]", body.replace(id, "$id-extra"),
            "[${body.removePrefix("[").removeSuffix("]")},${body.removePrefix("[").removeSuffix("]")}]",
            body.replace("VERIFIED", "MAGIC"), body.replace("\"version\":2", "\"version\":-1"),
            body.replace("\"is_mine\":false", "\"is_mine\":\"false\""))
        bad.forEach { value -> assertTrue(runCatching { parseSharedTerritories(value, listOf(id)) }.isFailure) }
        val neutral = parseSharedTerritories("""[{"site_id":"$id","version":0,"occupancy":null}]""", listOf(id))
        assertNull(neutral.single().occupancy)
    }

    @Test fun `HTTP failure contains status without server body or credentials`() = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val worker = thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 6\r\nConnection: close\r\n\r\nsecret".toByteArray())
                }
            }
            val error = runCatching { TerritoryOccupancyApi { "http://127.0.0.1:${server.localPort}" }.fetch("private-token", listOf(id)) }.exceptionOrNull()
            worker.join(5000)
            assertEquals(401, (error as TerritoryOccupancyApiException).status)
            assertFalse(error.message!!.contains("secret")); assertFalse(error.message!!.contains("private-token"))
        }
    }
}
