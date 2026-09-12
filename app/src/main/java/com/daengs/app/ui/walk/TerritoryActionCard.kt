package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.features.territory.*
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.PetAvatar
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.theme.*

/** A selected map object explains ownership first, then the next available action. */
@Composable
internal fun TerritoryActionCard(
    game: TerritoryGameState,
    onMark: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPhotograph: (String) -> Unit = {},
    onClose: () -> Unit = {},
    onSelectPet: (String, String) -> Unit = { _, _ -> },
    ownerPhoto: ImageBitmap? = null,
    ownerBreed: DogBreed? = null,
    onPrepareWalk: (() -> Unit)? = null,
) {
    val target = game.target ?: return
    val presentation = territoryCardPresentation(game) ?: return
    var detailsOpen by remember(target.site.id) { mutableStateOf(false) }
    val cardScroll = remember(target.site.id) { ScrollState(0) }
    val occupied = target.occupancyKnown && target.claim.occupancy != null
    Surface(modifier.widthIn(max = 360.dp), shape = RoundedCornerShape(24.dp), color = CardWhite,
        shadowElevation = 4.dp) {
        Column(Modifier.verticalScroll(cardScroll).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("전봇대", color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                Surface(shape = RoundedCornerShape(6.dp), color = PinkFaint, modifier = Modifier.widthIn(max = 130.dp)) {
                    Text(presentation.badge, Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        color = TextDark, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                com.daengs.app.ui.game.bookmarks.TerritoryBookmarkAction(target.site.id)
                WalkToolButton(WalkTool.CLOSE, "점령지 선택 닫기", onClose)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (occupied) PetAvatar(ownerPhoto, ownerBreed, 48.dp)
                else PawAvatar(Modifier, 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(presentation.title, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp)
                    Text(presentation.subtitle, color = TextDark.copy(alpha = .75f), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            if (target.occupancyKnown) target.leaseLabel?.let {
                Surface(color = PinkFaint, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, Modifier.padding(10.dp), color = TextDark, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            presentation.reward?.let {
                Surface(color = PinkFaint, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(it, color = DaengPinkDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp)
                        presentation.rewardDetail?.let { note -> Text(note, color = TextDark, fontSize = 11.sp, lineHeight = 16.sp) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(presentation.distance, color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("일반 ${game.radiusMeters.toInt()}m · 인증 10m · GPS 오차 포함",
                    color = TextDark.copy(alpha = .7f), fontSize = 11.sp, lineHeight = 16.sp)
            }
            TerritoryCardActions(game, onMark, onPhotograph, onSelectPet, onPrepareWalk)
            if (target.occupancyKnown) TextButton(onClick = { detailsOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("점령 정보 자세히", color = TextDark, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
    if (detailsOpen) AlertDialog(onDismissRequest = { detailsOpen = false },
        title = { Text("점령 정보") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                target.claim.occupancy?.let { Text("점령 시각 · ${territoryOccupiedAtLabel(it.occupiedAtMillis)}") }
                presentation.reward?.let { Text(it) }
                presentation.rewardDetail?.let { Text(it) }
                if (presentation.reward != null) Text("기본 점령 보상은 회원별·장소별·시즌별 한도예요. 실제 받을 점수는 이미 받은 보상을 반영해 서버에서 정해요.")
                Text("사진 인증은 현재 위치와 강아지를 확인해요. 전봇대를 사진에 담을 필요는 없어요.")
                Text("주인의 시즌 점수·순위는 아직 제공되지 않아요.")
            }
        }, confirmButton = { TextButton(onClick = { detailsOpen = false }) { Text("확인") } })
}
@Preview(showBackground = true)
@Composable
private fun TerritoryActionCardPreview() {
    val site = com.daengs.app.territory.TerritorySite("A", com.daengs.app.location.GeoPoint(37.5, 127.0), 0.0)
    val target = TerritoryGameSite(site, com.daengs.app.territory.TerritoryClaimSite("A"), "", null, null, false)
    DaengsTheme {
        Column {
            TerritoryWalkPhase.entries.forEach { phase ->
                TerritoryActionCard(TerritoryGameState(enabled = true, phase = phase, sites = listOf(target), targetId = "A",
                    representativeLabel = "보리", eligiblePets = mapOf("p1" to "보리"), canMark = true, canPhotograph = true), {})
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SharedTerritoryCardPreview() {
    val site = com.daengs.app.territory.TerritorySite("A", com.daengs.app.location.GeoPoint(37.5, 127.0), 0.0)
    val owner = com.daengs.app.territory.TerritoryOccupancy("p", null, null,
        com.daengs.app.territory.ClaimCertification.VERIFIED, 0)
    DaengsTheme { TerritoryActionCard(TerritoryGameState(enabled = true, readOnly = true,
        targetId = "A", guidance = "점유 정보 · 둘러보기",
        sites = listOf(TerritoryGameSite(site, com.daengs.app.territory.TerritoryClaimSite("A", owner),
            "두부", null, null, false))), {}) }
}

internal fun territoryOccupiedAtLabel(millis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
    java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", java.util.Locale.KOREA)
        .format(java.time.Instant.ofEpochMilli(millis).atZone(zone))

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun TerritoryReadStatesPreview() {
    val site = com.daengs.app.territory.TerritorySite("A", com.daengs.app.location.GeoPoint(37.5, 127.0), 0.0)
    DaengsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TerritoryOccupancyReadState.LOADING, TerritoryOccupancyReadState.FAILED,
                TerritoryOccupancyReadState.LOGIN_REQUIRED).forEach { readState ->
                TerritoryActionCard(TerritoryGameState(enabled = true, readOnly = true, targetId = "A",
                    guidance = when (readState) {
                        TerritoryOccupancyReadState.FAILED -> "점유 정보를 불러오지 못했어요 · 잠시 후 다시 확인해요"
                        TerritoryOccupancyReadState.LOGIN_REQUIRED -> "로그인하면 점유 정보를 볼 수 있어요"
                        else -> "점유 정보를 확인하고 있어요"
                    }, sites = listOf(TerritoryGameSite(site, com.daengs.app.territory.TerritoryClaimSite("A"),
                        "", null, null, false, occupancyKnown = false, occupancyReadState = readState))), {})
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FirstSeasonRenewalPreview() {
    val site = com.daengs.app.territory.TerritorySite("A", com.daengs.app.location.GeoPoint(37.5, 127.0), 0.0)
    val owner = com.daengs.app.territory.TerritoryOccupancy("p", null, null,
        com.daengs.app.territory.ClaimCertification.UNVERIFIED, 0)
    val target = TerritoryGameSite(site, com.daengs.app.territory.TerritoryClaimSite("A", owner),
        "두부", null, null, false, isOwnedByMe = true, leaseLabel = "점령 유지 · 2일 3시간 남음")
    DaengsTheme {
        TerritoryActionCard(TerritoryGameState(enabled = true, phase = TerritoryWalkPhase.WALKING,
            sites = listOf(target), targetId = "A", representativeLabel = "두부", eligiblePets = mapOf("p" to "두부"),
            canMark = true, actionLabel = "유지 연장 · 0점", onlinePhotos = true,
            guidance = "현장에서 유지 시간을 연장할 수 있어요 · 연장 보상 0점"), {})
    }
}
