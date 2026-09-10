package com.daengs.app.ui.walk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.DaengsApp
import com.daengs.app.map.layers.moments.MomentMarkerState
import com.daengs.app.map.layers.spatial.mapBounds
import com.daengs.app.map.layers.spatial.paintCells
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.diary.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.coroutineContext

@Composable
internal fun WalkBehaviorComparisonScreen(pets: List<Pet>, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as DaengsApp
    val api = remember { WalkBehaviorComparisonApi() }
    WalkBehaviorComparisonBrowser(pets, onBack, onOpen, load = { query ->
        val owner = app.tokenStore.load()?.appUserId
        val loaded = loadBehaviorComparison(query, { app.tokenStore.load()?.appUserId },
            app.sessionProvider::freshSession, api::query)
        coroutineContext.ensureActive()
        check(app.tokenStore.load()?.appUserId == owner) { "로그인 정보를 다시 확인해 주세요." }
        val available = loaded.evidence.mapNotNull { evidence -> evidence.clientSessionId?.let { it to evidence.walkId } }
            .distinct().mapNotNull { (id, walkId) ->
                id.takeIf { app.walkEntryDao.session(id)?.let { it.ownerId == owner && it.serverWalkId == walkId } == true }
            }.toSet()
        coroutineContext.ensureActive()
        check(app.tokenStore.load()?.appUserId == owner) { "로그인 정보를 다시 확인해 주세요." }
        LoadedBehaviorComparison(loaded, available)
    })
}

internal data class LoadedBehaviorComparison(
    val comparison: WalkBehaviorComparison,
    val localSessions: Set<String> = emptySet(),
)

@Composable
internal fun WalkBehaviorComparisonBrowser(
    pets: List<Pet>, onBack: () -> Unit, onOpen: (String) -> Unit,
    load: suspend (WalkBehaviorComparisonQuery) -> LoadedBehaviorComparison,
    today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
    map: @Composable (WalkBehaviorComparison, Boolean, Modifier) -> Unit = { view, matching, size ->
        BehaviorComparisonMap(view, matching, size)
    },
) {
    var petId by rememberSaveable { mutableStateOf(pets.firstOrNull { it.isPrimary }?.id ?: pets.firstOrNull()?.id) }
    var behaviorName by rememberSaveable { mutableStateOf(WalkMomentType.SNIFFING.name) }
    var days by rememberSaveable { mutableIntStateOf(30) }
    var reload by remember { mutableIntStateOf(0) }
    val behavior = WalkMomentType.valueOf(behaviorName)
    val query = petId?.let { WalkBehaviorComparisonQuery(SpatialDiaryQuery(it, today.minusDays(days - 1L), today,
        metric = SpatialDiaryMetric.WALK_UTILIZATION), behavior) }
    var result by remember(query) { mutableStateOf<WalkBehaviorComparison?>(null) }
    var error by remember(query) { mutableStateOf<String?>(null) }
    var loading by remember(query) { mutableStateOf(false) }
    var localSessions by remember(query) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(pets) {
        if (pets.none { it.id == petId }) petId = pets.firstOrNull { it.isPrimary }?.id ?: pets.firstOrNull()?.id
    }
    LaunchedEffect(query, reload) {
        result = null; error = null; localSessions = emptySet()
        if (query == null) return@LaunchedEffect
        loading = true
        try {
            val loaded = load(query)
            coroutineContext.ensureActive()
            result = loaded.comparison; localSessions = loaded.localSessions
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            coroutineContext.ensureActive()
            error = if (failure is SpatialDiaryHttpException) failure.message
                else "행동 기록을 불러오지 못했어요. 로그인과 연결을 확인해 주세요."
        } finally { if (coroutineContext[kotlinx.coroutines.Job]?.isActive == true) loading = false }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 뒤로") }
            Text("행동 기록 돌아보기", style = MaterialTheme.typography.titleLarge)
        }
        if (pets.isEmpty()) Text("기록을 볼 강아지를 먼저 등록해 주세요.", Modifier.padding(20.dp))
        else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pets.forEach { pet -> FilterChip(petId == pet.id, { petId = pet.id }, { Text(pet.name) }) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WalkMomentType.entries.filter { it != WalkMomentType.NOTE }.forEach { type ->
                    FilterChip(behavior == type, { behaviorName = type.name }, { Text(type.label) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 7, 30, 90).forEach { count ->
                        FilterChip(days == count, { days = count }, { Text("최근 ${count}일") })
                    }
                }
                TextButton(onClick = { reload++ }, enabled = !loading) { Text("새로고침") }
            }
            Text("${query?.spatial?.since} ~ ${query?.spatial?.until} · 서버에 반영된 기록 기준",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            WalkBehaviorComparisonContent(result, loading, error, { reload++ }, onOpen, localSessions,
                Modifier.weight(1f), map = map)
        }
    }
}

@Composable
internal fun WalkBehaviorComparisonContent(result: WalkBehaviorComparison?, loading: Boolean, error: String?,
    onRetry: () -> Unit, onOpen: (String) -> Unit, localSessions: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    map: @Composable (WalkBehaviorComparison, Boolean, Modifier) -> Unit = { view, matching, size ->
        BehaviorComparisonMap(view, matching, size)
    }) {
    var matching by rememberSaveable(result?.query?.spatial?.petId, result?.query?.behavior) { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) item { Text("행동 기록을 불러오고 있어요.") }
        else if (error != null) item { Text(error); TextButton(onClick = onRetry) { Text("다시 시도") } }
        else if (result != null) {
            item {
                val summary = result.summary
                Text("비교 가능한 산책 ${result.baseline.walkIds.size}회 중 ${result.matching.walkIds.size}회에서 ${result.query.behavior.label} 기록을 남겼어요.")
                Text("기록 ${summary.entryCount}건 · 서로 다른 ${summary.recordedDayCount}일", color = TextMuted)
                if (summary.excludedEmptyWalkCount > 0) Text("공간 분포가 없는 산책 ${summary.excludedEmptyWalkCount}회는 비교에서 제외했어요.", color = TextMuted)
                if (summary.unlocatedEntryCount > 0) Text("위치 없는 기록 ${summary.unlocatedEntryCount}건도 산책 선택에 포함했어요.", color = TextMuted)
            }
            if (result.baseline.walkIds.isEmpty()) item { Text("이 기간에는 공간 비교가 가능한 산책이 없어요.") }
            else {
                item {
                    Column {
                        FilterChip(!matching, { matching = false }, { Text("선택 기간 전체 산책 ${result.baseline.walkIds.size}회") }, Modifier.testTag("behavior-baseline"))
                        FilterChip(matching, { matching = true }, { Text("${result.query.behavior.label} 기록이 있는 산책 ${result.matching.walkIds.size}회") }, Modifier.testTag("behavior-matching"))
                        Text("행동 기록이 있는 산책은 전체 산책에 포함돼요.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                item { map(result, matching, Modifier.fillMaxWidth().height(300.dp).testTag("behavior-map")) }
                item {
                    Text("옅음 → 진함: 산책마다 같은 비중으로 합친 공간 분포", style = MaterialTheme.typography.labelSmall)
                    Text("지도는 산책 전체의 분포예요. 행동을 기록한 위치는 핀에서 확인해요.", color = TextMuted)
                    if (result.matching.walkIds.isEmpty()) Text("이 기간에는 ${result.query.behavior.label} 기록이 있는 산책이 없어요.")
                    else if (result.sameWalks) Text("모든 비교 산책에 이 행동 기록이 있어 두 분포가 같아요.")
                }
            }
            if (result.evidence.isNotEmpty()) item { Text("근거 기록", style = MaterialTheme.typography.titleMedium) }
            items(result.evidence, key = { it.key }) { evidence ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${formatWalkDay(evidence.content.recordedAtMillis)} ${formatWalkClock(evidence.content.recordedAtMillis)} · ${evidence.content.type.label}")
                        Text(evidence.locationLabel, color = TextMuted)
                        val localId = evidence.clientSessionId
                        if (localId != null && localId in localSessions) TextButton(onClick = { onOpen(localId) }) { Text("이 산책 보기") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BehaviorComparisonMap(result: WalkBehaviorComparison, matching: Boolean, modifier: Modifier) {
    val group = if (matching) result.matching else result.baseline
    val cells = remember(group, result.projection) { group.paintCells(result.projection.radiusU) }
    val bounds = remember(result.baseline, result.projection) { result.mapBounds() }
    var selected by remember(result.receipt.sourceRevision) { mutableStateOf<String?>(null) }
    val markers = remember(result.evidence, selected, matching) {
        if (!matching) emptyList() else result.evidence.mapNotNull { evidence -> evidence.point?.let {
            MomentMarkerState(evidence.key, it, "${evidence.content.type.label} · ${evidence.locationLabel}", evidence.key == selected)
        } }
    }
    if (LocalInspectionMode.current) Box(modifier.background(PinkFaint), contentAlignment = Alignment.Center) { Text("산책 공간 분포와 행동 핀") }
    else MapHost(MapScene(moments = markers, spatialCells = cells, allowRegionalOverview = true), searchOrigin = null, followDevice = false,
        fitBounds = bounds, onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {},
        onSelectMoment = { selected = it }, modifier = modifier)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun BehaviorComparisonLoadingPreview() {
    DaengsTheme { WalkBehaviorComparisonContent(null, true, null, {}, {}) }
}
