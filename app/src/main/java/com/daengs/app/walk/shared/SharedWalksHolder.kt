package com.daengs.app.walk.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** 공동 보호자 산책 읽기의 상태. */
sealed interface SharedWalksStatus {
    data object Idle : SharedWalksStatus
    data object Loading : SharedWalksStatus
    data object Ready : SharedWalksStatus

    /** 통합 목록 전 서버. 화면은 쓸 수 없다고만 말한다. */
    data object Unsupported : SharedWalksStatus

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
 * 산책 기록 「산책별」에 섞어 보여 줄 **공동 보호자 산책**을 읽기 전용으로 들고 있는 상태.
 *
 * **기기 기록(Room)을 고치지 않는다.** 서버에서 읽은 것을 메모리에만 들고, 내가 올린 산책(`is_mine`)은
 * 기기 기록에 이미 있으니 담지 않는다. 합치기·페이지 나누기는 화면(`unifiedWalkPage`)이 한다.
 *
 * **조건마다 처음부터다.** [ensure] 가 받은 조건이 앞과 다르면 받아 둔 목록·커서·합계를 버린다 — 앞
 * 조건의 산책이 새 조건 화면에 섞이면 안 된다.
 *
 * **한 로그인에 하나다.** 부르는 쪽이 계정(AccountScope)마다 새로 만들고, 기다리는 사이 계정이
 * 바뀌면 [isCurrentAccount] 로 알아채 늦게 온 결과를 버린다.
 */
class SharedWalksHolder(
    private val reader: SharedWalkReader,
    /** 이 계정의 access 토큰. 계정이 바뀌었거나 로그인 정보를 못 받으면 null. */
    private val accessToken: suspend () -> String?,
    private val isCurrentAccount: () -> Boolean,
) {
    var feedQuery: SharedWalkFeedQuery? by mutableStateOf(null)
        private set

    /** 받은 순서(최근 순) 그대로의 공동 보호자 산책. */
    var walks: List<SharedWalk> by mutableStateOf(emptyList())
        private set

    /** 지금 조건 전체의 합계. 첫 페이지를 받기 전이면 null. */
    var totals: SharedWalkTotals? by mutableStateOf(null)
        private set

    var nextCursor: String? by mutableStateOf(null)
        private set

    var status: SharedWalksStatus by mutableStateOf(SharedWalksStatus.Idle)
        private set

    /** 보호자 조건 후보. 조건과 무관하게 볼 수 있는 강아지 전부 기준이다. */
    var carers: List<SharedWalkCarer> by mutableStateOf(emptyList())
        private set

    var detail: SharedWalkDetailStatus by mutableStateOf(SharedWalkDetailStatus.Closed)
        private set

    private var carersLoaded = false
    private var listGeneration = 0
    private var detailGeneration = 0
    private var detailPetId: String? = null

    /** 이 조건으로 [atLeast] 건 이상 받아 두거나, 끝까지 받는다. 실패·미지원이면 [retry] 전까지 멈춘다. */
    suspend fun ensure(query: SharedWalkFeedQuery, atLeast: Int) {
        if (query != feedQuery) {
            listGeneration++
            feedQuery = query
            walks = emptyList()
            totals = null
            nextCursor = null
            status = SharedWalksStatus.Idle
        }
        while (true) {
            if (status is SharedWalksStatus.Failed || status == SharedWalksStatus.Unsupported) return
            val loaded = totals != null
            if (loaded && (walks.size >= atLeast || nextCursor == null)) return
            if (!load(query, if (loaded) nextCursor else null)) return
        }
    }

    /** 실패한 자리부터 다시 — 받아 둔 목록은 지키고 그 커서부터 읽는다. */
    suspend fun retry(query: SharedWalkFeedQuery, atLeast: Int) {
        if (query == feedQuery && status is SharedWalksStatus.Failed) status = SharedWalksStatus.Idle
        ensure(query, atLeast)
    }

    /** 보호자 후보만. 목록을 아직 한 번도 안 받았을 때(검색 조건 등으로 공동 산책을 묻지 않는 동안) 쓴다. */
    suspend fun loadCarers() {
        if (carersLoaded) return
        val token = accessToken() ?: return
        if (!isCurrentAccount()) return forget()
        val result = reader.feed(token, SharedWalkFeedQuery(), cursor = null, limit = 1)
        if (!isCurrentAccount()) return forget()
        if (result is SharedWalkResult.Ready && !carersLoaded) {
            carers = result.value.carers
            carersLoaded = true
        }
    }

    /** 한 페이지를 받는다. 이어서 더 받아도 되면 true. */
    private suspend fun load(query: SharedWalkFeedQuery, cursor: String?): Boolean {
        val generation = ++listGeneration
        status = SharedWalksStatus.Loading
        try {
            val token = accessToken()
            if (generation != listGeneration) return false
            if (!isCurrentAccount()) return false.also { forget() }
            if (token == null) {
                status = SharedWalksStatus.Failed("로그인 정보를 확인해 주세요.")
                return false
            }
            val result = reader.feed(token, query, cursor)
            // 기다리는 사이 조건이 바뀌었거나 계정이 바뀌었으면 늦게 온 답을 버린다.
            if (generation != listGeneration || query != feedQuery) return false
            if (!isCurrentAccount()) return false.also { forget() }
            return when (result) {
                is SharedWalkResult.Ready -> {
                    val known = if (cursor == null) emptyList() else walks
                    val fresh = result.value.walks.filter { walk -> !walk.isMine && known.none { it.id == walk.id } }
                    walks = known + fresh
                    nextCursor = result.value.nextCursor
                    totals = result.value.totals
                    carers = result.value.carers
                    carersLoaded = true
                    status = SharedWalksStatus.Ready
                    // 빈 페이지에 커서만 오는 일은 없지만, 오면 같은 자리를 계속 부르지 않는다.
                    result.value.walks.isNotEmpty()
                }
                SharedWalkResult.Unsupported -> {
                    walks = emptyList()
                    nextCursor = null
                    totals = null
                    status = SharedWalksStatus.Unsupported
                    false
                }
                // 받아 둔 것은 남긴다 — 이어 읽기에서 망이 흔들린 것이면 다시 시도하면 된다.
                is SharedWalkResult.NotFound -> false.also { status = SharedWalksStatus.Failed(result.message) }
                is SharedWalkResult.Failed -> false.also { status = SharedWalksStatus.Failed(result.message) }
            }
        } catch (e: CancellationException) {
            // 조건이 바뀌어 화면이 요청을 거둔 것이다 — 멈춘 채 "불러오는 중" 으로 남기지 않는다.
            if (generation == listGeneration) status = SharedWalksStatus.Idle
            throw e
        }
    }

    /**
     * 한 건과 그 경로. [petId] 는 그 산책이 태그된 **내 화면의 강아지** 하나다 — 서버가 그 강아지로
     * 볼 수 있는 산책인지 다시 확인한다. 볼 수 없으면 서버 문장을 남긴다.
     */
    suspend fun openDetail(petId: String, walkId: String) {
        detailPetId = petId
        val generation = ++detailGeneration
        detail = SharedWalkDetailStatus.Loading(walkId)
        val token = accessToken()
        if (generation != detailGeneration) return
        if (!isCurrentAccount()) return forget()
        if (token == null) {
            detail = SharedWalkDetailStatus.Unavailable(walkId, "로그인 정보를 확인해 주세요.", retryable = true)
            return
        }
        val result = reader.detail(token, petId, walkId)
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

    suspend fun retryDetail() {
        val current = detail as? SharedWalkDetailStatus.Unavailable ?: return
        val pet = detailPetId ?: return
        openDetail(pet, current.walkId)
    }

    fun closeDetail() {
        detailGeneration++
        detail = SharedWalkDetailStatus.Closed
    }

    /** 전부 버린다 — 계정이 바뀌었을 때. */
    fun forget() {
        listGeneration++
        detailGeneration++
        feedQuery = null
        walks = emptyList()
        totals = null
        nextCursor = null
        status = SharedWalksStatus.Idle
        carers = emptyList()
        carersLoaded = false
        detail = SharedWalkDetailStatus.Closed
        detailPetId = null
    }
}
