package com.daengs.app.ui.places

import com.daengs.app.activity.ActivityAuthenticationRequired
import com.daengs.app.activity.ActivitySessionChanged
import com.daengs.app.auth.AccountScope
import com.daengs.app.place.*
import com.daengs.app.place.bookmarks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaceBookmarkState(
    val session: PlaceBrowseSession = PlaceBrowseSession(),
    val phase: PlaceBookmarkPhase = PlaceBookmarkPhase.LOADING,
    val page: SavedPlacePage? = null,
    val hits: List<PlaceSearchHit> = emptyList(),
    val missing: Set<PlaceKey> = emptySet(),
    val distanceAvailable: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val undo: PlaceKey? = null,
    val errorText: String? = null,
)

/** Account lifetime + generation fence; writes serialize and always reconcile after uncertainty. */
class PlaceBookmarkController(private val scope: CoroutineScope,
    private val repository: PlaceBookmarkRepository, private val account: AccountScope) {
    private val mutable = MutableStateFlow(PlaceBookmarkState())
    val state = mutable.asStateFlow()
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var generation = 0
    private var started = false
    private var closed = false
    private var dogs = emptyList<PlaceDogSnapshot>()
    fun ensureLoaded() { if (!started) { started = true; refresh() } }
    fun enter(search: PlaceBrowseSnapshot, selectedDogs: List<PlaceDogSnapshot>) {
        val session = state.value.session.copy(search = search).select(PlaceBrowseTab.BOOKMARKS)
        mutable.value = state.value.copy(session = session)
        dogs = selectedDogs
        refresh()
    }
    fun returnToSearch() {
        mutable.value = state.value.copy(session = state.value.session.select(PlaceBrowseTab.SEARCH))
        refresh()
    }
    fun updateSnapshot(update: (PlaceBrowseSnapshot) -> PlaceBrowseSnapshot) {
        mutable.value = state.value.copy(session = state.value.session.updateCurrent(update))
    }
    fun filters(change: (PlaceBrowseFilters) -> PlaceBrowseFilters) {
        updateSnapshot { it.copy(filters = change(it.filters), selected = null, detail = null) }
        refresh()
    }
    fun all() {
        mutable.value = state.value.copy(session = state.value.session.showAllBookmarks())
        refresh()
    }
    fun updateDogs(value: List<PlaceDogSnapshot>) {
        if (dogs != value) { dogs = value; if (state.value.session.tab == PlaceBrowseTab.BOOKMARKS) refresh() }
    }
    fun dismissMessage() { mutable.value = state.value.copy(message = null, undo = null) }
    fun notice(message: String) { mutable.value = state.value.copy(message = message, undo = null) }
    fun refresh() {
        if (closed) return
        val id = ++generation
        readJob?.cancel()
        mutable.value = state.value.copy(phase = PlaceBookmarkPhase.LOADING, hits = emptyList(), missing = emptySet(), errorText = null)
        if (writeJob?.isActive == true) return
        readJob = scope.launch {
            try {
                if (state.value.session.tab == PlaceBrowseTab.BOOKMARKS) {
                    val result = repository.search(account, state.value.session.current.filters.savedQuery(dogs))
                    if (id == generation && !closed) mutable.value = state.value.copy(
                        phase = PlaceBookmarkPhase.READY, page = result.page, hits = result.hits,
                        missing = result.missing, distanceAvailable = result.distanceAvailable)
                } else {
                    val page = repository.list(account)
                    if (id == generation && !closed) mutable.value = state.value.copy(phase = PlaceBookmarkPhase.READY, page = page)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (id == generation && !closed) mutable.value = state.value.copy(phase = PlaceBookmarkPhase.FAILED,
                    hits = emptyList(), message = error.savedMessage(), errorText = error.savedMessage(), undo = null)
            }
        }
    }
    fun toggle(key: PlaceKey) {
        if (state.value.busy || state.value.phase != PlaceBookmarkPhase.READY || state.value.page == null) {
            if (!state.value.busy) { mutable.value = state.value.copy(message = "찜 상태를 확인한 뒤 다시 눌러 주세요."); refresh() }
            return
        }
        set(key, state.value.page!!.items.none { it.key == key })
    }
    fun undo() { state.value.undo?.let { set(it, true) } }
    private fun set(key: PlaceKey, saved: Boolean) {
        if (closed || state.value.busy) return
        generation++; readJob?.cancel()
        mutable.value = state.value.copy(busy = true, message = null, undo = null)
        writeJob = scope.launch {
            var message: String
            var undo: PlaceKey? = null
            try {
                val page = repository.set(account, key, saved)
                if (closed) return@launch
                mutable.value = state.value.copy(page = page,
                    hits = if (saved || state.value.session.tab == PlaceBrowseTab.SEARCH) state.value.hits
                        else state.value.hits.filterNot { it.place.key == key })
                message = if (saved) "찜한 시설에 담았어요." else "찜을 해제했어요."
                if (!saved) undo = key
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { message = error.savedMessage() }
            if (!closed) {
                mutable.value = state.value.copy(busy = false, message = message, undo = undo)
                writeJob = null
                refresh() // A timed-out write may have committed. Read before allowing another toggle.
            }
        }
    }
    fun close() { closed = true; generation++; readJob?.cancel(); writeJob?.cancel() }
}

private fun Throwable.savedMessage(): String = when {
    this is ActivityAuthenticationRequired || this is ActivitySessionChanged || this is PlaceBookmarkException && status == 401 -> "로그인 후 찜한 시설을 확인할 수 있어요."
    this is PlaceBookmarkException && code == "place_bookmark_limit" -> "찜은 최대 200곳까지 저장할 수 있어요. 기존 찜을 해제해 주세요."
    this is PlaceBookmarkException && code == "place_not_found" -> "현재 정보를 찾을 수 없는 시설이에요."
    this is PlaceBookmarkException && code == "invalid_bookmark_filters" -> "찜 검색 조건을 확인해 주세요. 전체 찜 보기로 다시 시작할 수 있어요."
    this is PlaceBookmarkException && status == 404 -> "시설 찜 기능을 준비하고 있어요. 잠시 후 다시 확인해 주세요."
    else -> "찜을 확인하지 못했어요. 연결을 확인하고 다시 불러와 주세요."
}
