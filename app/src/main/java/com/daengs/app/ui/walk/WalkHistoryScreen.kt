package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.DaengsApp
import com.daengs.app.walk.diary.WalkDiaryReader
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Search and conditions remain visible before the first recorded walk. */
@Composable
fun WalkHistoryScreen(
    history: WalkHistory, onBack: () -> Unit, onOpen: (String) -> Unit,
    modifier: Modifier = Modifier, onSync: (() -> Unit)? = null,
    pets: List<Pet> = emptyList(), photoOf: (String) -> ImageBitmap? = { null },
) {
    val app = LocalContext.current.applicationContext as DaengsApp
    val reader = remember(app) { WalkDiaryReader(app.walkEntryDao, app.walkPhotos) {
        app.tokenStore.load()?.appUserId.orEmpty()
    } }
    var comparisonOpen by rememberSaveable { mutableStateOf(false) }
    val savedHistory = rememberSaveableStateHolder()
    if (comparisonOpen) WalkBehaviorComparisonScreen(pets, { comparisonOpen = false }, onOpen)
    else savedHistory.SaveableStateProvider("history-list") {
        WalkHistoryBrowser(history, reader, onBack, onOpen, modifier, onSync, pets, photoOf,
            onCompare = { comparisonOpen = true })
    }
}

@Composable
internal fun WalkHistoryBrowser(
    history: WalkHistory, reader: WalkDiaryReader, onBack: () -> Unit, onOpen: (String) -> Unit,
    modifier: Modifier = Modifier, onSync: (() -> Unit)? = null,
    pets: List<Pet> = emptyList(), photoOf: (String) -> ImageBitmap? = { null },
    onCompare: (() -> Unit)? = null,
) {
    var dogId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable(stateSaver = HistoryFilterSaver) { mutableStateOf(WalkHistoryFilter()) }
    var cursors by rememberSaveable(dogId, filter) { mutableStateOf(listOf("")) }
    val position = cursors.last()
    var page by remember(dogId, filter, position) { mutableStateOf<WalkHistoryPage?>(null) }
    var error by remember(dogId, filter, position) { mutableStateOf<String?>(null) }
    var hasAny by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val pageIds = page?.walks.orEmpty().map { it.sessionId }
    val titles by remember(reader, pageIds) { reader.observeTitles(pageIds) }.collectAsState(initial = emptyMap())
    val savedLists = key(dogId, filter) { rememberSaveableStateHolder() }
    LaunchedEffect(history) { onSync?.invoke() }
    LaunchedEffect(pets) { if (dogId != null && pets.none { it.id == dogId }) dogId = null }
    LaunchedEffect(history, position, dogId, filter, retry) {
        page = null; error = null
        try {
            if (filter.keyword.isNotBlank()) delay(250)
            history.changes.collectLatest {
                val loaded = history.finishedPage(WalkHistoryCursor.decode(position), dogId, filter = filter)
                hasAny = loaded.walks.isNotEmpty() || ((filter.active || dogId != null) &&
                    history.finishedPage(size = 1).walks.isNotEmpty())
                if (loaded.walks.isEmpty() && cursors.size > 1) cursors = cursors.dropLast(1)
                else page = loaded
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "산책 기록을 불러오지 못했어요."
        }
    }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 뒤로") }
            // 완료된 산책 세션을 찾는 목록이다. 장면을 구성해 내보내는 일기와 구분한다.
            Text("산책 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (pets.size >= 2) DogFilterRow(pets, dogId, { dogId = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), photoOf = photoOf)
        onCompare?.let { TextButton(onClick = it, modifier = Modifier.padding(horizontal = 10.dp)) { Text("행동 기록 돌아보기") } }
        WalkHistorySearchLayout(filter, { filter = it }, page == null, error, page?.walks?.isEmpty() == true,
            hasAny, { retry++ }, { filter = WalkHistoryFilter(); dogId = null }, Modifier.weight(1f)) {
            savedLists.SaveableStateProvider(position) {
                WalkHistoryPageContent(page!!.walks, cursors.size, cursors.size > 1, page!!.next != null,
                    { cursors = cursors.dropLast(1) },
                    { page?.next?.let { cursors = cursors + it.encode() } }, onOpen, pets,
                    Modifier.weight(1f), titles)
            }
        }
    }
}

@Composable
internal fun WalkHistoryPageContent(
    walks: List<WalkSummary>, pageNumber: Int, hasPrevious: Boolean, hasNext: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onOpen: (String) -> Unit,
    pets: List<Pet> = emptyList(), modifier: Modifier = Modifier,
    titles: Map<String, String> = emptyMap(),
) {
    val scroll = rememberLazyListState()
    Column(modifier) {
        Text("최근 날짜순", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = TextMuted, style = MaterialTheme.typography.labelMedium)
        LazyColumn(state = scroll, modifier = Modifier.weight(1f), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(walks, key = { it.sessionId }) { walk ->
                OutlinedCard(onClick = { onOpen(walk.sessionId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        WalkRouteThumbnail(walk, Modifier.size(88.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(walkDiaryTitle(walk), fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val names = dogNames(walk.dogIds, pets)
                            if (names.isNotEmpty()) Text(names.joinToString(" · "), color = DaengPinkDeep,
                                style = MaterialTheme.typography.labelSmall)
                            Text(listOf(formatWalkDuration(walk.activeDurationMillis), formatWalkDistance(walk.distanceMeters))
                                .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text(if (WalkDepartureWeather.of(walk.weather?.weatherCode) == WalkDepartureWeather.UNKNOWN)
                                "출발 날씨 정보 없음" else "출발 ${weatherLabel(requireNotNull(walk.weather))}",
                                style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Text("›", color = TextMuted)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = hasPrevious, onClick = onPrevious) { Text("‹ 이전") }
            Text("$pageNumber 페이지", Modifier.padding(horizontal = 16.dp))
            TextButton(enabled = hasNext, onClick = onNext) { Text("다음 ›") }
        }
    }
}

internal fun walkDiaryTitle(walk: WalkSummary): String = "${formatWalkDay(walk.startedAtMillis)} 산책"

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun HistoryPagePreview() {
    DaengsTheme { WalkHistoryPageContent(listOf(previewDiarySummary()), 1, false, true, {}, {}, {}, modifier = Modifier.fillMaxSize()) }
}
