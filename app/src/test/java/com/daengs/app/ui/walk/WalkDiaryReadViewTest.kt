package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.detail.PreparedDiaryRoute
import com.daengs.app.ui.walk.detail.DiaryReadUpdate
import com.daengs.app.ui.walk.detail.walkDiaryReadUpdates

import com.daengs.app.auth.AccountScope
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkDiaryReadViewTest {
    private fun detail(offset: Double = 0.0) = readCompletedRoute(
        RecordedSession("read-view", startedAtMillis = 0, endedAtMillis = 100_000),
        (0..17).map { i -> RecordedFix(i, if (i < 9) 0 else 1,
            if (i < 9) 10_000 + i * 2_000L else 60_000 + (i - 9) * 2_000L,
            37.5, 127.0 + (offset + i * 4.0) / 88_000, 1f, false) })
    private fun diary(detail: WalkSessionDetail, text: String) = DiaryWalk(detail.summary,
        listOf(DiaryScene("read-view/scene", "read-view", 20_000, text, text, detail.route.points.first().point, "")), "")
    private fun List<DiaryReadUpdate>.ready() = filterIsInstance<DiaryReadUpdate.Ready>().map { it.view }

    @Test fun `old view stays ready until new route and indexes are prepared then only matching scenes attach`() = runTest {
        val first = detail(); val next = detail(1_000.0)
        var input = first
        val changes = MutableStateFlow(0); val updates = mutableListOf<DiaryReadUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            walkDiaryReadUpdates(changes, load = { input }, observe = { d -> flow {
                if (d === next) delay(500)
                emit(diary(d, if (d === first) "first" else "next"))
            } }, dispatcher = StandardTestDispatcher(testScheduler), prepare = {
                if (it === next) delay(1_000)
                PreparedDiaryRoute(it)
            }).collect { updates += it }
        }
        runCurrent(); val previous = updates.ready().last()
        input = next; changes.value++; runCurrent(); advanceTimeBy(999); runCurrent()
        assertSame(previous, updates.ready().last())
        advanceTimeBy(1); runCurrent()
        val pending = updates.ready().last()
        assertSame(next, pending.route.detail); assertSame(next, pending.route.review.detail)
        assertNull(pending.diary); assertTrue(pending.scenesLoading)
        advanceTimeBy(500); runCurrent()
        val ready = updates.ready().last()
        assertSame(pending.route, ready.route); assertEquals("next", ready.diary!!.scenes.single().title)
        assertEquals(setOf("read-view/scene"), ready.sceneFocus.keys)
    }

    @Test fun `same input reread preserves prepared route and does not flash scene loading`() = runTest {
        var input = detail(); var preparations = 0
        val changes = MutableStateFlow(0); val updates = mutableListOf<DiaryReadUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            walkDiaryReadUpdates(changes, load = { input }, observe = { flowOf(diary(it, "scene")) },
                dispatcher = StandardTestDispatcher(testScheduler), prepare = { preparations++; PreparedDiaryRoute(it) })
                .collect { updates += it }
        }
        runCurrent(); val previous = updates.ready().last()
        input = detail(); changes.value++; runCurrent()
        assertEquals(1, preparations)
        assertSame(previous.route, updates.ready().last().route)
        assertEquals(1, updates.ready().count { it.scenesLoading })
    }

    @Test fun `late scene revision cannot overwrite a newer scene on the same route`() = runTest {
        val detail = detail(); val scenes = MutableStateFlow(diary(detail, "initial"))
        val updates = mutableListOf<DiaryReadUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            walkDiaryReadUpdates(flowOf(Unit), load = { detail }, observe = { scenes },
                dispatcher = StandardTestDispatcher(testScheduler), bind = { route, value ->
                    if (value.scenes.single().title == "slow-old") delay(1_000)
                    value.scenes.associate { it.id to route.review.recordSceneFocus(it) }
                }).collect { updates += it }
        }
        runCurrent(); val route = updates.ready().last().route
        scenes.value = diary(detail, "slow-old"); runCurrent(); advanceTimeBy(10)
        scenes.value = diary(detail, "new"); runCurrent(); advanceTimeBy(2_000); runCurrent()
        assertEquals("new", updates.ready().last().diary!!.scenes.single().title)
        assertTrue(updates.ready().none { it.diary?.scenes?.single()?.title == "slow-old" })
        assertSame(route, updates.ready().last().route)
    }

    @Test fun `superseded preparation and the previous login generation never publish`() = runTest {
        val first = detail(); val second = detail(100.0); val third = detail(200.0)
        var input = first; val changes = MutableStateFlow(0); val updates = mutableListOf<DiaryReadUpdate>()
        val expected = AccountScope("same-owner", 1); var account = expected
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            walkDiaryReadUpdates(changes, load = { input }, observe = { flowOf(diary(it, "scene")) },
                isCurrentAccount = { account == expected }, dispatcher = StandardTestDispatcher(testScheduler),
                prepare = { if (it !== first) delay(1_000); PreparedDiaryRoute(it) }).collect { updates += it }
        }
        runCurrent()
        input = second; changes.value++; runCurrent()
        input = third; changes.value++; runCurrent()
        account = AccountScope("same-owner", 2)
        advanceTimeBy(2_000); runCurrent()
        assertTrue(updates.ready().all { it.route.detail === first })
    }

    @Test fun `scene failure retains route and a missing record never restores earlier scenes`() = runTest {
        val detail = detail(); val updates = mutableListOf<DiaryReadUpdate>()
        walkDiaryReadUpdates(flowOf(Unit), load = { detail }, observe = { flow<DiaryWalk?> { error("scene failure") } },
            dispatcher = StandardTestDispatcher(testScheduler)).toList(updates)
        assertFalse(updates.ready().last().scenesLoading)
        assertSame(detail, updates.ready().last().route.detail)
        assertTrue(updates.last() is DiaryReadUpdate.Failed)
        updates.clear()
        walkDiaryReadUpdates(flowOf(Unit), load = { detail }, observe = { flow {
            emit(diary(detail, "before deletion")); emit(null); error("after deletion")
        } }, dispatcher = StandardTestDispatcher(testScheduler)).toList(updates)
        val deleted = updates.indexOf(DiaryReadUpdate.Missing)
        assertTrue(deleted >= 0)
        assertTrue(updates.drop(deleted + 1).none { it is DiaryReadUpdate.Ready })
    }

    @Test fun `scene bindings use the entries carried by that same diary revision`() = runTest {
        val detail = detail(); val point = detail.route.points.last()
        val entry = WalkEntry(id = "note", sessionId = "read-view", type = WalkMomentType.NOTE,
            recordedAtMillis = point.capturedAtMillis, locationCapturedAtMillis = point.capturedAtMillis, point = point.point)
        val scene = DiaryScene("read-view/note", "read-view", point.capturedAtMillis, "메모", "함께 걸었다", point.point, "", entryId = entry.id)
        val value = DiaryWalk(detail.summary, listOf(scene), "", sourceEntries = listOf(entry))
        val updates = walkDiaryReadUpdates(flowOf(Unit), load = { detail }, observe = { flowOf(value) },
            dispatcher = StandardTestDispatcher(testScheduler)).toList()
        assertEquals(com.daengs.app.walk.routeexplorer.SceneRouteRelation.CONNECTED,
            updates.ready().last().sceneFocus.getValue(scene.id).relation)
    }
}
