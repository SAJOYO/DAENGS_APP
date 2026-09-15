package com.daengs.app.ui.walk.records

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengsTheme

internal data class RecordsMapInsets(val top: Int = 0, val bottom: Int = 0)
internal val LocalRecordsMapInsets = compositionLocalOf { RecordsMapInsets() }

/** The map's measured size never changes when the related list is opened or selected. */
@Composable
internal fun WalkRecordsMapFrame(
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit,
    map: @Composable (Modifier) -> Unit,
    summary: @Composable () -> Unit,
    details: @Composable () -> Unit,
    records: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("records-map-frame")) {
        val wide = maxWidth >= 600.dp
        val panelWidth = if (wide) 340.dp.coerceAtMost(maxWidth * .45f) else maxWidth
        val panelHeight = if (wide) maxHeight else if (expanded) maxHeight * .61f
            else 112.dp.coerceAtMost(maxHeight * .42f)
        val density = LocalDensity.current
        var controlsHeight by remember { mutableIntStateOf(0) }
        val insets = with(density) { RecordsMapInsets(
            top = (controlsHeight + 24.dp.roundToPx()).coerceAtMost((maxHeight * .28f).roundToPx()),
            bottom = if (wide) 0 else panelHeight.roundToPx()) }
        // Mount with final overlay insets; a second initial fit would overwrite a restored camera.
        if (controlsHeight > 0) CompositionLocalProvider(LocalRecordsMapInsets provides insets) {
            map(Modifier.fillMaxSize().padding(end = if (wide) panelWidth else 0.dp))
        }
        Surface(Modifier.align(Alignment.TopStart).padding(12.dp)
            .widthIn(max = (maxWidth - if (wide) panelWidth else 0.dp) - 24.dp),
            shape = RoundedCornerShape(12.dp), shadowElevation = 2.dp) {
            Column(Modifier.testTag("records-map-controls").onSizeChanged { controlsHeight = it.height }
                .heightIn(max = maxHeight * .35f).verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp)) { controls() }
        }
        Surface(Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter)
            .width(panelWidth).height(panelHeight).testTag("records-map-sheet").semantics {
                stateDescription = if (wide || expanded) "펼침" else "접힘"
            }, shape = RoundedCornerShape(topStart = 24.dp, topEnd = if (wide) 0.dp else 24.dp),
            shadowElevation = 8.dp) {
            Column {
                if (!wide) {
                    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
                    val update by rememberUpdatedState(onExpanded)
                    TextButton(onClick = { onExpanded(!expanded) }, modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp).testTag("records-map-sheet-toggle")
                        .semantics { contentDescription = if (expanded) "산책 목록 접기" else "산책 목록 펼치기" }
                        .pointerInput(threshold) {
                            var drag = 0f
                            detectVerticalDragGestures(onDragStart = { drag = 0f },
                                onVerticalDrag = { change, amount -> change.consume(); drag += amount },
                                onDragEnd = { if (drag < -threshold) update(true) else if (drag > threshold) update(false) })
                        }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.width(32.dp).height(4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)))
                            Text("$title · ${if (expanded) "접기" else "펼치기"}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else Text(title, Modifier.padding(18.dp), style = MaterialTheme.typography.titleSmall)
                summary()
                if (expanded || wide) Column(Modifier.heightIn(max = panelHeight * .30f)
                    .verticalScroll(rememberScrollState())) { details() }
                if (expanded || wide) records(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 580)
@Composable
private fun WalkRecordsMapFramePreview() {
    var expanded by remember { mutableStateOf(false) }
    DaengsTheme { WalkRecordsMapFrame(expanded, { expanded = it }, "관련 산책 6회",
        controls = { WalkRecordsTraceControls(false, 2, {}, {}) },
        map = { Box(it.background(MaterialTheme.colorScheme.surfaceVariant)) },
        summary = { Text("선택 산책 6회 · 표시 흔적 5개", Modifier.padding(horizontal = 18.dp)) },
        details = {}, records = { Text("저녁 산책 · 두부 · 32분", it.padding(18.dp)) }) }
}
