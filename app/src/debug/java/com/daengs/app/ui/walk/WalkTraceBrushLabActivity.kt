package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.map.layers.traces.TraceBrush
import com.daengs.app.map.layers.traces.TraceBrushPolicy
import com.daengs.app.map.layers.traces.TraceRasterTile
import com.daengs.app.map.layers.traces.WalkTraceMask
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Login-free, debug-only visual gate. Its synthetic sheets never enter real records. */
class WalkTraceBrushLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DaengsTheme { WalkTraceBrushLab() } }
    }
}

@Composable
private fun WalkTraceBrushLab() {
    val policy = remember { TraceBrushPolicy() }
    var masks by remember { mutableStateOf<List<WalkTraceMask>?>(null) }
    var tiles by remember { mutableStateOf<List<TraceRasterTile>>(emptyList()) }
    var includeSecond by rememberSaveable { mutableStateOf(true) }
    var hideSecond by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var maskError by remember { mutableStateOf<String?>(null) }
    var compositionError by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    LaunchedEffect(retry) {
        masks = null
        maskError = null
        try {
            masks = withContext(Dispatchers.Default) {
                val calculationContext = currentCoroutineContext()
                WalkTraceBrushLabFixture.sheets().map { sheet ->
                    ensureActive()
                    TraceBrush.mask(sheet, policy) { calculationContext.ensureActive() }
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            maskError = "산책 흔적을 만들지 못했어요. 다시 계산해 주세요."
        }
    }
    LaunchedEffect(masks, includeSecond, hideSecond) {
        tiles = emptyList()
        compositionError = null
        val ready = masks ?: return@LaunchedEffect
        composing = true
        try {
            val visible = if (includeSecond && !hideSecond) ready else ready.take(1)
            tiles = withContext(Dispatchers.Default) {
                val calculationContext = currentCoroutineContext()
                TraceBrush.compose(visible, policy.baseAlpha) { calculationContext.ensureActive() }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            compositionError = "흔적을 겹쳐 표시하지 못했어요. 다시 계산해 주세요."
        } finally {
            composing = false
        }
    }
    WalkTraceBrushLabContent(
        tiles = tiles,
        includeSecond = includeSecond,
        hideSecond = hideSecond,
        baseAlpha = policy.baseAlpha.toFloat(),
        loading = masks == null && maskError == null || composing,
        error = maskError ?: compositionError,
        onIncludeSecond = { includeSecond = it; hideSecond = false },
        onHideSecond = { hideSecond = it },
        onRetry = { retry++ },
    )
}

@Composable
private fun WalkTraceBrushLabContent(
    tiles: List<TraceRasterTile>,
    includeSecond: Boolean,
    hideSecond: Boolean,
    baseAlpha: Float,
    loading: Boolean,
    error: String?,
    onIncludeSecond: (Boolean) -> Unit,
    onHideSecond: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val selectedCount = if (includeSecond) 2 else 1
    val visibleCount = if (includeSecond && !hideSecond) 2 else 1
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("산책 흔적", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text("표시 검토 · 가상의 산책 2회", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !includeSecond, onClick = { onIncludeSecond(false) },
                    label = { Text("한 산책") })
                FilterChip(selected = includeSecond, onClick = { onIncludeSecond(true) },
                    label = { Text("두 산책 겹치기") })
            }
            Text("선택 산책 ${selectedCount}회 · 표시 흔적 ${visibleCount}개",
                style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapHost(
                scene = MapScene(traceTiles = tiles),
                searchOrigin = null,
                followDevice = false,
                fitBounds = WalkTraceBrushLabFixture.bounds,
                onCameraIdle = {},
                onCameraGesture = {},
                onSelectPlace = {},
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) {
                Surface(Modifier.align(Alignment.TopCenter).padding(12.dp),
                    shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("흔적을 준비하고 있어요", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            error?.let {
                Surface(Modifier.align(Alignment.Center).padding(20.dp),
                    shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onRetry) { Text("다시 계산") }
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("두 번째 산책을 지도에서 숨기기", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                Switch(checked = hideSecond, onCheckedChange = onHideSecond, enabled = includeSecond)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TraceLegendDot("한 산책", baseAlpha)
                TraceLegendDot("두 산책이 겹친 곳", 1f - (1f - baseAlpha) * (1f - baseAlpha))
            }
            Text("같은 산책에서 다시 걸은 곳은 진해지지 않아요.\n서로 다른 산책이 겹친 곳만 더 진해져요.",
                style = MaterialTheme.typography.bodySmall)
            Text("오른쪽 작은 흔적은 떨어진 한 구간이에요. 사이를 이어 그리지 않아요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TraceLegendDot(label: String, alpha: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).background(Color(0xFFB82758).copy(alpha = alpha), CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WalkTraceBrushLabPreview() {
    DaengsTheme {
        WalkTraceBrushLabContent(emptyList(), true, false, TraceBrushPolicy().baseAlpha.toFloat(), false, null, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun TraceLegendDotPreview() {
    DaengsTheme { TraceLegendDot("한 산책", TraceBrushPolicy().baseAlpha.toFloat()) }
}
