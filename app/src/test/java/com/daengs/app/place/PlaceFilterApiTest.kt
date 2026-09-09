package com.daengs.app.place

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PlaceFilterApiTest {
    @Test fun versionedEndpointCarriesTheWholeStateAndNeverFallsBackOnFailure() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val paths = mutableListOf<String>()
        val payloads = mutableListOf<JsonObject>()
        val request = filterRequestFixture()
        var reject = false
        server.createContext("/") { exchange ->
            paths += exchange.requestURI.path
            val body = if (exchange.requestMethod == "GET") filterCapabilitiesFixture().document else {
                payloads += Json.parseToJsonElement(exchange.requestBody.bufferedReader().use { it.readText() }).jsonObject
                filterResponseFixture(request).document
            }
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(if (reject) 404 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = PlaceApi({ "http://127.0.0.1:${server.address.port}" })
            runBlocking { api.filterCapabilities(); api.searchFiltered(request) }
            assertEquals(request.toJson(), payloads.single())
            reject = true
            assertThrows(PlaceApiException::class.java) { runBlocking { api.searchFiltered(request) } }
            assertEquals(listOf("/v3/places/capabilities", "/v3/places/search", "/v3/places/search"), paths)
        } finally { server.stop(0) }
    }
}
