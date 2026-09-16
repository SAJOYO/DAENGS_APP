package com.daengs.app.ui.dex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.dogcard.cardFileName
import com.daengs.app.dogcard.photo.PhotoCard
import com.daengs.app.dogcard.photo.PhotoCardStatus
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.dogcard.CardShot
import com.daengs.app.ui.dogcard.rememberCardSaver
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.io.File

// ---------------------------------------------------------------------------
// 포토 카드를 만든 뒤 보여 주기 (docs/photo-cards.md §8)
//
// 누끼 뽑기는 뽑자마자 그 자리에서 뒤집히고 결과가 뜬다. 포토 카드는 서버가 30~60초 그리는데,
// 예전에는 도감으로 돌아가 칸에 「만드는 중…」만 떴다 — 아무 일도 안 일어난 것처럼 보였다.
// 여기 부품을 만들기 화면(기다리면)과 도감(나가면 알림)이 같이 쓴다. 연출 값은 누끼
// `FlipToCard` 와 같게 둔다 — 틀(`CardTemplate`)에 묶여 있어 그 함수를 합치지는 않는다.
// ---------------------------------------------------------------------------

enum class PhotoMakeStage { Pick, Drawing, Reveal, Failed }

/** 만들기 화면 단계. **그림을 받기 전에는 뒤집지 않는다** — 앞면이 없다. */
fun photoMakeStage(card: PhotoCard?, file: File?): PhotoMakeStage = when {
    card == null -> PhotoMakeStage.Pick
    card.status == PhotoCardStatus.Failed -> PhotoMakeStage.Failed
    card.status == PhotoCardStatus.Ready && file != null -> PhotoMakeStage.Reveal
    else -> PhotoMakeStage.Drawing
}

/** 뒤집기 한 번의 길이. 누끼 `FlipToCard` 와 같다. */
private const val FLIP_MS = 760

/**
 * 뒷면. 서버가 그리는 동안 **살아 있어 보이게** 옅은 빛이 비스듬히 지나간다 — 멈춘 판이면
 * 고장 난 줄 안다(도감의 꾹 누르기 게이지와 같은 이유).
 */
@Composable
fun PhotoCardBack(modifier: Modifier = Modifier, label: String? = null, matchHeight: Boolean = false) {
    val sweep by rememberInfiniteTransition(label = "photoBack").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    Box(
        modifier
            .aspectRatio(PHOTO_RATIO, matchHeightConstraintsFirst = matchHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(TextDark)
            .drawWithContent {
                drawContent()
                val x = size.width * sweep
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, PinkSoft.copy(alpha = 0.28f), Color.Transparent),
                        start = Offset(x - size.width * 0.5f, 0f),
                        end = Offset(x + size.width * 0.5f, size.height),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = PinkSoft, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        label?.let {
            Text(
                it,
                color = CardWhite,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}

/** 뒷면에서 앞면으로. **그림이 읽힌 뒤에** 돈다 — 안 읽혔는데 돌면 빈 앞면이 나온다. */
@Composable
private fun PhotoFlip(file: File, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val art = rememberCardImage(CardArt.Local(file))
    val turn = remember(file.path) { Animatable(0f) }
    LaunchedEffect(file.path, art != null) {
        if (art == null) return@LaunchedEffect
        turn.animateTo(180f, tween(FLIP_MS))
        onDone()
    }
    Box(
        modifier
            .aspectRatio(PHOTO_RATIO)
            .graphicsLayer {
                rotationY = turn.value
                cameraDistance = 14f * density
            },
    ) {
        if (turn.value < 90f || art == null) {
            PhotoCardBack(Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                Image(art, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** 결과. 누끼 `ResultBody` 와 같은 배치 — 카드 · 이름 · 도감에서 보기 · 저장 · 공유. */
@Composable
private fun PhotoResult(dex: DexCard, card: PhotoCard, file: File, onOpenDex: () -> Unit) {
    val art = rememberCardImage(CardArt.Local(file))
    val saver = rememberCardSaver()
    Box(Modifier.fillMaxWidth(0.72f)) {
        art?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) }
    }
    Spacer(Modifier.height(12.dp))
    Text(dex.ko, color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text("${card.dogName}의 카드가 완성됐어요", color = TextMuted, fontSize = 13.sp)
    Spacer(Modifier.height(16.dp))
    DaengsWideButton("도감에서 보기", onOpenDex, Modifier.fillMaxWidth(), accent = true)
    // 사진첩·공유는 서버가 그린 PNG 그대로 — 틀이 없으니 `template = null` (도감 설명 시트와 같다).
    val shot = art?.let {
        CardShot(
            fileName = cardFileName(templateId = dex.id, cardId = card.id, at = card.createdAtMillis),
            art = it, template = null, face = null, name = card.dogName, code = "",
        )
    }
    Spacer(Modifier.height(8.dp))
    DaengsWideButton(
        when {
            saver.busy -> "저장하는 중…"
            saver.toGallery -> "갤러리에 저장"
            else -> "이미지로 저장"
        },
        { shot?.let(saver::save) },
        Modifier.fillMaxWidth(),
        enabled = shot != null && !saver.busy,
    )
    Spacer(Modifier.height(8.dp))
    DaengsWideButton("공유하기", { shot?.let(saver::share) }, Modifier.fillMaxWidth(), enabled = shot != null && !saver.busy)
    saver.note?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = TextMuted, fontSize = 12.sp)
    }
}

/**
 * 뒤집고 결과를 보여 준다. 뒤집기가 끝나는 순간 [onRevealed] 를 **한 번** 부른다 — 도감의
 * 「완성됐어요」 알림이 그때 사라진다.
 */
@Composable
fun PhotoRevealFlow(
    dex: DexCard,
    card: PhotoCard,
    file: File,
    onRevealed: (String) -> Unit,
    onOpenDex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!flipped) {
            Spacer(Modifier.height(40.dp))
            PhotoFlip(file, onDone = { flipped = true; onRevealed(card.id) }, modifier = Modifier.fillMaxWidth(0.72f))
        } else {
            PhotoResult(dex, card, file, onOpenDex)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0, heightDp = 420)
@Composable
private fun PhotoCardBackPreview() {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PhotoCardBack(Modifier.width(140.dp))
        PhotoCardBack(Modifier.width(140.dp), label = "만드는 중…")
    }
}
