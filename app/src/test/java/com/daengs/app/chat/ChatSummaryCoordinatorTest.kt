package com.daengs.app.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSummaryCoordinatorTest {
    @Test
    fun `SUMMARY_ALREADY_EXISTS 는 오류가 아니라 기존 summary id 선택 결과다`() = runTest {
        val gateway = FakeSummaryGateway().apply {
            createResults.add(
                Result.failure(
                    ChatApiError(
                        409,
                        ChatApiError.SUMMARY_ALREADY_EXISTS,
                        "이미 있음",
                        mapOf("summary_id" to SUMMARY_A),
                    ),
                ),
            )
        }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        assertTrue(coordinator.create(TOKEN, source()))
        advanceUntilIdle()

        assertEquals(SUMMARY_A, coordinator.state.value.selectedSummaryId)
        assertNull(coordinator.state.value.createError)
        assertEquals(1, gateway.listCalls.size)
    }

    @Test
    fun `실패로 닫힌 409와 공급자 502와 저장 503 뒤에는 fresh request id 다`() = runTest {
        val failures = listOf(
            ChatApiError(409, ChatApiError.SUMMARY_REQUEST_ALREADY_FAILED, "실패"),
            ChatApiError(502, null, "공급자 실패"),
            ChatApiError(
                503,
                ChatApiError.SUMMARY_PERSISTENCE_FAILED,
                "저장 실패",
                mapOf("retry_with_fresh_client_request_id" to "true"),
            ),
        )
        failures.forEach { failure ->
            val gateway = FakeSummaryGateway().apply {
                createResults.add(Result.failure(failure))
                createResults.add(Result.success(summary(SUMMARY_A, PET_A)))
            }
            val coordinator = coordinator(gateway)
            coordinator.selectPet(PET_A)
            coordinator.create(TOKEN, source())
            advanceUntilIdle()
            coordinator.create(TOKEN, source())
            advanceUntilIdle()
            assertTrue(gateway.createCalls[0].requestId != gateway.createCalls[1].requestId)
        }
    }

    @Test
    fun `processing 409 는 같은 request id 를 지킨다`() = runTest {
        val gateway = FakeSummaryGateway().apply {
            createResults.add(Result.failure(ChatApiError(409, ChatApiError.SUMMARY_PROCESSING, "처리 중")))
            createResults.add(Result.success(summary(SUMMARY_A, PET_A)))
        }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.create(TOKEN, source())
        advanceUntilIdle()
        coordinator.create(TOKEN, source())
        advanceUntilIdle()
        assertEquals(gateway.createCalls[0].requestId, gateway.createCalls[1].requestId)
    }

    @Test
    fun `인증과 소유권 오류는 ChatApiError 그대로 노출한다`() = runTest {
        listOf(
            ChatApiError(401, null, "다시 로그인"),
            ChatApiError(404, null, "없음"),
        ).forEach { failure ->
            val gateway = FakeSummaryGateway().apply { createResults.add(Result.failure(failure)) }
            val coordinator = coordinator(gateway)
            coordinator.selectPet(PET_A)
            coordinator.create(TOKEN, source())
            advanceUntilIdle()
            assertEquals(failure, coordinator.state.value.createError)
        }
    }

    @Test
    fun `완료 turn 이 없는 대화는 네트워크에 보내지 않는다`() = runTest {
        val gateway = FakeSummaryGateway()
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        assertFalse(coordinator.create(TOKEN, ChatSessionDetail(session(SESSION_A, PET_A), emptyList())))
        assertTrue(gateway.createCalls.isEmpty())
        assertEquals(409, coordinator.state.value.createError?.status)
    }

    @Test
    fun `source session id 가 null 인 완성 요약도 목록에 그대로 둔다`() = runTest {
        val orphan = summary(SUMMARY_A, PET_A, sourceSessionId = null)
        val gateway = FakeSummaryGateway().apply {
            listResult = Result.success(ChatSummaryList(listOf(orphan)))
        }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.load(TOKEN)
        advanceUntilIdle()

        val loaded = (coordinator.state.value.summaries as ChatLoadState.Ready).value.summaries.single()
        assertNull(loaded.sourceSessionId)
        assertEquals(orphan, loaded)
    }

    @Test
    fun `요약 삭제 뒤 서버 목록을 새로 받으며 선택도 비운다`() = runTest {
        val gateway = FakeSummaryGateway()
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.selectSummary(SUMMARY_A)
        coordinator.delete(TOKEN, SUMMARY_A)
        advanceUntilIdle()

        assertEquals(listOf(SUMMARY_A), gateway.deleteCalls)
        assertNull(coordinator.state.value.selectedSummaryId)
        assertEquals(1, gateway.listCalls.size)
    }

    @Test
    fun `강아지를 바꾼 뒤 늦게 끝난 요약 생성은 새 강아지 상태를 덮지 않는다`() = runTest {
        val pending = CompletableDeferred<Result<ChatSummary>>()
        val gateway = FakeSummaryGateway().apply { pendingCreate = pending }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.create(TOKEN, source())
        advanceUntilIdle()
        coordinator.selectPet(PET_B)
        pending.complete(Result.success(summary(SUMMARY_A, PET_A)))
        advanceUntilIdle()

        assertEquals(PET_B, coordinator.state.value.selectedPetId)
        assertNull(coordinator.state.value.selectedSummaryId)
        assertTrue(coordinator.state.value.summaries is ChatLoadState.Idle)
    }

    @Test
    fun `저장소가 닫힌 뒤 늦게 끝난 요약 생성은 선택을 바꾸지 않는다`() = runTest {
        val pending = CompletableDeferred<Result<ChatSummary>>()
        val gateway = FakeSummaryGateway().apply { pendingCreate = pending }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.create(TOKEN, source())
        advanceUntilIdle()

        coordinator.cancelPending()
        pending.complete(Result.success(summary(SUMMARY_A, PET_A)))
        advanceUntilIdle()

        assertNull(coordinator.state.value.selectedSummaryId)
        assertNull(coordinator.state.value.creatingSessionId)
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(gateway: FakeSummaryGateway) =
        ChatSummaryCoordinator(this, gateway, ChatRequestIds(sequenceIds()))

    private fun sequenceIds(): () -> String {
        val values = ArrayDeque(listOf(ID_1, ID_2, ID_3, ID_4))
        return { values.removeFirst() }
    }

    private data class CreateCall(val sessionId: String, val requestId: String)

    private class FakeSummaryGateway : ChatSummaryGateway {
        val listCalls = mutableListOf<String>()
        val createCalls = mutableListOf<CreateCall>()
        val deleteCalls = mutableListOf<String>()
        val createResults = ArrayDeque<Result<ChatSummary>>()
        var listResult: Result<ChatSummaryList> = Result.success(ChatSummaryList(emptyList()))
        var pendingCreate: CompletableDeferred<Result<ChatSummary>>? = null

        override suspend fun listSummaries(accessToken: String, petId: String): Result<ChatSummaryList> {
            listCalls += petId
            return listResult
        }

        override suspend fun createSummary(
            accessToken: String,
            sessionId: String,
            clientRequestId: String,
        ): Result<ChatSummary> {
            createCalls += CreateCall(sessionId, clientRequestId)
            pendingCreate?.let { return withContext(NonCancellable) { it.await() } }
            return createResults.removeFirst()
        }

        override suspend fun deleteSummary(accessToken: String, summaryId: String): Result<Unit> {
            deleteCalls += summaryId
            return Result.success(Unit)
        }
    }

    private companion object {
        const val TOKEN = "token"
        const val PET_A = "pet-a"
        const val PET_B = "pet-b"
        const val SESSION_A = "00000000-0000-4000-8000-000000000001"
        const val SUMMARY_A = "00000000-0000-4000-8000-000000000011"
        const val ID_1 = "10000000-0000-4000-8000-000000000001"
        const val ID_2 = "10000000-0000-4000-8000-000000000002"
        const val ID_3 = "10000000-0000-4000-8000-000000000003"
        const val ID_4 = "10000000-0000-4000-8000-000000000004"

        fun session(id: String, petId: String) = ChatSession(id, petId, "대화", emptyList(), 1, 2)

        fun source() = ChatSessionDetail(session(SESSION_A, PET_A), listOf(completedTurn()))

        fun completedTurn() = ChatTurn(
            id = "turn",
            clientMessageId = ID_1,
            processingStatus = ChatTurn.ProcessingStatus.COMPLETED,
            userContent = "질문",
            assistantContent = "답",
            agentCategories = listOf("training"),
            assistantStatus = "ANSWERED",
            publicResponse = null,
            errorCode = null,
            completedAtMs = 2,
            createdAtMs = 1,
        )

        fun summary(id: String, petId: String, sourceSessionId: String? = SESSION_A) = ChatSummary(
            id = id,
            petId = petId,
            sourceSessionId = sourceSessionId,
            sourceTurnCount = 1,
            title = "요약",
            questionSummary = "질문",
            answerSummary = "답",
            keyPoints = listOf("핵심"),
            cautions = emptyList(),
            sourceCitations = emptyList(),
            agentCategories = listOf("training"),
            model = "model",
            promptVersion = "v1",
            completedAtMs = 2,
            createdAtMs = 1,
        )
    }
}
