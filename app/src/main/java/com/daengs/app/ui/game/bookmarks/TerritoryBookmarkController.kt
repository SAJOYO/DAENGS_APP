package com.daengs.app.ui.game.bookmarks

import com.daengs.app.activity.ActivityAuthenticationRequired
import com.daengs.app.activity.ActivitySessionChanged
import com.daengs.app.auth.AccountScope
import com.daengs.app.territory.bookmarks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BookmarkStatus { INITIAL, LOADING, READY, ERROR, SIGN_IN }
internal data class BookmarkState(
    val status: BookmarkStatus = BookmarkStatus.INITIAL,
    val items: List<TerritoryBookmark> = emptyList(),
    val limit: Int? = null,
    val busySite: String? = null,
    val message: String? = null,
) {
    fun saved(id: String) = items.any { it.siteId == id }
    val busy get() = status == BookmarkStatus.LOADING || busySite != null
}

/** One serialized operation for all three surfaces. A write always reconciles with a fresh list. */
internal class TerritoryBookmarkController(
    private val scope: CoroutineScope, private val repository: TerritoryBookmarkRepository,
    private val account: AccountScope,
) {
    private val mutableState = MutableStateFlow(BookmarkState(
        if (account.ownerId == null) BookmarkStatus.SIGN_IN else BookmarkStatus.INITIAL))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    fun ensureLoaded() { if (state.value.status == BookmarkStatus.INITIAL) refresh() }
    fun dismissMessage() { mutableState.value = state.value.copy(message = null) }
    fun refresh() {
        if (account.ownerId == null || job?.isActive == true) return
        mutableState.value = state.value.copy(status = BookmarkStatus.LOADING, message = null)
        job = scope.launch { read() }
    }
    fun toggle(id: String) {
        if (state.value.status != BookmarkStatus.READY || job?.isActive == true) return
        val saved = !state.value.saved(id)
        mutableState.value = state.value.copy(busySite = id, message = null)
        job = scope.launch {
            val result = repository.set(account, id, saved)
            // A timeout may have committed on the server. Do not invert a stale star on retry.
            read(result.exceptionOrNull()?.bookmarkMessage())
        }
    }
    private suspend fun read(writeMessage: String? = null) {
        val result = repository.list(account)
        result.fold(onSuccess = { page ->
            mutableState.value = BookmarkState(BookmarkStatus.READY, page.items, page.limit, message = writeMessage)
        }, onFailure = { error ->
            mutableState.value = BookmarkState(if (error.requiresLogin()) BookmarkStatus.SIGN_IN else BookmarkStatus.ERROR,
                message = writeMessage ?: error.bookmarkMessage())
        })
    }
    fun close() { job?.cancel() }
}
internal fun Throwable.requiresLogin() = this is ActivityAuthenticationRequired || this is ActivitySessionChanged ||
    this is BookmarkHttpException && status == 401
internal fun Throwable.bookmarkMessage(): String = when {
    requiresLogin() -> "다시 로그인해 주세요."
    this is BookmarkHttpException && code == "bookmark_limit_reached" ->
        (limit?.let { "북마크는 최대 ${it}개까지 저장할 수 있어요." } ?: "북마크 한도에 도달했어요.") +
            " 기존 북마크를 해제해 주세요."
    this is BookmarkHttpException && code == "territory_site_not_found" -> "더 이상 찾을 수 없는 전봇대예요."
    this is BookmarkHttpException && code == "territory_sites_unavailable" -> "전봇대 정보를 잠시 불러올 수 없어요. 다시 시도해 주세요."
    this is BookmarkHttpException && status == 404 -> "북마크 기능을 준비하고 있어요. 잠시 후 다시 확인해 주세요."
    else -> "북마크를 확인하지 못했어요. 연결을 확인하고 다시 불러와 주세요."
}
