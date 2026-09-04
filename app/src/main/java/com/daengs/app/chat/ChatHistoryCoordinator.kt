package com.daengs.app.chat

import com.daengs.app.assistant.AssistantApi
import com.daengs.app.assistant.AssistantResponse
import com.daengs.app.location.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 서버에서 읽는 화면 자료의 상태. 값은 메모리에만 있고 서버 응답을 다시 정렬하지 않는다. */
sealed interface ChatLoadState<out T> {
    data object Idle : ChatLoadState<Nothing>
    data object Loading : ChatLoadState<Nothing>
    data class Ready<T>(val value: T) : ChatLoadState<T>
    data class Failed(val error: ChatApiError) : ChatLoadState<Nothing>
}

data class ChatHistoryState(
    val selectedPetId: String? = null,
    val recentSessions: ChatLoadState<ChatSessionList> = ChatLoadState.Idle,
    /** 상세를 받기 전에도 선택을 고정해, 이전 대화로 전송되는 것을 막는다. */
    val selectedSessionId: String? = null,
    val selectedSession: ChatLoadState<ChatSessionDetail> = ChatLoadState.Idle,
    val creatingDraft: Boolean = false,
    val sending: Boolean = false,
    val sendError: ChatApiError? = null,
    val lastResponse: AssistantResponse? = null,
    val deletingSessionId: String? = null,
    val deleteError: ChatApiError? = null,
) {
    val detail: ChatSessionDetail?
        get() = (selectedSession as? ChatLoadState.Ready)?.value

    val canSend: Boolean
        get() = !sending && detail?.session?.let {
            it.id == selectedSessionId && it.petId == selectedPetId
        } == true
}

/**
 * Phase 4A API 를 화면 생애와 연결하는 얇은 경계.
 *
 * 테스트는 이 인터페이스만 바꿔 끼우고, 실제 구현은 HTTP·JSON·오류 파싱을 다시 만들지
 * 않고 [ChatApi] 와 [AssistantApi] 에 그대로 위임한다.
 */
interface ChatHistoryGateway {
    suspend fun createSession(accessToken: String, petId: String): Result<ChatSession>
    suspend fun listSessions(accessToken: String, petId: String): Result<ChatSessionList>
    suspend fun session(accessToken: String, sessionId: String): Result<ChatSessionDetail>
    suspend fun deleteSession(accessToken: String, sessionId: String): Result<Unit>
    /**
     * @param activeDogId 지금 고른 강아지. 저장 경로에서도 [persistence] 와 **따로**
     *   받는다 — 무상태 질문이 싣는 것과 같은 칸이라 같은 자리에서 싣는다 (PR #112).
     */
    suspend fun send(
        accessToken: String,
        text: String,
        where: GeoPoint?,
        activeDogId: String?,
        persistence: ChatPersistence,
    ): Result<AssistantResponse>
}

private class RemoteChatHistoryGateway(
    private val chatApi: ChatApi = ChatApi(),
) : ChatHistoryGateway {
    override suspend fun createSession(accessToken: String, petId: String) =
        chatApi.createSession(accessToken, petId)

    override suspend fun listSessions(accessToken: String, petId: String) =
        chatApi.listSessions(accessToken, petId)

    override suspend fun session(accessToken: String, sessionId: String) =
        chatApi.session(accessToken, sessionId)

    override suspend fun deleteSession(accessToken: String, sessionId: String) =
        chatApi.deleteSession(accessToken, sessionId)

    override suspend fun send(
        accessToken: String,
        text: String,
        where: GeoPoint?,
        activeDogId: String?,
        persistence: ChatPersistence,
    ) = AssistantApi.query(accessToken, text, where, activeDogId, persistence)
}

/**
 * 고른 강아지의 최근 대화·현재 대화·저장 질문을 조율한다.
 *
 * [scope] 는 화면/ViewModel 생애의 scope 여야 한다. 화면이 닫히면 scope 취소가 실제
 * 네트워크 작업을 취소하고, 강아지나 대화를 바꾸면 job 취소와 generation 검사를 함께
 * 써서 취소에 협조하지 않는 늦은 응답까지 버린다.
 *
 * 토큰은 보관하지 않는다. `MainActivity.freshToken` 이 매 동작에 새 토큰을 넘기는 기존
 * 경계를 유지한다.
 */
class ChatHistoryCoordinator(
    private val scope: CoroutineScope,
    private val gateway: ChatHistoryGateway = RemoteChatHistoryGateway(),
    private val requestIds: ChatRequestIds = ChatRequestIds(),
) {
    private val mutableState = MutableStateFlow(ChatHistoryState())
    val state: StateFlow<ChatHistoryState> = mutableState.asStateFlow()

    private var petGeneration = 0L
    private var sessionGeneration = 0L
    private var recentJob: Job? = null
    private var sessionJob: Job? = null
    private var draftJob: Job? = null
    private var sendJob: Job? = null
    private var deleteJob: Job? = null

    fun selectPet(petId: String?) {
        require(petId == null || petId.isNotBlank()) { "pet_id 가 비어 있습니다" }
        if (petId == mutableState.value.selectedPetId) return
        cancelAll()
        petGeneration++
        sessionGeneration++
        requestIds.forget()
        mutableState.value = ChatHistoryState(selectedPetId = petId)
    }

    fun loadRecent(accessToken: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        val generation = petGeneration
        recentJob?.cancel()
        mutableState.update { it.copy(recentSessions = ChatLoadState.Loading) }
        recentJob = scope.launch {
            val result = gateway.listSessions(accessToken, petId)
            if (!isCurrentPet(petId, generation)) return@launch
            mutableState.update {
                it.copy(
                    recentSessions = result.fold(
                        onSuccess = { sessions -> ChatLoadState.Ready(sessions) },
                        onFailure = { error -> ChatLoadState.Failed(error.asChatError()) },
                    ),
                )
            }
        }
        return true
    }

    /** 같은 강아지에서 이미 고른 초안이면 재사용하고 서버를 다시 부르지 않는다. */
    fun createOrReuseDraft(accessToken: String): Boolean {
        val before = mutableState.value
        val petId = before.selectedPetId ?: return false
        val current = before.detail?.session
        if (current?.petId == petId && current.isDraft) return true
        if (before.creatingDraft || before.sending) return false

        val petSnapshot = petGeneration
        val selectionSnapshot = ++sessionGeneration
        sessionJob?.cancel()
        requestIds.forget()
        mutableState.update {
            it.copy(
                selectedSessionId = null,
                selectedSession = ChatLoadState.Loading,
                creatingDraft = true,
                sendError = null,
                lastResponse = null,
            )
        }
        draftJob = scope.launch {
            val result = gateway.createSession(accessToken, petId)
            if (!isCurrent(petId, petSnapshot, null, selectionSnapshot)) return@launch
            result.fold(
                onSuccess = { session ->
                    if (session.petId != petId) {
                        mutableState.update {
                            it.copy(
                                creatingDraft = false,
                                selectedSession = ChatLoadState.Failed(petMismatch(session.petId)),
                            )
                        }
                    } else {
                        mutableState.update {
                            it.copy(
                                selectedSessionId = session.id,
                                selectedSession = ChatLoadState.Ready(ChatSessionDetail(session, emptyList())),
                                creatingDraft = false,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            creatingDraft = false,
                            selectedSession = ChatLoadState.Failed(error.asChatError()),
                        )
                    }
                },
            )
        }
        return true
    }

    fun openSession(accessToken: String, sessionId: String): Boolean {
        val petId = mutableState.value.selectedPetId ?: return false
        require(sessionId.isNotBlank()) { "session_id 가 비어 있습니다" }
        val petSnapshot = petGeneration
        val selectionSnapshot = ++sessionGeneration
        sessionJob?.cancel()
        draftJob?.cancel()
        sendJob?.cancel()
        requestIds.forget()
        mutableState.update {
            it.copy(
                selectedSessionId = sessionId,
                selectedSession = ChatLoadState.Loading,
                creatingDraft = false,
                sending = false,
                sendError = null,
                lastResponse = null,
            )
        }
        sessionJob = scope.launch {
            val result = gateway.session(accessToken, sessionId)
            if (!isCurrent(petId, petSnapshot, sessionId, selectionSnapshot)) return@launch
            mutableState.update {
                it.copy(
                    selectedSession = result.fold(
                        onSuccess = { detail ->
                            if (detail.session.petId == petId && detail.session.id == sessionId) {
                                ChatLoadState.Ready(detail)
                            } else {
                                ChatLoadState.Failed(petMismatch(detail.session.petId))
                            }
                        },
                        onFailure = { error -> ChatLoadState.Failed(error.asChatError()) },
                    ),
                )
            }
        }
        return true
    }

    /** 같은 선택을 다시 읽는다. 화면 재진입 복구이므로 전송 재시도 id는 지우지 않는다. */
    fun refreshCurrent(accessToken: String): Boolean {
        val before = mutableState.value
        val petId = before.selectedPetId ?: return false
        val sessionId = before.selectedSessionId ?: return false
        val petSnapshot = petGeneration
        val selectionSnapshot = sessionGeneration
        sessionJob?.cancel()
        mutableState.update { it.copy(selectedSession = ChatLoadState.Loading) }
        sessionJob = scope.launch {
            val result = gateway.session(accessToken, sessionId)
            if (!isCurrent(petId, petSnapshot, sessionId, selectionSnapshot)) return@launch
            mutableState.update {
                it.copy(
                    selectedSession = result.fold(
                        onSuccess = { detail ->
                            if (detail.session.petId == petId && detail.session.id == sessionId) {
                                ChatLoadState.Ready(detail)
                            } else {
                                ChatLoadState.Failed(petMismatch(detail.session.petId))
                            }
                        },
                        onFailure = { error -> ChatLoadState.Failed(error.asChatError()) },
                    ),
                )
            }
        }
        return true
    }

    /**
     * 저장 질문을 한 번만 보낸다. 성공은 커밋된 응답이므로 현재 상세와 최근 목록을
     * 서버에서 모두 다시 받는다. 새 대화의 활성화·제목·최대 5개 정리는 서버 결과다.
     */
    fun send(accessToken: String, text: String, where: GeoPoint? = null): Boolean {
        val before = mutableState.value
        val petId = before.selectedPetId ?: return false
        val detail = before.detail ?: return false
        val sessionId = before.selectedSessionId ?: return false
        if (before.sending || detail.session.id != sessionId || detail.session.petId != petId) return false
        if (text.isBlank()) return false

        val petSnapshot = petGeneration
        val selectionSnapshot = sessionGeneration
        val messageId = requestIds.clientMessageId(sessionId, text)
        val persistence = ChatPersistence(sessionId, messageId)
        mutableState.update { it.copy(sending = true, sendError = null, lastResponse = null) }
        sendJob = scope.launch {
            val result = try {
                gateway.send(accessToken, text, where, petId, persistence)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (!isCurrent(petId, petSnapshot, sessionId, selectionSnapshot)) return@launch

            result.fold(
                onSuccess = { response ->
                    requestIds.turnDelivered()
                    mutableState.update { it.copy(lastResponse = response, sendError = null) }
                    refreshAfterCommittedResponse(
                        accessToken,
                        petId,
                        sessionId,
                        petSnapshot,
                        selectionSnapshot,
                    )
                },
                onFailure = { failure ->
                    val error = failure.asChatError()
                    requestIds.turnFailed(error)
                    mutableState.update { it.copy(sending = false, sendError = error) }
                },
            )
        }
        return true
    }

    fun deleteSession(accessToken: String, sessionId: String): Boolean {
        val before = mutableState.value
        val petId = before.selectedPetId ?: return false
        if (before.deletingSessionId != null) return false
        val petSnapshot = petGeneration
        val selectionSnapshot = sessionGeneration
        mutableState.update { it.copy(deletingSessionId = sessionId, deleteError = null) }
        deleteJob = scope.launch {
            val deleted = gateway.deleteSession(accessToken, sessionId)
            if (!isCurrentPet(petId, petSnapshot)) return@launch
            deleted.fold(
                onSuccess = {
                    if (mutableState.value.selectedSessionId == sessionId && selectionSnapshot == sessionGeneration) {
                        sessionGeneration++
                        sendJob?.cancel()
                        requestIds.forget()
                        mutableState.update {
                            it.copy(
                                selectedSessionId = null,
                                selectedSession = ChatLoadState.Idle,
                                sending = false,
                                sendError = null,
                                lastResponse = null,
                            )
                        }
                    }
                    val sessions = gateway.listSessions(accessToken, petId)
                    if (!isCurrentPet(petId, petSnapshot)) return@fold
                    mutableState.update {
                        it.copy(
                            deletingSessionId = null,
                            recentSessions = sessions.fold(
                                onSuccess = { list -> ChatLoadState.Ready(list) },
                                onFailure = { error -> ChatLoadState.Failed(error.asChatError()) },
                            ),
                        )
                    }
                },
                onFailure = { failure ->
                    mutableState.update {
                        it.copy(deletingSessionId = null, deleteError = failure.asChatError())
                    }
                },
            )
        }
        return true
    }

    fun clearErrors() {
        mutableState.update { it.copy(sendError = null, deleteError = null) }
    }

    /** 화면이 사라질 때 진행 중인 I/O를 끊고, 취소에 늦게 협조한 응답도 무효화한다. */
    fun cancelPending() {
        cancelAll()
        petGeneration++
        sessionGeneration++
        mutableState.update {
            it.copy(
                creatingDraft = false,
                sending = false,
                deletingSessionId = null,
                lastResponse = null,
            )
        }
    }

    fun forget() = selectPet(null)

    private suspend fun refreshAfterCommittedResponse(
        accessToken: String,
        petId: String,
        sessionId: String,
        petSnapshot: Long,
        selectionSnapshot: Long,
    ) {
        val detail = gateway.session(accessToken, sessionId)
        val recent = gateway.listSessions(accessToken, petId)
        if (!isCurrent(petId, petSnapshot, sessionId, selectionSnapshot)) return
        mutableState.update { old ->
            old.copy(
                selectedSession = detail.fold(
                    onSuccess = { loaded ->
                        if (loaded.session.petId == petId && loaded.session.id == sessionId) {
                            ChatLoadState.Ready(loaded)
                        } else {
                            ChatLoadState.Failed(petMismatch(loaded.session.petId))
                        }
                    },
                    onFailure = { error -> ChatLoadState.Failed(error.asChatError()) },
                ),
                recentSessions = recent.fold(
                    onSuccess = { sessions -> ChatLoadState.Ready(sessions) },
                    onFailure = { error -> ChatLoadState.Failed(error.asChatError()) },
                ),
                sending = false,
            )
        }
    }

    private fun isCurrentPet(petId: String, generation: Long): Boolean =
        generation == petGeneration && mutableState.value.selectedPetId == petId

    private fun isCurrent(
        petId: String,
        petSnapshot: Long,
        sessionId: String?,
        selectionSnapshot: Long,
    ): Boolean = isCurrentPet(petId, petSnapshot) &&
        selectionSnapshot == sessionGeneration &&
        (sessionId == null || mutableState.value.selectedSessionId == sessionId)

    private fun cancelAll() {
        recentJob?.cancel()
        sessionJob?.cancel()
        draftJob?.cancel()
        sendJob?.cancel()
        deleteJob?.cancel()
    }

    private fun petMismatch(actualPetId: String?) = ChatApiError(
        status = 409,
        code = ChatApiError.ACTIVE_DOG_MISMATCH,
        message = "지금 고른 강아지와 이 대화의 강아지가 달라요. 대화를 다시 열어 주세요.",
        data = actualPetId?.let { mapOf("session_pet_id" to it) }.orEmpty(),
    )
}

private fun Throwable.asChatError(): ChatApiError =
    this as? ChatApiError ?: ChatApiError.unreachable("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", this)
