package com.daengs.app.walk.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 함께 보기 목록의 상태. */
sealed interface SharedWalksStatus {
    data object Idle : SharedWalksStatus
    data object Loading : SharedWalksStatus
    data object Ready : SharedWalksStatus

    /** 공동 조회 전 서버. 화면은 쓸 수 없다고만 말한다. */
    data object Unsupported : SharedWalksStatus

    /** 그 강아지를 볼 수 없다(구성원이 아님·나감·내보내짐). */
    data class NotFound(val message: String) : SharedWalksStatus

    data class Failed(val message: String) : SharedWalksStatus
}

/** 함께 보는 산책 한 건의 상세 상태. */
sealed interface SharedWalkDetailStatus {
    data object Closed : SharedWalkDetailStatus
    data class Loading(val walkId: String) : SharedWalkDetailStatus
    data class Ready(val detail: SharedWalkDetail) : SharedWalkDetailStatus

    /** 볼 수 없거나 못 읽었다. [retryable] 이 거짓이면 다시 시도해도 같은 답이다. */
    data class Unavailable(val walkId: String, val message: String, val retryable: Boolean) : SharedWalkDetailStatus
}

/**
 * 다른 보호자가 다녀온 산책을 **읽기 전용으로** 모아 보는 상태.
 *
 * **기기 기록(Room)과 섞지 않는다.** 이 목록은 서버에서 읽은 것을 메모리에만 들고, 내가 올린
 * 산책(`is_mine`)은 기기 기록 목록에 이미 있으니 뺀다. 여기서 산책을 고치거나 지우는 길은 없다.
 *
 * **한 로그인에 하나다.** 부르는 쪽이 계정(AccountScope)마다 새로 만들고, 기다리는 사이 계정이
 * 바뀌면 [isCurrentAccount] 로 알아채 늦게 온 결과를 버린다 — 이전 계정의 산책이 다음 계정
 * 화면에 남으면 안 된다.
 */
class SharedWalksHolder(
    private val reader: SharedWalkReader,
    /** 이 계정의 access 토큰. 계정이 바뀌었거나 로그인 정보를 못 받으면 null. */
    private val accessToken: suspend () -> String?,
    private val isCurrentAccount: () -> Boolean,
) {
    var petId: String? by mutableStateOf(null)
        private set

    var walks: List<SharedWalk> by mutableStateOf(emptyList())
        private set

    var nextCursor: String? by mutableStateOf(null)
        private set

    var status: SharedWalksStatus by mutableStateOf(SharedWalksStatus.Idle)
        private set

    var detail: SharedWalkDetailStatus by mutableStateOf(SharedWalkDetailStatus.Closed)
        private set

    private var listGeneration = 0
    private var detailGeneration = 0

    /** 그 강아지의 첫 페이지. 다른 강아지였으면 앞 목록을 버린다. */
    suspend fun open(petId: String) {
        this.petId = petId
        walks = emptyList()
        nextCursor = null
        closeDetail()
        load(petId, cursor = null)
    }

    suspend fun loadMore() {
        val pet = petId ?: return
        val cursor = nextCursor ?: return
        if (status == SharedWalksStatus.Loading) return
        load(pet, cursor)
    }

    /**
     * 다시 읽는다. 이어 읽기에서 실패했으면(받은 것이 있고 다음 커서가 남아 있으면) 그 자리부터,
     * 아니면 첫 페이지부터 — 이어 읽을 커서가 없는데 이어 읽기로 가면 아무것도 안 부른다.
     */
    suspend fun retry() {
        val pet = petId ?: return
        if (status is SharedWalksStatus.Failed && walks.isNotEmpty() && nextCursor != null) loadMore()
        else load(pet, cursor = null)
    }

    private suspend fun load(pet: String, cursor: String?) {
        val generation = ++listGeneration
        status = SharedWalksStatus.Loading
        val token = accessToken()
        if (generation != listGeneration) return
        if (!isCurrentAccount()) return forget()
        if (token == null) {
            status = SharedWalksStatus.Failed("로그인 정보를 확인해 주세요.")
            return
        }
        val result = reader.list(token, pet, cursor)
        // 기다리는 사이 다른 강아지를 골랐거나 계정이 바뀌었으면 늦게 온 답을 버린다.
        if (generation != listGeneration) return
        if (!isCurrentAccount()) return forget()
        when (result) {
            is SharedWalkResult.Ready -> {
                val known = if (cursor == null) emptyList() else walks
                val fresh = result.value.walks.filter { walk -> !walk.isMine && known.none { it.id == walk.id } }
                walks = known + fresh
                nextCursor = result.value.nextCursor
                status = SharedWalksStatus.Ready
            }
            SharedWalkResult.Unsupported -> {
                walks = emptyList()
                nextCursor = null
                status = SharedWalksStatus.Unsupported
            }
            is SharedWalkResult.NotFound -> {
                walks = emptyList()
                nextCursor = null
                status = SharedWalksStatus.NotFound(result.message)
            }
            // 받아 둔 것은 남긴다 — 이어 읽기에서 망이 흔들린 것이면 다시 시도하면 된다.
            is SharedWalkResult.Failed -> status = SharedWalksStatus.Failed(result.message)
        }
    }

    /** 한 건과 그 경로. 볼 수 없으면 서버 문장을 남긴다. */
    suspend fun openDetail(walkId: String) {
        val pet = petId ?: return
        val generation = ++detailGeneration
        detail = SharedWalkDetailStatus.Loading(walkId)
        val token = accessToken()
        if (generation != detailGeneration) return
        if (!isCurrentAccount()) return forget()
        if (token == null) {
            detail = SharedWalkDetailStatus.Unavailable(walkId, "로그인 정보를 확인해 주세요.", retryable = true)
            return
        }
        val result = reader.detail(token, pet, walkId)
        if (generation != detailGeneration) return
        if (!isCurrentAccount()) return forget()
        detail = when (result) {
            is SharedWalkResult.Ready -> SharedWalkDetailStatus.Ready(result.value)
            SharedWalkResult.Unsupported ->
                SharedWalkDetailStatus.Unavailable(walkId, "지금 서버에서는 함께 보기를 쓸 수 없어요.", retryable = false)
            is SharedWalkResult.NotFound -> SharedWalkDetailStatus.Unavailable(walkId, result.message, retryable = false)
            is SharedWalkResult.Failed -> SharedWalkDetailStatus.Unavailable(walkId, result.message, retryable = true)
        }
    }

    fun closeDetail() {
        detailGeneration++
        detail = SharedWalkDetailStatus.Closed
    }

    /** 전부 버린다 — 계정이 바뀌었을 때. */
    fun forget() {
        listGeneration++
        detailGeneration++
        petId = null
        walks = emptyList()
        nextCursor = null
        status = SharedWalksStatus.Idle
        detail = SharedWalkDetailStatus.Closed
    }
}
