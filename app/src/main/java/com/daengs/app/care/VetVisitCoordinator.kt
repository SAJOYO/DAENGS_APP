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
import java.time.LocalDate
import java.util.UUID

/** 영수증 한 장이 지금 어디까지 갔나. */
enum class ReceiptStep {
    /** ① 초안 + ② 사진 PUT */
    UPLOADING,

    /** ③ Gemini 추출 */
    EXTRACTING,

    /** 확인 화면이 떠 있다 */
    READY,

    /** ④ 확정을 보내는 중 */
    CONFIRMING,
}

/**
 * 처리 중인 영수증 하나.
 *
 * [clientEventId] 는 **촬영이 끝난 순간** 만들어 이 흐름이 끝날 때까지 안 바뀐다 —
 * 초안(①)과 확정(④)이 같은 키를 쓰므로, 두 번 눌려도 초안도 기록도 하나다.
 */
data class ReceiptFlow(
    val clientEventId: String,
    val step: ReceiptStep,
    val draftId: String? = null,
    val draft: VetVisitDraft? = null,
    val error: ChatApiError? = null,
)

/** 유저가 확인 화면에서 고친 값. `client_event_id` 는 화면이 모른다 — 흐름이 들고 있다. */
data class ReceiptEdits(
    val reasonCode: String,
    val reasonDetail: String?,
    val visitedOn: LocalDate,
    val totalKrw: Int,
    val hospitalName: String?,
    val hospitalAddress: String?,
    val hospitalPhone: String?,
    val isEmergency: Boolean,
    val isOncology: Boolean,
)

data class VetVisitState(
    val selectedPetId: String? = null,
    val visits: ChatLoadState<List<VetVisit>> = ChatLoadState.Idle,
    /** 코드 → 표시명. **목록도 이걸로 라벨을 그린다** — 확정 응답에는 코드만 온다. */
    val reasonLabels: Map<String, String> = emptyMap(),
    /** 드롭다운 순서 그대로 (이 강아지가 최근 쓴 사유가 앞). */
    val reasonOptions: List<VetReasonOption> = emptyList(),
    val receipt: ReceiptFlow? = null,
    val deletingVisitId: String? = null,
    val deleteError: ChatApiError? = null,
)

interface VetVisitGateway {
    suspend fun startDraft(accessToken: String, petId: String, clientEventId: String): Result<VetVisitTicket>
    suspend fun upload(ticket: VetVisitTicket, jpeg: ByteArray): Result<Unit>
    suspend fun extract(accessToken: String, draftId: String): Result<VetVisitDraft>
    suspend fun confirm(
        accessToken: String,
        draftId: String,
        confirmation: VetVisitConfirmation,
    ): Result<VetVisit>
    suspend fun list(accessToken: String, petId: String): Result<List<VetVisit>>
    suspend fun reasonOptions(accessToken: String, petId: String): Result<List<VetReasonOption>>
    suspend fun delete(accessToken: String, visitId: String): Result<Unit>
}

private class RemoteVetVisitGateway(private val api: VetVisitApi = VetVisitApi()) : VetVisitGateway {
    override suspend fun startDraft(accessToken: String, petId: String, clientEventId: String) =
        api.startDraft(accessToken, petId, clientEventId)
    override suspend fun upload(ticket: VetVisitTicket, jpeg: ByteArray) = api.upload(ticket, jpeg)
    override suspend fun extract(accessToken: String, draftId: String) = api.extract(accessToken, draftId)
    override suspend fun confirm(
        accessToken: String,
        draftId: String,
        confirmation: VetVisitConfirmation,
    ) = api.confirm(accessToken, draftId, confirmation)
    override suspend fun list(accessToken: String, petId: String) = api.list(accessToken, petId)
    override suspend fun reasonOptions(accessToken: String, petId: String) =
        api.reasonOptions(accessToken, petId)
    override suspend fun delete(accessToken: String, visitId: String) = api.delete(accessToken, visitId)
}

/**
 * 저장소 탭 "진료비" 의 상태. `CareLogCoordinator` 와 같은 꼴이다 — 값은 서버 사본이고
 * 기기에 저장하지 않는다.
 *
 * 이 클래스의 일은 **재시도가 무엇을 다시 하느냐** 하나다:
 *
 * | 어디서 실패했나 | 다시 누르면 |
 * | --- | --- |
 * | ① 초안 · ② 업로드 | **새 키로 새 초안.** 그 사진이 어디까지 갔는지 모른다 |
 * | ③ 추출 (연결) | **같은 초안으로 추출만.** 저쪽이 저장된 결과를 준다 — Gemini 재호출 없음 |
 * | ③ 추출 (409 `photo_not_uploaded`) | **새 키로 새 초안.** 사진이 그 자리에 없다 |
 * | ④ 확정 | **같은 키로 확정만.** 저쪽이 그 키로 먼저 조회해 있던 기록을 준다 |
 *
 * ⚠️ **추출을 다시 불러 화면을 복구하려 들지 않는다.** 이미 추출된 초안을 다시 부르면
 *    저장된 결과가 오는데, 유저가 미동의였다면 항목이 애초에 저장되지 않아 그때도 안
 *    온다 (저쪽 docs §3). 확인 화면이 들고 있는 값이 원본이다.
 */
class VetVisitCoordinator(
    private val scope: CoroutineScope,
    private val gateway: VetVisitGateway = RemoteVetVisitGateway(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutableState = MutableStateFlow(VetVisitState())
    val state: StateFlow<VetVisitState> = mutableState.asStateFlow()

    /** 재시도가 다시 올릴 바이트. 흐름이 끝나면 놓는다 — 사진 하나가 계속 앉아 있지 않게. */
    private var pendingJpeg: ByteArray? = null

    private var petGeneration = 0L
    private var loadJob: Job? = null
    private var receiptJob: Job? = null
    private var deleteJob: Job? = null

    fun selectPet(petId: String?) {
        require(petId == null || petId.isNotBlank()) { "pet_id 가 비어 있습니다" }
        if (petId == mutableState.value.selectedPetId) return
        cancelAll()
        petGeneration++
        pendingJpeg = null
        mutableState.value = VetVisitState(selectedPetId = petId)
    }

    /**
     * 목록과 사유 표시명을 같이 받는다.
     *
     * **사유 목록이 실패해도 기록 목록은 보인다.** 라벨이 없으면 코드가 그대로 보일 뿐,
     * 기록을 못 보여 줄 이유가 아니다.
     */
    fun load(accessToken: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        val generation = petGeneration
        loadJob?.cancel()
        mutableState.update { it.copy(visits = ChatLoadState.Loading) }
        loadJob = scope.launch {
            val options = gateway.reasonOptions(accessToken, petId).getOrNull()
            val visits = gateway.list(accessToken, petId)
            if (!isCurrentPet(petId, generation)) return@launch
            mutableState.update { state ->
                state.copy(
                    visits = visits.fold(
                        onSuccess = { ChatLoadState.Ready(it) },
                        onFailure = { ChatLoadState.Failed(it.asVetError()) },
                    ),
                    reasonOptions = options ?: state.reasonOptions,
                    reasonLabels = options?.associate { it.code to it.label } ?: state.reasonLabels,
                )
            }
        }
        return true
    }

    /**
     * 사진을 찍었다. **여기서 `client_event_id` 가 생긴다** — 업로드 버튼을 누를 때가
     * 아니다. 화면이 얼어 보여 두 번 눌려도 같은 키여야 초안이 하나다.
     */
    fun beginReceipt(accessToken: String, jpeg: ByteArray): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        if (mutableState.value.receipt != null) return false
        pendingJpeg = jpeg
        val flow = ReceiptFlow(clientEventId = newId(), step = ReceiptStep.UPLOADING)
        mutableState.update { it.copy(receipt = flow) }
        runReceipt(accessToken, petId, flow, fromStart = true)
        return true
    }

    /** 실패한 자리에서 다시. 무엇을 다시 하는지는 클래스 머리말의 표대로다. */
    fun retryReceipt(accessToken: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        val flow = mutableState.value.receipt ?: return false
        val error = flow.error ?: return false

        // 사진이 어디까지 갔는지 모르는 실패는 **새 키로 새 초안**이다.
        val restart = flow.draftId == null || error.code == PHOTO_NOT_UPLOADED
        val next = if (restart) {
            ReceiptFlow(clientEventId = newId(), step = ReceiptStep.UPLOADING)
        } else {
            flow.copy(step = ReceiptStep.EXTRACTING, error = null)
        }
        mutableState.update { it.copy(receipt = next) }
        runReceipt(accessToken, petId, next, fromStart = restart)
        return true
    }

    /**
     * ④ 확정. 실패하면 확인 화면으로 돌아가고, 같은 키로 다시 보낼 수 있다 — 저쪽이
     * 그 키로 먼저 조회하므로 이미 저장됐다면 그 기록이 온다.
     */
    fun confirm(accessToken: String, edits: ReceiptEdits): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        val flow = mutableState.value.receipt ?: return false
        val draftId = flow.draftId ?: return false
        if (flow.step == ReceiptStep.CONFIRMING) return false

        val generation = petGeneration
        receiptJob?.cancel()
        mutableState.update { it.copy(receipt = flow.copy(step = ReceiptStep.CONFIRMING, error = null)) }
        receiptJob = scope.launch {
            val result = runSafely {
                gateway.confirm(accessToken, draftId, edits.toConfirmation(flow.clientEventId))
            }
            if (!isCurrentPet(petId, generation)) return@launch
            result.fold(
                onSuccess = { visit ->
                    pendingJpeg = null
                    mutableState.update { it.copy(receipt = null, visits = it.visits.withVisit(visit)) }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(receipt = flow.copy(step = ReceiptStep.READY, error = error.asVetError()))
                    }
                },
            )
        }
        return true
    }

    /** 확인 화면을 닫는다. **확정 안 한 초안은 저쪽이 24시간 뒤 사진째 지운다.** */
    fun dismissReceipt() {
        receiptJob?.cancel()
        pendingJpeg = null
        mutableState.update { it.copy(receipt = null) }
    }

    fun delete(accessToken: String, visitId: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        if (mutableState.value.deletingVisitId != null) return false
        val generation = petGeneration
        mutableState.update { it.copy(deletingVisitId = visitId, deleteError = null) }
        deleteJob = scope.launch {
            val result = gateway.delete(accessToken, visitId)
            if (!isCurrentPet(petId, generation)) return@launch
            result.fold(
                onSuccess = {
                    mutableState.update {
                        it.copy(deletingVisitId = null, visits = it.visits.without(visitId))
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(deletingVisitId = null, deleteError = error.asVetError())
                    }
                },
            )
        }
        return true
    }

    fun clearErrors() {
        mutableState.update { it.copy(deleteError = null) }
    }

    /** 저장소 화면이 사라질 때 네트워크 작업과 늦은 결과를 함께 무효화한다. */
    fun cancelPending() {
        cancelAll()
        petGeneration++
        mutableState.update { it.copy(deletingVisitId = null) }
    }

    fun forget() = selectPet(null)

    // -- 아래는 배관 -------------------------------------------------------

    /**
     * ①②③ 을 한 코루틴에서 잇는다. [fromStart] 가 false 면 ③만 다시 부른다.
     *
     * 세 걸음을 갈라 함수를 셋 두지 않는 이유는 **중간 상태가 화면에 없어서**다 —
     * 유저에게는 "읽는 중" 하나이고, 어디서 끊겼는지는 [ReceiptFlow.draftId] 가 안다.
     */
    private fun runReceipt(
        accessToken: String,
        petId: String,
        flow: ReceiptFlow,
        fromStart: Boolean,
    ) {
        val generation = petGeneration
        receiptJob?.cancel()
        receiptJob = scope.launch {
            var current = flow
            if (fromStart) {
                val jpeg = pendingJpeg ?: return@launch
                val ticket = runSafely { gateway.startDraft(accessToken, petId, flow.clientEventId) }
                    .getOrElse { return@launch failReceipt(petId, generation, current, it) }
                runSafely { gateway.upload(ticket, jpeg) }
                    .getOrElse { return@launch failReceipt(petId, generation, current, it) }
                current = current.copy(step = ReceiptStep.EXTRACTING, draftId = ticket.draftId)
                if (!isCurrentPet(petId, generation)) return@launch
                mutableState.update { it.copy(receipt = current) }
            }
            val draftId = current.draftId ?: return@launch
            val draft = runSafely { gateway.extract(accessToken, draftId) }
                .getOrElse { return@launch failReceipt(petId, generation, current, it) }
            if (!isCurrentPet(petId, generation)) return@launch
            mutableState.update { state ->
                state.copy(
                    receipt = current.copy(
                        step = ReceiptStep.READY,
                        draft = draft,
                        error = null,
                    ),
                    // 초안이 실어 온 사유 목록이 더 최신이다 (이 강아지의 최근 사유가 앞).
                    reasonOptions = draft.reasonOptions.ifEmpty { state.reasonOptions },
                    reasonLabels = draft.reasonOptions
                        .takeIf { it.isNotEmpty() }
                        ?.associate { it.code to it.label }
                        ?: state.reasonLabels,
                )
            }
        }
    }

    private fun failReceipt(petId: String, generation: Long, flow: ReceiptFlow, error: Throwable) {
        if (!isCurrentPet(petId, generation)) return
        mutableState.update { it.copy(receipt = flow.copy(error = error.asVetError())) }
    }

    private suspend fun <T> runSafely(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun isCurrentPet(petId: String, generation: Long): Boolean =
        generation == petGeneration && mutableState.value.selectedPetId == petId

    private fun cancelAll() {
        loadJob?.cancel()
        receiptJob?.cancel()
        deleteJob?.cancel()
    }

    private companion object {
        const val PHOTO_NOT_UPLOADED = "photo_not_uploaded"
    }
}

private fun ReceiptEdits.toConfirmation(clientEventId: String) = VetVisitConfirmation(
    clientEventId = clientEventId,
    reasonCode = reasonCode,
    reasonDetail = reasonDetail,
    visitedOn = visitedOn,
    totalKrw = totalKrw,
    hospitalName = hospitalName,
    hospitalAddress = hospitalAddress,
    hospitalPhone = hospitalPhone,
    isEmergency = isEmergency,
    isOncology = isOncology,
)

/** 방금 확정한 기록을 맨 앞에 넣는다. 아직 목록을 못 읽었으면 그대로 둔다. */
private fun ChatLoadState<List<VetVisit>>.withVisit(visit: VetVisit): ChatLoadState<List<VetVisit>> {
    val visits = (this as? ChatLoadState.Ready)?.value ?: return this
    if (visits.any { it.id == visit.id }) return this
    return ChatLoadState.Ready(listOf(visit) + visits)
}

private fun ChatLoadState<List<VetVisit>>.without(visitId: String): ChatLoadState<List<VetVisit>> {
    val visits = (this as? ChatLoadState.Ready)?.value ?: return this
    return ChatLoadState.Ready(visits.filterNot { it.id == visitId })
}

private fun Throwable.asVetError(): ChatApiError =
    this as? ChatApiError ?: ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", this)
