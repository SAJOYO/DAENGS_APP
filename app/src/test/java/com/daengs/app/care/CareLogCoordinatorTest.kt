package com.daengs.app.care

import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 탭 한 번 = 기록 한 건. 여기서 지키는 것 —
 * **멱등키는 실패 뒤 같은 종류의 재시도에만 같고**, 성공 뒤·다른 종류는 새 키다.
 * 성공 응답은 곧바로 화면에 반영하고 그 뒤에 `/today` 를 다시 읽는다 (리페치 대기 없음).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CareLogCoordinatorTest {

    @Test
    fun `탭하면 기록을 보내고 응답을 목록 맨 앞에 넣고 건수를 올린 뒤 오늘을 다시 읽는다`() = runTest {
        val gateway = FakeCareGateway()
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.load(TOKEN)
        advanceUntilIdle()

        assertTrue(coordinator.record(TOKEN, CareKind.MEAL))
        advanceUntilIdle()
        val ready = ready(coordinator)
        assertEquals(listOf(EVENT_1), ready.events.map { it.id })
        assertEquals(3, ready.meal)
        assertEquals(1, gateway.recordCalls.size)
        assertEquals(CareKind.MEAL, gateway.recordCalls.single().kind)
        assertEquals(NOW, gateway.recordCalls.single().occurredAt)
        assertEquals(ID_1, gateway.recordCalls.single().clientEventId)
        assertEquals(2, gateway.todayCalls.size)
        assertNull(coordinator.state.value.recording)
    }

    @Test
    fun `실패 뒤 같은 종류의 재시도는 같은 멱등키와 같은 시각이다`() = runTest {
        val gateway = FakeCareGateway().apply {
            recordResults.add(Result.failure(ChatApiError(0, null, "닿지 못함")))
        }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.record(TOKEN, CareKind.MEDICATION)
        advanceUntilIdle()
        assertEquals("닿지 못함", coordinator.state.value.recordError?.message)
        assertEquals(CareKind.MEDICATION, coordinator.state.value.lastRecordKind)

        coordinator.record(TOKEN, CareKind.MEDICATION)
        advanceUntilIdle()

        assertEquals(listOf(ID_1, ID_1), gateway.recordCalls.map { it.clientEventId })
        assertEquals(listOf(NOW, NOW), gateway.recordCalls.map { it.occurredAt })
        assertNull(coordinator.state.value.recordError)
    }

    @Test
    fun `실패 뒤 다른 종류를 누르면 새 멱등키다`() = runTest {
        val gateway = FakeCareGateway().apply {
            recordResults.add(Result.failure(ChatApiError(0, null, "닿지 못함")))
        }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.record(TOKEN, CareKind.MEAL)
        advanceUntilIdle()
        coordinator.record(TOKEN, CareKind.SNACK)
        advanceUntilIdle()

        assertEquals(listOf(ID_1, ID_2), gateway.recordCalls.map { it.clientEventId })
    }

    @Test
    fun `성공 뒤 같은 종류를 다시 누르면 새 멱등키다 — 두 번째 밥은 두 번째 기록이다`() = runTest {
        val gateway = FakeCareGateway()
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.record(TOKEN, CareKind.MEAL)
        advanceUntilIdle()
        coordinator.record(TOKEN, CareKind.MEAL)
        advanceUntilIdle()

        assertEquals(listOf(ID_1, ID_2), gateway.recordCalls.map { it.clientEventId })
    }

    @Test
    fun `보내는 중에는 다시 눌러도 안 보낸다`() = runTest {
        val pending = CompletableDeferred<Result<CareEvent>>()
        val gateway = FakeCareGateway().apply { pendingRecord = pending }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        assertTrue(coordinator.record(TOKEN, CareKind.MEAL))
        advanceUntilIdle()
        assertEquals(CareKind.MEAL, coordinator.state.value.recording)

        assertFalse(coordinator.record(TOKEN, CareKind.SNACK))
        assertEquals(1, gateway.recordCalls.size)
        pending.complete(Result.success(event(EVENT_1, CareKind.MEAL)))
        advanceUntilIdle()
        assertNull(coordinator.state.value.recording)
    }

    @Test
    fun `삭제하면 목록에서 빼고 건수를 내린 뒤 오늘을 다시 읽는다`() = runTest {
        val gateway = FakeCareGateway().apply {
            todayResult = Result.success(summary(events = listOf(event(EVENT_1, CareKind.SNACK)), snack = 1))
        }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.load(TOKEN)
        advanceUntilIdle()

        assertTrue(coordinator.delete(TOKEN, EVENT_1))
        advanceUntilIdle()

        assertEquals(listOf(EVENT_1), gateway.deleteCalls)
        assertEquals(2, gateway.todayCalls.size)
        assertNull(coordinator.state.value.deletingEventId)
    }

    @Test
    fun `삭제 실패는 오류로 남고 목록은 그대로다`() = runTest {
        val gateway = FakeCareGateway().apply {
            todayResult = Result.success(summary(events = listOf(event(EVENT_1, CareKind.SNACK)), snack = 1))
            deleteResult = Result.failure(ChatApiError(404, null, "기록을 찾을 수 없습니다."))
        }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.load(TOKEN)
        advanceUntilIdle()
        coordinator.delete(TOKEN, EVENT_1)
        advanceUntilIdle()

        assertEquals(404, coordinator.state.value.deleteError?.status)
        assertEquals(listOf(EVENT_1), ready(coordinator).events.map { it.id })
    }

    @Test
    fun `강아지를 바꾼 뒤 늦게 끝난 기록은 새 강아지 상태를 덮지 않는다`() = runTest {
        val pending = CompletableDeferred<Result<CareEvent>>()
        val gateway = FakeCareGateway().apply { pendingRecord = pending }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.record(TOKEN, CareKind.MEAL)
        advanceUntilIdle()
        coordinator.selectPet(PET_B)
        pending.complete(Result.success(event(EVENT_1, CareKind.MEAL)))
        advanceUntilIdle()

        assertEquals(PET_B, coordinator.state.value.selectedPetId)
        assertNull(coordinator.state.value.recording)
        assertTrue(coordinator.state.value.today is ChatLoadState.Idle)
    }

    @Test
    fun `저장소가 닫히면 진행 중 표시를 지우고 늦은 결과를 버린다`() = runTest {
        val pending = CompletableDeferred<Result<CareEvent>>()
        val gateway = FakeCareGateway().apply { pendingRecord = pending }
        val coordinator = coordinator(gateway)
        coordinator.selectPet(PET_A)
        coordinator.record(TOKEN, CareKind.MEAL)
        advanceUntilIdle()

        coordinator.cancelPending()
        pending.complete(Result.success(event(EVENT_1, CareKind.MEAL)))
        advanceUntilIdle()

        assertNull(coordinator.state.value.recording)
        assertTrue(coordinator.state.value.today is ChatLoadState.Idle)
    }

    @Test
    fun `강아지가 없으면 아무것도 보내지 않는다`() = runTest {
        val gateway = FakeCareGateway()
        val coordinator = coordinator(gateway)
        assertFalse(coordinator.load(TOKEN))
        assertFalse(coordinator.record(TOKEN, CareKind.MEAL))
        assertTrue(gateway.todayCalls.isEmpty() && gateway.recordCalls.isEmpty())
    }

    private fun ready(coordinator: CareLogCoordinator): CareDaySummary =
        (coordinator.state.value.today as ChatLoadState.Ready).value

    private fun TestScope.coordinator(gateway: FakeCareGateway) =
        CareLogCoordinator(this, gateway, newId = sequenceIds(), now = { NOW })

    private fun sequenceIds(): () -> String {
        val values = ArrayDeque(listOf(ID_1, ID_2, ID_3))
        return { values.removeFirst() }
    }

    private data class RecordCall(val petId: String, val kind: CareKind, val occurredAt: OffsetDateTime, val clientEventId: String)

    /** 서버 흉내. 올라간 기록은 다음 `/today` 에 실려 온다 — 진짜 서버가 그렇다. */
    private class FakeCareGateway : CareGateway {
        val todayCalls = mutableListOf<String>()
        val recordCalls = mutableListOf<RecordCall>()
        val deleteCalls = mutableListOf<String>()
        val recordResults = ArrayDeque<Result<CareEvent>>()
        var todayResult: Result<CareDaySummary> = Result.success(summary())
        var deleteResult: Result<Unit> = Result.success(Unit)
        var pendingRecord: CompletableDeferred<Result<CareEvent>>? = null
        private val stored = mutableListOf<CareEvent>()

        override suspend fun today(accessToken: String, petId: String): Result<CareDaySummary> {
            todayCalls += petId
            return todayResult.map { base ->
                base.copy(
                    events = stored.reversed() + base.events,
                    meal = base.meal + stored.count { it.kind == CareKind.MEAL },
                    medication = base.medication + stored.count { it.kind == CareKind.MEDICATION },
                    snack = base.snack + stored.count { it.kind == CareKind.SNACK },
                )
            }
        }

        override suspend fun record(
            accessToken: String,
            petId: String,
            kind: CareKind,
            occurredAt: OffsetDateTime,
            clientEventId: String,
        ): Result<CareEvent> {
            recordCalls += RecordCall(petId, kind, occurredAt, clientEventId)
            val result = pendingRecord?.let { withContext(NonCancellable) { it.await() } }
                ?: recordResults.removeFirstOrNull()
                ?: Result.success(event(EVENT_1, kind, clientEventId))
            result.onSuccess { stored += it }
            return result
        }

        override suspend fun delete(accessToken: String, eventId: String): Result<Unit> {
            deleteCalls += eventId
            return deleteResult
        }
    }

    private companion object {
        const val TOKEN = "token"
        const val PET_A = "pet-a"
        const val PET_B = "pet-b"
        const val EVENT_1 = "00000000-0000-4000-8000-000000000101"
        const val ID_1 = "10000000-0000-4000-8000-000000000001"
        const val ID_2 = "10000000-0000-4000-8000-000000000002"
        const val ID_3 = "10000000-0000-4000-8000-000000000003"
        val NOW: OffsetDateTime = OffsetDateTime.of(2025, 9, 1, 13, 30, 0, 0, ZoneOffset.ofHours(9))

        fun event(id: String, kind: CareKind, clientEventId: String = ID_1) =
            CareEvent(id, PET_A, kind, NOW.toInstant().toEpochMilli(), null, clientEventId)

        fun summary(events: List<CareEvent> = emptyList(), meal: Int = 2, snack: Int = 0) =
            CareDaySummary(PET_A, "2025-09-01", "Asia/Seoul", meal, 0, snack, 1, events)
    }
}
