package com.daengs.app.place

import com.daengs.app.location.GeoPoint
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class PlaceApiAddressTest {
    private class Stub(private val responseBody: String = """{"groups":[]}""") {
        val hits = mutableListOf<String>()
        val bodies = mutableListOf<JsonObject>()
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base: String get() = "http://127.0.0.1:${server.address.port}"

        init {
            server.createContext("/v2/places/search") { exchange ->
                hits += exchange.requestURI.path
                bodies += Json.parseToJsonElement(
                    exchange.requestBody.bufferedReader().use { it.readText() },
                ).jsonObject
                val response = responseBody.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }

    @Test
    fun `name filter travels on the wire and requires matching server acknowledgement`() {
        val query = PlaceSearchRequest(GeoPoint(37.556, 126.923),
            kinds = listOf(PlaceKind.CAFE), nameQuery = "  홍대  ")
        val compatible = Stub("""{"groups":[],"name_query":"홍대"}""")
        val oldServer = Stub()
        val wrongQuery = Stub("""{"groups":[],"name_query":"다른 이름"}""")
        try {
            assertEquals(emptyList<PlaceSearchGroup>(),
                runBlocking { PlaceApi({ compatible.base }).search(query) }.groups)
            assertEquals("홍대", compatible.bodies.single()["name_query"]?.jsonPrimitive?.content)
            for (server in listOf(oldServer, wrongQuery)) {
                assertThrows(SerializationException::class.java) {
                    runBlocking { PlaceApi({ server.base }).search(query) }
                }
            }
        } finally {
            compatible.stop()
            oldServer.stop()
            wrongQuery.stop()
        }
    }

    @Test
    fun `posts typed request to v2 endpoint and follows address at call time`() {
        val first = Stub()
        val second = Stub()
        var address = first.base
        val api = PlaceApi(baseUrl = { address })
        val request = PlaceSearchRequest(
            origin = GeoPoint(37.556, 126.923),
            kinds = listOf(PlaceKind.CAFE),
        )

        try {
            runBlocking { api.search(request) }
            assertEquals(listOf("/v2/places/search"), first.hits)
            assertFalse(first.bodies.single().containsKey("preferences"))

            address = second.base
            runBlocking { api.search(request) }

            assertEquals(1, first.hits.size)
            assertEquals(listOf("/v2/places/search"), second.hits)
        } finally {
            first.stop()
            second.stop()
        }
    }
}
