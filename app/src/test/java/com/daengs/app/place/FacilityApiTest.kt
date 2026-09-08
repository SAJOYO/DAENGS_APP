package com.daengs.app.place

import com.daengs.app.auth.Session
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FacilityApiTest {
    @Test fun queryActionAndBearerTravelOnFacilityEndpoints() = runBlocking {
        val query = facilityQuery()
        val first = facilityResponse(query)
        val action = FacilityAction(first.searchId, first.revision, FacilityChoice.Confirm("lens:cafe"))
        val requests = mutableListOf<Pair<String, JsonObject>>()
        val headers = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/app/places/discovery") { exchange ->
            val path = exchange.requestURI.path
            requests += path to Json.parseToJsonElement(exchange.requestBody.bufferedReader().use { it.readText() }).jsonObject
            headers += exchange.requestHeaders.getFirst("Authorization")
            val body = facilityJson(query, if (path.endsWith("/actions")) action else null).toString().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val api = FacilityApi { "http://127.0.0.1:${server.address.port}" }
            assertEquals(first, api.discover("test-token", query))
            val result = api.act("test-token", first, action)
            assertEquals("lens:cafe", result.confirmedLensId)
            assertEquals(listOf("/app/places/discovery", "/app/places/discovery/actions"), requests.map { it.first })
            assertEquals(listOf(query.toJson(), action.toJson()), requests.map { it.second })
            assertEquals(listOf("Bearer test-token", "Bearer test-token"), headers)
        } finally { server.stop(0) }
    }

    @Test fun wrongQueryRevisionActionAndPlacePresentationAreRejected() {
        val query = facilityQuery()
        val raw = facilityJson(query)
        assertThrows(IllegalArgumentException::class.java) { raw.toFacilityResponse(query.copy(radiusMeters = 5000)) }
        assertThrows(IllegalArgumentException::class.java) { JsonObject(raw + ("contract_version" to JsonPrimitive("place-capability-v1"))).toFacilityResponse(query) }
        val action = FacilityAction(facilityResponse().searchId, 1, FacilityChoice.Confirm("lens:cafe"))
        assertThrows(IllegalArgumentException::class.java) { raw.toFacilityResponse(query, action) }
        val lens = raw.getValue("lenses").jsonArray.first().jsonObject
        val malformed = JsonObject(raw + ("lenses" to JsonArray(listOf(JsonObject(lens + ("presentations" to JsonArray(emptyList())))))))
        assertThrows(IllegalArgumentException::class.java) { malformed.toFacilityResponse(query) }
    }

    @Test fun dogEchoAndManualCategoryCannotBeIgnored() {
        val query = facilityQuery().copy(dogs = listOf(PlaceDogSnapshot("a", "v2", weightKg = 7.0)))
        assertThrows(kotlinx.serialization.SerializationException::class.java) { facilityJson(query).toFacilityResponse(query) }
        val other = facilityQuery().copy(kinds = listOf(PlaceKind.HOSPITAL))
        assertThrows(IllegalArgumentException::class.java) { facilityJson(other).toFacilityResponse(other) }
        assertTrue(facilityQuery().copy(kinds = emptyList()).toJson().getValue("kinds").jsonArray.isEmpty())
    }

    @Test fun unknownFactsAndProxySupportNotesStayVerbatim() {
        val response = facilityResponse()
        assertEquals("확인되지 않았어요.", response.lenses.single().presentations.first().facts.single().text)
        val signal = FacilitySignal("cost", "비용", "needs_selection", true, "가격 정보가 없어요.", listOf(
            FacilityOption("distance", "가까운 곳", "proxy", "실제 가격이 아니라 거리 기준이에요."),
            FacilityOption("price", "상품 가격", "unavailable", "가격 정보가 없어요.")), null)
        val selectable = response.copy(signals = listOf(signal))
        assertTrue(selectable.canChoose(FacilityChoice.Refine("cost", "distance")))
        assertFalse(selectable.canChoose(FacilityChoice.Refine("cost", "price")))
        assertFalse(selectable.canChoose(FacilityChoice.Refine("invented", "distance")))
    }

    @Test fun logoutOrAccountChangeDuringRequestDiscardsResponse() = runBlocking {
        var session: Session? = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        var calls = 0
        val client = object : FacilityClient {
            override suspend fun discover(token: String, query: FacilityQuery): FacilityResponse {
                calls++; session = null; return facilityResponse(query)
            }
            override suspend fun act(token: String, previous: FacilityResponse, action: FacilityAction) = error("unused")
        }
        val repo = AuthenticatedFacilityRepository(client, { session }, { session })
        try { repo.discover("other", facilityQuery()); fail("must reject wrong owner") } catch (_: FacilityException) { }
        assertEquals(0, calls)
        try { repo.discover("owner", facilityQuery()); fail("must reject logout") } catch (_: FacilityException) { }
        assertEquals(1, calls)
    }
}
