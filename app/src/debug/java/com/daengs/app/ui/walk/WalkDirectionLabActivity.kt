package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.BuildConfig
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.completedroute.*
import com.daengs.app.map.layers.moments.DiaryPinAppearance
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.map.style.WalkSpeedPoint
import com.daengs.app.ui.theme.DaengsTheme
import kotlin.math.*

/** In-memory route fixtures only. No account, walk recording, database or search API calls. */
class WalkDirectionLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DaengsTheme {
            if (BuildConfig.APPLICATION_ID.endsWith(".directionreview")) WalkDirectionLab()
            else Text("별도 방향 검토 앱에서만 여는 가상 동선이에요.", Modifier.padding(24.dp))
        } }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 740)
@Composable
private fun WalkDirectionLab() {
    var scenario by remember { mutableStateOf("곡선") }
    var count by remember { mutableIntStateOf(0) }
    var showPins by remember { mutableStateOf(true) }
    val loop = remember { (0..160).map { i ->
        val t = i * 2 * PI / 160
        GeoPoint(37.5445 + sin(t) * .0011, 127.0377 + cos(t) * .0013)
    } }
    val straight = remember { (0..80).map { GeoPoint(37.5445, 127.0363 + it * .000035) } }
    val paths = remember(scenario) { if (scenario == "곡선") listOf(loop) else listOf(straight, straight.asReversed()) }
    val scene = remember(paths, scenario, showPins) { MapScene(
        completedRoute = CompletedRouteLayerState(paths = paths,
            speedPaths = paths.map { path -> path.mapIndexed { i, p -> WalkSpeedPoint(p, i * 4_000L) } }),
        moments = if (showPins) listOf(30, 70, 110).mapIndexed { i, sample ->
            MomentMarkerState("scene-$i", loop[sample], "${i + 1}", diaryPin = DiaryPinAppearance(i + 1))
        } else emptyList(),
        sessionExplorer = if (scenario == "통과 선택") SessionRouteExplorerLayerState(
            highlightPaths = listOf(straight), useOverviewDirections = false) else SessionRouteExplorerLayerState(),
    ) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text("가상 동선 · 방향 표시 검토 · ${count}개", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row {
            listOf("곡선", "왕복", "통과 선택").forEach { item -> TextButton(onClick = { scenario = item }) { Text(item) } }
            TextButton(onClick = { showPins = !showPins }) { Text(if (showPins) "핀 숨김" else "핀 표시") }
        }
        MapHost(scene, null, false, fitBounds = paths.flatten(), cameraRequestKey = scenario.hashCode(),
            onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {}, onRouteDirectionCount = { count = it },
            modifier = Modifier.weight(1f).fillMaxWidth())
    }
}
