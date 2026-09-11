package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.territory.*
import com.daengs.app.map.layers.territory.*
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.territory.*
import com.daengs.app.ui.theme.*

/** Real card/map with explicit fixtures. No session, claim, GPS or photo writes. */
class TerritoryCardLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DaengsTheme { TerritoryCardLabBookmarks { TerritoryCardLab() } } }
    }
}

@Composable
private fun TerritoryCardLab(showMap: Boolean = true) {
    var kind by remember { mutableIntStateOf(0) }
    var walking by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(true) }
    var cardPixels by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val point = GeoPoint(37.545, 127.04)
    val occupancy = if (kind == 0) null else TerritoryOccupancy("preview-pet", null, null,
        if (kind == 1) ClaimCertification.VERIFIED else ClaimCertification.UNVERIFIED, 1_800_000_000_000)
    val siteId = "territory-site:hex-v1:140:324:777"
    val target = TerritoryGameSite(TerritorySite(siteId, point, 0.0), TerritoryClaimSite(siteId, occupancy),
        if (kind == 1) "두부" else "똥개", null, if (walking) 5.0 else 48.0, false,
        isOwnedByMe = kind == 2, sharedState = SharedTerritorySite(siteId, 1, null, policyVersion = FIRST_SEASON_POLICY),
        leaseLabel = if (kind == 0) null else "점령 유지 · 2일 4시간 남음")
    val game = TerritoryGameState(enabled = true, onlinePhotos = true, targetId = target.site.id, sites = listOf(target),
        phase = if (walking) TerritoryWalkPhase.WALKING else TerritoryWalkPhase.BROWSING,
        canMark = walking && kind != 1, canPhotograph = walking, representativeLabel = "똥개",
        eligiblePets = mapOf("preview-pet" to "똥개"), actionLabel = if (kind == 2) "유지 연장 · 0점" else "영역표시",
        guidance = if (walking) "점령 범위 안이에요 · 시안" else "점유 정보 · 둘러보기", readOnly = !walking)
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("개발 시안 · 예시 점령지 / 실제 요청 없음", Modifier.padding(horizontal = 12.dp), fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("미점유", "다른 강아지", "내 점령지").forEachIndexed { index, title ->
                TextButton(onClick = { kind = index; selected = true }) { Text(title) }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("산책 중 · 범위 안", Modifier.weight(1f))
            Switch(checked = walking, onCheckedChange = { walking = it })
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cardHeight = maxHeight * .72f
            if (showMap) MapHost(scene = MapScene(territorySites = listOf(TerritorySiteMarkerState(target.site.id, point,
                selected = selected, occupancy = when (kind) { 0 -> TerritoryMarkerOccupancy.NEUTRAL
                    1 -> TerritoryMarkerOccupancy.VERIFIED; else -> TerritoryMarkerOccupancy.UNVERIFIED },
                occupancyKnown = true, label = target.occupancyLabel, radiusMeters = 20.0.takeIf { selected }))),
                searchOrigin = null, followDevice = false, centerOn = point, centerZoom = 18.0,
                bottomPaddingPx = if (selected) cardPixels else 0,
                onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {}, onSelectTerritorySite = { selected = true },
                modifier = Modifier.fillMaxSize())
            if (selected) TerritoryActionCard(game, onMark = { message = "일반 점령 콜백 · 서버 요청 없음" },
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { cardPixels = it.height }
                    .padding(12.dp).fillMaxWidth().heightIn(max = cardHeight),
                onPhotograph = { message = "인증 촬영 콜백 · 카메라를 실행하지 않아요" }, onClose = { selected = false },
                onPrepareWalk = { walking = true }, ownerBreed = DogBreed.byId("dog_bichon_frise").takeIf { kind == 2 })
        }
    }
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("시안 동작") },
        text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("확인") } }) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TerritoryCardLabPreview() = DaengsTheme { TerritoryCardLab(showMap = false) }
