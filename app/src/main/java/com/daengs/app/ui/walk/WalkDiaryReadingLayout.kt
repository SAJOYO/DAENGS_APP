package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.R
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.DiarySceneContent
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Stable map geometry; sheet position is deliberately not part of this value. */
internal data class DiaryMapViewport(val bottomPaddingPx: Int, val selectionYFraction: Float)

/** The sheet overlays one fixed map. Swiping it never issues a camera request. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalkDiaryMapContent(
    scenes: List<DiaryScene>, selected: DiaryScene?, loading: Boolean, error: String?,
    onSelect: (DiaryScene) -> Unit, onClose: () -> Unit, onEdit: (DiaryScene) -> Unit,
    onPhoto: (WalkPhoto) -> Unit, onRetry: () -> Unit, onAdd: () -> Unit,
    adding: Boolean = false, modifier: Modifier = Modifier, map: @Composable (DiaryMapViewport) -> Unit,
    generationNotice: String? = null, generating: Boolean = false, onGenerate: (() -> Unit)? = null,
    title: String = "산책 일기", subtitle: String = "", onBack: () -> Unit = {},
    mapSettings: @Composable () -> Unit = {},
    onOverview: () -> Unit = {},
    generationActionLabel: String = "일기 생성·갱신",
    backLabel: String = "산책 목록으로",
    summaryContent: @Composable () -> Unit = {},
    backupAction: @Composable () -> Unit = {},
    directionNotice: Boolean = false,
    onZoomRoute: () -> Unit = {},
    explorerSelected: Boolean = false,
    onChooseExplorer: (Boolean) -> Unit = {},
    explorerPanel: (@Composable () -> Unit)? = null,
) {
    val sheet = rememberStandardBottomSheetState(
        initialValue = if (selected == null) SheetValue.PartiallyExpanded else SheetValue.Expanded)
    val scaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheet)
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val latestClose by rememberUpdatedState(onClose)
    var menu by remember { mutableStateOf(false) }
    val expanded = sheet.targetValue == SheetValue.Expanded
    LaunchedEffect(selected?.id, adding) {
        if (selected != null && !adding) sheet.expand() else sheet.partialExpand()
    }
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.currentValue }.drop(1).collect {
            if (it == SheetValue.PartiallyExpanded) latestClose()
        }
    }
    BackHandler(enabled = expanded && selected == null) { scope.launch { sheet.partialExpand() } }
    Column(modifier.fillMaxSize().background(CreamBg)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backLabel }) {
                Text("‹", fontSize = 30.sp, color = TextDark)
            }
            Text(subtitle.ifBlank { "산책 일기" },
                Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = TextMuted)
            backupAction()
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "일기 메뉴" }) {
                    Text("⋯", fontSize = 26.sp, color = TextDark)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("전체 동선 보기") },
                        onClick = { menu = false; onClose(); onOverview(); scope.launch { sheet.partialExpand() } })
                    DropdownMenuItem(text = { Text("기록 남기기") }, enabled = !loading && error == null,
                        onClick = { menu = false; onAdd() })
                    onGenerate?.let { generate ->
                        DropdownMenuItem(text = { Text(if (generating) "준비 중" else generationActionLabel) },
                            enabled = !generating, onClick = { menu = false; generate() })
                    }
                }
            }
        }
        Text(title,
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
            color = TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
        summaryContent()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val mapPeek = (maxHeight * .25f).coerceIn(96.dp, 180.dp).coerceAtMost(maxHeight * .4f)
            val panelHeight = maxHeight - mapPeek
            val peek = if (explorerPanel == null)
                (maxHeight * .34f).coerceIn(180.dp, 260.dp).coerceAtMost(panelHeight)
            else (maxHeight * .43f).coerceIn(210.dp, 280.dp).coerceAtMost(maxHeight * .6f)
            // Padding/fit use the browsing viewport even while the sheet covers more of the map.
            // Only an explicit scene selection uses the upper, still-visible band as its pivot.
            val viewport = DiaryMapViewport(with(LocalDensity.current) { peek.roundToPx() },
                (mapPeek.value / (2f * (maxHeight - peek).value)).coerceIn(0f, 1f))
            BottomSheetScaffold(
                scaffoldState = scaffold, sheetPeekHeight = peek,
                sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                sheetContainerColor = CardWhite, sheetContentColor = TextDark,
                sheetShadowElevation = 8.dp, sheetDragHandle = null,
                containerColor = CreamBg,
                sheetContent = {
                    Column(Modifier.fillMaxWidth().height(panelHeight).testTag("diary-sheet")) {
                        val showSceneHeading = selected != null || explorerPanel == null
                        Surface(onClick = {
                            if (expanded) { onClose(); scope.launch { sheet.partialExpand() } }
                            else scope.launch { sheet.expand() }
                        }, color = CardWhite, modifier = Modifier.fillMaxWidth()
                            .height(if (showSceneHeading) 52.dp else 24.dp)
                            .testTag("diary-sheet-handle").semantics {
                                contentDescription = if (expanded) "지도 넓게 보기" else "상세 패널 펼치기"
                            }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = if (showSceneHeading) Arrangement.Top else Arrangement.Center) {
                                Box(Modifier.padding(top = if (showSceneHeading) 8.dp else 0.dp).width(32.dp).height(4.dp)
                                    .background(PinkSoft, RoundedCornerShape(4.dp)))
                                if (showSceneHeading) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (loading) "산책 장면" else if (selected == null) "${scenes.size}개 장면 · 시간순" else "‹ 장면 목록",
                                        Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    if (selected != null) Text("장면 ${scenes.indexOfFirst { it.id == selected.id } + 1}",
                                        fontSize = 13.sp, color = TextMuted)
                                }
                            }
                        }
                        if (explorerPanel != null) TabRow(selectedTabIndex = if (explorerSelected) 1 else 0) {
                            Tab(selected = !explorerSelected, onClick = { onChooseExplorer(false) },
                                text = { Text("장면 " + scenes.size) })
                            Tab(selected = explorerSelected, onClick = { onChooseExplorer(true) },
                                text = { Text("동선 탐색") })
                        }
                        if (adding) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("동선에서 위치를 골라 주세요.", Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = onAdd) { Text("취소") }
                        }
                        generationNotice?.takeIf { it.isNotBlank() }?.let {
                            Text(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        if (directionNotice) Row(Modifier.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("현재 화면에서는 방향을 표시하기 어려워요.", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            TextButton(onClick = onZoomRoute) { Text("동선 확대") }
                        }
                        if (error != null && !loading) Row(Modifier.padding(horizontal = 20.dp)) {
                            Text(error, Modifier.weight(1f))
                            TextButton(onClick = onRetry) { Text("다시 시도") }
                        }
                        if (explorerSelected && explorerPanel != null) {
                            Box(Modifier.weight(1f).fillMaxWidth()) { explorerPanel() }
                        } else if (loading) {
                            WalkDiaryPreparing(onRefresh = onRetry, error = error)
                        } else if (selected == null) {
                            if (scenes.isEmpty() && error == null) {
                                Text("아직 남긴 장면이 없어요.", Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                                TextButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 12.dp)) { Text("기록 남기기") }
                            }
                            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 20.dp)) {
                                itemsIndexed(scenes, key = { _, it -> it.id }) { index, scene ->
                                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { onSelect(scene) }, modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                                            Surface(shape = CircleShape, color = PinkFaint, modifier = Modifier.size(32.dp)) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("${index + 1}", fontWeight = FontWeight.Bold, color = DaengPinkDeep)
                                                }
                                            }
                                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                                Text(scene.title, color = TextDark, fontSize = 18.sp, lineHeight = 24.sp,
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                                Text(formatWalkClock(scene.atMillis), fontSize = 13.sp, color = TextMuted)
                                            }
                                        }
                                        IconButton(onClick = { onEdit(scene) }) {
                                            Icon(painterResource(R.drawable.ic_diary_edit), "장면 ${index + 1} 수정",
                                                Modifier.size(20.dp), tint = TextMuted)
                                        }
                                    }
                                }
                            }
                        } else {
                            key(selected.id) {
                                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("diary-scene-body"),
                                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(selected.title, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                                                Text(listOfNotNull(formatWalkClock(selected.atMillis),
                                                    selected.content?.address?.takeIf(String::isNotBlank)).joinToString(" · "),
                                                    fontSize = 13.sp, color = TextMuted)
                                            }
                                            IconButton(onClick = { onEdit(selected) }) {
                                                Icon(painterResource(R.drawable.ic_diary_edit), "장면 수정", Modifier.size(22.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        DiarySceneText(selected.body)
                                        if (selected.needsReview) Text("원본 기록이 바뀌었어요. 수정한 문장은 유지했어요.",
                                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                                        selected.photo?.let { photo -> TextButton(onClick = { onPhoto(photo) }) { Text("사진 보기") } }
                                        if (selected.content?.photoId != null && selected.photo == null)
                                            Text("사진 파일은 촬영한 기기에서 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            HorizontalDivider(color = PinkFaint)
                            val index = scenes.indexOfFirst { it.id == selected.id }
                            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(enabled = index > 0, onClick = { scenes.getOrNull(index - 1)?.let(onSelect) }) { Text("이전") }
                                Text("${index + 1} / ${scenes.size}", color = TextMuted, fontSize = 14.sp)
                                TextButton(enabled = index in 0 until scenes.lastIndex,
                                    onClick = { scenes.getOrNull(index + 1)?.let(onSelect) }) { Text("다음") }
                            }
                        }
                    }
                },
            ) {
                Box(Modifier.fillMaxSize()) {
                    map(viewport)
                    Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), shape = CircleShape,
                        color = CardWhite, shadowElevation = 2.dp) { mapSettings() }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun DiaryReadingPreview() {
    val a = DiaryScene("s/n", "s", 0, "두부와 잠깐 쉬어 간 길", "공원으로 이어지는 길 옆이었다. 두부랑 사진 한 장!\n잠깐 쉬었다가 다시 걸었다.", null, "",
        content = DiarySceneContent("두부랑 사진 한 장!\n잠깐 쉬었다가 다시 걸었다.", "note", locationLabel = "기록한 위치"))
    DaengsTheme { WalkDiaryMapContent(listOf(a), a, false, null, {}, {}, {}, {}, {}, {},
        title = "두부와 함께한 저녁 산책", subtitle = "9월 9일 · 저녁",
        summaryContent = { WalkSessionSummary(com.daengs.app.walk.WalkSummary("s", emptyList(), 0, 1_800_000,
            null, 1_200.0, 1_800_000, emptyList(), null), listOf("두부")) },
        map = { Box(Modifier.fillMaxSize().background(PinkFaint)) }) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DiaryPreparingMapPreview() {
    DaengsTheme { WalkDiaryMapContent(emptyList(), null, true, null, {}, {}, {}, {}, {}, {},
        title = "9월 11일 산책", subtitle = "9월 11일 · 오후",
        explorerPanel = { Text("동선 탐색") },
        map = { Box(Modifier.fillMaxSize().background(PinkFaint)) }) }
}
