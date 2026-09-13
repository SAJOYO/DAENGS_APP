package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.location.GeoPoint
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*

/** Dialog order, dismissal and draft ownership match the detail screen's original composition. */
@Composable
internal fun WalkDiaryEditorDialogs(
    editors: WalkDiaryEditorState,
    state: WalkDetailState,
    route: WalkSessionRoute?,
    dogIds: List<String>,
    pets: List<Pet>,
    deletePhoto: suspend (String) -> Unit,
) {
    if (editors.adding && editors.chosenPoint != null && !editors.editorOpen) {
        DiaryPointChoiceDialog(requireNotNull(editors.chosenPoint), route?.points.orEmpty(),
            onChoosePoint = editors::choosePoint, onDismiss = { editors.choosePoint(null) },
            onType = { type ->
                editors.chooseType(type, dogIds.singleOrNull())
                state.clearEntryError()
            })
    }
    if (editors.editorOpen) WalkEntryEditor(state.entries, editors.entry, pets.filter { it.id in dogIds },
        state.entryError, state.savingEntry, { state.saveEntry(it, editors::entrySaved) },
        { state.deleteEntry(it.id, editors::entrySaved) }, editors::dismissEntry)
    editors.editingScene?.let { scene ->
        DiarySceneEditor(scene, state.savingScene, state.sceneError, onSave = { title, body ->
            state.saveScene(scene, title, body, editors::dismissScene)
        }, onDismiss = editors::dismissScene)
    }
    editors.photo?.let { WalkPhotoDialog(it, deletePhoto, editors::dismissPhoto) }
}

@Composable
private fun DiaryPointChoiceDialog(point: WalkRoutePoint, points: List<WalkRoutePoint>,
    onChoosePoint: (WalkRoutePoint) -> Unit, onType: (WalkMomentType) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("이 지점에 기록 남기기") },
        text = { Column {
            Text("${formatWalkClock(point.capturedAtMillis)}에 저장된 위치예요.")
            // A loop may visit one spot repeatedly. Let the user choose the observed time explicitly.
            val nearby = points.filter { it.point.distanceTo(point.point) <= 6.0 }
            val alternatives = nearby.filterIndexed { i, p -> i == 0 || p.capturedAtMillis - nearby[i-1].capturedAtMillis > 30_000 }
            if (alternatives.size > 1) Row {
                alternatives.take(6).forEach { p -> TextButton(onClick = { onChoosePoint(p) }) { Text(formatWalkClock(p.capturedAtMillis)) } }
            }
            WalkMomentType.entries.forEach { type -> TextButton(onClick = { onType(type) }) { Text(type.label) } }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("다른 위치") } })
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun DiaryPointChoicePreview() { DaengsTheme {
    val point = WalkRoutePoint(GeoPoint(37.5, 127.0), 1000, 3f, 1000, 10.0, 1.0, 0, 1)
    DiaryPointChoiceDialog(point, listOf(point), {}, {}, {})
} }
