package com.daengs.app.ui.places.lab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.places.PlaceSearchStyle
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One scrollable list stays mounted while its sheet moves and a detail sheet opens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceResultsScaffold(
    detailOpen: Boolean,
    header: @Composable ColumnScope.() -> Unit,
    categories: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    map: @Composable () -> Unit,
    listState: LazyListState? = null,
    collapseRequest: Int = 0,
    navigation: (@Composable (Boolean, () -> Unit, () -> Unit) -> Unit)? = null,
    initialPosition: PlaceResultsPosition = PlaceResultsPosition.Preview,
    results: LazyListScope.() -> Unit,
) {
    val sheet = rememberSaveable(saver = AnchoredDraggableState.Saver()) { AnchoredDraggableState(initialPosition) }
    val fling = AnchoredDraggableDefaults.flingBehavior(sheet)
    val nestedScroll = remember(sheet, fling) { placeResultsNestedScroll(sheet, fling) }
    val scope = rememberCoroutineScope()
    val defaultList = rememberLazyListState()
    val list = listState ?: defaultList
    val expanded = sheet.targetValue == PlaceResultsPosition.Expanded
    LaunchedEffect(collapseRequest) {
        if (collapseRequest > 0) sheet.animateTo(PlaceResultsPosition.Collapsed)
    }
    BackHandler(enabled = sheet.currentValue == PlaceResultsPosition.Expanded && !detailOpen) {
        scope.launch { sheet.animateTo(PlaceResultsPosition.Preview) }
    }
    Column(Modifier.fillMaxSize().background(DaengsColors.AppBackground).safeDrawingPadding()) {
        // Keep the 48dp search controls; reclaim only the gap before categories (12 -> 4dp).
        Column(Modifier.fillMaxWidth().background(DaengsColors.Surface)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), content = header)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val density = LocalDensity.current
            var handleHeight by remember { mutableStateOf(if (navigation != null) 56.dp else 48.dp) }
            val panelHeight = (maxHeight - 96.dp).coerceAtLeast(180.dp).coerceAtMost(maxHeight)
            val peek = (if (maxWidth < 372.dp) 246.dp else 220.dp).coerceAtMost(panelHeight)
            val collapsedHeight = handleHeight.coerceAtMost(panelHeight)
            val anchors = with(density) {
                DraggableAnchors {
                    PlaceResultsPosition.Expanded at (maxHeight - panelHeight).toPx()
                    PlaceResultsPosition.Preview at (maxHeight - peek).toPx()
                    PlaceResultsPosition.Collapsed at (maxHeight - collapsedHeight).toPx()
                }
            }
            SideEffect { sheet.updateAnchors(anchors, sheet.targetValue) }
            val mapBottom = if (sheet.targetValue == PlaceResultsPosition.Collapsed) collapsedHeight else peek
            Column(Modifier.fillMaxSize().padding(bottom = mapBottom)) {
                AnimatedVisibility(visible = !expanded && !detailOpen) {
                    Column(Modifier.fillMaxWidth().background(DaengsColors.Surface).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        categories()
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { map() }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = BottomSheetDefaults.SheetMaxWidth)
                    .fillMaxWidth().height(panelHeight)
                    .offset { IntOffset(0, (sheet.offset.takeUnless { it.isNaN() }
                        ?: anchors.positionOf(initialPosition)).roundToInt()) }
                    .nestedScroll(nestedScroll)
                    .anchoredDraggable(sheet, Orientation.Vertical, enabled = !detailOpen, flingBehavior = fling)
                    .testTag("place-results-sheet").semantics {
                        stateDescription = when (sheet.targetValue) {
                            PlaceResultsPosition.Collapsed -> "목록 접힘"
                            PlaceResultsPosition.Preview -> "결과 미리 보기"
                            PlaceResultsPosition.Expanded -> "목록 펼침"
                        }
                    },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = DaengsColors.Surface, contentColor = DaengsColors.TextPrimary, shadowElevation = 6.dp,
            ) {
                Column {
                    val toggle: () -> Unit = {
                        scope.launch { sheet.animateTo(if (expanded) PlaceResultsPosition.Collapsed else PlaceResultsPosition.Expanded) }
                    }
                    val reveal: () -> Unit = {
                        if (sheet.targetValue == PlaceResultsPosition.Collapsed) scope.launch { sheet.animateTo(PlaceResultsPosition.Preview) }
                    }
                    Box(Modifier.fillMaxWidth().onSizeChanged { handleHeight = with(density) { it.height.toDp() } }) {
                        if (navigation != null) navigation(expanded, toggle, reveal)
                        else PlaceResultsHandle(expanded, toggle)
                    }
                    controls()
                    HorizontalDivider(Modifier.padding(top = 4.dp), color = PlaceSearchStyle.Border)
                    LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("place-results-list"),
                        contentPadding = PaddingValues(bottom = 16.dp), content = results)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CollapsedResultsScaffoldPreview() {
    DaengsTheme {
        PlaceResultsScaffold(false, { Text("내 주변 검색") }, { Text("카테고리") }, { Text("카페 12곳", Modifier.padding(16.dp)) },
            { Box(Modifier.fillMaxSize().background(DaengsColors.SurfaceMuted)) },
            navigation = { expanded, toggle, _ ->
                com.daengs.app.ui.places.PlaceBookmarkHandle(com.daengs.app.ui.places.PlaceBrowseTab.SEARCH, expanded, {}, toggle)
            }, initialPosition = PlaceResultsPosition.Collapsed) {
            items(12) { PlaceResultRow(PreviewPlaceHit(), false, {}) }
        }
    }
}

@Composable
private fun PlaceResultsHandle(expanded: Boolean, onToggle: () -> Unit) {
    Surface(onClick = onToggle, color = DaengsColors.Surface,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("place-results-handle").semantics {
            contentDescription = if (expanded) "지도 보기" else "목록 보기"
            stateDescription = if (expanded) "목록 펼침" else "지도 중심"
        }) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).background(PlaceSearchStyle.Border, RoundedCornerShape(4.dp)))
            Text(if (expanded) "지도 보기 ↓" else "목록 보기 ↑", Modifier.align(Alignment.CenterEnd),
                fontSize = 11.sp, color = DaengsColors.TextSecondary)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun ResultsScaffoldPreview() {
    DaengsTheme {
        PlaceResultsScaffold(false, { Text("내 주변 검색") }, { Text("카테고리") }, { Text("카페 12곳", Modifier.padding(16.dp)) },
            { Box(Modifier.fillMaxSize().background(DaengsColors.SurfaceMuted)) }) {
            items(12) { PlaceResultRow(PreviewPlaceHit(), false, {}) }
        }
    }
}
