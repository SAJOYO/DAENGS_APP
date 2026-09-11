package com.daengs.app.place

import com.daengs.app.auth.Session
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.bookmarks.*
import com.daengs.app.place.support.conversationFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationBookmarkTest {
    private val session = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
    private val request = PlaceSearchRequest(GeoPoint(37.5, 127.0), kinds = listOf(PlaceKind.PET_SHOP, PlaceKind.SHOPPING))
    private fun response(payload: JsonObject): JsonObject {
        val base = conversationFixture("manual", payload)
        if (payload["mode"]?.jsonPrimitive?.content != "chat") return base
        val command = buildJsonObject {
            put("key", payload["visible_selected"] ?: base.getValue("display_order").jsonArray.first())
            put("name", "시설"); put("saved", true)
        }
        return JsonObject(base + ("receipt" to JsonObject(base.getValue("receipt").jsonObject +
            ("bookmark_command" to command))))
    }
    private fun repository(client: ConversationClient) = FacilityConversationRepository(client,
        PlaceSearchRepository { error("no fallback") }, { session }, { session })

    @Test fun waitsForActualBookmarkCompletionAndDoesNotAskServerForSuccessText() = runTest {
        val gate = CompletableDeferred<Unit>()
        var written: PlaceKey? = null
        val repository = repository { _, payload ->
            if (payload["mode"]?.jsonPrimitive?.content == "chat") assertEquals("v1", payload["bookmark_commands"]?.jsonPrimitive?.content)
            response(payload)
        }
        repository.search(request)
        val before = repository.state.value.result!!
        repository.select(before.order[1])
        val task = async { repository.chat("여기 찜해줘", before.order, BookmarkTurn { _, key, saved ->
            assertTrue(saved); written = key; gate.await()
            BookmarkOutcome(BookmarkCompletion.CONFIRMED, "저장 확인")
        }) }
        runCurrent()
        assertEquals(before.order[1], written)
        assertNull(repository.state.value.commandAnswer)
        assertTrue(repository.state.value.answerBusy)
        repository.select(before.order[0])
        gate.complete(Unit); task.await()
        assertEquals("저장 확인", repository.state.value.commandAnswer)
        assertEquals(before.order[0], repository.state.value.selected)
        assertEquals(before.search, repository.state.value.result!!.search)
        repository.completeAnswer()
        assertEquals("none", repository.state.value.result!!.answerStatus)
    }

    @Test fun categoryChangeDoesNotRetargetBookmarkOrRestoreOldMap() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = repository { _, payload ->
            if (payload["mode"]?.jsonPrimitive?.content == "chat") gate.await()
            response(payload)
        }
        repository.search(request)
        val before = repository.state.value.result!!
        repository.select(before.order.first())
        var written: PlaceKey? = null
        val task = async { repository.chat("여기 찜해줘", before.order, BookmarkTurn { _, key, _ ->
            written = key; BookmarkOutcome(BookmarkCompletion.CONFIRMED, "저장 확인")
        }) }
        runCurrent()
        repository.cancelPending()
        repository.select(before.order.last())
        val current = repository.state.value
        gate.complete(Unit); runCurrent()
        assertEquals(before.order.first(), written)
        assertTrue(task.isCancelled)
        assertEquals(current, repository.state.value)
    }

    @Test fun ordinaryFeedbackWithoutCommandNeverCallsBookmarkExecutor() = runTest {
        val repository = repository { _, payload -> conversationFixture("manual", payload) }
        repository.search(request)
        repository.chat("여기 괜찮네", repository.state.value.result!!.order, BookmarkTurn { _, _, _ ->
            error("feedback must not save")
        })
        assertNull(repository.state.value.commandAnswer)
    }

    @Test fun feedbackResponsePreservesSelectionMadeWhileWaiting() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = repository { _, payload ->
            val base = conversationFixture("manual", payload)
            if (payload["mode"]?.jsonPrimitive?.content == "chat") {
                gate.await()
                JsonObject(base + ("receipt" to JsonObject(base.getValue("receipt").jsonObject +
                    ("code" to JsonPrimitive("feedback_no_mutation")))))
            } else base
        }
        repository.search(request)
        val before = repository.state.value.result!!
        val task = async { repository.chat("여기 괜찮네", before.order) }
        runCurrent()
        repository.select(before.order.last())
        gate.complete(Unit); task.await()
        assertEquals(before.order.last(), repository.state.value.selected)
        assertTrue(repository.state.value.result!!.preservesDisplay)
    }
}
