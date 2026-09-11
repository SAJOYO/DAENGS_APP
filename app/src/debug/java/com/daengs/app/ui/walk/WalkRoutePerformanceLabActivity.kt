package com.daengs.app.ui.walk

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.trail.TrailLayerState
import com.daengs.app.map.provider.naver.LocalWalkRouteProbe
import com.daengs.app.map.provider.naver.WalkRouteProbe
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.map.style.WalkSpeedPoint
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.delay
import java.util.Locale

/** Synthetic routes only. No GPS, recording, server or account operations. */
class WalkRoutePerformanceLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { DaengsTheme { WalkRoutePerformanceLab(intent.getBooleanExtra("auto", false), intent.getBooleanExtra("single", false)) } }
    }
}

private fun routePoint(segment: Int, index: Int): WalkSpeedPoint {
    val column = if (index / 100 % 2 == 0) index % 100 else 99 - index % 100
    return WalkSpeedPoint(GeoPoint(37.545 + segment * .00075 + (index / 100) * .000008,
        127.04 + column * .000008), index * (500L + segment * 600L))
}

@Composable
private fun WalkRoutePerformanceLab(auto: Boolean, single: Boolean = false) {
    val segmentCount = if (single) 1 else 4
    val probe = remember { WalkRouteProbe() }
    var count by remember { mutableIntStateOf(1000) }
    var extra by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(auto) }
    var show by remember { mutableStateOf(true) }
    var gap by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("가상 경로 ${segmentCount}구간 · 마지막 구간에만 좌표 추가") }
    val base = remember(count, segmentCount) { List(segmentCount) { segment -> List(count / segmentCount) { routePoint(segment, it) } } }
    val trail = remember(base, extra, show, gap) {
        val paths = if (!show) emptyList() else base.dropLast(1) + listOf(
            base.last() + List(extra) { routePoint(segmentCount - 1, count / segmentCount + it) }) +
            if (gap) listOf(List(20) { routePoint(segmentCount, it) }) else emptyList()
        TrailLayerState(paths = paths.map { segment -> segment.map { it.point } }, speedPaths = paths)
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        delay(4000)
        for (size in if (single) listOf(1000, 5000) else listOf(1000, 5000, 10000)) {
            count = size; extra = 0; show = true; gap = false
            delay(2000)
            probe.reset()
            repeat(20) { extra++; delay(250) }
            val line = String.format(Locale.US,
                "n=%d phase=append20 create=%d remove=%d update=%d prepared=%d points=%d edges=%d prepareMs=%.2f sdkMs=%.2f",
                count, probe.created, probe.removed, probe.updated, probe.prepared, probe.pointsPrepared, probe.edgesPainted,
                probe.prepareNanos.sum() / 1e6, probe.sdkNanos.sum() / 1e6)
            Log.i("WalkRoutePerf", line); report = line
        }
        Log.i("WalkRoutePerf", "DONE"); running = false
    }
    CompositionLocalProvider(LocalWalkRouteProbe provides probe) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Text("개발용 · 누적 경로 비교", Modifier.padding(8.dp))
            Row {
                TextButton(enabled = !running, onClick = { running = true }) { Text("자동 비교") }
                TextButton(enabled = !running, onClick = { extra++ }) { Text("좌표 추가") }
                TextButton(enabled = !running, onClick = { gap = !gap }) { Text("끊긴 구간") }
                TextButton(enabled = !running, onClick = { show = !show }) { Text("숨김/표시") }
            }
            Text(report, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
            MapHost(scene = MapScene(trail = trail), searchOrigin = null, followDevice = false,
                centerOn = GeoPoint(37.5465, 127.0404), centerZoom = 16.0,
                onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Preview
@Composable
private fun WalkRoutePerformanceLabPreview() { DaengsTheme { WalkRoutePerformanceLab(false) } }
