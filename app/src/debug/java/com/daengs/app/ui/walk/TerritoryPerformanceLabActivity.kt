package com.daengs.app.ui.walk

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
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
import com.daengs.app.map.provider.naver.LocalTerritoryOverlayProbe
import com.daengs.app.map.provider.naver.TerritoryOverlayProbe
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.territory.TerritoryProximityRange
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.delay
import java.util.Locale

/** Synthetic sites only. No location, account, claim or bookmark requests. */
class TerritoryPerformanceLabActivity : ComponentActivity() {
    private val frames = LabFrames()
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        frames.millis += metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
        frames.dropped += dropped
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
        setContent { DaengsTheme { TerritoryPerformanceLab(frames, intent.getBooleanExtra("auto", false)) } }
    }
    override fun onDestroy() {
        window.removeOnFrameMetricsAvailableListener(listener)
        super.onDestroy()
    }
}

private class LabFrames {
    val millis = mutableListOf<Double>()
    var dropped = 0
    fun reset() { millis.clear(); dropped = 0 }
}

@Composable
private fun TerritoryPerformanceLab(frames: LabFrames, auto: Boolean) {
    val probe = remember { TerritoryOverlayProbe() }
    var count by remember { mutableIntStateOf(50) }
    var selected by remember { mutableIntStateOf(-1) }
    var ready by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(auto) }
    var report by remember { mutableStateOf("50·200·500개 / 실제 지도·가상 장소") }
    var feedback by remember { mutableStateOf<TerritoryFeedback?>(null) }
    val center = remember { GeoPoint(37.545, 127.04) }
    val sites = remember(count, selected, ready, verified, feedback) {
        List(count) { i ->
            val style = if (i == 0 && verified) TerritoryPoleStyle.MINE_VERIFIED
                else TerritoryPoleStyle.entries[i % 5]
            TerritorySiteMarkerState("perf-$i",
                GeoPoint(center.latitude + (i / 25 - 10) * .00012, center.longitude + (i % 25 - 12) * .00012),
                selected = selected == i, occupancy = style.occupancy, isMine = style.isMine,
                occupancyKnown = true, label = "가상 장소 $i", ready = ready,
                radiusMeters = 20.0.takeIf { selected == i }, proximity = TerritoryProximityRange.IN_RANGE,
                feedback = feedback?.takeIf { selected == i && it.siteId == "perf-$i" })
        }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        fun reset() { probe.reset(); frames.reset() }
        fun result(phase: String) {
            val sorted = frames.millis.sorted()
            val p95 = sorted.getOrNull(((sorted.size - 1) * .95).toInt().coerceAtLeast(0)) ?: 0.0
            val text = String.format(Locale.US,
                "n=%d phase=%s create=%d remove=%d update=%d syncMs=%.2f frames=%d uiP95Ms=%.2f over16ms=%d dropped=%d framePasses=%d handleFrames=%d maxFrameTargets=%d",
                count, phase, probe.created, probe.removed, probe.updated, probe.syncNanos.sum() / 1_000_000.0,
                sorted.size, p95, sorted.count { it > 16.667 }, frames.dropped, probe.framePasses, probe.handleFrames, probe.maxFrameTargets)
            Log.i("TerritoryPerf", text)
            report = text
        }
        delay(4000) // Allow map initialization/tiles to settle before the repeatable operations.
        for (size in listOf(50, 200, 500)) {
            selected = -1; feedback = null; count = size
            delay(2000)
            reset()
            repeat(20) { selected = it % size; delay(250) }
            result("select20")
            selected = -1
            delay(500)
            reset()
            repeat(20) { ready = !ready; delay(100) }
            result("ready20")
            reset()
            repeat(10) { verified = !verified; delay(200) }
            result("occupancy10")
            selected = 0
            delay(300)
            reset()
            val now = System.nanoTime()
            feedback = TerritoryFeedback(now, "perf-0", TerritoryFeedbackKind.VERIFIED, now)
            delay(1400)
            feedback = null; selected = -1
            delay(300)
            result("effectAndClear")
        }
        Log.i("TerritoryPerf", "DONE")
        running = false
    }
    CompositionLocalProvider(LocalTerritoryOverlayProbe provides probe) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Text("개발용 · 전봇대 갱신 비교", Modifier.padding(8.dp))
            Row {
                listOf(50, 200, 500).forEach { size -> TextButton(enabled = !running, onClick = {
                    count = size; selected = -1; feedback = null
                }) { Text("$size") } }
                TextButton(enabled = !running, onClick = { running = true }) { Text("자동 비교") }
            }
            Text(report, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
            MapHost(scene = MapScene(territorySites = sites), searchOrigin = null, followDevice = false,
                centerOn = center, centerZoom = 16.0, onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
                onSelectTerritorySite = { selected = it.removePrefix("perf-").toInt(); feedback = null },
                onMapTap = { selected = -1; feedback = null }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Preview
@Composable
private fun TerritoryPerformanceLabPreview() { DaengsTheme { TerritoryPerformanceLab(remember { LabFrames() }, false) } }
