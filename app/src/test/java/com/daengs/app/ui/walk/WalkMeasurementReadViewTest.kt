package com.daengs.app.ui.walk

import com.daengs.app.auth.AccountScope
import com.daengs.app.walk.WalkSessionDetail
import com.daengs.app.walk.diary.DiaryWalk
import com.daengs.app.walk.routeexplorer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkMeasurementReadViewTest {
    private fun diary(detail: WalkSessionDetail, title: String = "장면", seq: Int = 2) =
        DiaryWalk(detail.summary, listOf(measuredScene(detail, seq, title)), "")
    private fun List<DiaryReadUpdate>.ready() = filterIsInstance<DiaryReadUpdate.Ready>().map { it.view }
    private fun bind(route: PreparedDiaryRoute, diary: DiaryWalk) = diary.scenes.associate {
        it.id to route.review.recordSceneFocus(it, diary.sourceEntries.singleOrNull { entry -> entry.id == it.entryId })
    }

    @Test fun `new measurement waits for matching scene bindings before atomic publication`() = runTest {
        val first = measuredSceneDetail(); val next = measuredSceneDetail("measurement-b")
        var input = first; val changes = MutableStateFlow(0); val updates = mutableListOf<DiaryReadUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            walkDiaryReadUpdates(changes, { input }, { d -> flow {
                if (d === next) delay(500)
                emit(diary(d))
            } }, dispatcher = StandardTestDispatcher(testScheduler), prepare = { d ->
                if (d === next) delay(100)
                PreparedDiaryRoute(d)
            }, bind = { route, diary -> if (route.detail === next) delay(200); bind(route, diary) })
                .collect { updates += it }
        }
        runCurrent(); val before = updates.ready().last(); val scene = before.diary!!.scenes.single()
        assertNotNull(before.focusFor(scene))
        input = next; changes.value++; runCurrent(); advanceTimeBy(799); runCurrent()
        assertSame(before, updates.ready().last())
        advanceTimeBy(1); runCurrent()
        val after = updates.ready().last()
        assertSame(next, after.route.detail)
        assertEquals("measurement-b", after.focusFor(after.diary!!.scenes.single())!!.key!!.measurementId)
        assertTrue(updates.ready().filter { it.route.detail === next }.all { it.diary != null && it.sceneFocus.isNotEmpty() })
    }

    @Test fun `changed scene and borrowed measurement binding cannot authorize navigation`() = runTest {
        val detail = measuredSceneDetail(); val value = diary(detail)
        val route = PreparedDiaryRoute(detail); val first = WalkDiaryReadView(route, value, bind(route, value))
        val original = value.scenes.single(); val edited = original.copy(title = "수정한 제목", body = "사용자가 고친 글")
        val newDiary = value.copy(scenes = listOf(edited))
        val stale = first.copy(diary = newDiary)
        assertFalse(stale.acceptsScene(original)); assertFalse(stale.acceptsScene(edited))
        val ready = stale.copy(sceneFocus = bind(route, newDiary))
        assertTrue(ready.acceptsScene(edited)); assertFalse(ready.acceptsScene(original))
        val next = measuredSceneDetail("measurement-b")
        assertFalse(first.copy(route = PreparedDiaryRoute(next)).acceptsScene(original))
        assertEquals("사용자가 고친 글", ready.diary!!.scenes.single().body)
    }

    @Test fun `scene edits publish together with their own revision and preserve the prepared measurement`() = runTest {
        val detail = measuredSceneDetail(); val scenes = MutableStateFlow(diary(detail))
        val updates = mutableListOf<DiaryReadUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            walkDiaryReadUpdates(flowOf(Unit), { detail }, { scenes }, dispatcher = StandardTestDispatcher(testScheduler),
                bind = { route, value -> if (value.scenes.single().title == "slow") delay(1_000); bind(route, value) })
                .collect { updates += it }
        }
        runCurrent(); val first = updates.ready().last()
        scenes.value = diary(detail, "slow"); runCurrent()
        scenes.value = diary(detail, "최종 수정", 3); runCurrent(); advanceTimeBy(2_000); runCurrent()
        val last = updates.ready().last(); val scene = last.diary!!.scenes.single()
        assertSame(first.route, last.route)
        assertEquals("최종 수정", scene.title)
        assertEquals(3, last.focusFor(scene)!!.binding!!.locationSource!!.clientSeq)
        assertTrue(updates.ready().none { it.diary?.scenes?.single()?.title == "slow" })
    }

    @Test fun `failed binding keeps the prior complete read and later deletion clears it`() = runTest {
        var detail: WalkSessionDetail? = measuredSceneDetail()
        val changes = MutableStateFlow(0); val updates = mutableListOf<DiaryReadUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            walkDiaryReadUpdates(changes, { detail }, { flowOf(diary(it)) }, dispatcher = StandardTestDispatcher(testScheduler),
                bind = { route, value -> if (route.detail.measurement!!.id == "bad") emptyMap() else bind(route, value) })
                .collect { updates += it }
        }
        runCurrent(); val before = updates.ready().last()
        detail = measuredSceneDetail("bad"); changes.value++; runCurrent()
        assertSame(before.route, updates.ready().last().route)
        assertTrue(updates.last() is DiaryReadUpdate.Failed)
        detail = null; changes.value++; runCurrent()
        assertEquals(DiaryReadUpdate.Missing, updates.last())
    }

    @Test fun `late same owner login generation and superseded measurement never publish`() = runTest {
        var detail = measuredSceneDetail(); var account = AccountScope("owner", 1); val expected = account
        val changes = MutableStateFlow(0); val updates = mutableListOf<DiaryReadUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            walkDiaryReadUpdates(changes, { detail }, { flowOf(diary(it)) }, isCurrentAccount = { account == expected },
                dispatcher = StandardTestDispatcher(testScheduler), bind = { route, value ->
                    if (route.detail.measurement!!.id != "measurement-a") delay(1_000)
                    bind(route, value)
                }).collect { updates += it }
        }
        runCurrent(); detail = measuredSceneDetail("measurement-b"); changes.value++; runCurrent()
        detail = measuredSceneDetail("measurement-c"); changes.value++; runCurrent()
        account = AccountScope("owner", 3); advanceTimeBy(2_000); runCurrent()
        assertTrue(updates.ready().all { it.route.detail.measurement!!.id == "measurement-a" })
    }
}
