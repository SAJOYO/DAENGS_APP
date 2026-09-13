package com.daengs.app.ui.walk.records

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.records.WalkActionPinGroup
import com.daengs.app.walk.WalkMomentType

@Stable
internal class WalkRecordsActionPinState(
    val enabled: MutableState<Boolean>, val type: MutableState<WalkMomentType?>,
    val browsing: MutableState<Boolean>, val selectedKey: MutableState<String?>,
    val groupPoint: MutableState<GeoPoint?>, val listState: LazyListState,
    val groupKeys: MutableState<List<String>>,
) {
    fun inspect(group: WalkActionPinGroup) {
        groupPoint.value = group.point
        groupKeys.value = group.records.map { it.key }
        selectedKey.value = group.records.first().key.takeIf { group.records.size == 1 }
        browsing.value = true
    }
    fun allRecords() { groupPoint.value = null; groupKeys.value = emptyList(); browsing.value = true }
    fun clearInspection() { groupPoint.value = null; groupKeys.value = emptyList(); selectedKey.value = null }
}

/** Owned above tab/loading branches so diary return restores the same pin inspection. */
@Composable
internal fun rememberWalkRecordsActionPinState(vararg keys: Any?): WalkRecordsActionPinState {
    val enabled = rememberSaveable(*keys) { mutableStateOf(true) }
    val type = rememberSaveable(*keys) { mutableStateOf<WalkMomentType?>(null) }
    val browsing = rememberSaveable(*keys) { mutableStateOf(false) }
    val selected = rememberSaveable(*keys) { mutableStateOf<String?>(null) }
    val point = rememberSaveable(*keys, stateSaver = OverlapPointSaver) { mutableStateOf<GeoPoint?>(null) }
    val scroll = rememberSaveable(*keys, saver = LazyListState.Saver) { LazyListState() }
    val groupKeys = rememberSaveable(*keys) { mutableStateOf<List<String>>(emptyList()) }
    return remember(enabled, type, browsing, selected, point, scroll, groupKeys) { WalkRecordsActionPinState(enabled, type, browsing, selected, point, scroll, groupKeys) }
}
