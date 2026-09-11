package com.daengs.app.ui.game.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.territory.TerritorySiteMarkerState
import com.daengs.app.map.shell.*
import com.daengs.app.territory.bookmarks.*
import com.daengs.app.ui.*
import com.daengs.app.ui.game.owned.OwnedMapPresentation
import com.daengs.app.ui.game.owned.OwnedTerritoryMap
import com.daengs.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun TerritoryBookmarksRoute(onBack: () -> Unit, onSignIn: () -> Unit) {
    val controller = LocalTerritoryBookmarks.current ?: return
    val state = key(controller) { controller.state.collectAsState().value }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(controller, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { controller.refresh() }
    }
    key(controller) { TerritoryBookmarksScreen(state, onBack, onSignIn, controller::refresh, controller::toggle) }
}

internal fun bookmarkScene(items: List<TerritoryBookmark>, selected: String?) = MapScene(
    baseMapStyle = BaseMapStyle.TERRITORY_FOCUSED, allowRegionalOverview = true,
    territorySites = items.mapNotNull { item -> item.point?.let {
        TerritorySiteMarkerState(item.siteId, it, selected = item.siteId == selected, label = "북마크한 전봇대")
    } },
)

@Composable
internal fun TerritoryBookmarksScreen(
    state: BookmarkState, onBack: () -> Unit, onSignIn: () -> Unit, onRefresh: () -> Unit, onToggle: (String) -> Unit,
    mapSurface: @Composable (OwnedMapPresentation, (String?) -> Unit, (MapCameraSnapshot) -> Unit) -> Unit =
        { value, select, snapshot -> OwnedTerritoryMap(value, select, snapshot) },
) {
    var showMap by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var request by rememberSaveable { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<MapCameraSnapshot?>(null) }
    val selected = state.items.firstOrNull { it.siteId == selectedId }
    val select: (String?) -> Unit = { id ->
        if (id == null) selectedId = null
        else if (state.items.any { it.siteId == id && it.point != null }) {
            selectedId = id; showMap = true; camera = null; request++
        }
    }
    BackHandler {
        if (showMap) showMap = false else onBack()
    }
    Column(Modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (showMap) showMap = false else onBack() },
                modifier = Modifier.testTag("bookmarks-back").semantics { contentDescription = "북마크 뒤로 가기" }) {
                DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(24.dp).rotate(180f), TextDark)
            }
            Text("북마크", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
            TextButton(onClick = onRefresh, enabled = !state.busy) { Text("새로고침", color = TextDark) }
        }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (state.status == BookmarkStatus.READY) "저장한 전봇대 ${state.items.size} / ${state.limit}곳" else "다시 찾고 싶은 전봇대",
                fontWeight = FontWeight.Bold, color = TextDark)
            Text("주인이 바뀌어도 북마크는 유지돼요.", color = TextMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showMap, { showMap = false }, label = { Text("목록") }, modifier = Modifier.testTag("bookmarks-list-tab"))
                FilterChip(showMap, { showMap = true }, label = { Text("지도") }, modifier = Modifier.testTag("bookmarks-map-tab"))
            }
        }
        when {
            state.status in setOf(BookmarkStatus.INITIAL, BookmarkStatus.LOADING) ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.status != BookmarkStatus.READY -> Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message ?: "로그인하면 북마크를 볼 수 있어요.", color = TextDark)
                OutlinedButton(onClick = if (state.status == BookmarkStatus.SIGN_IN) onSignIn else onRefresh) {
                    Text(if (state.status == BookmarkStatus.SIGN_IN) "로그인하기" else "다시 불러오기")
                }
            }
            state.items.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("아직 저장한 전봇대가 없어요.\n점령지 카드의 ☆을 눌러 저장해 보세요.", color = TextDark)
            }
            !showMap -> LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("bookmarks-list"),
                contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(state.items, key = { _, item -> item.siteId }) { index, item ->
                    BookmarkCard(item, index + 1, state.busy, { onToggle(item.siteId) }, { select(item.siteId) })
                }
            }
            else -> Column(Modifier.weight(1f).fillMaxWidth()) {
                if (state.items.any { it.point != null }) Box(Modifier.weight(1f).fillMaxWidth().testTag("bookmarks-map")) {
                    mapSurface(OwnedMapPresentation(bookmarkScene(state.items, selected?.siteId), selected?.point,
                        request, camera), select, { camera = it })
                    TextButton(onClick = { selectedId = null; camera = null; request++ },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(CardWhite, RoundedCornerShape(14.dp))) {
                        Text("모아보기", color = TextDark)
                    }
                } else Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("위치를 불러올 수 없어요. 목록에서 확인하거나 해제할 수 있어요.", color = TextDark)
                }
                if (selected != null) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp), contentPadding = PaddingValues(12.dp)) {
                    item { BookmarkCard(selected, state.items.indexOf(selected) + 1, state.busy, { onToggle(selected.siteId) }, null) }
                } else Text("저장한 전봇대를 눌러 확인해 보세요.", Modifier.padding(18.dp), color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BookmarkCard(item: TerritoryBookmark, number: Int, busy: Boolean, onRemove: () -> Unit, onOpen: (() -> Unit)?) {
    Surface(Modifier.fillMaxWidth().testTag("bookmark-${item.siteId}"), shape = RoundedCornerShape(20.dp), color = CardWhite) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("저장한 전봇대 $number", color = TextDark, fontWeight = FontWeight.Bold)
                    Text("${BOOKMARK_DATE.format(Instant.ofEpochMilli(item.createdAtMillis))} 저장", color = TextMuted, fontSize = 12.sp)
                }
                BookmarkStar(true, busy, onClick = onRemove)
            }
            if (item.point == null) Text(if (item.locationStatus == BookmarkLocationStatus.NOT_FOUND)
                "더 이상 찾을 수 없는 위치 · 해제 가능" else "위치를 잠시 불러올 수 없어요 · 해제 가능", color = TextMuted, fontSize = 12.sp)
            if (onOpen != null) TextButton(onClick = onOpen, enabled = item.point != null, modifier = Modifier.align(Alignment.End)) {
                Text("지도에서 보기", color = if (item.point != null) TextDark else TextMuted)
            }
        }
    }
}
private val BOOKMARK_DATE = DateTimeFormatter.ofPattern("M월 d일 HH:mm").withZone(ZoneId.of("Asia/Seoul"))

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TerritoryBookmarksPreview() = DaengsTheme {
    TerritoryBookmarksScreen(BookmarkState(BookmarkStatus.READY, listOf(
        TerritoryBookmark("territory-site:hex-v1:140:324:777", 1_800_000_000_000, GeoPoint(37.5, 127.0), BookmarkLocationStatus.AVAILABLE),
        TerritoryBookmark("territory-site:hex-v1:140:324:778", 1_800_000_000_000, null, BookmarkLocationStatus.NOT_FOUND)), 20),
        {}, {}, {}, {})
}
