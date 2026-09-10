package com.daengs.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.activity.*
import com.daengs.app.pet.Pet
import com.daengs.app.ui.*
import com.daengs.app.ui.theme.*
import java.math.BigDecimal
import java.math.BigInteger

/** The overview is a full destination, with a scrollable body and a reachable map action. */
@Composable
internal fun TerritoryGameScreen(
    overview: TerritoryGameOverview, pet: Pet?, pets: List<Pet>, photoOf: (String) -> ImageBitmap?,
    nowNanos: Long, walkActive: Boolean,
    onBack: () -> Unit, onOpenMap: () -> Unit, onSelectPet: (String) -> Unit, onRetry: () -> Unit,
    onAddPet: () -> Unit = onBack, onSignIn: () -> Unit = onBack,
) {
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !rulesOpen, onBack = onBack)
    Column(Modifier.fillMaxSize().background(CreamBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "점령 게임 뒤로 가기" }) {
                DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(24.dp).rotate(180f), TextDark)
            }
            Text("점령 게임", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { rulesOpen = true }, modifier = Modifier.testTag("game-rules-open")) {
                DaengsIconView(DaengsIcon.Book, Modifier.size(16.dp), TextDark)
                Spacer(Modifier.width(4.dp))
                Text("점령 규칙", color = TextDark, fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("game-overview-list"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "season") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DaengsIconView(DaengsIcon.Clock, Modifier.size(16.dp), DaengsColors.Success)
                            Text(overview.seasonTitle(), fontWeight = FontWeight.Bold, color = TextDark, fontSize = 14.sp)
                        }
                        Text(overview.seasonTime(nowNanos), fontSize = 13.sp, color = TextDark)
                    }
                    TextButton(onClick = onRetry) { Text("새로고침", color = TextDark, fontSize = 12.sp) }
                }
            }
            item(key = "dog-score") { GameDogScore(overview, pet, pets, photoOf, onSelectPet) }
            if (overview.status == GameOverviewStatus.ERROR || overview.status == GameOverviewStatus.PETS_ERROR) item(key = "retry") {
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("다시 불러오기") }
            }
            if (overview.status == GameOverviewStatus.READY) item(key = "score-breakdown") { GameScoreBreakdown(overview) }
        }
        Surface(color = CardWhite, shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                val label = when (overview.status) {
                    GameOverviewStatus.SIGN_IN -> "로그인하기"
                    GameOverviewStatus.NO_PET -> "강아지 등록하기"
                    else -> if (walkActive) "산책 지도로 돌아가기" else "점령 지도 보기"
                }
                Button(onClick = when (overview.status) {
                    GameOverviewStatus.SIGN_IN -> onSignIn
                    GameOverviewStatus.NO_PET -> onAddPet
                    else -> onOpenMap
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("game-map-action"),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = TextDark)) {
                    DaengsIconView(DaengsIcon.Pin, Modifier.size(18.dp), CardWhite)
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (rulesOpen) TerritoryGameRulesDialog(onDismiss = { rulesOpen = false })
}

@Composable
private fun GameDogScore(overview: TerritoryGameOverview, pet: Pet?, pets: List<Pet>,
                         photoOf: (String) -> ImageBitmap?, onSelectPet: (String) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(24.dp), color = CardWhite) {
        Column(Modifier.background(Brush.verticalGradient(listOf(PinkSoft.copy(alpha = 0.55f), CardWhite)))
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("game-dog-score")) {
                PetAvatar(pet?.let { photoOf(it.id) }, pet?.breedArt, 76.dp)
                Column(Modifier.weight(1f)) {
                    Text(pet?.let { "${it.name}의 이번 시즌" } ?: "우리 강아지의 이번 시즌",
                        fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextDark,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${overview.points ?: "—"} 점", fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp, color = TextDark, maxLines = 2)
                    if (pets.size > 1) Box {
                        TextButton(onClick = { choosing = true }, contentPadding = PaddingValues(0.dp)) {
                            Text("강아지 변경", color = TextDark, fontSize = 12.sp)
                            DaengsIconView(DaengsIcon.CaretDown, Modifier.size(16.dp), TextDark)
                        }
                        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                            pets.forEach { option ->
                                DropdownMenuItem(text = { Text(option.name) }, leadingIcon = {
                                    PetAvatar(photoOf(option.id), option.breedArt, 28.dp)
                                }, onClick = { choosing = false; onSelectPet(option.id) },
                                    modifier = Modifier.testTag("game-pet-${option.id}"))
                            }
                        }
                    }
                }
            }
            Text(overview.message, fontSize = 13.sp, lineHeight = 19.sp, color = TextDark)
            if (overview.status == GameOverviewStatus.READY) {
                HorizontalDivider(color = PinkSoft)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("현재 점령", color = TextDark.copy(alpha = 0.76f), fontSize = 12.sp)
                        Text("${overview.owned ?: "—"}곳", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("이번 시즌 탈취", color = TextDark.copy(alpha = 0.76f), fontSize = 12.sp)
                        Text("${overview.takeovers ?: "—"}회", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                }
                overview.summary?.finalRank?.let { Text("시즌 최종 ${it}위", color = TextDark, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun GameScoreBreakdown(overview: TerritoryGameOverview) {
    Surface(shape = RoundedCornerShape(20.dp), color = CardWhite) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("어떻게 쌓인 점수일까요?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
            val score = overview.summary?.score
            if (score?.baseBonus != null && score.takeoverBonus != null) {
                GameScoreLine("기본 점령", gamePoints(BigDecimal.valueOf(score.baseBonus)))
                GameScoreLine("탈취 보너스", gamePoints(BigDecimal.valueOf(score.takeoverBonus)))
            } else {
                GameScoreLine("기본 점령·탈취 합산", score?.bonus?.let { gamePoints(BigDecimal.valueOf(it)) })
            }
            GameScoreLine("보유 점수", score?.let { gamePoints(holdingPoints(it)) })
            Text(overview.summary?.scoreAsOfMs?.let { "${scoreAsOf(it)} 정산 기준 · 보유 점수는 정산 후 반영돼요" }
                ?: "보유 점수는 서버 정산 후 반영돼요", fontSize = 11.sp, lineHeight = 17.sp, color = TextDark.copy(alpha = 0.76f))
        }
    }
}

@Composable
private fun GameScoreLine(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = TextDark, fontSize = 14.sp)
        Text("${value ?: "—"}점", color = TextDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

internal fun previewGamePet(id: String = "preview", name: String = "두부") =
    Pet(id, name, "dog_bichon_frise", null, null, null, null, null, isPrimary = true)

internal fun previewGameOverview() = TerritoryGameOverview(GameOverviewStatus.READY,
    ActivitySeason("territory-2026-09", 1790780400000, 1789009200000),
    ActivityTerritorySummary("territory-2026-09", "preview", ActivityTerritoryStatus.READY, 1, 1, null,
        ActivityScore(1120, BigInteger("4320000000000"), 0, 4, 3, 6, 12, 6, 0, 1000, 120),
        1789009200000, emptyList()), receivedAtNanos = 0)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TerritoryGameScreenPreview() = DaengsTheme {
    val dog = previewGamePet()
    TerritoryGameScreen(previewGameOverview(), dog, listOf(dog), { null }, 0, false, {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun TerritoryGamePreparingPreview() = DaengsTheme {
    val dog = previewGamePet()
    TerritoryGameScreen(TerritoryGameOverview(GameOverviewStatus.PREPARING), dog, listOf(dog), { null }, 0, false, {}, {}, {}, {})
}
