package com.daengs.app.chat

import com.daengs.app.assistant.AssistantResponse
import com.daengs.app.assistant.ScreeningFollowUp
import com.daengs.app.location.GeoPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatHistoryCoordinatorTest {
    @Test
    fun `같은 강아지의 초안은 다시 만들지 않고 재사용한다`() = runTest {
        val gateway = FakeHistoryGateway()
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)

        assertTrue(coordinator.createOrReuseDraft(TOKEN))
        advanceUntilIdle()
        assertTrue(coordinator.createOrReuseDraft(TOKEN))

        assertEquals(1, gateway.createCalls.size)
        assertEquals(SESSION_A, coordinator.state.value.detail?.session?.id)
    }

    @Test
    fun `최근 대화는 능력별로 묶거나 다시 정렬하지 않는다`() = runTest {
        val gateway = FakeHistoryGateway().apply {
            listResult = Result.success(
                ChatSessionList(
                    listOf(
                        session(SESSION_B, PET_A, "산책", listOf("walk")),
                        session(SESSION_A, PET_A, "훈련", listOf("training")),
                        session(SESSION_C, PET_A, "제도", listOf("life")),
                    ),
                    maxSessions = 5,
                ),
            )
        }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.loadRecent(TOKEN)
        advanceUntilIdle()

        val list = (coordinator.state.value.recentSessions as ChatLoadState.Ready).value
        assertEquals(listOf(SESSION_B, SESSION_A, SESSION_C), list.recent.map { it.id })
    }

    @Test
    fun `고른 강아지와 다른 대화 상세은 열리지도 전송되지도 않는다`() = runTest {
        val gateway = FakeHistoryGateway().apply {
            sessionResults[SESSION_A] = Result.success(detail(SESSION_A, PET_B))
        }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.openSession(TOKEN, SESSION_A)
        advanceUntilIdle()

        val failed = coordinator.state.value.selectedSession as ChatLoadState.Failed
        assertEquals(ChatApiError.ACTIVE_DOG_MISMATCH, failed.error.code)
        assertFalse(coordinator.send(TOKEN, "질문"))
        assertTrue(gateway.sendCalls.isEmpty())
    }

    @Test
    fun `피부 판정 이어 묻기는 저장 질문에도 기록 id 를 그대로 싣는다`() = runTest {
        val gateway = FakeHistoryGateway()
        val coordinator = coordinator(gateway)
        open(coordinator)

        assertTrue(coordinator.send(TOKEN, "이 결과가 무슨 뜻이에요?", screening = ScreeningFollowUp("rec-1")))
        advanceUntilIdle()

        assertEquals(ScreeningFollowUp("rec-1"), gateway.sendCalls.single().screening)
    }

    @Test
    fun `보통 저장 질문에는 이어 묻기가 실리지 않는다`() = runTest {
        val gateway = FakeHistoryGateway()
        val coordinator = coordinator(gateway)
        open(coordinator)

        assertTrue(coordinator.send(TOKEN, "밤에 짖어요"))
        advanceUntilIdle()

        assertEquals(null, gateway.sendCalls.single().screening)
    }

    @Test
    fun `저장 질문은 session id 와 client message id 와 현재 pet id 를 함께 보낸다`() = runTest {
        val gateway = FakeHistoryGateway()
        val coordinator = coordinator(gateway)
        open(coordinator)

        assertTrue(coordinator.send(TOKEN, "밤에 짖어요", GeoPoint(37.5, 127.0)))
        advanceUntilIdle()

        val call = gateway.sendCalls.single()
        assertEquals(SESSION_A, call.persistence.sessionId)
        assertEquals(ID_1, call.persistence.clientMessageId)
        // 대표 강아지는 저장 id 와 **따로** 실린다. 무상태 질의가 싣는 것과 같은 칸이다.
        assertEquals(PET_A, call.activeDogId)
        assertEquals("밤에 짖어요", call.text)
    }

    /**
     * 저장 경로의 `active_dog_id` 는 **지금 고른 강아지**여야 한다. 대화의 강아지를
     * 그대로 되쓰면 강아지를 바꾼 직후 어긋난 값이 실려서, 저쪽이 막아 주려던
     * `ACTIVE_DOG_MISMATCH` 가 도리어 안 걸린다.
     */
    @Test
    fun `저장 질문의 대표 강아지는 지금 고른 강아지다`() = runTest {
        val gateway = FakeHistoryGateway()
        val coordinator = coordinator(gateway)
        open(coordinator)

        coordinator.send(TOKEN, "질문")
        advanceUntilIdle()

        assertEquals(coordinator.state.value.selectedPetId, gateway.sendCalls.single().activeDogId)
    }

    @Test
    fun `응답을 기다리는 동안 중복 전송을 막는다`() = runTest {
        val pending = CompletableDeferred<Result<AssistantResponse>>()
        val gateway = FakeHistoryGateway().apply { pendingSend = pending }
        val coordinator = coordinator(gateway)
        open(coordinator)

        assertTrue(coordinator.send(TOKEN, "질문"))
        advanceUntilIdle()
        assertFalse(coordinator.send(TOKEN, "질문"))
        assertEquals(1, gateway.sendCalls.size)

        pending.complete(Result.success(RESPONSE))
        advanceUntilIdle()
    }

    @Test
    fun `커밋된 답을 받으면 현재 상세과 최근 목록을 모두 새로 받는다`() = runTest {
        val gateway = FakeHistoryGateway()
        val coordinator = coordinator(gateway)
        open(coordinator)
        val detailCallsBefore = gateway.sessionCalls.size
        val listCallsBefore = gateway.listCalls.size

        coordinator.send(TOKEN, "질문")
        advanceUntilIdle()

        assertEquals(detailCallsBefore + 1, gateway.sessionCalls.size)
        assertEquals(listCallsBefore + 1, gateway.listCalls.size)
        assertFalse(coordinator.state.value.sending)
        assertEquals(RESPONSE, coordinator.state.value.lastResponse)
    }

    @Test
    fun `TURN_PROCESSING 409 와 gateway 502 는 같은 id 로 재시도한다`() = runTest {
        listOf(
            ChatApiError(409, ChatApiError.TURN_PROCESSING, "처리 중"),
            ChatApiError(502, null, "gateway"),
        ).forEach { firstError ->
            val gateway = FakeHistoryGateway().apply {
                sendResults.add(Result.failure(firstError))
                sendResults.add(Result.success(RESPONSE))
            }
            val coordinator = coordinator(gateway)
            open(coordinator)
            coordinator.send(TOKEN, "같은 질문")
            advanceUntilIdle()
            coordinator.send(TOKEN, "같은 질문")
            advanceUntilIdle()
            assertEquals(
                gateway.sendCalls[0].persistence.clientMessageId,
                gateway.sendCalls[1].persistence.clientMessageId,
            )
        }
    }

    @Test
    fun `저장 실패 503 플래그 뒤에는 새 message id 로 재시도한다`() = runTest {
        val gateway = FakeHistoryGateway().apply {
            sendResults.add(
                Result.failure(
                    ChatApiError(
                        503,
                        ChatApiError.TURN_PERSISTENCE_FAILED,
                        "저장 실패",
                        mapOf("retry_with_fresh_client_message_id" to "true"),
                    ),
                ),
            )
            sendResults.add(Result.success(RESPONSE))
        }
        val coordinator = coordinator(gateway)
        open(coordinator)
        coordinator.send(TOKEN, "같은 질문")
        advanceUntilIdle()
        coordinator.send(TOKEN, "같은 질문")
        advanceUntilIdle()

        assertTrue(gateway.sendCalls[0].persistence.clientMessageId != gateway.sendCalls[1].persistence.clientMessageId)
    }

    @Test
    fun `대화를 지우면 서버 목록을 새로 받고 선택 중인 상세을 비운다`() = runTest {
        val gateway = FakeHistoryGateway()
        val coordinator = coordinator(gateway)
        open(coordinator)
        coordinator.deleteSession(TOKEN, SESSION_A)
        advanceUntilIdle()

        assertEquals(listOf(SESSION_A), gateway.deleteCalls)
        assertEquals(null, coordinator.state.value.selectedSessionId)
        assertTrue(coordinator.state.value.selectedSession is ChatLoadState.Idle)
        assertTrue(coordinator.state.value.recentSessions is ChatLoadState.Ready)
    }

    @Test
    fun `대화를 바꾼 뒤 늦게 끝난 이전 전송은 새 대화를 덮지 않는다`() = runTest {
        val pending = CompletableDeferred<Result<AssistantResponse>>()
        val gateway = FakeHistoryGateway().apply {
            pendingSend = pending
            sessionResults[SESSION_B] = Result.success(detail(SESSION_B, PET_A))
        }
        val coordinator = coordinator(gateway)
        open(coordinator)
        coordinator.send(TOKEN, "느린 질문")
        advanceUntilIdle()

        coordinator.openSession(TOKEN, SESSION_B)
        advanceUntilIdle()
        pending.complete(Result.success(RESPONSE))
        advanceUntilIdle()

        assertEquals(SESSION_B, coordinator.state.value.selectedSessionId)
        assertEquals(SESSION_B, coordinator.state.value.detail?.session?.id)
        assertEquals(null, coordinator.state.value.lastResponse)
    }

    @Test
    fun `화면이 닫힌 뒤 늦게 끝난 전송은 상태를 갱신하지 않는다`() = runTest {
        val pending = CompletableDeferred<Result<AssistantResponse>>()
        val gateway = FakeHistoryGateway().apply { pendingSend = pending }
        val coordinator = coordinator(gateway)
        open(coordinator)
        coordinator.send(TOKEN, "느린 질문")
        advanceUntilIdle()

        coordinator.cancelPending()
        pending.complete(Result.success(RESPONSE))
        advanceUntilIdle()

        assertFalse(coordinator.state.value.sending)
        assertEquals(null, coordinator.state.value.lastResponse)
        assertEquals(SESSION_A, coordinator.state.value.selectedSessionId)
    }

    @Test
    fun `화면 재진입 새로고침은 미확정 전송의 재시도 id를 보존한다`() = runTest {
        val pending = CompletableDeferred<Result<AssistantResponse>>()
        val gateway = FakeHistoryGateway().apply { pendingSend = pending }
        val coordinator = coordinator(gateway)
        open(coordinator)
        coordinator.send(TOKEN, "같은 질문")
        advanceUntilIdle()

        coordinator.cancelPending()
        pending.complete(Result.failure(ChatApiError(502, null, "연결 끊김")))
        advanceUntilIdle()
        gateway.pendingSend = null
        coordinator.refreshCurrent(TOKEN)
        advanceUntilIdle()
        coordinator.send(TOKEN, "같은 질문")
        advanceUntilIdle()

        assertEquals(
            gateway.sendCalls[0].persistence.clientMessageId,
            gateway.sendCalls[1].persistence.clientMessageId,
        )
    }

    /**
     * 신고(`POST /app/reports`)가 turn id 를 요구하는데 `POST /assistant/query` 응답에는
     * 그 값이 없다. 성공 뒤 다시 받는 상세에서 **우리가 보낸 키로** 짚어야 한다 —
     * 순서나 시각으로 짚으면 답이 여럿 쌓인 뒤 엉뚱한 turn 을 신고하게 된다.
     */
    @Test
    fun `방금 답한 turn 을 client message id 로 짚는다`() = runTest {
        val gateway = FakeHistoryGateway().apply {
            sessionResults[SESSION_A] = Result.success(
                ChatSessionDetail(
                    session(SESSION_A, PET_A, "대화"),
                    listOf(turn("turn-먼저", ID_2), turn("turn-방금", ID_1)),
                ),
            )
        }
        val coordinator = coordinator(gateway)
        open(coordinator)

        coordinator.send(TOKEN, "밤에 짖어요")
        advanceUntilIdle()

        assertEquals(ID_1, gateway.sendCalls.single().persistence.clientMessageId)
        assertEquals("turn-방금", coordinator.state.value.lastTurnId)
    }

    @Test
    fun `대화를 다시 열면 앞 답변의 turn id 를 잊는다`() = runTest {
        val gateway = FakeHistoryGateway().apply {
            sessionResults[SESSION_A] = Result.success(
                ChatSessionDetail(session(SESSION_A, PET_A, "대화"), listOf(turn("turn-방금", ID_1))),
            )
        }
        val coordinator = coordinator(gateway)
        open(coordinator)
        coordinator.send(TOKEN, "질문")
        advanceUntilIdle()
        assertEquals("turn-방금", coordinator.state.value.lastTurnId)

        // 남은 값으로 다른 대화의 답변을 신고하면 안 된다.
        coordinator.openSession(TOKEN, SESSION_A)
        assertEquals(null, coordinator.state.value.lastTurnId)
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(gateway: FakeHistoryGateway) =
        ChatHistoryCoordinator(
            scope = this,
            gateway = gateway,
            requestIds = ChatRequestIds(sequenceIds()),
        )

    private suspend fun kotlinx.coroutines.test.TestScope.open(coordinator: ChatHistoryCoordinator) {
        coordinator.selectPet(PET_A)
        coordinator.openSession(TOKEN, SESSION_A)
        advanceUntilIdle()
    }

    private fun sequenceIds(): () -> String {
        val values = ArrayDeque(listOf(ID_1, ID_2, ID_3, ID_4))
        return { values.removeFirst() }
    }

    private data class SendCall(
        val text: String,
        val activeDogId: String?,
        val persistence: ChatPersistence,
        val screening: ScreeningFollowUp? = null,
    )

    private class FakeHistoryGateway : ChatHistoryGateway {
        val createCalls = mutableListOf<String>()
        val listCalls = mutableListOf<String>()
        val sessionCalls = mutableListOf<String>()
        val deleteCalls = mutableListOf<String>()
        val sendCalls = mutableListOf<SendCall>()
        val sessionResults = mutableMapOf(SESSION_A to Result.success(detail(SESSION_A, PET_A)))
        val sendResults = ArrayDeque<Result<AssistantResponse>>()
        var listResult: Result<ChatSessionList> = Result.success(
            ChatSessionList(listOf(session(SESSION_A, PET_A, "대화")), 5),
        )
        var pendingSend: CompletableDeferred<Result<AssistantResponse>>? = null

        override suspend fun createSession(accessToken: String, petId: String): Result<ChatSession> {
            createCalls += petId
            return Result.success(session(SESSION_A, petId, "새 대화", lastMessageAtMs = null))
        }

        override suspend fun listSessions(accessToken: String, petId: String): Result<ChatSessionList> {
            listCalls += petId
            return listResult
        }

        override suspend fun session(accessToken: String, sessionId: String): Result<ChatSessionDetail> {
            sessionCalls += sessionId
            return sessionResults[sessionId] ?: Result.failure(ChatApiError(404, null, "없음"))
        }

        override suspend fun deleteSession(accessToken: String, sessionId: String): Result<Unit> {
            deleteCalls += sessionId
            return Result.success(Unit)
        }

        override suspend fun send(
            accessToken: String,
            text: String,
            where: GeoPoint?,
            activeDogId: String?,
            persistence: ChatPersistence,
            screening: ScreeningFollowUp?,
        ): Result<AssistantResponse> {
            sendCalls += SendCall(text, activeDogId, persistence, screening)
            pendingSend?.let { return withContext(NonCancellable) { it.await() } }
            return if (sendResults.isEmpty()) Result.success(RESPONSE) else sendResults.removeFirst()
        }
    }

    private companion object {
        const val TOKEN = "token"
        const val PET_A = "pet-a"
        const val PET_B = "pet-b"
        const val SESSION_A = "00000000-0000-4000-8000-000000000001"
        const val SESSION_B = "00000000-0000-4000-8000-000000000002"
        const val SESSION_C = "00000000-0000-4000-8000-000000000003"
        const val ID_1 = "10000000-0000-4000-8000-000000000001"
        const val ID_2 = "10000000-0000-4000-8000-000000000002"
        const val ID_3 = "10000000-0000-4000-8000-000000000003"
        const val ID_4 = "10000000-0000-4000-8000-000000000004"

        val RESPONSE = AssistantResponse("request", AssistantResponse.Status.ANSWERED, "답", emptyList(), null)

        fun session(
            id: String,
            petId: String,
            title: String,
            categories: List<String> = emptyList(),
            lastMessageAtMs: Long? = 2,
        ) = ChatSession(id, petId, title, categories, createdAtMs = 1, lastMessageAtMs = lastMessageAtMs)

        fun detail(id: String, petId: String) = ChatSessionDetail(session(id, petId, "대화"), emptyList())

        fun turn(id: String, clientMessageId: String) = ChatTurn(
            id = id,
            clientMessageId = clientMessageId,
            processingStatus = ChatTurn.ProcessingStatus.COMPLETED,
            userContent = "질문",
            assistantContent = "답",
            agentCategories = emptyList(),
            assistantStatus = "ANSWERED",
            publicResponse = null,
            errorCode = null,
            completedAtMs = 2,
            createdAtMs = 1,
        )
    }
}
