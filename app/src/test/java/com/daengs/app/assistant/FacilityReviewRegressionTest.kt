package com.daengs.app.assistant

import com.daengs.app.auth.Session
import com.daengs.app.auth.AccountScope
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.*
import com.daengs.app.place.bookmarks.*
import com.daengs.app.place.support.conversationFixture
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Regression cases that failed before the account lifetime fix. */
class FacilityReviewRegressionTest {
    private var login = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val search = PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = listOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING))
    private var recovered: JsonObject? = null
    private val repository = FacilityConversationRepository(object : ConversationClient {
        override suspend fun exchange(token: String, payload: JsonObject) = conversationFixture("manual", payload)
        override suspend fun recover(token: String, payload: JsonObject) = requireNotNull(recovered)
    }, PlaceSearchRepository { error("Unexpected legacy search") }, { login }, { login }, { AccountScope("owner", 1) })

    private fun answer(context: JsonObject, bookmark: Boolean = false): AssistantResponse {
        val before = repository.state.value.result!!
        recovered = conversationFixture(if (bookmark) "manual" else "picked", buildJsonObject {
            put("client_request_id", context.getValue("client_request_id"))
            put("revision", before.revision + 1)
        })
        if (bookmark) {
            val snapshot = recovered!!
            recovered = JsonObject(snapshot + ("receipt" to JsonObject(snapshot.getValue("receipt").jsonObject + (
                "bookmark_command" to buildJsonObject {
                    put("key", buildJsonObject { put("source", before.order.first().source); put("ref", before.order.first().ref) })
                    put("saved", true); put("name", "선택한 장소")
                }
            ))))
        }
        val result = recovered!!.toConversationResult()
        return AssistantResponse("review", AssistantResponse.Status.ANSWERED,
            result.answer ?: "요청을 준비했어요.", emptyList(), null, resultCount = 1,
            facility = FacilityAssistantReference(result.sessionId, result.revision, result.requestId, result.answer ?: "요청을 준비했어요."))
    }

    @Test fun normalTokenRefreshMustNotDiscardAnAlreadyCommittedFacilityTurn() = runTest {
        repository.search(search)
        val assistant = FacilityAssistant(repository, send = { _, _, _, _, _, context ->
            val result = answer(context)
            // SessionProvider rotates both tokens without changing AccountScope on normal refresh.
            login = login.copy(accessToken = "refreshed-access", refreshToken = "rotated-refresh")
            Result.success(result)
        })
        val response = assistant.query("access", "하나 골라줘", search.origin, null, null)
        assertTrue("A normal token refresh discarded a committed answer: ${response.exceptionOrNull()}", response.isSuccess)
        assertEquals(2, repository.state.value.result!!.revision)
    }

    @Test fun normalTokenRefreshDuringBookmarkWriteMustStillShowItsActualCompletion() = runTest {
        repository.search(search)
        var writes = 0
        val assistant = FacilityAssistant(repository, captureBookmarks = { BookmarkTurn { _, _, _ ->
            // Bookmark repository obtains freshSession before writing. This is the same login.
            login = login.copy(accessToken = "refreshed-access", refreshToken = "rotated-refresh")
            writes++
            BookmarkOutcome(BookmarkCompletion.CONFIRMED, "여기 찜해뒀어요!")
        } }, send = { _, _, _, _, _, context -> Result.success(answer(context, bookmark = true)) })
        val response = assistant.query("access", "첫 번째 찜해줘", search.origin, null, null)
        assertEquals(1, writes)
        assertTrue("Bookmark was saved but completion became a failure: ${response.exceptionOrNull()}", response.isSuccess)
        assertEquals("여기 찜해뒀어요!", response.getOrThrow().message)
    }
}
