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
class ConversationFiltersTest {
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val request = PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = listOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING))
    private fun repository(client: ConversationClient) = FacilityConversationRepository(client,
        PlaceSearchRepository { error("Unexpected fallback") }, { session }, { session })

    @Test fun removalUsesIdsAndCommittedRevisionWithoutQueryOrAnswerAndKeepsStateUntilCommit() = runTest {
        val calls = mutableListOf<JsonObject>()
        val release = CompletableDeferred<Unit>()
        val repository = repository { _, payload ->
            calls += payload
            if (calls.size == 1) filteredConversationFixture(payload) else {
                release.await()
                conversationFixture("manual", payload)
            }
        }
        repository.search(request)
        val before = repository.state.value.result!!
        val edit = ConversationFilterEdit(before.sessionId, before.revision, removeAll = listOf("parking"), removeAny = listOf("shop", "pet"))
        val operation = async { repository.applyFilters(edit) }
        runCurrent()
        assertEquals(before, repository.state.value.result)
        assertTrue(repository.state.value.busy)
        assertEquals("filters", calls.last().getValue("mode").jsonPrimitive.content)
        assertEquals(edit.toJson(), calls.last().getValue("remove_filters"))
        assertEquals(1, calls.last().getValue("expected_revision").jsonPrimitive.int)
        assertFalse(calls.last().containsKey("manual"))
        assertEquals("", calls.last().getValue("query").jsonPrimitive.content)
        release.complete(Unit)
        assertTrue(operation.await())
        assertEquals(2, repository.state.value.result!!.revision)
        assertEquals("none", repository.state.value.result!!.answerStatus)
        assertNull(repository.state.value.filterRetry)
        // A click captured before the commit cannot remove an ID reused by a later turn.
        assertFalse(repository.applyFilters(edit))
        assertEquals(2, calls.size)
    }

    @Test fun failedRemovalRetainsFilterAndExplicitRetryUsesTheFailedCommit() = runTest {
        val calls = mutableListOf<JsonObject>()
        val repository = repository { _, payload ->
            calls += payload
            if (calls.size == 3) conversationFixture("manual", payload) else {
                val body = filteredConversationFixture(payload)
                if (calls.size == 1) body else JsonObject(body + ("receipt" to
                    JsonObject(body.getValue("receipt").jsonObject + ("execution" to JsonPrimitive("failed")))))
            }
        }
        repository.search(request)
        val before = repository.state.value.result!!
        repository.applyFilters(ConversationFilterEdit(before.sessionId, before.revision, removeAll = listOf("parking")))
        assertEquals(before.filters, repository.state.value.result!!.filters)
        val retry = repository.state.value.filterRetry!!
        assertEquals(2, retry.revision)
        repository.applyFilters(retry)
        assertEquals(2, calls.last().getValue("expected_revision").jsonPrimitive.int)
        assertNull(repository.state.value.filterRetry)
    }

    @Test fun transportRetryRetainsRequestIdentityButDifferentRemovalIsANewIntent() = runTest {
        val calls = mutableListOf<JsonObject>()
        val repository = repository { _, payload ->
            calls += payload
            if (calls.size == 1) filteredConversationFixture(payload) else throw java.net.SocketTimeoutException()
        }
        repository.search(request)
        val before = repository.state.value.result!!
        val edit = ConversationFilterEdit(before.sessionId, before.revision, removeAll = listOf("parking"))
        repeat(2) {
            try { repository.applyFilters(edit); fail() } catch (_: java.net.SocketTimeoutException) { }
        }
        assertEquals(calls[1], calls[2])
        try { repository.applyFilters(edit.copy(removeAll = emptyList(), removeAny = listOf("shop", "pet"))); fail() }
        catch (_: java.net.SocketTimeoutException) { }
        assertNotEquals(calls[2].getValue("client_request_id"), calls[3].getValue("client_request_id"))
        assertEquals(edit.copy(removeAll = emptyList(), removeAny = listOf("shop", "pet")).toJson(), calls[3].getValue("remove_filters"))
    }
}
