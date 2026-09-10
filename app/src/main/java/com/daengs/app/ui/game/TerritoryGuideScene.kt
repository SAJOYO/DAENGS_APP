package com.daengs.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.layers.territory.*
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.miniroom.sprite.SpriteSheet
import com.daengs.app.miniroom.sprite.drawSpriteFrame
import com.daengs.app.territory.TerritoryProximityRange
import com.daengs.app.ui.*
import com.daengs.app.ui.theme.*

/** Uses the map's pole renderer/ring palette and existing dog art; not a map or camera session. */
@Composable
internal fun TerritoryGuideScene(scenario: GuideScenario, step: GuideStep, onSelectPole: (() -> Unit)? = null) {
    val mine = step == GuideStep.RESULT || (scenario == GuideScenario.EMPTY && step >= GuideStep.MARKED)
    val occupied = mine || scenario == GuideScenario.TAKEOVER
    val photo = step == GuideStep.PHOTO
    val ownerName = if (mine) "두부" else if (occupied) "초코" else "아직 주인이 없어요"
    val ownerBreed = if (mine) DogBreed.BICHON_FRISE else DogBreed.TOY_POODLE_CHOCOLATE
    val status = if (step == GuideStep.RESULT) "인증 영역" else if (occupied) "미인증 영역" else "빈 전봇대"
    val context = LocalContext.current
    val pole = remember(context, occupied) { territoryMarkerIcon(context,
        if (occupied) TerritoryMarkerOccupancy.UNVERIFIED else TerritoryMarkerOccupancy.NEUTRAL).asImageBitmap() }
    val dogImage = ImageBitmap.imageResource(DogBreed.BICHON_FRISE.sheetRes)
    val dogSheet = remember(dogImage) { SpriteSheet(dogImage, dogImage.width / 4, dogImage.height, 4, 4, filterQuality = FilterQuality.None) }
    val proximity = if (step == GuideStep.APPROACH) TerritoryProximityRange.APPROACHING else TerritoryProximityRange.IN_RANGE
    val rangeColor = Color(territoryRangeStyle(proximity).outlineArgb)
    Surface(shape = RoundedCornerShape(18.dp), color = CardWhite) {
        Column {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (occupied) PetAvatar(null, ownerBreed, 32.dp)
                else DaengsIconView(DaengsIcon.Pin, Modifier.size(28.dp), TextDark)
                Column(Modifier.weight(1f)) {
                    Text(if (occupied) "${ownerName}의 전봇대" else ownerName, color = TextDark, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.testTag("game-guide-owner"))
                    Text(status, fontSize = 11.sp, color = TextDark.copy(alpha = .76f))
                }
                Text("예시", fontSize = 11.sp, color = TextDark)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(12.dp))
                .background(if (photo) Color(0xFFEAF0E7) else Color(0xFFF1EDE2))) {
                val poleX = maxWidth * .62f
                val baseY = 143.dp
                val dogX = if (step == GuideStep.APPROACH) 30.dp else poleX - 42.dp
                Canvas(Modifier.fillMaxSize()) {
                    if (!photo) {
                        drawLine(Color.White.copy(alpha = .8f), Offset(0f, 45.dp.toPx()), Offset(size.width, 110.dp.toPx()), 32.dp.toPx())
                        drawLine(Color.White.copy(alpha = .8f), Offset(35.dp.toPx(), 0f), Offset(80.dp.toPx(), size.height), 22.dp.toPx())
                        val center = Offset(poleX.toPx(), baseY.toPx())
                        drawCircle(rangeColor.copy(alpha = .13f), 60.dp.toPx(), center)
                        drawCircle(rangeColor, 60.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                        drawLine(TextDark.copy(alpha = .5f), Offset(dogX.toPx(), baseY.toPx()), center,
                            strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                    }
                }
                Image(pole, contentDescription = if (onSelectPole != null) "예시 전봇대 선택" else null,
                    modifier = Modifier.offset(x = poleX - 32.dp, y = baseY - 160.dp * TerritoryPoleArt.ANCHOR_Y)
                        .size(64.dp, 160.dp).testTag("game-guide-pole").then(
                            if (onSelectPole != null) Modifier.clickable(role = Role.Button, onClick = onSelectPole) else Modifier))
                if (photo) {
                    Canvas(Modifier.offset(x = dogX - 42.dp, y = baseY - 72.dp).size(84.dp, 82.dp)
                        .semantics { contentDescription = "촬영 구도 예시: 두부의 전신과 전봇대 주변을 함께 담아요" }) {
                        drawSpriteFrame(dogSheet, 0, size)
                    }
                    Box(Modifier.matchParentSize().padding(10.dp).border(2.dp, TextDark.copy(alpha = .5f), RoundedCornerShape(10.dp)))
                    Text("촬영 구도 예시", Modifier.align(Alignment.TopStart).padding(18.dp), fontSize = 11.sp, color = TextDark)
                } else {
                    Column(Modifier.offset(x = dogX - 24.dp, y = baseY - 38.dp).width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PetAvatar(null, DogBreed.BICHON_FRISE, 40.dp)
                        Text("두부 · 나", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }
                    Text(if (step == GuideStep.APPROACH) "32m · 더 가까이" else "8m · 범위 안",
                        Modifier.align(Alignment.TopStart).padding(12.dp).background(CardWhite, RoundedCornerShape(8.dp)).padding(6.dp),
                        color = TextDark, fontSize = 11.sp)
                    if (step == GuideStep.SELECT) Text("전봇대를 눌러요 ↓", Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
                        color = TextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DaengsIconView(if (photo) DaengsIcon.Camera else if (mine) DaengsIcon.Check else DaengsIcon.Pin, Modifier.size(18.dp), TextDark)
                Text(when {
                    step == GuideStep.RESULT && scenario == GuideScenario.TAKEOVER -> "초코 → 두부  ·  기본 100 + 탈취 20점"
                    step == GuideStep.RESULT -> "두부  ·  20 + 80 = 기본 100점"
                    step == GuideStep.MARKED -> "두부  ·  기본 +20점"
                    photo -> "두부 + 전봇대 주변이 한 화면에"
                    step == GuideStep.ACTION -> "영역표시할 강아지  ·  두부"
                    else -> "전봇대를 선택하면 주인과 행동을 확인해요"
                }, color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 330)
@Composable
private fun TerritoryGuideMapPreview() = DaengsTheme { TerritoryGuideScene(GuideScenario.EMPTY, GuideStep.SELECT, {}) }

@Preview(showBackground = true, widthDp = 330)
@Composable
private fun TerritoryGuidePhotoPreview() = DaengsTheme { TerritoryGuideScene(GuideScenario.TAKEOVER, GuideStep.PHOTO) }
