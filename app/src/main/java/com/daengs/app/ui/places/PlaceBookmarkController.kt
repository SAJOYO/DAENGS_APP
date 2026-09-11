package com.daengs.app.ui.places

import com.daengs.app.activity.ActivityAuthenticationRequired
import com.daengs.app.activity.ActivitySessionChanged
import com.daengs.app.auth.AccountScope
import com.daengs.app.place.*
import com.daengs.app.place.bookmarks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

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
    val aiBusy: Boolean = false,
    val aiAnswer: String? = null,
)

/** Account lifetime + generation fence; writes serialize and always reconcile after uncertainty. */
class PlaceBookmarkController(private val scope: CoroutineScope,
    private val repository: PlaceBookmarkRepository, private val account: AccountScope) {
    private val mutable = MutableStateFlow(PlaceBookmarkState())
    val state = mutable.asStateFlow()
    private var readJob: Job? = null
    private val writes = Mutex()
    private val intentVersions = mutableMapOf<PlaceKey, Long>()
    private var intentClock = 0L
    private val commands = linkedMapOf<String, Deferred<BookmarkOutcome>>()
    private val activeWrites = mutableSetOf<Deferred<BookmarkOutcome>>()
    private var generation = 0
    private var started = false
    private var closed = false
    private var dogs = emptyList<PlaceDogSnapshot>()
    private var searchDogs = emptyList<PlaceDogSnapshot>()
    private var aiJob: Job? = null
    private var aiGeneration = 0
    fun ensureLoaded() { if (!started) { started = true; refresh() } }
    fun enter(search: PlaceBrowseSnapshot, selectedDogs: List<PlaceDogSnapshot>) {
        val first = state.value.session.bookmarks == null
        val session = state.value.session.copy(search = search).select(PlaceBrowseTab.BOOKMARKS)
        mutable.value = state.value.copy(session = session)
        searchDogs = selectedDogs
        if (first) dogs = selectedDogs
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
    fun copySearchConditions() {
        mutable.value = state.value.copy(session = state.value.session.copySearchToBookmarks())
        dogs = searchDogs
        refresh()
    }
    fun cancelConversation() {
        aiGeneration++; aiJob?.cancel()
        mutable.value = state.value.copy(aiBusy = false)
    }
    fun chat(query: String) {
        if (closed || query.isBlank() || query.length > 1000 || state.value.session.tab != PlaceBrowseTab.BOOKMARKS) return
        cancelConversation()
        val mine = aiGeneration
        val view = generation
        val before = state.value.session.current.filters
        val selectedDogs = dogs.toList()
        fun current() = !closed && mine == aiGeneration && view == generation && state.value.session.tab == PlaceBrowseTab.BOOKMARKS
        mutable.value = state.value.copy(aiBusy = true, aiAnswer = null)
        aiJob = scope.launch {
            try {
                val plan = repository.interpret(account, query, before.savedQuery(selectedDogs))
                if (!current()) return@launch
                when (plan.action) {
                    "search" -> {
                        val candidate = before.withSavedPlan(requireNotNull(plan.filters), selectedDogs)
                        val result = repository.search(account, candidate.savedQuery(selectedDogs))
                        if (!current()) return@launch
                        publishSaved(candidate, result)
                    }
                    "return_search" -> returnToSearch()
                    else -> mutable.value = state.value.copy(aiAnswer = plan.message)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (current()) mutable.value = state.value.copy(aiAnswer = error.savedMessage())
            } finally {
                if (mine == aiGeneration) mutable.value = state.value.copy(aiBusy = false)
            }
        }
    }
    private fun publishSaved(filters: PlaceBrowseFilters, result: SavedPlaceResults, search: PlaceBrowseSnapshot? = null) {
        generation++; readJob?.cancel()
        val session = if (search == null) state.value.session else state.value.session.copy(search = search).select(PlaceBrowseTab.BOOKMARKS)
        val previous = session.current
        mutable.value = state.value.copy(session = session.updateCurrent { it.copy(filters = filters, draft = filters.name,
            selected = previous.selected.takeIf { key -> result.hits.any { it.place.key == key } }, detail = null) },
            phase = PlaceBookmarkPhase.READY, page = result.page, hits = result.hits, missing = result.missing,
            distanceAvailable = result.distanceAvailable, errorText = null,
            aiAnswer = "찜한 시설 안에서 조건에 맞는 ${result.hits.size}곳을 찾았어요.")
    }
    fun updateDogs(value: List<PlaceDogSnapshot>) {
        if (dogs != value) { dogs = value; if (state.value.session.tab == PlaceBrowseTab.BOOKMARKS) refresh() }
    }
    fun dismissMessage() { mutable.value = state.value.copy(message = null, undo = null) }
    fun notice(message: String) { mutable.value = state.value.copy(message = message, undo = null) }
    fun refresh() {
        if (closed) return
        cancelConversation()
        val id = ++generation
        readJob?.cancel()
        mutable.value = state.value.copy(phase = PlaceBookmarkPhase.LOADING, hits = emptyList(), missing = emptySet(), errorText = null)
        if (state.value.busy) return
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
    /** Direct UI intent gets its own sequence; a read refresh never advances it. */
    fun set(key: PlaceKey, saved: Boolean): Deferred<BookmarkOutcome> {
        val version = ++intentClock
        intentVersions[key] = version
        return submit(key, saved) { intentVersions[key] == version }
    }

    fun captureTurn(search: PlaceBrowseSnapshot? = null, selectedDogs: List<PlaceDogSnapshot> = emptyList()): BookmarkTurn {
        val turn = ++intentClock
        val searchView = generation
        return object : BookmarkTurn {
            var cancelled = false
            override fun cancel() { cancelled = true }
            override val supportsSearch = search != null
            override suspend fun search(filters: JsonObject, isCurrent: () -> Boolean): String {
                fun current() = !closed && !cancelled && searchView == generation && isCurrent()
                if (!current()) throw CancellationException("Saved workspace changed")
                val snapshot = requireNotNull(search)
                val candidate = snapshot.filters.withSavedPlan(filters, selectedDogs)
                val result = repository.search(account, candidate.savedQuery(selectedDogs))
                if (!current()) throw CancellationException("Saved workspace changed")
                dogs = selectedDogs; searchDogs = selectedDogs
                publishSaved(candidate, result, snapshot)
                return state.value.aiAnswer.orEmpty()
            }
            override suspend fun execute(requestId: String, key: PlaceKey, saved: Boolean): BookmarkOutcome {
                if (closed || cancelled || (intentVersions[key] ?: 0) > turn) return superseded()
                intentVersions[key] = turn
                val command = commands[requestId] ?: submit(key, saved) {
                    !cancelled && intentVersions[key] == turn
                }.also {
                    commands[requestId] = it
                    while (commands.size > 128 && commands.values.first().isCompleted) commands.remove(commands.keys.first())
                }
                return command.await()
            }
        }
    }

    private fun superseded() = BookmarkOutcome(BookmarkCompletion.SUPERSEDED,
        "이전 찜 요청은 더 진행하지 않아요. 현재 찜 상태를 확인해 주세요.")

    private fun submit(key: PlaceKey, saved: Boolean, current: () -> Boolean): Deferred<BookmarkOutcome> =
        scope.async(start = CoroutineStart.UNDISPATCHED) {
            writes.withLock {
                if (closed || !current()) return@withLock superseded()
                generation++; readJob?.cancel()
                mutable.value = state.value.copy(busy = true, message = null, undo = null)
                var outcome: BookmarkOutcome
                try {
                    val page = repository.set(account, key, saved)
                    if (closed) return@withLock superseded()
                    mutable.value = state.value.copy(page = page,
                        hits = if (saved || state.value.session.tab == PlaceBrowseTab.SEARCH) state.value.hits
                            else state.value.hits.filterNot { it.place.key == key })
                    outcome = if (page.items.any { it.key == key } == saved)
                        BookmarkOutcome(BookmarkCompletion.CONFIRMED,
                            if (saved) "찜에 저장된 것을 확인했어요." else "찜이 해제된 것을 확인했어요.")
                    else BookmarkOutcome(BookmarkCompletion.UNKNOWN,
                        "요청한 찜 상태를 확인하지 못했어요. 찜 목록을 새로고침해 주세요.")
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    // Transport failure does not prove rollback. Reconcile through the ordinary list API.
                    mutable.value = state.value.copy(phase = PlaceBookmarkPhase.LOADING)
                    outcome = try {
                        val page = repository.list(account)
                        if (closed) return@withLock superseded()
                        mutable.value = state.value.copy(page = page)
                        if (page.items.any { it.key == key } == saved)
                            BookmarkOutcome(BookmarkCompletion.CONFIRMED,
                                if (saved) "현재 찜에 저장돼 있어요." else "현재 찜에서 해제돼 있어요.")
                        else if (error is PlaceBookmarkException && error.status in listOf(401, 404, 409, 422, 503))
                            BookmarkOutcome(BookmarkCompletion.FAILED, error.savedMessage())
                        else BookmarkOutcome(BookmarkCompletion.UNKNOWN,
                            "아직 요청한 찜 상태를 확인하지 못했어요. 찜 목록을 새로고침해 주세요.")
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) {
                        BookmarkOutcome(BookmarkCompletion.UNKNOWN, "찜 처리 결과를 확인하지 못했어요. 찜 목록을 새로고침해 주세요.")
                    }
                } finally {
                    if (!closed) mutable.value = state.value.copy(busy = false)
                }
                if (closed) return@withLock superseded()
                mutable.value = state.value.copy(message = outcome.message,
                    undo = key.takeIf { !saved && outcome.completion == BookmarkCompletion.CONFIRMED })
                refresh()
                // A newer queued intent must not be announced as this command's success.
                if (!current()) superseded() else outcome
            }
        }.also { job ->
            activeWrites += job
            job.invokeOnCompletion { activeWrites -= job }
        }
    fun close() {
        cancelConversation()
        closed = true; generation++; readJob?.cancel()
        activeWrites.toList().forEach { it.cancel() }
    }
}

private fun Throwable.savedMessage(): String = when {
    this is ActivityAuthenticationRequired || this is ActivitySessionChanged || this is PlaceBookmarkException && status == 401 -> "로그인 후 찜한 시설을 확인할 수 있어요."
    this is PlaceBookmarkException && code == "place_bookmark_limit" -> "찜은 최대 200곳까지 저장할 수 있어요. 기존 찜을 해제해 주세요."
    this is PlaceBookmarkException && code == "place_not_found" -> "현재 정보를 찾을 수 없는 시설이에요."
    this is PlaceBookmarkException && code == "invalid_bookmark_filters" -> "찜 검색 조건을 확인해 주세요. 전체 찜 보기로 다시 시작할 수 있어요."
    this is PlaceBookmarkException && status == 404 -> "시설 찜 기능을 준비하고 있어요. 잠시 후 다시 확인해 주세요."
    else -> "찜을 확인하지 못했어요. 연결을 확인하고 다시 불러와 주세요."
}
