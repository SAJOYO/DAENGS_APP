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
class FacilityConversationTest {
    @Test fun outsideOrInvalidPlanKeepsClickedCardAndSearchWhileAdvancingRevision() = runTest {
        for (code in listOf("facility_out_of_scope", "invalid_plan", "facility_filters")) {
            val repository = repository { _, payload ->
                val fixture = conversationFixture("manual", payload)
                if (payload["mode"]?.jsonPrimitive?.content == "chat")
                    JsonObject(fixture + mapOf("receipt" to JsonObject(fixture.getValue("receipt").jsonObject + mapOf(
                        "code" to JsonPrimitive(code), "execution" to JsonPrimitive("not_run"), "action" to JsonPrimitive("clarify")))))
                else fixture
            }
            repository.search(request)
            val before = repository.state.value.result!!
            val clicked = before.order.last()
            repository.select(clicked)
            repository.chat("시 써줘", before.order)
            val after = repository.state.value.result!!
            assertEquals(clicked, repository.state.value.selected)
            assertEquals(before.filters, after.filters)
            assertEquals(before.order, after.order)
            assertEquals(before.search, after.search)
            assertEquals(before.revision + 1, after.revision)
        }
    }
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

    @Test fun lostFirstReplyRetriesExactlyTheSameRequestAfterCancellation() = runTest {
        val calls = mutableListOf<JsonObject>()
        val repository = repository { _, payload ->
            calls += payload
            if (calls.size == 1) throw java.net.SocketTimeoutException("response lost after commit")
            conversationFixture("manual", payload)
        }
        try { repository.search(request); fail() } catch (_: java.net.SocketTimeoutException) { }
        repository.cancelPending()
        repository.search(request)
        assertEquals(calls[0], calls[1])
        assertEquals(1, repository.state.value.result!!.revision)
    }

    @Test fun conflictRecoversWholeStateAndDoesNotReplayTheRelativeQuery() = runTest {
        val requests = mutableListOf<JsonObject>()
        val latest = conversationFixture("picked").let { JsonObject(it + mapOf(
            "revision" to JsonPrimitive(4), "answer" to JsonNull, "answer_status" to JsonPrimitive("pending"),
            "filters" to JsonObject(it.getValue("filters").jsonObject + ("name_query" to JsonPrimitive("서버 조건"))),
        )) }
        val repository = repository(object : ConversationClient {
            override suspend fun exchange(token: String, payload: JsonObject): JsonObject {
                requests += payload
                if (requests.size == 2) throw FacilityException(409)
                return conversationFixture("manual", payload)
            }
            override suspend fun recover(token: String, payload: JsonObject) = latest
        })
        repository.search(request)
        repository.chat("두 번째 골라줘", repository.state.value.result!!.order)
        assertEquals(2, requests.size)
        assertEquals("서버 조건", repository.state.value.result!!.nameQuery)
        assertEquals(4, repository.state.value.result!!.revision)
        assertNotNull(repository.state.value.notice)
        repository.search(request)
        assertEquals(4, requests.last().getValue("expected_revision").jsonPrimitive.int)
    }

    @Test fun expiredSessionRestoresFullFiltersAndLostRestoreReplyRetainsItsIdentity() = runTest {
        val calls = mutableListOf<JsonObject>()
        val hard = Json.parseToJsonElement("""{"all":[{"id":"p","capability":"operations.parking","op":"eq","value":true}],"any":[]}""")
        val repository = repository { _, payload ->
            calls += payload
            when (payload.getValue("mode").jsonPrimitive.content) {
                "chat" -> throw FacilityException(410)
                "restore" -> {
                    if (calls.size == 3) throw java.net.SocketTimeoutException("restore reply lost")
                    JsonObject(conversationFixture("manual", payload) + mapOf(
                        "session_id" to JsonPrimitive("new-session"), "filters" to payload.getValue("restore_filters")))
                }
                else -> conversationFixture("manual", payload).let { JsonObject(it + (
                    "filters" to JsonObject(it.getValue("filters").jsonObject + ("hard" to hard)))) }
            }
        }
        repository.search(request)
        val before = repository.state.value.result!!
        try { repository.chat("두 번째", before.order); fail() } catch (_: java.net.SocketTimeoutException) { }
        repository.chat("두 번째", before.order)
        assertEquals(calls[2], calls[3])
        assertEquals(before.filters, repository.state.value.result!!.filters)
        assertEquals("new-session", repository.state.value.result!!.sessionId)
        assertEquals(listOf("manual", "chat", "restore", "restore"), calls.map { it.getValue("mode").jsonPrimitive.content })
    }

    @Test fun delayedAnswerDoesNotHoldCommitOrOverwriteNewManualState() = runTest {
        val release = CompletableDeferred<Unit>()
        val repository = repository(object : ConversationClient {
            override suspend fun exchange(token: String, payload: JsonObject) =
                conversationFixture(if (payload.getValue("mode").jsonPrimitive.content == "manual") "manual" else "picked", payload)
            override suspend fun answer(token: String, payload: JsonObject): JsonObject {
                release.await()
                return conversationFixture("picked", payload)
            }
        })
        repository.search(request)
        repository.chat("하나 골라줘", repository.state.value.result!!.order)
        assertEquals(2, repository.state.value.result!!.revision)
        assertNull(repository.state.value.result!!.answer)
        val answering = async { repository.completeAnswer() }
        runCurrent()
        repository.search(request)
        val latest = repository.state.value
        release.complete(Unit)
        runCurrent()
        assertTrue(answering.isCancelled)
        assertEquals(latest, repository.state.value)
        assertEquals(3, latest.result!!.revision)
    }

    @Test fun answerFailureIsRetryableWithoutRerunningSearchOrPlanner() = runTest {
        var turns = 0
        var answers = 0
        val repository = repository(object : ConversationClient {
            override suspend fun exchange(token: String, payload: JsonObject): JsonObject {
                turns++
                return conversationFixture(if (turns == 1) "manual" else "picked", payload)
            }
            override suspend fun answer(token: String, payload: JsonObject): JsonObject {
                if (++answers == 1) throw FacilityException(503)
                return conversationFixture("picked", payload)
            }
        })
        repository.search(request)
        repository.chat("골라줘", repository.state.value.result!!.order)
        repository.completeAnswer()
        assertNotNull(repository.state.value.answerError)
        repository.completeAnswer()
        assertEquals(2, turns)
        assertNotNull(repository.state.value.result!!.answer)
        assertNull(repository.state.value.answerError)
    }
}
