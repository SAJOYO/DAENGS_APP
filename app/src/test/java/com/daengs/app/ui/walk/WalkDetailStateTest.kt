package com.daengs.app.ui.walk

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.WalkDetailActions
import com.daengs.app.walk.detail.WalkDetailDeliveryPending
import com.daengs.app.walk.detail.WalkDetailSource
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkDetailStateTest {
    private val detail = readCompletedRoute(RecordedSession("s", startedAtMillis = 0, endedAtMillis = 1000), emptyList())
    private val original = StoryboardScene("scene", 500, "원래 제목", "원래 내용", "", "fingerprint")
    private val scene = DiaryScene("s/scene", "s", 500, original.title, original.body, null, "", source = original)
    private val diary = DiaryWalk(detail.summary, listOf(scene), "")
    private val note = WalkEntry("note", "s", WalkMomentType.NOTE, 500, note = "남길 내용")

    private inner class Source : WalkDetailSource {
        val revision = MutableStateFlow(0)
        var current = true
        var loadDetail: suspend () -> WalkSessionDetail? = { detail }
        var readDiary: (WalkSessionDetail) -> Flow<DiaryWalk?> = { flowOf(diary) }
        var readEntries: Flow<List<WalkEntry>> = flowOf(emptyList())
        override val changes = revision.map { Unit }
        override val entries get() = readEntries
        override fun isCurrentAccount() = current
        override suspend fun load() = loadDetail()
        override fun observeDiary(detail: WalkSessionDetail) = readDiary(detail)
    }
    private class Actions : WalkDetailActions {
        var opens = 0; var prepares = 0; var saves = 0; var deletes = 0; var scenes = 0; var generations = 0
        var openAction: suspend () -> Unit = {}
        var entryAction: suspend () -> Unit = {}
        var sceneAction: suspend () -> Unit = {}
        var removedScenes = 0
        var generateAction: suspend () -> Unit = {}
        override suspend fun open() { opens++; openAction() }
        override fun prepareDiary() { prepares++ }
        override suspend fun saveEntry(entry: WalkEntry) { saves++; entryAction() }
        override suspend fun deleteEntry(id: String) { deletes++; entryAction() }
        override suspend fun saveScene(scene: StoryboardScene, title: String, body: String) { scenes++; sceneAction() }
        override suspend fun generateDiary() { generations++; generateAction() }
        override suspend fun deletePhoto(id: String) = Unit
        override suspend fun deleteScene(scene: StoryboardScene) { removedScenes++; sceneAction() }
    }
    private fun TestScope.state(source: Source = Source(), actions: Actions = Actions(),
        scope: CoroutineScope = backgroundScope, adopt: (WalkDiaryReadView?) -> Unit = {}): WalkDetailState {
        val state = WalkDetailState(source, actions, scope, adopt, StandardTestDispatcher(testScheduler))
        scope.launch { state.observe() }
        runCurrent()
        return state
    }

    @Test fun `read failure retries without reopening a successful delivery and clears only read error`() = runTest {
        val source = Source(); val actions = Actions()
        source.loadDetail = { error("offline") }
        val state = state(source, actions)
        assertTrue(state.loaded); assertNotNull(state.error); assertNull(state.readView)
        source.loadDetail = { detail }
        state.retry(); runCurrent()
        assertNull(state.error); assertEquals(diary, state.readView!!.diary)
        assertEquals(1, actions.prepares); assertEquals(1, actions.opens)
    }

    @Test fun `opening failure is visible and retry repeats opening without discarding the ready read`() = runTest {
        val actions = Actions().apply { openAction = { error("scheduler") } }
        val state = state(actions = actions)
        assertNotNull(state.error); assertNotNull(state.readView)
        actions.openAction = {}
        state.retry(); state.retry(); runCurrent()
        assertNull(state.error); assertNotNull(state.readView)
        assertEquals(2, actions.opens)
    }

    @Test fun `entries failure can retry independently of a ready diary`() = runTest {
        val source = Source().apply { readEntries = flow { error("entries") } }
        val state = state(source)
        assertNotNull(state.entryError); assertNotNull(state.readView)
        source.readEntries = flowOf(listOf(note))
        state.retry(); runCurrent()
        assertNull(state.entryError); assertEquals(listOf(note), state.entries)
    }

    @Test fun `entry save and delete share a synchronous busy gate and only success dismisses`() = runTest {
        val actions = Actions(); val result = CompletableDeferred<Unit>()
        actions.entryAction = { result.await() }
        val state = state(actions = actions)
        var dismissed = 0
        state.saveEntry(note) { dismissed++ }
        state.saveEntry(note) { dismissed++ }
        state.deleteEntry(note.id) { dismissed++ }
        assertTrue(state.savingEntry)
        runCurrent(); assertEquals(1, actions.saves); assertEquals(0, actions.deletes)
        result.completeExceptionally(IllegalStateException("편집 충돌")); runCurrent()
        assertFalse(state.savingEntry); assertEquals("편집 충돌", state.entryError); assertEquals(0, dismissed)
        actions.entryAction = {}
        state.saveEntry(note) { dismissed++ }
        assertNull(state.entryError); runCurrent()
        assertEquals(1, dismissed); assertFalse(state.savingEntry)
        state.deleteEntry(note.id) { dismissed++ }; runCurrent()
        assertEquals(1, actions.deletes); assertEquals(2, dismissed)
    }

    @Test fun `scene save retains failure until retry and refuses duplicates and missing source`() = runTest {
        val actions = Actions(); val result = CompletableDeferred<Unit>()
        actions.sceneAction = { result.await() }
        val state = state(actions = actions)
        var dismissed = false
        state.saveScene(scene.copy(source = null), "제목", "내용") { dismissed = true }
        assertNotNull(state.sceneError); assertEquals(0, actions.scenes)
        state.saveScene(scene, "제목", "내용") { dismissed = true }
        state.saveScene(scene, "중복", "중복") { dismissed = true }
        runCurrent(); assertEquals(1, actions.scenes); assertNull(state.sceneError)
        result.completeExceptionally(IllegalStateException("저장 실패")); runCurrent()
        assertFalse(dismissed); assertFalse(state.savingScene); assertEquals("저장 실패", state.sceneError)
        actions.sceneAction = {}
        state.saveScene(scene, "제목", "내용") { dismissed = true }; runCurrent()
        assertTrue(dismissed); assertNull(state.sceneError)
    }

    @Test fun `scene removal shares edit gate retains failure and rejects wrong session or missing source`() = runTest {
        val actions = Actions(); val result = CompletableDeferred<Unit>()
        actions.sceneAction = { result.await() }
        val state = state(actions = actions)
        var removed = 0
        state.deleteScene(scene.copy(sessionId = "other")) { removed++ }
        state.deleteScene(scene.copy(source = null)) { removed++ }
        assertEquals(0, actions.removedScenes); assertNotNull(state.sceneError)
        state.deleteScene(scene) { removed++ }
        state.deleteScene(scene) { removed++ }
        state.saveScene(scene, "제목", "내용") { removed++ }
        runCurrent()
        assertEquals(1, actions.removedScenes); assertEquals(0, actions.scenes); assertTrue(state.savingScene)
        result.completeExceptionally(IllegalStateException("삭제 실패")); runCurrent()
        assertEquals("삭제 실패", state.sceneError); assertFalse(state.savingScene); assertEquals(0, removed)
        actions.sceneAction = {}
        state.deleteScene(scene) { removed++ }; runCurrent()
        assertEquals(1, removed); assertNull(state.sceneError)
    }

    @Test fun `scene removal never completes in a replaced account or deleted walk`() = runTest {
        val source = Source(); val actions = Actions(); val gate = CompletableDeferred<Unit>()
        actions.sceneAction = { gate.await() }
        val state = state(source, actions)
        state.deleteScene(scene) { fail("old account") }; runCurrent()
        source.current = false; gate.complete(Unit); runCurrent()
        state.deleteScene(scene) { fail("old account") }; runCurrent()
        assertEquals(1, actions.removedScenes); assertNull(state.sceneError)
        source.current = true
        actions.sceneAction = { awaitCancellation() }
        state.deleteScene(scene) { fail("deleted walk") }; runCurrent()
        source.loadDetail = { null }; source.revision.value++; runCurrent()
        assertFalse(state.savingScene); assertNull(state.sceneError)
        state.deleteScene(scene) { fail("missing walk") }; runCurrent()
        assertEquals(2, actions.removedScenes)
    }

    @Test fun `generation blocks repeated calls resets failure on retry and published diary only refreshes`() = runTest {
        val source = Source(); val actions = Actions(); val result = CompletableDeferred<Unit>()
        actions.generateAction = { result.await() }
        val state = state(source, actions)
        state.generateDiary(); state.generateDiary(); runCurrent()
        assertEquals(1, actions.generations); assertTrue(state.generating)
        result.completeExceptionally(IllegalStateException("offline")); runCurrent()
        assertFalse(state.generating); assertNotNull(state.generationError)
        actions.generateAction = {}
        state.generateDiary(); assertNull(state.generationError); runCurrent()
        assertEquals(2, actions.generations)
        source.readDiary = { flowOf(diary.copy(published = true)) }; source.revision.value++; runCurrent()
        state.generateDiary(); runCurrent()
        assertEquals(2, actions.generations); assertEquals(1, actions.prepares)
    }

    @Test fun `cancellation is not reported as a save or generation error`() = runTest {
        val actions = Actions().apply {
            entryAction = { throw CancellationException() }
            sceneAction = { throw CancellationException() }
            generateAction = { throw CancellationException() }
        }
        val state = state(actions = actions)
        state.saveEntry(note) { fail("cancelled save") }
        state.saveScene(scene, "제목", "내용") { fail("cancelled scene") }
        state.generateDiary(); runCurrent()
        assertFalse(state.savingEntry); assertFalse(state.savingScene); assertFalse(state.generating)
        assertNull(state.entryError); assertNull(state.sceneError); assertNull(state.generationError)
    }

    @Test fun `account replacement rejects late success and ignores further actions`() = runTest {
        val source = Source(); val actions = Actions(); val result = CompletableDeferred<Unit>()
        actions.entryAction = { result.await() }
        val state = state(source, actions)
        state.saveEntry(note) { fail("previous account must not close an editor") }; runCurrent()
        source.current = false; result.complete(Unit); runCurrent()
        state.saveEntry(note) {}; state.generateDiary(); state.retry(); runCurrent()
        assertEquals(1, actions.saves); assertEquals(0, actions.generations); assertEquals(0, actions.prepares)
        assertNull(state.entryError)
    }

    @Test fun `screen lifetime cancels reads and edits and cannot be restarted from a stale callback`() = runTest {
        val source = Source(); val actions = Actions()
        var readCancelled = false; var editCancelled = false
        source.readDiary = { flow { emit(diary); try { awaitCancellation() } finally { readCancelled = true } } }
        actions.entryAction = { try { awaitCancellation() } finally { editCancelled = true } }
        val owner = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val state = state(source, actions, owner)
        state.saveEntry(note) { fail("left screen") }; runCurrent()
        owner.cancel(); runCurrent()
        assertTrue(readCancelled); assertTrue(editCancelled)
        state.saveEntry(note) {}; state.retry(); runCurrent()
        assertEquals(1, actions.saves); assertEquals(0, actions.prepares)
    }

    @Test fun `missing walk cancels old edits and their late cleanup cannot reset a new save`() = runTest {
        val source = Source(); val actions = Actions(); val old = CompletableDeferred<Unit>()
        actions.entryAction = { withContext(NonCancellable) { old.await() } }
        val state = state(source, actions)
        state.saveEntry(note) { fail("deleted input") }; runCurrent()
        source.loadDetail = { null }; source.revision.value++; runCurrent()
        assertNull(state.readView); assertFalse(state.savingEntry)
        source.loadDetail = { detail }; source.revision.value++; runCurrent()
        val next = CompletableDeferred<Unit>(); actions.entryAction = { next.await() }
        var saved = false
        state.saveEntry(note) { saved = true }; runCurrent()
        old.complete(Unit); runCurrent()
        assertTrue(state.savingEntry); assertFalse(saved)
        next.complete(Unit); runCurrent()
        assertFalse(state.savingEntry); assertTrue(saved)
    }

    @Test fun `read and adopted route change in one snapshot while scene failure retains the route`() = runTest {
        val source = Source()
        var adopted by mutableStateOf<WalkDiaryReadView?>(null)
        val state = state(source, adopt = { adopted = it })
        val observer = Snapshot.registerApplyObserver { _, _ -> assertSame(state.readView, adopted) }
        try {
            source.readDiary = { flow { error("scenes") } }; source.revision.value++; runCurrent()
            assertSame(detail, state.readView!!.route.detail)
            assertFalse(state.readView!!.scenesLoading); assertNotNull(state.error)
            source.loadDetail = { null }; source.revision.value++; runCurrent()
            assertNull(adopted); assertNull(state.readView)
        } finally { observer.dispose() }
    }

    @Test fun `deleted walk clears a delivery notice and rejects late opening failure`() = runTest {
        val source = Source(); val actions = Actions()
        val state = state(source, actions)
        actions.entryAction = { throw WalkDetailDeliveryPending(IllegalStateException("scheduler")) }
        var saved = false
        state.saveEntry(note) { saved = true }; runCurrent()
        assertTrue(saved); assertNotNull(state.error)
        val retry = CompletableDeferred<Unit>()
        actions.openAction = { retry.await() }
        state.retry(); runCurrent()
        source.loadDetail = { null }; source.revision.value++; runCurrent()
        retry.completeExceptionally(IllegalStateException("late scheduler failure")); runCurrent()
        assertTrue(state.loaded); assertNull(state.readView); assertNull(state.error)
    }

    @Test fun `deleted walk drops an unacknowledged delivery notice`() = runTest {
        val source = Source(); val actions = Actions()
        val state = state(source, actions)
        actions.entryAction = { throw WalkDetailDeliveryPending(IllegalStateException("scheduler")) }
        state.deleteEntry(note.id) {}; runCurrent()
        assertNotNull(state.error)
        source.loadDetail = { null }; source.revision.value++; runCurrent()
        assertTrue(state.loaded); assertNull(state.readView); assertNull(state.error)
    }
}
