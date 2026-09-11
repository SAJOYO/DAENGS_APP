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

/** Debug 전용: 실제 지도 렌더러에 소유/인증별 가상 5개 장소. 점령 API/GPS 기록 없음. */
class TerritoryPoleLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DaengsTheme { TerritoryPoleLab() } }
    }
}

@Composable
private fun TerritoryPoleLab() {
    var selected by remember { mutableStateOf<String?>(null) }
    var firstStyle by remember { mutableStateOf(TerritoryPoleStyle.NEUTRAL) }
    var feedback by remember { mutableStateOf<TerritoryFeedback?>(null) }
    val center = remember { GeoPoint(37.545, 127.04) }
    val styles = listOf(firstStyle) + TerritoryPoleStyle.entries.drop(1)
    val offsets = listOf(0.0 to 0.0, .00018 to -.00023, .00018 to .00023,
        -.00018 to -.00023, -.00018 to .00023)
    val sites = styles.mapIndexed { index, style ->
        TerritorySiteMarkerState("pole-$index", GeoPoint(center.latitude + offsets[index].first, center.longitude + offsets[index].second),
            selected = selected == "pole-$index", occupancy = style.occupancy, isMine = style.isMine, occupancyKnown = true,
            label = when (style) {
                TerritoryPoleStyle.NEUTRAL -> "미점유"
                TerritoryPoleStyle.MINE_UNVERIFIED -> "내 미인증"
                TerritoryPoleStyle.MINE_VERIFIED -> "내 인증"
                TerritoryPoleStyle.OTHER_UNVERIFIED -> "상대 미인증"
                TerritoryPoleStyle.OTHER_VERIFIED -> "상대 인증"
            }, radiusMeters = 10.0.takeIf { selected == "pole-$index" },
            feedback = feedback?.takeIf { it.siteId == "pole-$index" })
    }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("개발용 · 가상 장소 / 실제 지도", Modifier.padding(12.dp))
        Row {
            TextButton(onClick = {
                firstStyle = TerritoryPoleStyle.entries[(firstStyle.ordinal + 1) % TerritoryPoleStyle.entries.size]
                selected = "pole-0"
                val now = System.nanoTime()
                feedback = TerritoryFeedback(now, "pole-0", when (firstStyle.occupancy) {
                    TerritoryMarkerOccupancy.NEUTRAL -> TerritoryFeedbackKind.READY
                    TerritoryMarkerOccupancy.UNVERIFIED -> TerritoryFeedbackKind.MARKED
                    TerritoryMarkerOccupancy.VERIFIED -> TerritoryFeedbackKind.VERIFIED
                }, now)
            }) { Text("가운데 상태 전환") }
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
