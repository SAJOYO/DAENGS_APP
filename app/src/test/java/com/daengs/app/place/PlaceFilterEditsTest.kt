package com.daengs.app.place

import com.daengs.app.auth.Session
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PlaceFilterEditsTest {
    @Test fun actualPythonWireParsesAndExecutesWithoutDroppingDefaults() {
        val fixture = Json.parseToJsonElement(javaClass.getResource("/place_filter_edit_wire.json")!!.readText()).jsonObject
        val wire = fixture.getValue("request").jsonObject
        val query = PlaceFilterEditQuery(wire.getValue("query").jsonPrimitive.content, filterRequestFixture(), wire.getValue("client_request_id").jsonPrimitive.content)
        assertTrue(sameFilterJson(query.toJson(), wire))
        val proposed = PlaceFilterEditResponse(fixture.getValue("proposal_response").jsonObject, query)
        val action = PlaceFilterEditAction(proposed, false, fixture.getValue("action").jsonObject.getValue("client_request_id").jsonPrimitive.content)
        assertEquals(fixture.getValue("action"), action.toJson())
        val applied = PlaceFilterEditResponse(fixture.getValue("applied_response").jsonObject, query, action)
        assertTrue(applied.result!!.request.criteria.all.isEmpty())
        assertEquals(query.base.request.dogs, applied.result.request.request.dogs)
    }
    @Test fun resultRevisionAndWholeStateMustMatch() {
        val proposal = filterEditFixture()
        val action = PlaceFilterEditAction(proposal, false)
        val applied = appliedFilterEditFixture(action)
        assertEquals(proposal.query.base.revision + 1, applied.result!!.request.revision)
        val result = applied.document.getValue("result").jsonObject
        val changed = JsonObject(result + ("applied_state" to JsonObject(applied.proposed!! + ("unknown_policy" to JsonPrimitive("exclude")))))
        assertThrows(IllegalArgumentException::class.java) { PlaceFilterEditResponse(JsonObject(applied.document + ("result" to changed)), proposal.query, action) }
        assertThrows(IllegalArgumentException::class.java) { PlaceFilterEditResponse(applied.document, proposal.query, action.copy(id = java.util.UUID.randomUUID().toString())) }
    }

    @Test fun unicodeEvidenceUsesCodePointsAndCannotChangeDogOrSpatialScope() {
        val query = PlaceFilterEditQuery("🐕주차", filterRequestFixture())
        val original = filterEditFixture(query)
        fun raw(end: Int) = JsonObject(original.document + ("compiled" to JsonObject(original.compiled + ("proposal" to buildJsonObject {
            put("edits", JsonArray(listOf(buildJsonObject { put("evidence", buildJsonObject { put("start", 1); put("end", end); put("quote", "주차"); put("origin", "explicit") }) })))
            put("unresolved", JsonArray(emptyList()))
        }))))
        PlaceFilterEditResponse(raw(3), query)
        assertThrows(IllegalArgumentException::class.java) { PlaceFilterEditResponse(raw(4), query) }
        val changed = JsonObject(original.proposed!! + ("dogs" to JsonArray(emptyList())))
        assertThrows(IllegalArgumentException::class.java) { PlaceFilterEditResponse(JsonObject(original.document + ("compiled" to JsonObject(original.compiled + ("proposed_state" to changed)))), query) }
    }

    @Test fun purposeExclusionAndEveryPreferenceRoundTripWithoutLoss() {
        val criteria = PlaceFilterCriteria(listOf(PlaceKind.CAFE, PlaceKind.RESTAURANT), all = listOf(
            PlaceFilterAtom("exclude", "purpose.kind", "not_in", JsonArray(listOf(JsonPrimitive("cafe"))))),
            preferences = listOf(PlaceFilterPreference(PlaceFilterAtom("prefer", "operations.parking", "eq", JsonPrimitive(true)), listOf(PlaceKind.RESTAURANT))))
        assertEquals(criteria, criteria.state(filterRequestFixture(criteria).request).filterCriteria())
    }

    @Test fun authenticatedTransportUsesEditEndpointsAndRejectsAccountSwitch() = runBlocking {
        val query = PlaceFilterEditQuery("주차 빼줘", filterRequestFixture())
        val proposed = filterEditFixture(query)
        val action = PlaceFilterEditAction(proposed, false)
        var session: Session? = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        var logout = false
        val requests = mutableListOf<Pair<String, JsonObject>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/app/places/filter-edits") { exchange ->
            assertEquals("Bearer access", exchange.requestHeaders.getFirst("Authorization"))
            requests += exchange.requestURI.path to Json.parseToJsonElement(exchange.requestBody.bufferedReader().use { it.readText() }).jsonObject
            val bytes = (if (exchange.requestURI.path.endsWith("/actions")) appliedFilterEditFixture(action) else proposed).document.toString().toByteArray()
            if (logout) session = null
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val repo = AuthenticatedPlaceFilterEditRepository({ "http://127.0.0.1:${server.address.port}" }, { session }, { session })
            assertEquals(proposed, repo.propose("owner", query)); assertNotNull(repo.apply("owner", action).result)
            assertEquals(listOf(query.toJson(), action.toJson()), requests.map { it.second })
            logout = true
            try { repo.propose("owner", query); fail("logout must reject") } catch (error: FacilityException) { assertEquals(401, error.status) }
        } finally { server.stop(0) }
    }
}
