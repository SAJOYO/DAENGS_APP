package com.daengs.app.ui.walk

import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.diary.DiaryWalk
import com.daengs.app.walk.routeexplorer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/** All route consumers share this prepared read, including its original observation identities. */
internal class PreparedDiaryRoute(val detail: WalkSessionDetail,
    val review: CompletedRouteReview = CompletedRouteReview(detail),
    val index: RouteExplorerIndex = RouteExplorerIndex(detail.route))

internal data class WalkDiaryReadView(val route: PreparedDiaryRoute, val diary: DiaryWalk?,
    val sceneFocus: Map<String, SceneRouteFocus> = emptyMap(), val scenesLoading: Boolean = false,
    val revisionKey: String = UUID.randomUUID().toString())

internal sealed interface DiaryReadUpdate {
    data class Ready(val view: WalkDiaryReadView) : DiaryReadUpdate
    data object Missing : DiaryReadUpdate
    data class Failed(val message: String) : DiaryReadUpdate
}

/**
 * Hold the previous ready view while preparing a new route. Each nested scene stream belongs to
 * that exact read. mapLatest/transformLatest cancel stale route and scene revisions before emit;
 * the account-generation guard also rejects work from a previous login, including the same owner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> walkDiaryReadUpdates(changes: Flow<T>, load: suspend () -> WalkSessionDetail?,
    observe: (WalkSessionDetail) -> Flow<DiaryWalk?>, isCurrentAccount: () -> Boolean = { true },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    prepare: suspend (WalkSessionDetail) -> PreparedDiaryRoute = { PreparedDiaryRoute(it) },
    bind: suspend (PreparedDiaryRoute, DiaryWalk) -> Map<String, SceneRouteFocus> = { route, diary ->
        val entries = diary.sourceEntries.associateBy { it.id }
        diary.scenes.associate { it.id to route.review.recordSceneFocus(it, entries[it.entryId]) }
    },
): Flow<DiaryReadUpdate> = flow {
    var previousRoute: PreparedDiaryRoute? = null
    var previousView: WalkDiaryReadView? = null
    emitAll(changes.transformLatest {
        try {
            val detail = load()
            if (!isCurrentAccount()) return@transformLatest
            if (detail == null) {
                previousRoute = null; previousView = null
                emit(DiaryReadUpdate.Missing); return@transformLatest
            }
            val reused = previousRoute?.takeIf { sameDiaryRouteInput(it.detail, detail) }
            val route = reused ?: withContext(dispatcher) { prepare(detail) }.also {
                require(it.detail === detail && it.review.detail === detail)
            }
            if (!isCurrentAccount()) return@transformLatest
            var latest = previousView?.takeIf { it.route === route }
            if (latest == null) {
                latest = WalkDiaryReadView(route, null, scenesLoading = true)
                emit(DiaryReadUpdate.Ready(latest))
            }
            previousRoute = route; previousView = latest
            var sceneRevision = 0L
            var sourceMissing = false
            try {
                observe(route.detail).map { diary ->
                    sourceMissing = diary == null
                    ++sceneRevision to diary
                }.mapLatest { (revision, diary) ->
                    revision to if (diary == null) null else {
                        require(diary.summary == route.detail.summary && diary.scenes.all { it.sessionId == route.detail.summary.sessionId })
                        withContext(dispatcher) { WalkDiaryReadView(route, diary, bind(route, diary), diary.preparing) }
                    }
                }.collect { (revision, view) ->
                    if (revision == sceneRevision && isCurrentAccount()) {
                        if (view == null) {
                            latest = null; previousRoute = null; previousView = null
                            emit(DiaryReadUpdate.Missing)
                        } else { latest = view; previousRoute = route; previousView = view; emit(DiaryReadUpdate.Ready(view)) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (isCurrentAccount()) {
                    if (sourceMissing) {
                        previousRoute = null; previousView = null
                        emit(DiaryReadUpdate.Missing)
                    } else latest?.let { emit(DiaryReadUpdate.Ready(it.copy(scenesLoading = false))) }
                    emit(DiaryReadUpdate.Failed("장면을 불러오지 못했어요. 다시 시도해 주세요."))
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (isCurrentAccount()) emit(DiaryReadUpdate.Failed("산책 기록을 불러오지 못했어요. 다시 시도해 주세요."))
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        if (isCurrentAccount()) emit(DiaryReadUpdate.Failed("산책 기록을 불러오지 못했어요. 다시 시도해 주세요."))
    })
}

/** Reuse the entire old read, never attach its provenance to a different summary instance. */
private fun sameDiaryRouteInput(a: WalkSessionDetail, b: WalkSessionDetail) =
    a.summary == b.summary && a.route == b.route && a.observations == b.observations &&
        a.moments == b.moments && a.stayStamps == b.stayStamps &&
        a.legacyRouteEvidence?.readerVersion == b.legacyRouteEvidence?.readerVersion &&
        a.legacyRouteEvidence?.matches(a) == b.legacyRouteEvidence?.matches(b)

/** Caller updates its read view in the same Compose snapshot as this selection state. */
internal fun WalkRouteExplorerState.adopt(view: WalkDiaryReadView) {
    if (review !== view.route.review) replaceRoute(view.route.index, view.route.review, view.route.detail.summary.activeDurationMillis)
    if (!view.scenesLoading && selectedSceneId != null && view.diary?.scenes.orEmpty().none { it.id == selectedSceneId }) overview()
}
