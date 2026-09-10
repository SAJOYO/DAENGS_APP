package com.daengs.app.place

import com.daengs.app.auth.Session
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.support.filteredConversationFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationUndoTest {
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val request = PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = listOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING))
    private fun repository(client: ConversationClient) = FacilityConversationRepository(client,
        PlaceSearchRepository { error("Unexpected fallback") }, { session }, { session })
    private fun response(payload: JsonObject): JsonObject = when (payload.getValue("mode").jsonPrimitive.content) {
        "chat" -> filteredConversationFixture(payload)
        "restore" -> JsonObject(conversationFixture("manual", payload) + mapOf(
            "filters" to payload.getValue("restore_filters"), "session_id" to JsonPrimitive("restored-session")))
        else -> conversationFixture("manual", payload)
    }

    @Test fun undoRestoresExactFiltersOnServerAndRetryKeepsRequestIdentity() = runTest {
        val requests = mutableListOf<JsonObject>()
        var failRestore = true
        val repository = repository { _, payload ->
            requests += payload
            if (payload["mode"]?.jsonPrimitive?.content == "restore" && failRestore) throw FacilityException(503)
            response(payload)
        }
        repository.search(request)
        val before = repository.state.value.result!!
        repository.chat("주차 가능한 곳만", before.order)
        val applied = repository.state.value.result!!
        assertTrue(repository.state.value.canUndo)
        try { repository.undo(); fail("Expected failure") } catch (_: FacilityException) {}
        assertEquals(applied, repository.state.value.result)
        assertTrue(repository.state.value.canUndo)
        failRestore = false
        assertTrue(repository.undo())
        assertEquals(requests[2], requests[3])
        assertEquals(before.filters, requests.last()["restore_filters"])
        assertFalse(requests.last().containsKey("session_id"))
        assertEquals(before.filters, repository.state.value.result!!.filters)
        assertFalse(repository.state.value.canUndo)
    }

    @Test fun manualChangeInvalidatesUndoAndLateRestoreCannotOverwriteIt() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = repository { _, payload ->
            if (payload["mode"]?.jsonPrimitive?.content == "restore") withContext(NonCancellable) { gate.await() }
            response(payload)
        }
        repository.search(request)
        repository.chat("주차 가능한 곳만", repository.state.value.result!!.order)
        val restoring = async { repository.undo() }
        runCurrent()
        repository.cancelPending()
        // A new manual intent must run as manual, rather than replay the abandoned undo.
        repository.search(request)
        val newer = repository.state.value.result
        gate.complete(Unit); runCurrent()
        assertTrue(restoring.isCancelled)
        assertEquals(newer, repository.state.value.result)
        assertFalse(repository.state.value.canUndo)
        assertFalse(repository.undo())
    }

    @Test fun unchangedFiltersDoNotOfferUndo() = runTest {
        val repository = repository { _, payload -> conversationFixture("manual", payload) }
        repository.search(request)
        repository.chat("조건 설명해줘", repository.state.value.result!!.order)
        assertFalse(repository.state.value.canUndo)
    }
}
