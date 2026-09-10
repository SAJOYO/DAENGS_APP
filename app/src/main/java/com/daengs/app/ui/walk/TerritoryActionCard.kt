package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.daengs.app.map.features.territory.*
import com.daengs.app.territory.ClaimPhotoStatus
import com.daengs.app.ui.theme.*

/** Occupancy is readable without a walk; only the action section needs a session. */
@Composable
internal fun TerritoryActionCard(
    game: TerritoryGameState,
    onMark: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPhotograph: (String) -> Unit = {},
    onClose: () -> Unit = {},
    onSelectPet: (String, String) -> Unit = { _, _ -> },
) {
    val target = game.target ?: return
    var choosingPet by remember(game.targetId) { mutableStateOf(false) }
    Surface(modifier.widthIn(max = 360.dp), shape = RoundedCornerShape(20.dp), color = CardWhite) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("전봇대", Modifier.weight(1f), color = TextDark, fontSize = 13.sp)
                com.daengs.app.ui.game.bookmarks.TerritoryBookmarkAction(target.site.id)
                WalkToolButton(WalkTool.CLOSE, "점령지 선택 닫기", onClose)
            }
            Text(target.occupancyLabel, color = TextDark, fontSize = 13.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (target.occupancyKnown) target.claim.occupancy?.let { occupancy ->
                if (target.isOwnedByMe == true) Text("우리 강아지의 점령지", color = TextMuted, fontSize = 11.sp)
                Text("점령 시각 · ${territoryOccupiedAtLabel(occupancy.occupiedAtMillis)}",
                    color = TextMuted, fontSize = 11.sp)
            }
            target.leaseLabel?.let { Text(it, color = TextMuted, fontSize = 11.sp) }
            if (game.readOnly) {
                Text(game.guidance, color = TextMuted, fontSize = 11.sp)
            } else when (game.phase) {
                TerritoryWalkPhase.BROWSING -> Text(
                    "점령 연습 · 점유 정보", color = TextMuted, fontSize = 11.sp,
                )
                TerritoryWalkPhase.PAUSED -> Text(
                    "산책을 재개하면 영역표시할 수 있어요", color = TextMuted, fontSize = 11.sp,
                )
                TerritoryWalkPhase.WALKING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("영역표시할 강아지", Modifier.weight(1f), color = TextMuted, fontSize = 11.sp)
                        Box {
                            TextButton(onClick = { choosingPet = true }, enabled = !game.petLocked && game.eligiblePets.isNotEmpty()) {
                                Text(game.representativeLabel ?: "참여견 없음", fontSize = 12.sp)
                            }
                            DropdownMenu(expanded = choosingPet, onDismissRequest = { choosingPet = false }) {
                                game.eligiblePets.forEach { (id, name) ->
                                    DropdownMenuItem(text = { Text(name) }, onClick = {
                                        choosingPet = false; onSelectPet(target.site.id, id)
                                    })
                                }
                            }
                        }
                    }
                    TerritoryFeedbackLine(game)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (game.canMark) Button(onClick = { onMark(target.site.id) }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TextDark)) { Text(game.actionLabel, fontSize = 12.sp) }
                        if (game.canPhotograph) {
                            if (game.canMark) WalkToolButton(WalkTool.CAMERA, "영역표시 인증 촬영", { onPhotograph(target.site.id) })
                            else Button(onClick = { onPhotograph(target.site.id) }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = TextDark)) {
                                Text(if (game.photoStatus in setOf(ClaimPhotoStatus.REJECTED, ClaimPhotoStatus.RETRY_PENDING)) "다시 촬영" else game.photoActionLabel, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
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
