package com.daengs.app.ui.game.owned

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.shell.MapCameraSnapshot
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.territory.ClaimCertification
import com.daengs.app.territory.owned.OwnedTerritory
import com.daengs.app.ui.*
import com.daengs.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun OwnedTerritoryScreen(
    state: OwnedBrowserState, pets: List<Pet>, petId: String?, photoOf: (String) -> ImageBitmap?, nowNanos: Long,
    onBack: () -> Unit, onSignIn: () -> Unit, onSelectPet: (String?) -> Unit,
    onRefresh: () -> Unit, onLoadMore: () -> Unit,
    mapSurface: @Composable (OwnedMapPresentation, (String) -> Unit, (MapCameraSnapshot) -> Unit) -> Unit =
        { value, select, snapshot -> OwnedTerritoryMap(value, select, snapshot) },
) {
    var showMap by rememberSaveable { mutableStateOf(true) }
    var selectedId by rememberSaveable(petId) { mutableStateOf<String?>(null) }
    var cameraRequest by rememberSaveable(petId) { mutableIntStateOf(0) }
    var camera by remember(petId) { mutableStateOf<MapCameraSnapshot?>(null) }
    val items = state.visibleItems(nowNanos)
    val selected = items.firstOrNull { it.siteId == selectedId }
    val select: (String) -> Unit = { id ->
        selectedId = id; showMap = true; camera = null; cameraRequest++
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("owned-back").semantics { contentDescription = "내 점령지 뒤로 가기" }) {
                DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(24.dp).rotate(180f), TextDark)
            }
            Text("내 점령지", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextDark)
            TextButton(onClick = onRefresh, enabled = state.status != OwnedBrowserStatus.LOADING) { Text("새로고침", color = TextDark) }
        }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.total?.let { "조회 기준 ${it}곳 보유" } ?: "우리 강아지가 차지한 영역",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
            if (state.status == OwnedBrowserStatus.READY) Text(
                "${state.items.size}곳 불러옴 · 위치 ${items.count { it.point != null }}곳" +
                    if (state.items.size != items.size) " · 만료된 영역 제외" else "",
                fontSize = 12.sp, color = TextDark.copy(alpha = .7f))
        }
        LazyRow(Modifier.fillMaxWidth().testTag("owned-pet-filters"), contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = petId == null, onClick = { onSelectPet(null) }, label = { Text("전체") },
                modifier = Modifier.testTag("owned-pet-all")) }
            items(pets, key = { it.id }) { pet ->
                FilterChip(selected = petId == pet.id, onClick = { onSelectPet(pet.id) }, label = { Text(pet.name) },
                    leadingIcon = { PetAvatar(photoOf(pet.id), pet.breedArt, 24.dp) },
                    modifier = Modifier.testTag("owned-pet-${pet.id}"))
            }
        }
        Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showMap, onClick = { showMap = false }, label = { Text("목록") }, modifier = Modifier.testTag("owned-list-tab"))
            FilterChip(selected = showMap, onClick = { showMap = true }, label = { Text("지도") }, modifier = Modifier.testTag("owned-map-tab"))
        }
        when {
            state.status == OwnedBrowserStatus.LOADING -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.status != OwnedBrowserStatus.READY || items.isEmpty() -> {
                val message = when (state.status) {
                    OwnedBrowserStatus.SIGN_IN -> "로그인하면 내 점령지를 볼 수 있어요."
                    OwnedBrowserStatus.NO_SEASON -> "아직 진행 중인 시즌이 없어요."
                    OwnedBrowserStatus.PREPARING -> "점령 게임을 준비하고 있어요."
                    OwnedBrowserStatus.ERROR -> state.message ?: "점령지를 불러오지 못했어요."
                    else -> if (state.items.isNotEmpty()) "불러온 영역의 유지 기간이 끝났어요." else "현재 보유한 점령지가 없어요."
                }
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message, color = TextDark, modifier = Modifier.testTag("owned-empty-message"))
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = if (state.status == OwnedBrowserStatus.SIGN_IN) onSignIn else onRefresh) {
                        Text(if (state.status == OwnedBrowserStatus.SIGN_IN) "로그인하기" else "다시 불러오기")
                    }
                }
            }
            !showMap -> LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("owned-list"),
                contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.siteId }) { site ->
                    OwnedTerritoryCard(site, photoOf(site.petId), state.nowMillis(nowNanos),
                        onOpen = { select(site.siteId) })
                }
            }
            else -> Column(Modifier.weight(1f).fillMaxWidth()) {
                if (items.any { it.point != null }) Box(Modifier.weight(1f).fillMaxWidth().testTag("owned-map")) {
                    mapSurface(OwnedMapPresentation(ownedTerritoryScene(items, selected?.siteId), selected?.point,
                        cameraRequest, camera), select, { camera = it })
                    TextButton(onClick = { selectedId = null; camera = null; cameraRequest++ },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(CardWhite, RoundedCornerShape(14.dp))) {
                        Text("모아보기", color = TextDark)
                    }
                } else Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("점령지는 있지만 위치 정보가 없어요. 목록에서 확인해 주세요.", color = TextDark)
                }
                if (selected != null) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp), contentPadding = PaddingValues(12.dp)) {
                    item {
                        OwnedTerritoryCard(selected, photoOf(selected.petId), state.nowMillis(nowNanos), onOpen = null)
                        TextButton(onClick = { selectedId = null }) { Text("선택 해제", color = TextDark) }
                    }
                } else Text("전봇대를 누르면 점령한 강아지와 남은 시간을 볼 수 있어요.",
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = TextDark, fontSize = 12.sp)
            }
        }
        if (state.status == OwnedBrowserStatus.READY) {
            state.message?.let { Text(it, Modifier.padding(horizontal = 18.dp), fontSize = 12.sp, color = TextDark) }
            if (state.nextCursor != null) OutlinedButton(onClick = onLoadMore, enabled = !state.loadingMore,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth().testTag("owned-load-more")) {
                Text(if (state.loadingMore) "불러오는 중…" else "더 불러오기 (${state.items.size}/${state.total}곳)")
            }
        }
    }
}

@Composable
internal fun OwnedTerritoryCard(site: OwnedTerritory, photo: ImageBitmap?, now: Long, onOpen: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(20.dp), color = CardWhite, modifier = Modifier.fillMaxWidth().testTag("owned-site-${site.siteId}")
        .clickable(enabled = onOpen != null && site.point != null) { onOpen?.invoke() }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PetAvatar(photo, site.petBreed?.let(DogBreed::byId), 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${site.petName}의 점령지", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (site.certification == ClaimCertification.VERIFIED) "사진 인증 완료" else "영역 표시 · 미인증",
                        color = if (site.certification == ClaimCertification.VERIFIED) DaengsColors.Success else TextDark, fontSize = 12.sp)
                }
                com.daengs.app.ui.game.bookmarks.TerritoryBookmarkAction(site.siteId)
            }
            Text(ownedRemaining(site.expiresAtMillis, now), color = TextDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("${OWNED_DATE.format(Instant.ofEpochMilli(site.occupiedAtMillis))} 점령", color = TextDark.copy(alpha = .7f), fontSize = 12.sp)
            if (site.point == null) Text("위치 정보 없음", color = TextDark, fontSize = 12.sp)
            else if (onOpen != null) TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) {
                Text("지도에서 보기", color = TextDark)
            }
        }
    }
}

private val OWNED_DATE = DateTimeFormatter.ofPattern("M월 d일 HH:mm").withZone(ZoneId.of("Asia/Seoul"))

internal fun previewOwnedSite(id: String = "territory-site:hex-v1:140:324:777", name: String = "두부") = OwnedTerritory(
    id, 2, "00000000-0000-0000-0000-000000000001", name, "dog_bichon_frise", ClaimCertification.VERIFIED,
    1_800_000_000_000, 1_800_200_000_000, com.daengs.app.location.GeoPoint(37.5, 127.0))

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OwnedTerritoryScreenPreview() = DaengsTheme {
    OwnedTerritoryScreen(OwnedBrowserState(OwnedBrowserStatus.READY, listOf(previewOwnedSite()), 1,
        "preview", serverNowMillis = 1_800_000_000_000), emptyList(), null, { null }, 0, {}, {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun OwnedTerritoryCardPreview() = DaengsTheme { OwnedTerritoryCard(previewOwnedSite(), null, 1_800_000_000_000, {}) }
