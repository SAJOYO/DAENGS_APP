package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.R
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.DiarySceneContent
import com.daengs.app.walk.diary.DiarySceneKind
import com.daengs.app.walk.trajectory.RecordContext
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Camera padding stays stable. Occlusion alone follows the actual sheet position. */
internal data class DiaryMapViewport(val bottomPaddingPx: Int, val selectionYFraction: Float,
    val contextBottomPaddingPx: Int = bottomPaddingPx, val bottomOcclusionPx: Int = bottomPaddingPx,
    val controlsWidthPx: Int = 0, val controlsHeightPx: Int = 0, val settingsCoverPx: Int = 0)

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
    explorerPanel: (@Composable (@Composable () -> Unit) -> Unit)? = null,
    onSlotPreview: (() -> Unit)? = null,
    onPlaceComparison: (() -> Unit)? = null,
    comparisonContent: @Composable () -> Unit = {},
    selectedRouteNotice: String? = null,
    gapContexts: List<RecordContext> = emptyList(),
    selectedGap: RecordContext? = null,
    onSelectGap: (RecordContext) -> Unit = {},
    explorerFocusId: String? = null,
    onContextDismiss: () -> Unit = {},
    selectionFromMap: Boolean = false,
    selectionPending: Boolean = false,
    mapView: DiaryMapView? = null,
    onWalkingOverview: () -> Unit = {},
    offscreenScenes: List<DiaryScene> = emptyList(),
    readingMemory: DiaryReadingMemory? = null,
    onDelete: ((DiaryScene) -> Unit)? = null,
    sceneGroup: List<DiaryScene>? = null,
    onClearGroup: () -> Unit = {},
    sceneKinds: Map<String, DiarySceneKind> = emptyMap(),
    walkDogIds: List<String> = emptyList(),
    walkPets: List<Pet> = emptyList(),
    onReturnToRange: (() -> Unit)? = null,
    onSceneNeighborhood: (() -> Unit)? = null,
) {
    val compactDrawer = explorerPanel != null
    val sheet = readingMemory?.drawer ?: rememberDiaryDrawerState(
        initialValue = if (compactDrawer || selected == null || selectionFromMap) DiaryDrawerValue.Browsing else DiaryDrawerValue.Expanded,
        compactEnabled = compactDrawer)
    val scope = rememberCoroutineScope()
    val fullList = readingMemory?.list ?: rememberLazyListState()
    val groupList = readingMemory?.groupList ?: rememberLazyListState()
    val list = if (sceneGroup != null) groupList else fullList
    val displayedScenes = sceneGroup ?: scenes
    val ordinals = remember(scenes) { scenes.withIndex().associate { it.value.id to it.index+1 } }
    val gapSlots = remember(scenes, gapContexts, sceneGroup) { if (sceneGroup != null) emptyMap() else diaryGapSlots(scenes, gapContexts) }
    LaunchedEffect(readingMemory?.pendingList, loading, selected == null, explorerSelected, sceneGroup == null) {
        val saved = readingMemory?.pendingList
        if (!loading && selected == null && !explorerSelected && saved != null && sceneGroup == null) {
            val keys = buildList { scenes.forEachIndexed { index, scene ->
                gapSlots[index].orEmpty().forEach { add("gap:${it.id}") }; add("scene:${scene.id}")
            }; gapSlots[scenes.size].orEmpty().forEach { add("gap:${it.id}") } }
            val index = keys.indexOf(saved.optString("key"))
            if (index >= 0) list.scrollToItem(index, saved.optInt("offset").coerceIn(0, 100_000))
            readingMemory.pendingList = null
        }
    }
    val latestClose by rememberUpdatedState(onClose)
    var menu by remember { mutableStateOf(false) }
    val expanded = sheet.targetValue == DiaryDrawerValue.Expanded
    // With tabs, the user owns the drawer height. Selection/loading/replay updates only
    // change its contents; even a late replacement scene must not open it again.
    LaunchedEffect(compactDrawer, adding) {
        if (compactDrawer && adding) sheet.showDetails()
    }
    LaunchedEffect(selected?.id, adding, explorerFocusId, selectionFromMap, selectionPending) {
        if (compactDrawer) return@LaunchedEffect
        // A visible marker is already in view. Keep the user's map and sheet framing on a map tap.
        if ((selectionFromMap || selectionPending) && !adding) return@LaunchedEffect
        if (selectedGap == null && (selected != null || explorerFocusId != null) && !adding) sheet.expand()
        else sheet.partialExpand()
    }
    LaunchedEffect(sheet, compactDrawer) {
        if (compactDrawer) return@LaunchedEffect
        snapshotFlow { sheet.currentValue }.drop(1).collect {
            if (it == DiaryDrawerValue.Browsing) latestClose()
        }
    }
    BackHandler(enabled = if (compactDrawer) sheet.targetValue != DiaryDrawerValue.Compact else expanded && selected == null) {
        if (!compactDrawer) onContextDismiss()
        scope.launch { sheet.lower() }
    }
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
                    onPlaceComparison?.let { compare ->
                        DropdownMenuItem(text = { Text("현재 장면 설명 비교") }, enabled = !loading,
                            onClick = { menu = false; compare() })
                    }
                    onSlotPreview?.let { preview ->
                        DropdownMenuItem(text = { Text("개발용 일기 미리보기") },
                            enabled = !loading, onClick = { menu = false; preview() })
                    }
                }
            }
        }
        DiaryReadingHeader(title, walkDogIds, walkPets)
        summaryContent()
        comparisonContent()
        mapView?.let { view -> Box(Modifier.padding(start = 20.dp, bottom = 8.dp)) {
            DiaryRecordMapButtons(view,
                onWalking = { onClose(); onContextDismiss(); onWalkingOverview(); scope.launch { sheet.partialExpand() } },
                onWhole = { onClose(); onContextDismiss(); onOverview(); scope.launch { sheet.partialExpand() } })
        } }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val density = LocalDensity.current
            var tabHeightPx by remember(density) { mutableIntStateOf(0) }
            val mapPeek = (maxHeight * .25f).coerceIn(96.dp, 180.dp).coerceAtMost(maxHeight * .4f)
            val panelHeight = maxHeight - mapPeek
            val peek = if (!compactDrawer)
                (maxHeight * .34f).coerceIn(180.dp, 260.dp).coerceAtMost(panelHeight)
            else (maxHeight * .43f).coerceIn(210.dp, 280.dp).coerceAtMost(maxHeight * .6f)
            val compactHeight = (24.dp + with(density) { tabHeightPx.toDp() }.coerceAtLeast(48.dp)).coerceAtMost(peek)
            // Padding/fit use the browsing viewport even while the sheet covers more of the map.
            // Only an explicit scene selection uses the upper, still-visible band as its pivot.
            val heightPx = with(density) { maxHeight.roundToPx() }
            val peekPx = with(density) { peek.roundToPx() }
            val occlusion by remember(sheet, heightPx, peekPx) { derivedStateOf {
                val offset = runCatching { sheet.requireOffset() }.getOrNull()?.takeIf { it.isFinite() }
                offset?.let { (heightPx - it).toInt().coerceIn(0, heightPx) } ?: peekPx
            } }
            val viewport = DiaryMapViewport(peekPx,
                (mapPeek.value / (2f * (maxHeight - peek).value)).coerceIn(0f, 1f),
                with(density) { panelHeight.roundToPx() }, occlusion,
                0, 0,
                with(density) { 68.dp.roundToPx() })
            DiaryDrawerLayout(
                state = sheet, height = maxHeight, expandedHeight = panelHeight,
                browsingHeight = peek, compactHeight = compactHeight,
                sheetContent = { contentHeight ->
                    Column(Modifier.fillMaxWidth().height(contentHeight).testTag("diary-sheet")) {
                        val showSceneHeading = !compactDrawer
                        Surface(onClick = {
                            if (compactDrawer) scope.launch {
                                if (expanded) sheet.partialExpand() else sheet.raise()
                            } else if (expanded) {
                                if (!compactDrawer) { onClose(); onContextDismiss() }
                                scope.launch { sheet.partialExpand() }
                            }
                            else scope.launch { sheet.expand() }
                        }, color = CardWhite, modifier = Modifier.fillMaxWidth()
                            .height(if (showSceneHeading) 52.dp else 24.dp)
                            .testTag("diary-sheet-handle").semantics {
                                contentDescription = if (expanded) "지도 넓게 보기" else "상세 패널 펼치기"
                                if (compactDrawer) {
                                    stateDescription = when (sheet.targetValue) {
                                        DiaryDrawerValue.Compact -> "탭만 보기"
                                        DiaryDrawerValue.Browsing -> "장면과 지도 함께 보기"
                                        DiaryDrawerValue.Expanded -> "상세 넓게 보기"
                                    }
                                    if (!expanded) expand { scope.launch { sheet.raise() }; true }
                                    if (sheet.targetValue != DiaryDrawerValue.Compact) collapse { scope.launch { sheet.lower() }; true }
                                }
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
                        if (explorerPanel != null) TabRow(selectedTabIndex = if (explorerSelected) 1 else 0,
                            containerColor = CardWhite, contentColor = TextDark,
                            divider = { HorizontalDivider(color = PinkFaint) },
                            modifier = Modifier.onSizeChanged { tabHeightPx = it.height }.testTag("diary-tabs")) {
                            Tab(selected = !explorerSelected, onClick = {
                                if (explorerSelected) onChooseExplorer(false)
                                scope.launch { sheet.showDetails() }
                            },
                                selectedContentColor = TextDark, unselectedContentColor = TextMuted,
                                text = { Text("장면 " + scenes.size, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) })
                            Tab(selected = explorerSelected, onClick = {
                                if (!explorerSelected) onChooseExplorer(true)
                                scope.launch { sheet.showDetails() }
                            },
                                selectedContentColor = TextDark, unselectedContentColor = TextMuted,
                                text = { Text("동선 탐색", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) })
                        }
                        val showSceneActions = compactDrawer && !explorerSelected && selected != null
                        if (showSceneActions) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onClose(); onContextDismiss() }) {
                                    Text(if (sceneGroup != null) "‹ 이 근처 장면 ${sceneGroup.size}개" else "‹ 장면 목록")
                                }
                                Spacer(Modifier.weight(1f))
                                Text("장면 ${scenes.indexOfFirst { it.id == selected?.id } + 1}",
                                    Modifier.padding(end = 8.dp), fontSize = 13.sp, color = TextMuted)
                                onReturnToRange?.let { back -> TextButton(onClick = back) { Text("구간 복귀") } }
                                if (offscreenScenes.isNotEmpty()) DiaryOffscreenMenu(scenes, offscreenScenes, onSelect)
                            }
                        }
                        if ((!explorerSelected || explorerPanel == null) && !showSceneActions && offscreenScenes.isNotEmpty()) DiaryOffscreenMenu(scenes, offscreenScenes, onSelect)
                        if (sceneGroup != null && selected == null && !explorerSelected) {
                            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                val same = sceneGroup.mapNotNull { it.point }.distinct().size == 1
                                Text("${if (same) "같은 위치" else "이 근처"} 장면 ${sceneGroup.size}개",
                                    Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                TextButton(onClick = onClearGroup) { Text("전체 장면") }
                            }
                        }
                        if (adding) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("동선에서 위치를 골라 주세요.", Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = onAdd) { Text("취소") }
                        }
                        if (!explorerSelected || explorerPanel == null) DiaryReadingNotices(generationNotice,
                            directionNotice, error.takeUnless { loading }, onZoomRoute, onRetry)
                        if (explorerSelected && explorerPanel != null) {
                            Box(Modifier.weight(1f).fillMaxWidth()) { explorerPanel {
                                if (offscreenScenes.isNotEmpty()) DiaryOffscreenMenu(scenes, offscreenScenes, onSelect)
                                DiaryReadingNotices(generationNotice, directionNotice, error.takeUnless { loading }, onZoomRoute, onRetry)
                            } }
                        } else if (loading) {
                            WalkDiaryPreparing(onRefresh = onRetry, error = error)
                        } else if (selectedGap != null) {
                            DiaryGapDetail(selectedGap, onContextDismiss)
                        } else if (selected == null) {
                            if (scenes.isEmpty() && gapSlots.isEmpty() && error == null) {
                                Text("아직 남긴 장면이 없어요.", Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                                TextButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 12.dp)) { Text("기록 남기기") }
                            }
                            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("diary-scene-list"),
                                contentPadding = PaddingValues(bottom = 20.dp)) {
                                displayedScenes.forEachIndexed { index, scene ->
                                    gapSlots[index].orEmpty().forEach { gap ->
                                        item(key = "gap:${gap.id}") { DiaryGapItem(gap) { onSelectGap(gap) } }
                                    }
                                    item(key = "scene:${scene.id}") {
                                        Surface(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp), color = CardWhite,
                                            border = BorderStroke(1.dp, DaengsColors.BorderNeutral)) {
                                            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                DiarySceneListButton(scene, sceneKinds[scene.id] ?: DiarySceneKind.GENERAL,
                                                    onClick = { onSelect(scene) }, modifier = Modifier.weight(1f), ordinal = ordinals[scene.id])
                                                IconButton(onClick = { onEdit(scene) }) {
                                                    Icon(painterResource(R.drawable.ic_diary_edit), "장면 ${ordinals[scene.id]} 수정",
                                                        Modifier.size(20.dp), tint = TextMuted)
                                                }
                                                onDelete?.let { remove ->
                                                    IconButton(onClick = { remove(scene) }) {
                                                        Icon(painterResource(R.drawable.ic_diary_delete), "장면 ${ordinals[scene.id]} 삭제",
                                                            Modifier.size(20.dp), tint = TextMuted)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                gapSlots[scenes.size].orEmpty().forEach { gap ->
                                    item(key = "gap:${gap.id}") { DiaryGapItem(gap) { onSelectGap(gap) } }
                                }
                            }
                        } else {
                            key(selected.id) {
                                val savedBody = readingMemory?.restoredBody?.takeIf { it.optString("id") == selected.id }
                                val bodyList = rememberLazyListState(savedBody?.optInt("index")?.coerceIn(0, 10_000) ?: 0,
                                    savedBody?.optInt("offset")?.coerceIn(0, 100_000) ?: 0)
                                LaunchedEffect(bodyList, selected.id) {
                                    readingMemory?.restoredBody = null
                                    snapshotFlow { bodyList.firstVisibleItemIndex to bodyList.firstVisibleItemScrollOffset }.collect { (index, offset) ->
                                        readingMemory?.let { it.bodyScene = selected.id; it.bodyIndex = index; it.bodyOffset = offset }
                                    }
                                }
                                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("diary-scene-body"),
                                    state = bodyList,
                                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            DiarySceneHeading(selected, sceneKinds[selected.id] ?: DiarySceneKind.GENERAL,
                                                Modifier.weight(1f), detail = true)
                                            IconButton(onClick = { onEdit(selected) }) {
                                                Icon(painterResource(R.drawable.ic_diary_edit), "장면 수정", Modifier.size(22.dp))
                                            }
                                            onDelete?.let { remove ->
                                                IconButton(onClick = { remove(selected) }) {
                                                    Icon(painterResource(R.drawable.ic_diary_delete), "장면 삭제",
                                                        Modifier.size(22.dp), tint = TextMuted)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        DiarySceneText(selected.body)
                                        selectedRouteNotice?.let { Text(it, Modifier.padding(top = 12.dp),
                                            style = MaterialTheme.typography.bodySmall, color = TextMuted) }
                                        if (selected.needsReview) Text("원본 기록이 바뀌었어요. 수정한 문장은 유지했어요.",
                                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                                        selected.photo?.let { photo -> DiaryReadingPhoto(photo) { onPhoto(photo) } }
                                        if (selected.content?.photoId != null && selected.photo == null)
                                            Text("사진 파일은 촬영한 기기에서 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
                                        DiarySceneExploreActions(if (compactDrawer) null else onReturnToRange, onSceneNeighborhood)
                                    }
                                }
                            }
                            if (!compactDrawer || expanded) {
                                HorizontalDivider(color = PinkFaint)
                                val index = displayedScenes.indexOfFirst { it.id == selected.id }
                                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(enabled = index > 0, onClick = { displayedScenes.getOrNull(index - 1)?.let(onSelect) }) { Text("이전") }
                                    Text("${index + 1} / ${displayedScenes.size}", color = TextMuted, fontSize = 14.sp)
                                    TextButton(enabled = index in 0 until displayedScenes.lastIndex,
                                        onClick = { displayedScenes.getOrNull(index + 1)?.let(onSelect) }) { Text("다음") }
                                }
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
        onDelete = {},
        selectedRouteNotice = "이 장면에는 확인된 위치가 없어요.",
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

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun DiaryCompactDrawerPreview() {
    val scene = DiaryScene("s/n", "s", 0, "잠깐 쉬었던 벤치", "함께 쉬었다가 다시 걸었다.", null, "")
    DaengsTheme { WalkDiaryMapContent(listOf(scene), null, false, null, {}, {}, {}, {}, {}, {},
        onDelete = {},
        explorerPanel = { Text("동선을 골라 살펴보세요.") },
        map = { Box(Modifier.fillMaxSize().background(PinkFaint)) }) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DiaryGroupReadingPreview() { DaengsTheme {
    val scenes = (1..8).map { DiaryScene("s/$it","s",it*60_000L,"나무 아래의 순간 $it","함께 잠깐 쉬었어요.",null,"") }
    WalkDiaryMapContent(scenes,null,false,null,{},{},{},{},{},{},sceneGroup=scenes.drop(1),onDelete={},
        explorerPanel={ Text("동선 탐색") },map={ Box(Modifier.fillMaxSize().background(PinkFaint)) })
} }
