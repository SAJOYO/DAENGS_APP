package com.daengs.app.place

import com.daengs.app.auth.Session
import com.daengs.app.location.GeoPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Fixtures are serialized by the real Place workflow with synthetic shopping records. */
fun conversationFixture(name: String, request: JsonObject? = null): JsonObject {
    val body = object {}.javaClass.getResourceAsStream("/conversation_$name.json")!!
        .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    return if (request == null) body else JsonObject(body + mapOf(
        "client_request_id" to request.getValue("client_request_id"),
        "revision" to JsonPrimitive((request["expected_revision"]?.jsonPrimitive?.int ?: 0) + 1),
    ))
}

@OptIn(ExperimentalCoroutinesApi::class)
class FacilityConversationTest {
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val request = PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = listOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING))
    private fun repository(client: ConversationClient) = FacilityConversationRepository(client,
        PlaceSearchRepository { error("Unexpected legacy search") }, { session }, { session })

    @Test fun manualThenAiSendsSameSessionAndServerOrderWithoutAnotherManualSearch() = runTest {
        val requests = mutableListOf<JsonObject>()
        val repository = repository { _, payload ->
            requests += payload
            conversationFixture(if (payload.getValue("mode").jsonPrimitive.content == "manual") "manual" else "picked", payload)
        }
        val manual = repository.search(request)
        val before = repository.state.value.result!!
        repository.chat("아무 데나 하나 골라줘", before.order.reversed())
        val after = repository.state.value.result!!
        assertEquals(before.sessionId, after.sessionId)
        assertEquals(2, after.revision)
        assertEquals(PlaceKey("test:facility", "first"), after.selected)
        assertEquals(manual, after.search)
        assertEquals(listOf("manual", "chat"), requests.map { it.getValue("mode").jsonPrimitive.content })
        assertEquals("second", requests.last().getValue("visible_order").jsonArray.first().jsonObject.getValue("ref").jsonPrimitive.content)
        assertFalse(requests.last().containsKey("manual"))
        assertFalse(repository.state.value.busy)
    }

    @Test fun lateAiResponseCannotOverwriteNewerManualSearch() = runTest {
        val release = CompletableDeferred<Unit>()
        val repository = repository { _, payload ->
            if (payload.getValue("mode").jsonPrimitive.content == "chat") {
                release.await()
                conversationFixture("picked", payload)
            } else conversationFixture("manual", payload)
        }
        repository.search(request)
        val slow = async { repository.chat("골라줘", repository.state.value.result!!.order) }
        runCurrent()
        repository.cancelPending()
        repository.search(request)
        val manual = repository.state.value
        release.complete(Unit)
        runCurrent()
        assertTrue(slow.isCancelled)
        assertEquals(manual, repository.state.value)
    }

    @Test fun failureKeepsPreviousResultsAndSurfacesAnError() = runTest {
        val repository = repository { _, payload ->
            if (payload.getValue("mode").jsonPrimitive.content == "chat") throw FacilityException(503)
            conversationFixture("manual", payload)
        }
        repository.search(request)
        val before = repository.state.value.result
        try { repository.chat("골라줘", before!!.order); fail("must fail") }
        catch (_: FacilityException) { }
        assertEquals(before, repository.state.value.result)
        assertNotNull(repository.state.value.error)
        assertFalse(repository.state.value.busy)
    }
}
