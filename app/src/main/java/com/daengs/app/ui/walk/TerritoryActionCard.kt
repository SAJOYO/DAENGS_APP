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
                Text(target.occupancyLabel, color = TextMuted, fontSize = 11.sp)
                WalkToolButton(WalkTool.CLOSE, "점령지 선택 닫기", onClose)
            }
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
                            colors = ButtonDefaults.buttonColors(containerColor = TextDark)) { Text("영역표시", fontSize = 12.sp) }
                        if (game.canPhotograph) {
                            if (game.canMark) WalkToolButton(WalkTool.CAMERA, "영역표시 인증 촬영", { onPhotograph(target.site.id) })
                            else Button(onClick = { onPhotograph(target.site.id) }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = TextDark)) {
                                Text(if (game.photoStatus == ClaimPhotoStatus.REJECTED) "다시 촬영" else "영역표시 인증 촬영", fontSize = 12.sp)
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
