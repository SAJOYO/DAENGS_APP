package com.daengs.app.ui.walk.records

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapCameraSnapshot

/** Lives at the screen's stable composition location while its native map may unmount. */
@Stable
internal class WalkRecordsBehaviorState(
    val selectedEntryKey: MutableState<String?>,
    val hiddenWalkIds: MutableState<Set<String>>,
    val camera: MutableState<MapCameraSnapshot?>,
    val listState: LazyListState,
    val expanded: MutableState<Boolean>,
    val selectedWalkId: MutableState<String?>,
    val minimumWalks: MutableState<Int>,
    val overlapPoint: MutableState<GeoPoint?>,
    val walkListState: LazyListState,
)

@Composable
internal fun rememberWalkRecordsBehaviorState(vararg inputs: Any?): WalkRecordsBehaviorState {
    // Inputs reset an active query's inspection, without changing the registry's positional keys.
    val selected = rememberSaveable(*inputs) { mutableStateOf<String?>(null) }
    val hidden = rememberSaveable(*inputs, stateSaver = BehaviorHiddenIdsSaver) { mutableStateOf(emptySet<String>()) }
    val camera = rememberSaveable(*inputs, stateSaver = BehaviorCameraSaver) { mutableStateOf<MapCameraSnapshot?>(null) }
    val listState = rememberSaveable(*inputs, saver = LazyListState.Saver) { LazyListState() }
    val expanded = rememberSaveable(*inputs) { mutableStateOf(false) }
    val selectedWalk = rememberSaveable(*inputs) { mutableStateOf<String?>(null) }
    val minimum = rememberSaveable(*inputs) { mutableStateOf(2) }
    val point = rememberSaveable(*inputs, stateSaver = BehaviorPointSaver) { mutableStateOf<GeoPoint?>(null) }
    val walkList = rememberSaveable(*inputs, saver = LazyListState.Saver) { LazyListState() }
    return remember(selected, hidden, camera, listState, expanded, selectedWalk, minimum, point, walkList) {
        WalkRecordsBehaviorState(selected, hidden, camera, listState, expanded, selectedWalk, minimum, point, walkList)
    }
}

private val BehaviorPointSaver = Saver<GeoPoint?, List<Double>>(
    save = { it?.let { p -> listOf(p.latitude, p.longitude) } ?: emptyList() },
    restore = { it.takeIf { v -> v.size == 2 }?.let { v -> GeoPoint(v[0], v[1]) } },
)

private val BehaviorHiddenIdsSaver = listSaver<Set<String>, String>(
    save = { it.sorted() }, restore = { it.toSet() },
)

private val BehaviorCameraSaver = Saver<MapCameraSnapshot?, List<Double>>(
    save = { camera -> camera?.let {
        listOf(it.target.latitude, it.target.longitude, it.zoom, it.bearing, it.tilt)
    } ?: emptyList() },
    restore = { values -> values.takeIf { it.size == 5 }?.let {
        MapCameraSnapshot(GeoPoint(it[0], it[1]), it[2], it[3], it[4])
    } },
)
