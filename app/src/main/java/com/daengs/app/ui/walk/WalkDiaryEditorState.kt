package com.daengs.app.ui.walk

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene

/** Open targets only. Text drafts remain in the editors; asynchronous writes remain in WalkDetailState. */
internal class WalkDiaryEditorState(private val sessionId: String, adding: Boolean = false) {
    var adding by mutableStateOf(adding); private set
    var chosenPoint by mutableStateOf<WalkRoutePoint?>(null); private set
    var entry by mutableStateOf<WalkEntry?>(null); private set
    var editorOpen by mutableStateOf(false); private set
    var photo by mutableStateOf<WalkPhoto?>(null); private set
    var editingScene by mutableStateOf<DiaryScene?>(null); private set
    var removingScene by mutableStateOf<DiaryScene?>(null); private set

    fun beginAdding(summary: WalkSummary, hasRoute: Boolean) {
        chosenPoint = null
        if (hasRoute) adding = !adding
        else {
            entry = WalkEntry(sessionId = sessionId, type = WalkMomentType.NOTE,
                recordedAtMillis = summary.startedAtMillis)
            editorOpen = true
        }
    }

    fun choosePoint(point: WalkRoutePoint?) { chosenPoint = point }
    fun chooseType(type: WalkMomentType, petId: String?) {
        entry = requireNotNull(chosenPoint).toDiaryEntry(sessionId, type, petId)
        editorOpen = true
    }
    fun cancelAdding() { adding = false; chosenPoint = null }
    fun dismissEntry() { editorOpen = false; chosenPoint = null }
    fun entrySaved() { dismissEntry(); adding = false }
    fun editScene(scene: DiaryScene?) { editingScene = scene }
    fun dismissScene() { editingScene = null }
    fun removeScene(scene: DiaryScene?) { removingScene = scene }
    fun dismissRemoval() { removingScene = null }
    fun showPhoto(value: WalkPhoto) { photo = value }
    fun dismissPhoto() { photo = null }

    fun clear() {
        cancelAdding(); entry = null; editorOpen = false; editingScene = null; removingScene = null; photo = null
    }

    companion object {
        /** Preserve the previous restoration policy: only adding survives, never an open target. */
        fun saver(sessionId: String) = Saver<WalkDiaryEditorState, Boolean>(
            save = { it.adding }, restore = { WalkDiaryEditorState(sessionId, it) },
        )
    }
}

/** Caller keys the containing composition by both session and login generation. */
@Composable
internal fun rememberWalkDiaryEditorState(sessionId: String): WalkDiaryEditorState =
    rememberSaveable(sessionId, saver = WalkDiaryEditorState.saver(sessionId)) { WalkDiaryEditorState(sessionId) }

internal fun WalkRoutePoint.toDiaryEntry(sessionId: String, type: WalkMomentType, petId: String?): WalkEntry =
    WalkEntry(sessionId = sessionId, type = type, recordedAtMillis = capturedAtMillis, point = point,
        locationCapturedAtMillis = capturedAtMillis, accuracyMeters = accuracyMeters, petId = petId)
