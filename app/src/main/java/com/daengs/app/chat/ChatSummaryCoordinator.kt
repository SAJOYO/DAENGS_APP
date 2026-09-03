package com.daengs.app.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatSummaryState(
    val selectedPetId: String? = null,
    val summaries: ChatLoadState<ChatSummaryList> = ChatLoadState.Idle,
    /** 화면/내비게이션이 열어야 할 완성 요약. `SUMMARY_ALREADY_EXISTS` 도 여기로 온다. */
    val selectedSummaryId: String? = null,
    val creatingSessionId: String? = null,
    val createError: ChatApiError? = null,
    val deletingSummaryId: String? = null,
    val deleteError: ChatApiError? = null,
)

interface ChatSummaryGateway {
    suspend fun listSummaries(accessToken: String, petId: String): Result<ChatSummaryList>
    suspend fun createSummary(
        accessToken: String,
        sessionId: String,
        clientRequestId: String,
    ): Result<ChatSummary>
    suspend fun deleteSummary(accessToken: String, summaryId: String): Result<Unit>
}

private class RemoteChatSummaryGateway(
    private val chatApi: ChatApi = ChatApi(),
) : ChatSummaryGateway {
    override suspend fun listSummaries(accessToken: String, petId: String) =
        chatApi.listSummaries(accessToken, petId)

    override suspend fun createSummary(
        accessToken: String,
        sessionId: String,
        clientRequestId: String,
    ) = chatApi.createSummary(accessToken, sessionId, clientRequestId)

    override suspend fun deleteSummary(accessToken: String, summaryId: String) =
        chatApi.deleteSummary(accessToken, summaryId)
}

/** 보관함 요약 목록과 생성·삭제 생애를 맡는다. 값은 서버 사본이며 기기에 저장하지 않는다. */
class ChatSummaryCoordinator(
    private val scope: CoroutineScope,
    private val gateway: ChatSummaryGateway = RemoteChatSummaryGateway(),
    private val requestIds: ChatRequestIds = ChatRequestIds(),
) {
    private val mutableState = MutableStateFlow(ChatSummaryState())
    val state: StateFlow<ChatSummaryState> = mutableState.asStateFlow()

    private var petGeneration = 0L
    private var selectionGeneration = 0L
    private var loadJob: Job? = null
    private var createJob: Job? = null
    private var deleteJob: Job? = null

    fun selectPet(petId: String?) {
        require(petId == null || petId.isNotBlank()) { "pet_id 가 비어 있습니다" }
        if (petId == mutableState.value.selectedPetId) return
        cancelAll()
        petGeneration++
        selectionGeneration++
        requestIds.forget()
        mutableState.value = ChatSummaryState(selectedPetId = petId)
    }

    fun load(accessToken: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        val generation = petGeneration
        loadJob?.cancel()
        mutableState.update { it.copy(summaries = ChatLoadState.Loading) }
        loadJob = scope.launch {
            val result = gateway.listSummaries(accessToken, petId)
            if (!isCurrentPet(petId, generation)) return@launch
            mutableState.update {
                it.copy(
                    summaries = result.fold(
                        onSuccess = { list -> ChatLoadState.Ready(list) },
                        onFailure = { error -> ChatLoadState.Failed(error.asSummaryError()) },
                    ),
                )
            }
        }
        return true
    }

    fun selectSummary(summaryId: String?) {
        require(summaryId == null || summaryId.isNotBlank()) { "summary_id 가 비어 있습니다" }
        selectionGeneration++
        mutableState.update { it.copy(selectedSummaryId = summaryId) }
    }

    /** 완료 turn 이 하나 이상 있는 현재 강아지의 대화만 요약 대상으로 받는다. */
    fun create(accessToken: String, source: ChatSessionDetail): Boolean {
        val before = mutableState.value
        val petId = before.selectedPetId ?: return false
        if (before.creatingSessionId != null || source.session.petId != petId) return false
        if (source.turns.none { it.processingStatus == ChatTurn.ProcessingStatus.COMPLETED }) {
            mutableState.update {
                it.copy(
                    createError = ChatApiError(
                        status = 409,
                        code = null,
                        message = "요약할 완료된 대화 내용이 없습니다.",
                    ),
                )
            }
            return false
        }

        val sessionId = source.session.id
        val petSnapshot = petGeneration
        val selectionSnapshot = selectionGeneration
        val requestId = requestIds.clientRequestId(sessionId)
        mutableState.update { it.copy(creatingSessionId = sessionId, createError = null) }
        createJob = scope.launch {
            val result = try {
                gateway.createSummary(accessToken, sessionId, requestId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (!isCurrentPet(petId, petSnapshot)) return@launch

            result.fold(
                onSuccess = { summary ->
                    requestIds.summaryDelivered(sessionId)
                    mutableState.update {
                        it.copy(
                            creatingSessionId = null,
                            selectedSummaryId = if (selectionSnapshot == selectionGeneration) {
                                summary.id
                            } else {
                                it.selectedSummaryId
                            },
                            createError = null,
                        )
                    }
                    refreshAfterMutation(accessToken, petId, petSnapshot)
                },
                onFailure = { failure ->
                    val error = failure.asSummaryError()
                    requestIds.summaryFailed(sessionId, error)
                    val existing = error.existingSummaryId
                    mutableState.update {
                        it.copy(
                            creatingSessionId = null,
                            selectedSummaryId = if (existing != null && selectionSnapshot == selectionGeneration) {
                                existing
                            } else {
                                it.selectedSummaryId
                            },
                            // 이미 있는 요약으로 가는 것은 성공 결과다.
                            createError = if (existing == null) error else null,
                        )
                    }
                    if (existing != null) refreshAfterMutation(accessToken, petId, petSnapshot)
                },
            )
        }
        return true
    }

    fun delete(accessToken: String, summaryId: String): Boolean {
        val before = mutableState.value
        val petId = before.selectedPetId ?: return false
        if (before.deletingSummaryId != null) return false
        val petSnapshot = petGeneration
        mutableState.update { it.copy(deletingSummaryId = summaryId, deleteError = null) }
        deleteJob = scope.launch {
            val result = gateway.deleteSummary(accessToken, summaryId)
            if (!isCurrentPet(petId, petSnapshot)) return@launch
            result.fold(
                onSuccess = {
                    mutableState.update {
                        it.copy(
                            deletingSummaryId = null,
                            selectedSummaryId = it.selectedSummaryId.takeUnless { selected -> selected == summaryId },
                        )
                    }
                    refreshAfterMutation(accessToken, petId, petSnapshot)
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(deletingSummaryId = null, deleteError = error.asSummaryError())
                    }
                },
            )
        }
        return true
    }

    fun clearErrors() {
        mutableState.update { it.copy(createError = null, deleteError = null) }
    }

    fun forget() = selectPet(null)

    private suspend fun refreshAfterMutation(accessToken: String, petId: String, generation: Long) {
        val result = gateway.listSummaries(accessToken, petId)
        if (!isCurrentPet(petId, generation)) return
        mutableState.update {
            it.copy(
                summaries = result.fold(
                    onSuccess = { list -> ChatLoadState.Ready(list) },
                    onFailure = { error -> ChatLoadState.Failed(error.asSummaryError()) },
                ),
            )
        }
    }

    private fun isCurrentPet(petId: String, generation: Long): Boolean =
        generation == petGeneration && mutableState.value.selectedPetId == petId

    private fun cancelAll() {
        loadJob?.cancel()
        createJob?.cancel()
        deleteJob?.cancel()
    }
}

private fun Throwable.asSummaryError(): ChatApiError =
    this as? ChatApiError ?: ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", this)
