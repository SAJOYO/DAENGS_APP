package com.daengs.app.place

import com.daengs.app.auth.Session
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.support.conversationFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CandidatePoolsTest {
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val request = PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = listOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING))
    private fun repository(client: ConversationClient) = FacilityConversationRepository(client,
        PlaceSearchRepository { error("No fallback") }, { session }, { session })
    private fun response(payload: JsonObject, pool: String): JsonObject {
        var body = conversationFixture("manual", payload)
        if (payload["mode"]?.jsonPrimitive?.content == "restore") body = JsonObject(body + mapOf(
            "session_id" to JsonPrimitive("restored"), "filters" to payload.getValue("restore_filters")))
        return JsonObject(body + mapOf("search_pool" to JsonPrimitive(pool),
            "receipt" to JsonObject(body.getValue("receipt").jsonObject + ("search_pool" to JsonPrimitive(pool)))))
    }

    @Test fun scopeOnlyChatSupportsUndoAndRestoresUsingOwnedExploration() = runTest {
        val calls = mutableListOf<JsonObject>()
        val repository = repository { _, payload ->
            calls += payload
            assertEquals("v1", payload["candidate_pools"]?.jsonPrimitive?.content)
            response(payload, if (payload["mode"]?.jsonPrimitive?.content == "chat") "new_candidates" else "all_places")
        }
        repository.search(request)
        val before = repository.state.value.result!!
        repository.chat("새로운 곳", before.order)
        assertEquals("new_candidates", repository.state.value.result!!.searchPool)
        assertEquals(before.filters, repository.state.value.result!!.filters)
        assertTrue(repository.state.value.canUndo)
        assertTrue(repository.undo())
        assertEquals("all_places", calls.last()["restore_pool"]?.jsonPrimitive?.content)
        assertEquals(before.sessionId, calls.last()["source_session_id"]?.jsonPrimitive?.content)
        assertEquals(2, calls.last()["source_revision"]?.jsonPrimitive?.int)
    }

    @Test fun directAllPlacesControlRetainsFilterAndSessionIdentity() = runTest {
        val calls = mutableListOf<JsonObject>()
        val repository = repository { _, payload ->
            calls += payload
            response(payload, if (payload["mode"]?.jsonPrimitive?.content == "filters") "all_places" else "new_candidates")
        }
        repository.search(request)
        val before = repository.state.value.result!!
        repository.applyFilters(ConversationFilterEdit(before.sessionId, before.revision, searchPool = "all_places"))
        assertEquals("all_places", calls.last().getValue("remove_filters").jsonObject["search_pool"]?.jsonPrimitive?.content)
        assertEquals(before.sessionId, repository.state.value.result!!.sessionId)
        assertEquals(before.filters, repository.state.value.result!!.filters)
        assertEquals("전체 장소", repository.state.value.result!!.poolLabel)
    }

    @Test fun savedPlanCarriesPoolAndOwnedExplorationAndRequiresExactEcho() = runTest {
        val calls = mutableListOf<JsonObject>()
        var echo = "new_candidates"
        val repository = repository { _, payload ->
            calls += payload
            response(payload, if (payload["mode"]?.jsonPrimitive?.content == "restore") echo else "all_places")
        }
        repository.search(request)
        val before = repository.state.value.result!!
        val plan = SearchPlanTransfer(before.filters, "owner", "new_candidates") { true }
        val result = repository.applySearchPlan(plan)
        assertEquals("새 후보", result.poolLabel)
        assertEquals(before.sessionId, calls.last()["source_session_id"]?.jsonPrimitive?.content)
        assertEquals("new_candidates", calls.last()["restore_pool"]?.jsonPrimitive?.content)
        echo = "all_places"
        assertTrue(runCatching { repository.applySearchPlan(plan) }.isFailure)
        assertEquals(result, repository.state.value.result)
    }

    @Test fun failedReplacementAnnouncesRetainedCorrectionAndKeepsExistingPlaces() = runTest {
        var fail = false
        val repository = repository { _, payload ->
            val body = response(payload, "new_candidates")
            if (!fail) body else JsonObject(body + ("receipt" to JsonObject(body.getValue("receipt").jsonObject + mapOf(
                "execution" to JsonPrimitive("failed"), "result_matches_filters" to JsonPrimitive(false),
                "known_places" to buildJsonArray { add(buildJsonObject { put("name", "A") }) }))))
        }
        repository.search(request)
        val before = repository.state.value.result!!
        fail = true
        repository.chat("첫 번째 이미 알아", before.order)
        assertEquals(before.search, repository.state.value.result!!.search)
        assertTrue(repository.state.value.notice!!.contains("정정은 반영"))
        assertFalse(repository.state.value.canUndo)
    }

    @Test fun malformedPoolCannotBePublished() {
        val payload = buildJsonObject { put("client_request_id", "request"); put("mode", "manual") }
        assertTrue(runCatching { response(payload, "visited").toConversationResult() }.isFailure)
        val body = response(payload, "new_candidates")
        assertTrue(runCatching { JsonObject(body + ("search_pool" to JsonPrimitive("all_places"))).toConversationResult() }.isFailure)
    }
}
