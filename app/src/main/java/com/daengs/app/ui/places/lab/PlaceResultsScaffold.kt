package com.daengs.app.ui.places.lab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.places.PlaceSearchStyle
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.launch

/** One scrollable list stays mounted while its sheet moves and a detail sheet opens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceResultsScaffold(
    detailOpen: Boolean,
    header: @Composable ColumnScope.() -> Unit,
    categories: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    map: @Composable () -> Unit,
    results: LazyListScope.() -> Unit,
) {
    val sheet = rememberStandardBottomSheetState()
    val scaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheet)
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val expanded = sheet.targetValue == SheetValue.Expanded
    BackHandler(enabled = sheet.currentValue == SheetValue.Expanded && !detailOpen) {
        scope.launch { sheet.partialExpand() }
    }
    Column(Modifier.fillMaxSize().background(DaengsColors.AppBackground).safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().background(DaengsColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp), content = header)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val panelHeight = (maxHeight - 96.dp).coerceAtLeast(180.dp).coerceAtMost(maxHeight)
            val peek = (if (maxWidth < 372.dp) 246.dp else 220.dp).coerceAtMost(panelHeight)
            BottomSheetScaffold(
                scaffoldState = scaffold,
                sheetPeekHeight = peek,
                sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                sheetContainerColor = DaengsColors.Surface,
                sheetContentColor = DaengsColors.TextPrimary,
                sheetShadowElevation = 6.dp,
                sheetDragHandle = null,
                containerColor = DaengsColors.Surface,
                sheetContent = {
                    Column(Modifier.fillMaxWidth().height(panelHeight).testTag("place-results-sheet")) {
                        PlaceResultsHandle(expanded) {
                            scope.launch { if (expanded) sheet.partialExpand() else sheet.expand() }
                        }
                        controls()
                        HorizontalDivider(Modifier.padding(top = 4.dp), color = PlaceSearchStyle.Border)
                        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("place-results-list"),
                            contentPadding = PaddingValues(bottom = 16.dp), content = results)
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    AnimatedVisibility(visible = !expanded && !detailOpen) {
                        Column(Modifier.fillMaxWidth().background(DaengsColors.Surface).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            categories()
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) { map() }
                }
            }
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
