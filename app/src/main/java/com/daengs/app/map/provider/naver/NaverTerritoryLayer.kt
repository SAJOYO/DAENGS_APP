package com.daengs.app.map.provider.naver

import androidx.compose.runtime.*
import com.daengs.app.map.layers.territory.*
import com.daengs.app.ui.walk.rememberTerritoryFeedbackProgress
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.OverlayImage

/** Map lifetime owns objects; site changes only reconcile rendered properties. */
@Composable
internal fun NaverTerritoryLayer(map: NaverMap?, sites: List<TerritorySiteMarkerState>, onSelect: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val probe = LocalTerritoryOverlayProbe.current
    val icons = remember(context) {
        TerritoryPoleStyle.entries.associateWith {
            OverlayImage.fromBitmap(territoryMarkerIcon(context, it.occupancy, it.isMine))
        }
    }
    val latestSelect by rememberUpdatedState(onSelect)
    val store = remember(map, icons, density, probe) {
        map?.let { TerritoryOverlayStore({ state ->
            NaverTerritoryOverlay(it, state, icons, density) { id -> latestSelect(id) }
        }, probe) }
    }
    val rendered = remember(sites) { sites.map { it.renderState() } }
    val feedback = sites.firstOrNull { it.selected && it.feedback != null }?.feedback
    val progress = rememberTerritoryFeedbackProgress(feedback)
    DisposableEffect(store) { onDispose { store?.clear() } }
    SideEffect {
        store?.sync(rendered)
        store?.frame(feedback, progress)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 320, heightDp = 620)
@Composable
private fun NaverTerritoryLayerPreview() { com.daengs.app.ui.walk.TerritoryRangePreview() }
