package com.daengs.app.assistant

import com.daengs.app.auth.Session
import com.daengs.app.auth.AccountScope
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatRequestIds
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.*
import com.daengs.app.place.bookmarks.*
import com.daengs.app.place.support.conversationFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FacilityAssistantTest {
    private var login = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private var account = AccountScope("owner", 1)
    private var refreshOnNextRead = false
    private var recoveryToken: String? = null
    private val search = PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = listOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING))
    private var recovered: JsonObject? = null
    private var recoveries = 0
    private var expired = false
    private val exchanges = mutableListOf<JsonObject>()
    private val repository = FacilityConversationRepository(object : ConversationClient {
        override suspend fun exchange(token: String, payload: JsonObject): JsonObject {
            exchanges += payload
            val result = conversationFixture("manual", payload)
            return if (payload["mode"]?.jsonPrimitive?.content == "restore")
                JsonObject(result + ("session_id" to JsonPrimitive("33333333-3333-4333-8333-333333333333"))) else result
        }
        override suspend fun recover(token: String, payload: JsonObject): JsonObject {
            recoveries++
            recoveryToken = token
            if (expired) throw FacilityException(410)
            return requireNotNull(recovered)
        }
    }, PlaceSearchRepository { error("Unexpected legacy search") }, {
        if (refreshOnNextRead) {
            login = login.copy(accessToken = "new-access", refreshToken = "rotated")
            refreshOnNextRead = false
        }
        login
    }, { login }, { account })

    private fun answer(context: JsonObject, bookmark: Boolean = false): AssistantResponse {
        val before = repository.state.value.result
        val body = buildJsonObject {
            put("client_request_id", context.getValue("client_request_id"))
            put("revision", (before?.revision ?: 1) + 1)
        }
        recovered = conversationFixture(if (bookmark) "manual" else "picked", body)
        if (bookmark) {
            val snapshot = recovered!!
            recovered = JsonObject(snapshot + ("receipt" to JsonObject(snapshot.getValue("receipt").jsonObject + (
                "bookmark_command" to buildJsonObject {
                    put("key", buildJsonObject { put("source", before!!.order.first().source); put("ref", before.order.first().ref) })
                    put("saved", true); put("name", "선택한 장소")
                }
            ))))
        }
        val result = recovered!!.toConversationResult()
        return AssistantResponse("assistant-request", AssistantResponse.Status.ANSWERED,
            result.answer ?: "요청을 준비했어요.", emptyList(), null, resultCount = 1,
            facility = FacilityAssistantReference(result.sessionId, result.revision, result.requestId, result.answer ?: "요청을 준비했어요."))
    }

    @Test fun mapAndAssistantUseOneSessionAndApplyOnlyTheRecoveredRevision() = runTest {
        repository.search(search)
        val before = repository.state.value.result!!
        repository.select(before.order.last())
        var sent: JsonObject? = null
        val assistant = FacilityAssistant(repository, send = { _, _, _, _, _, context ->
            sent = context
            Result.success(answer(context))
        })
        val response = assistant.query("access", "하나 골라줘", search.origin, null, null).getOrThrow()
        assertEquals(before.sessionId, sent!!.getValue("session_id").jsonPrimitive.content)
        assertEquals(1, sent!!.getValue("expected_revision").jsonPrimitive.int)
        assertEquals(before.order.last().ref, sent!!.getValue("visible_selected").jsonObject.getValue("ref").jsonPrimitive.content)
        assertFalse(sent!!.containsKey("initial_search"))
        assertEquals(response.facility!!.revision, repository.state.value.result!!.revision)
        assertEquals(before.filters, repository.state.value.result!!.filters)
        assertEquals(1, recoveries)
    }

    @Test fun failedTransportRetriesTheFrozenRequestInsteadOfSelectingANewTarget() = runTest {
        repository.search(search)
        val sent = mutableListOf<JsonObject>()
        val assistant = FacilityAssistant(repository, send = { _, _, _, _, _, context ->
            sent += context
            if (sent.size == 1) Result.failure(IOException("lost reply")) else Result.success(answer(context))
        })
        assertTrue(assistant.query("access", "첫 번째 골라줘", search.origin, null, null).isFailure)
        assertEquals(1, repository.state.value.result!!.revision)
        assertTrue(assistant.query("access", "첫 번째 골라줘", search.origin, null, null).isSuccess)
        assertEquals(sent[0], sent[1])
        assertEquals(2, repository.state.value.result!!.revision)
    }

    @Test fun manualSearchWinsOverALateAssistantReply() = runTest {
        repository.search(search)
        val release = CompletableDeferred<Unit>()
        val assistant = FacilityAssistant(repository, send = { _, _, _, _, _, context ->
            val response = answer(context)
            release.await()
            Result.success(response)
        })
        val pending = async { assistant.query("access", "골라줘", search.origin, null, null) }
        runCurrent()
        repository.search(search)
        val latest = repository.state.value
        release.complete(Unit)
        val error = pending.await().exceptionOrNull() as ChatApiError
        val ids = ChatRequestIds()
        val oldId = ids.clientMessageId("chat", "골라줘")
        assertTrue(error.retryWithFreshClientMessageId)
        assertFalse(ids.turnFailed(error))
        assertNotEquals(oldId, ids.clientMessageId("chat", "골라줘"))
        assertEquals(latest, repository.state.value)
        assertEquals(0, recoveries)
    }

    @Test fun recoveryOfADifferentTurnDoesNotRestoreItsMap() = runTest {
        repository.search(search)
        val before = repository.state.value
        val assistant = FacilityAssistant(repository, send = { _, _, _, _, _, context ->
            val response = answer(context)
            recovered = JsonObject(recovered!! + ("client_request_id" to JsonPrimitive("different-request")))
            Result.success(response)
        })
        assertTrue(assistant.query("access", "골라줘", search.origin, null, null).isFailure)
        assertEquals(before, repository.state.value)
    }

    @Test fun newLoginLifetimeRejectsTheOldReplyEvenForTheSameOwner() = runTest {
        repository.search(search)
        val before = repository.state.value
        val assistant = FacilityAssistant(repository, send = { _, _, _, _, _, context ->
            val response = answer(context)
            account = account.copy(generation = account.generation + 1)
            Result.success(response)
        })
        assertTrue(assistant.query("access", "골라줘", search.origin, null, null).isFailure)
        assertEquals(before, repository.state.value)
        assertEquals(0, recoveries)
    }

    @Test fun unrelatedReplyDoesNotLoadOrChangeTheFacilityView() = runTest {
        repository.search(search)
        val before = repository.state.value
        val assistant = FacilityAssistant(repository, send = { _, _, _, _, _, _ ->
            Result.success(AssistantResponse("r", AssistantResponse.Status.ANSWERED, "반가워요!", emptyList(), null))
        })
        assertEquals("반가워요!", assistant.query("access", "안녕", null, null, null).getOrThrow().message)
        assertEquals(before, repository.state.value)
        assertEquals(0, recoveries)
    }

    @Test fun recoveryObtainsFreshAuthenticationWithinTheSameLogin() = runTest {
        repository.search(search)
        val assistant = FacilityAssistant(repository, send = { _, _, _, _, _, context ->
            val response = answer(context)
            refreshOnNextRead = true
            Result.success(response)
        })
        assertTrue(assistant.query("stale-caller-token", "골라줘", null, null, null).isSuccess)
        assertEquals("new-access", recoveryToken)
        assertEquals(2, repository.state.value.result!!.revision)
    }

    @Test fun sendUsesRefreshedAuthenticationInsteadOfTheCallersOldToken() = runTest {
        repository.search(search)
        refreshOnNextRead = true
        val assistant = FacilityAssistant(repository, send = { token, _, _, _, _, context ->
            assertEquals("new-access", token)
            Result.success(answer(context))
        })
        assertTrue(assistant.query("access", "골라줘", null, null, null).isSuccess)
    }

    @Test fun missingLoginKeepsAuthenticationFailureInsteadOfAChangedViewError() = runTest {
        val loggedOut = FacilityConversationRepository(ConversationClient { _, _ -> error("No request") },
            PlaceSearchRepository { error("No search") }, { null }, { null }, { AccountScope(null, 2) })
        val assistant = FacilityAssistant(loggedOut, send = { _, _, _, _, _, _ -> error("No request") })
        val error = assistant.query("old-token", "골라줘", null, null, null).exceptionOrNull() as ChatApiError
        assertEquals(401, error.status)
    }

    @Test fun bookmarkCompletionWaitsForTheActualCommandAndKeepsTheMap() = runTest {
        repository.search(search)
        val before = repository.state.value.result!!
        val completed = CompletableDeferred<BookmarkOutcome>()
        var executions = 0
        val assistant = FacilityAssistant(repository, captureBookmarks = { BookmarkTurn { _, key, saved ->
            executions++
            assertEquals(before.order.first(), key)
            assertTrue(saved)
            completed.await()
        } }, send = { _, _, _, _, _, context ->
            assertEquals("v1", context.getValue("bookmark_commands").jsonPrimitive.content)
            Result.success(answer(context, bookmark = true))
        })
        val pending = async { assistant.query("access", "첫 번째 찜해줘", search.origin, null, null) }
        runCurrent()
        assertFalse(pending.isCompleted)
        assertEquals(before, repository.state.value.result)
        completed.complete(BookmarkOutcome(BookmarkCompletion.CONFIRMED, "여기 찜해뒀어요!"))
        assertEquals("여기 찜해뒀어요!", pending.await().getOrThrow().message)
        assertEquals("여기 찜해뒀어요!", repository.state.value.commandAnswer)
        assertEquals(before.search, repository.state.value.result!!.search)
        assertEquals(1, executions)
    }

    @Test fun expiredSessionRestoresConditionsWithoutReplayingAnOrdinal() = runTest {
        repository.search(search)
        val before = repository.state.value.result!!
        expired = true
        var sends = 0
        val assistant = FacilityAssistant(repository, send = { _, _, _, _, _, _ ->
            sends++
            Result.success(AssistantResponse("r", AssistantResponse.Status.FAILED, "검색이 만료됐어요.", emptyList(), null,
                resultCount = 1, facilityError = "facility_expired"))
        })
        val response = assistant.query("access", "첫 번째 찜해줘", search.origin, null, null).getOrThrow()
        assertEquals("검색을 다시 불러왔어요. 원하는 요청을 다시 말해 주세요.", response.message)
        assertNotEquals(before.sessionId, repository.state.value.result!!.sessionId)
        assertEquals(before.filters, repository.state.value.result!!.filters)
        assertEquals("restore", exchanges.last().getValue("mode").jsonPrimitive.content)
        assertFalse(exchanges.last().containsKey("query"))
        assertEquals(1, sends)
    }

    @Test fun conflictLoadsCurrentStateButDoesNotExecuteItsBookmarkReceipt() = runTest {
        repository.search(search)
        var commands = 0
        val assistant = FacilityAssistant(repository, captureBookmarks = { BookmarkTurn { _, _, _ ->
            commands++
            error("A recovered old command must never execute")
        } }, send = { _, _, _, _, _, context ->
            answer(context, bookmark = true)
            Result.success(AssistantResponse("r", AssistantResponse.Status.PARTIAL,
                "[훈련]\n훈련 답변\n\n[장소]\n검색이 바뀌었어요.", emptyList(), null, resultCount = 2,
                facilityError = "facility_conflict", facilityErrorMessage = "검색이 바뀌었어요."))
        })
        val response = assistant.query("access", "첫 번째 찜해줘", search.origin, null, null).getOrThrow()
        assertEquals("[훈련]\n훈련 답변\n\n[장소]\n검색을 다시 불러왔어요. 원하는 요청을 다시 말해 주세요.", response.message)
        assertEquals(2, repository.state.value.result!!.revision)
        assertEquals(0, commands)
    }
}
