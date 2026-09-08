package com.daengs.app.care

import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.UUID

data class CareLogState(
    val selectedPetId: String? = null,
    val today: ChatLoadState<CareDaySummary> = ChatLoadState.Idle,
    /** 지금 서버로 가고 있는 종류. 버튼이 이걸로 busy 를 그린다. */
    val recording: CareKind? = null,
    val recordError: ChatApiError? = null,
    /** 마지막으로 누른 종류. [recordError] 의 "다시 시도" 가 무엇을 다시 보낼지 안다. */
    val lastRecordKind: CareKind? = null,
    val deletingEventId: String? = null,
    val deleteError: ChatApiError? = null,
)

interface CareGateway {
    suspend fun today(accessToken: String, petId: String): Result<CareDaySummary>
    suspend fun record(
        accessToken: String,
        petId: String,
        kind: CareKind,
        occurredAt: OffsetDateTime,
        clientEventId: String,
    ): Result<CareEvent>
    suspend fun delete(accessToken: String, eventId: String): Result<Unit>
}

private class RemoteCareGateway(private val api: CareApi = CareApi()) : CareGateway {
    override suspend fun today(accessToken: String, petId: String) = api.today(accessToken, petId)
    override suspend fun record(
        accessToken: String,
        petId: String,
        kind: CareKind,
        occurredAt: OffsetDateTime,
        clientEventId: String,
    ) = api.record(accessToken, petId, kind, occurredAt, clientEventId)
    override suspend fun delete(accessToken: String, eventId: String) = api.delete(accessToken, eventId)
}

/**
 * 저장소 탭 "오늘의 케어 기록" 의 상태 — 오늘 요약과 기록·삭제의 생애. `ChatSummaryCoordinator`
 * 와 같은 꼴이다: 값은 서버 사본이고 기기에 저장하지 않는다.
 *
 * **멱등키(`client_event_id`)는 여기서 만든다.** 규칙은 `ChatRequestIds` 와 같다 —
 * 실패로 끝난 탭을 **같은 종류로** 다시 누르면 같은 키와 같은 시각을 다시 보내고(응답을
 * 잃었을 뿐 서버에는 올라갔을 수 있다), 성공한 뒤의 탭과 다른 종류의 탭은 새 키다.
 *
 * **기기에 저장하지 않는다.** 키가 의미 있는 구간은 POST 가 날아가는 몇 초뿐이고, 앱이
 * 죽었다 돌아오면 화면이 `/today` 를 먼저 읽어 이미 올라간 기록을 보여 준다 — 그 뒤의 탭은
 * 조용한 중복이 아니라 의식적인 두 번째 기록이다. 키를 저장했다가 다른 탭에 잘못 붙이는
 * 쪽이 더 나쁘다 (`ChatRequestIds` 의 판단과 같다).
 *
 * 성공 응답은 **곧바로 화면에 반영**하고(목록 맨 앞 + 건수) 그 뒤에 `/today` 를 다시 읽어
 * 산책 수까지 맞춘다. 확인 전 삽입(낙관적 업데이트)은 하지 않는다 — 실패 시 되돌리기가
 * 필요해지는데, 버튼 busy 가 이미 즉시 피드백을 준다.
 */
class CareLogCoordinator(
    private val scope: CoroutineScope,
    private val gateway: CareGateway = RemoteCareGateway(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> OffsetDateTime = { OffsetDateTime.now() },
) {
    private val mutableState = MutableStateFlow(CareLogState())
    val state: StateFlow<CareLogState> = mutableState.asStateFlow()

    /** 실패로 끝나 아직 결론이 안 난 탭. 같은 종류로 다시 누르면 이 키를 다시 쓴다. */
    private data class PendingRecord(val kind: CareKind, val clientEventId: String, val occurredAt: OffsetDateTime)

    private var pending: PendingRecord? = null
    private var petGeneration = 0L
    private var loadJob: Job? = null
    private var recordJob: Job? = null
    private var deleteJob: Job? = null

    fun selectPet(petId: String?) {
        require(petId == null || petId.isNotBlank()) { "pet_id 가 비어 있습니다" }
        if (petId == mutableState.value.selectedPetId) return
        cancelAll()
        petGeneration++
        pending = null
        mutableState.value = CareLogState(selectedPetId = petId)
    }

    fun load(accessToken: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        val generation = petGeneration
        loadJob?.cancel()
        mutableState.update { it.copy(today = ChatLoadState.Loading) }
        loadJob = scope.launch {
            val result = gateway.today(accessToken, petId)
            if (!isCurrentPet(petId, generation)) return@launch
            mutableState.update { it.copy(today = result.asLoadState()) }
        }
        return true
    }

    /** 탭 한 번. 보내는 중이면 무시한다 — 같은 탭이 두 줄이 되면 안 된다. */
    fun record(accessToken: String, kind: CareKind): Boolean {
        val before = mutableState.value
        val petId = before.selectedPetId ?: return false
        if (before.recording != null) return false

        val attempt = pending?.takeIf { it.kind == kind } ?: PendingRecord(kind, newId(), now())
        pending = attempt
        val generation = petGeneration
        mutableState.update { it.copy(recording = kind, recordError = null, lastRecordKind = kind) }
        recordJob = scope.launch {
            val result = try {
                gateway.record(accessToken, petId, kind, attempt.occurredAt, attempt.clientEventId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (!isCurrentPet(petId, generation)) return@launch
            result.fold(
                onSuccess = { event ->
                    pending = null
                    mutableState.update {
                        it.copy(recording = null, recordError = null, today = it.today.withRecorded(event))
                    }
                    refresh(accessToken, petId, generation)
                },
                onFailure = { error ->
                    mutableState.update { it.copy(recording = null, recordError = error.asCareError()) }
                },
            )
        }
        return true
    }

    fun delete(accessToken: String, eventId: String): Boolean {
        val before = mutableState.value
        val petId = before.selectedPetId ?: return false
        if (before.deletingEventId != null) return false
        val generation = petGeneration
        mutableState.update { it.copy(deletingEventId = eventId, deleteError = null) }
        deleteJob = scope.launch {
            val result = gateway.delete(accessToken, eventId)
            if (!isCurrentPet(petId, generation)) return@launch
            result.fold(
                onSuccess = {
                    mutableState.update {
                        it.copy(deletingEventId = null, today = it.today.withoutEvent(eventId))
                    }
                    refresh(accessToken, petId, generation)
                },
                onFailure = { error ->
                    mutableState.update { it.copy(deletingEventId = null, deleteError = error.asCareError()) }
                },
            )
        }
        return true
    }

    fun clearErrors() {
        mutableState.update { it.copy(recordError = null, deleteError = null) }
    }

    /** 저장소 화면이 사라질 때 네트워크 작업과 늦은 결과를 함께 무효화한다. */
    fun cancelPending() {
        cancelAll()
        petGeneration++
        mutableState.update { it.copy(recording = null, deletingEventId = null) }
    }

    fun forget() = selectPet(null)

    private suspend fun refresh(accessToken: String, petId: String, generation: Long) {
        val result = gateway.today(accessToken, petId)
        if (!isCurrentPet(petId, generation)) return
        // 새로 읽기에 실패해도 방금 반영한 화면을 오류로 덮지 않는다 — 기록은 이미 올라갔다.
        result.onSuccess { summary -> mutableState.update { it.copy(today = ChatLoadState.Ready(summary)) } }
    }

    private fun isCurrentPet(petId: String, generation: Long): Boolean =
        generation == petGeneration && mutableState.value.selectedPetId == petId

    private fun cancelAll() {
        loadJob?.cancel()
        recordJob?.cancel()
        deleteJob?.cancel()
    }
}

private fun Result<CareDaySummary>.asLoadState(): ChatLoadState<CareDaySummary> = fold(
    onSuccess = { ChatLoadState.Ready(it) },
    onFailure = { ChatLoadState.Failed(it.asCareError()) },
)

/** 서버가 확인해 준 기록을 맨 앞에 넣고 건수를 올린다. 아직 못 읽은 상태면 그대로 둔다. */
private fun ChatLoadState<CareDaySummary>.withRecorded(event: CareEvent): ChatLoadState<CareDaySummary> {
    val summary = (this as? ChatLoadState.Ready)?.value ?: return this
    if (summary.events.any { it.id == event.id }) return this
    return ChatLoadState.Ready(
        summary.copy(
            events = listOf(event) + summary.events,
            meal = summary.meal + (if (event.kind == CareKind.MEAL) 1 else 0),
            medication = summary.medication + (if (event.kind == CareKind.MEDICATION) 1 else 0),
            snack = summary.snack + (if (event.kind == CareKind.SNACK) 1 else 0),
        ),
    )
}

private fun ChatLoadState<CareDaySummary>.withoutEvent(eventId: String): ChatLoadState<CareDaySummary> {
    val summary = (this as? ChatLoadState.Ready)?.value ?: return this
    val removed = summary.events.firstOrNull { it.id == eventId } ?: return this
    return ChatLoadState.Ready(
        summary.copy(
            events = summary.events.filterNot { it.id == eventId },
            meal = summary.meal - (if (removed.kind == CareKind.MEAL) 1 else 0),
            medication = summary.medication - (if (removed.kind == CareKind.MEDICATION) 1 else 0),
            snack = summary.snack - (if (removed.kind == CareKind.SNACK) 1 else 0),
        ),
    )
}

private fun Throwable.asCareError(): ChatApiError =
    this as? ChatApiError ?: ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", this)
