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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.*
import kotlinx.coroutines.CancellationException

/** List entry only. Season/weather belong to the later in-map cohort picker. */
@Composable
fun WalkHistoryScreen(
    history: WalkHistory, onBack: () -> Unit, onOpen: (String) -> Unit,
    modifier: Modifier = Modifier, onSync: (() -> Unit)? = null,
    pets: List<Pet> = emptyList(), photoOf: (String) -> ImageBitmap? = { null },
) {
    var dogId by rememberSaveable { mutableStateOf<String?>(null) }
    var cursors by rememberSaveable(dogId) { mutableStateOf(listOf("")) }
    var page by remember { mutableStateOf<WalkHistoryPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val position = cursors.last()
    val savedLists = rememberSaveableStateHolder()
    LaunchedEffect(history) { onSync?.invoke() }
    LaunchedEffect(pets) { if (dogId != null && pets.none { it.id == dogId }) dogId = null }
    LaunchedEffect(history, position, dogId, retry) {
        page = null; error = null
        try {
            history.changes.collect {
                val loaded = history.finishedPage(WalkHistoryCursor.decode(position), dogId)
                if (loaded.walks.isEmpty() && cursors.size > 1) cursors = cursors.dropLast(1)
                else page = loaded
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "산책 기록을 불러오지 못했어요."
        }
    }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 뒤로") }
            Text("지난 산책", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (pets.size >= 2) DogFilterRow(pets, dogId, { dogId = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), photoOf = photoOf)
        when {
            error != null -> Column(Modifier.padding(20.dp)) {
                Text(error.orEmpty()); TextButton(onClick = { retry++ }) { Text("다시 시도") }
            }
            page == null -> Text("산책 기록을 불러오고 있어요.", Modifier.padding(20.dp))
            page!!.walks.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (dogId == null) "아직 산책 기록이 없어요.\n방문을 열고 산책을 시작해 보세요."
                    else "이 아이와 나간 산책이 아직 없어요.", color = TextMuted)
            }
            else -> savedLists.SaveableStateProvider("${dogId.orEmpty()}/$position") {
                WalkHistoryPageContent(page!!.walks, cursors.size, cursors.size > 1, page!!.next != null,
                    { cursors = cursors.dropLast(1) },
                    { page?.next?.let { cursors = cursors + it.encode() } }, onOpen, pets,
                    Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun WalkHistoryPageContent(
    walks: List<WalkSummary>, pageNumber: Int, hasPrevious: Boolean, hasNext: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onOpen: (String) -> Unit,
    pets: List<Pet> = emptyList(), modifier: Modifier = Modifier,
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
                            Text("${formatWalkDay(walk.startedAtMillis)} 산책", fontWeight = FontWeight.SemiBold)
                            val names = dogNames(walk.dogIds, pets)
                            if (names.isNotEmpty()) Text(names.joinToString(" · "), color = DaengPinkDeep,
                                style = MaterialTheme.typography.labelSmall)
                            Text(listOf(formatWalkDuration(walk.activeDurationMillis), formatWalkDistance(walk.distanceMeters))
                                .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            walk.weather?.let { Text(weatherLabel(it), style = MaterialTheme.typography.labelSmall, color = TextMuted) }
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

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun HistoryPagePreview() {
    DaengsTheme { WalkHistoryPageContent(listOf(previewDiarySummary()), 1, false, true, {}, {}, {}, modifier = Modifier.fillMaxSize()) }
}
