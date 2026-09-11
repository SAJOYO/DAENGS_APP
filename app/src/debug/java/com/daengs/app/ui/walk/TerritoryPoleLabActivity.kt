package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.territory.*
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.ui.theme.DaengsTheme

/** Debug 전용: 실제 지도 렌더러에 가상 3개 장소만 전달한다. 점령 API/GPS 기록 없음. */
class TerritoryPoleLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DaengsTheme { TerritoryPoleLab() } }
    }
}

@Composable
private fun TerritoryPoleLab() {
    var selected by remember { mutableStateOf<String?>(null) }
    var firstState by remember { mutableStateOf(TerritoryMarkerOccupancy.NEUTRAL) }
    var feedback by remember { mutableStateOf<TerritoryFeedback?>(null) }
    val center = remember { GeoPoint(37.545, 127.04) }
    val states = listOf(firstState, TerritoryMarkerOccupancy.UNVERIFIED, TerritoryMarkerOccupancy.VERIFIED)
    val sites = states.mapIndexed { index, occupancy ->
        TerritorySiteMarkerState("pole-$index", GeoPoint(center.latitude, center.longitude + (index - 1) * .00025),
            selected = selected == "pole-$index", occupancy = occupancy, occupancyKnown = true,
            label = when (occupancy) {
                TerritoryMarkerOccupancy.NEUTRAL -> "기본"
                TerritoryMarkerOccupancy.UNVERIFIED -> "점령 · 미인증"
                TerritoryMarkerOccupancy.VERIFIED -> "점령 · 인증"
            }, radiusMeters = 10.0.takeIf { selected == "pole-$index" },
            feedback = feedback?.takeIf { it.siteId == "pole-$index" })
    }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("개발용 · 가상 장소 / 실제 지도", Modifier.padding(12.dp))
        Row {
            TextButton(onClick = {
                firstState = TerritoryMarkerOccupancy.entries[(firstState.ordinal + 1) % 3]
                selected = "pole-0"
                val now = System.nanoTime()
                feedback = TerritoryFeedback(now, "pole-0", when (firstState) {
                    TerritoryMarkerOccupancy.NEUTRAL -> TerritoryFeedbackKind.READY
                    TerritoryMarkerOccupancy.UNVERIFIED -> TerritoryFeedbackKind.MARKED
                    TerritoryMarkerOccupancy.VERIFIED -> TerritoryFeedbackKind.VERIFIED
                }, now)
            }) { Text("왼쪽 상태 전환") }
            TextButton(onClick = { selected = null; feedback = null }) { Text("선택 해제") }
        }
        MapHost(scene = MapScene(territorySites = sites), searchOrigin = null, followDevice = false,
            centerOn = center, centerZoom = 18.0,
            onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
            onSelectTerritorySite = { selected = it; feedback = null },
            onMapTap = { selected = null; feedback = null },
            modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Preview
@Composable
private fun TerritoryPoleLabPreview() { DaengsTheme { TerritoryPoleLab() } }
