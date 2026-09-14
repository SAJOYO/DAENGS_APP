package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.detail.WalkDiaryReadView
import com.daengs.app.ui.walk.detail.DiaryReadUpdate
import com.daengs.app.ui.walk.detail.walkDiaryReadUpdates

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.detail.WalkDetailActions
import com.daengs.app.walk.detail.WalkDetailDeliveryPending
import com.daengs.app.walk.detail.WalkDetailSource
import com.daengs.app.walk.diary.DiaryScene
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

/** One detail composition's IO state. Editor drafts, map selection and camera stay with their UI. */
internal class WalkDetailState(
    private val source: WalkDetailSource,
    private val actions: WalkDetailActions,
    private val scope: CoroutineScope,
    /** Called in the same snapshot as readView, so the map never adopts a different read. */
    private val adoptRead: (WalkDiaryReadView?) -> Unit,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    var readView by mutableStateOf<WalkDiaryReadView?>(null); private set
    var loaded by mutableStateOf(false); private set
    private var readError by mutableStateOf<String?>(null)
    private var openingError by mutableStateOf<String?>(null)
    val error get() = readError ?: openingError
    var entries by mutableStateOf<List<WalkEntry>>(emptyList()); private set
    private var entryReadError by mutableStateOf<String?>(null)
    private var entryWriteError by mutableStateOf<String?>(null)
    val entryError get() = entryWriteError ?: entryReadError
    var savingEntry by mutableStateOf(false); private set
    var generating by mutableStateOf(false); private set
    var generationError by mutableStateOf<String?>(null); private set
    var savingScene by mutableStateOf(false); private set
    var sceneError by mutableStateOf<String?>(null); private set

    val canGenerateDiary get() = loaded && readView?.diary?.let { !it.preparing && !it.published } == true
    private val retries = MutableStateFlow(0)
    private var opening = false
    private var operationEpoch = 0
    private var entryJob: Job? = null
    private var sceneJob: Job? = null
    private var generationJob: Job? = null
    private fun current() = scope.isActive && source.isCurrentAccount()

    /** The composition cancels both observation and action jobs on session/account replacement. */
    suspend fun observe(): Unit = coroutineScope {
        open()
        retries.collectLatest {
            if (!current()) return@collectLatest
            readError = null; entryReadError = null
            coroutineScope {
                launch {
                    try {
                        source.entries.collect { if (current()) entries = it }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { if (current()) entryReadError = "기록을 불러오지 못했어요. 다시 시도해 주세요." }
                }
                walkDiaryReadUpdates(source.changes, source::load, source::observeDiary,
                    isCurrentAccount = ::current, dispatcher = computeDispatcher).collect { update ->
                    if (!current()) return@collect
                    Snapshot.withMutableSnapshot {
                        loaded = true
                        when (update) {
                            is DiaryReadUpdate.Ready -> { readView = update.view; adoptRead(update.view); readError = null }
                            DiaryReadUpdate.Missing -> { readView = null; adoptRead(null); readError = null }
                            is DiaryReadUpdate.Failed -> readError = update.message
                        }
                    }
                    if (update == DiaryReadUpdate.Missing) cancelEdits()
                }
            }
        }
    }

    private fun open() {
        if (opening || !current()) return
        opening = true; openingError = null
        scope.launch {
            try { actions.open() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current() && (!loaded || readView != null))
                    openingError = "산책 준비를 시작하지 못했어요. 다시 시도해 주세요."
            }
            finally { opening = false }
        }
    }

    fun retry() {
        if (!current()) return
        if (openingError != null) open()
        else if (!opening) {
            try { actions.prepareDiary() }
            catch (_: CancellationException) { return }
            catch (_: Exception) { openingError = "산책 준비를 시작하지 못했어요. 다시 시도해 주세요." }
        }
        retries.value++
    }

    fun generateDiary() {
        if (generating || !current()) return
        if (!canGenerateDiary) { retry(); return }
        generating = true; generationError = null
        val epoch = operationEpoch
        generationJob = scope.launch {
            try { actions.generateDiary(); checkOperation(epoch) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current() && epoch == operationEpoch)
                    generationError = "일기를 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요."
            } finally { if (epoch == operationEpoch) generating = false }
        }
    }

    fun clearEntryError() { entryWriteError = null }
    fun clearSceneError() { sceneError = null }
    fun saveEntry(entry: WalkEntry, onSaved: () -> Unit) = changeEntry(onSaved) { actions.saveEntry(entry) }
    fun deleteEntry(id: String, onDeleted: () -> Unit) = changeEntry(onDeleted) { actions.deleteEntry(id) }

    private fun changeEntry(onSuccess: () -> Unit, change: suspend () -> Unit) {
        if (savingEntry || !current()) return
        savingEntry = true; entryWriteError = null
        val epoch = operationEpoch
        entryJob = scope.launch {
            try { change(); checkOperation(epoch); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: WalkDetailDeliveryPending) {
                checkOperation(epoch)
                // The local write succeeded. The screen's existing retry repeats open/enqueue only.
                openingError = e.message
                onSuccess()
            }
            catch (e: Exception) {
                if (current() && epoch == operationEpoch) entryWriteError = e.message ?: "저장하지 못했어요."
            } finally { if (epoch == operationEpoch) savingEntry = false }
        }
    }

    fun saveScene(scene: DiaryScene, title: String, body: String, onSaved: () -> Unit) =
        changeScene(scene, onSaved) { actions.saveScene(it, title, body) }

    fun deleteScene(scene: DiaryScene, onDeleted: () -> Unit) {
        if (!current() || savingScene) return
        if (scene.sessionId != readView?.route?.detail?.summary?.sessionId) {
            sceneError = "장면을 다시 열어 주세요."; return
        }
        changeScene(scene, onDeleted) { actions.deleteScene(it) }
    }

    private fun changeScene(scene: DiaryScene, onSaved: () -> Unit,
        change: suspend (com.daengs.app.walk.diary.StoryboardScene) -> Unit) {
        if (savingScene || !current()) return
        val original = scene.source
        if (original == null) { sceneError = "장면을 다시 열어 주세요."; return }
        savingScene = true; sceneError = null
        val epoch = operationEpoch
        sceneJob = scope.launch {
            try { change(original); checkOperation(epoch); onSaved() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current() && epoch == operationEpoch) sceneError = e.message ?: "저장하지 못했어요."
            } finally { if (epoch == operationEpoch) savingScene = false }
        }
    }

    private suspend fun checkOperation(epoch: Int) {
        currentCoroutineContext().ensureActive()
        if (!current() || epoch != operationEpoch) throw CancellationException("상세 화면이 변경되었어요.")
    }

    private fun cancelEdits() {
        operationEpoch++
        entryJob?.cancel(); sceneJob?.cancel(); generationJob?.cancel()
        savingEntry = false; savingScene = false; generating = false
        entryWriteError = null; sceneError = null; generationError = null
        openingError = null
        entries = emptyList()
    }
}

@Composable
internal fun rememberWalkDetailState(source: WalkDetailSource, actions: WalkDetailActions,
    explorer: WalkRouteExplorerState): WalkDetailState = key(source, actions, explorer) {
    val scope = rememberCoroutineScope()
    val state = remember {
        WalkDetailState(source, actions, scope, adoptRead = { view ->
            if (view == null) explorer.overview() else explorer.adopt(view)
        })
    }
    LaunchedEffect(state) { state.observe() }
    state
}
