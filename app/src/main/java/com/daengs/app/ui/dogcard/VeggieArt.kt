package com.daengs.app.ui.dogcard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.R

// ---------------------------------------------------------------------------
// 채소 — **VectorDrawable 이다. PNG 가 아니다**
//
// 카드의 큰 그림창에 들어가는 것은 "강아지 얼굴이 채소 안에 들어간" 모습이다
// (`assets/neo-hologram/art/cabbage-subject.webp` 가 원본 개념).
//
// ## 왜 벡터인가
//
// 원화(PNG)로 받으면 채소를 늘릴 때마다 그림을 요청해야 하고 APK 도 그만큼 커진다.
// `<vector>` XML 은 **라이브러리가 필요 없고**(안드로이드 기본, Compose 가 그대로
// 그린다), 한 장에 몇 KB 이고, 확대해도 안 깨진다. 채소를 늘리는 일이 그림 요청이
// 아니라 텍스트 한 장을 더하는 일이 된다 — 에이전트가 만들 수 있는 형태다.
//
// 그림은 `tools/make_veggie_vector.py` 가 만든다. **직접 고치지 말 것** — 다음에
// 스크립트를 돌리면 덮어쓴다.
//
// ## 코틀린에서 직접 그리다 버린 이유
//
// 처음에는 `DrawScope` 에서 sin/cos 로 잎을 그렸다. 종이를 오려 붙인 것처럼 나왔고,
// 값을 아무리 만져도 나아지지 않았다. **문제는 어디서 도느냐가 아니라 잎 모양
// 자체**였다. 잎 하나를 베지에로 제대로 그려 두고 회전시켜 배치하는 쪽이 맞다.
//
// ## 앞잎과 뒷잎을 나눈다
//
// **뒷잎 → 얼굴 → 앞잎** 순서로 그린다. 잎이 얼굴 가장자리를 덮어서 얼굴이 잎
// 사이에 끼어 든 것으로 보이고, 덤으로 **누끼에서 제일 어려운 털 가장자리가
// 안 보이는 자리로 간다.** 그림 한 장으로는 앞뒤를 못 나눈다.
// ---------------------------------------------------------------------------

/** 채소 한 종류. */
@Immutable
data class VeggieSpec(
    val id: String,
    val label: String,
    /** 얼굴 뒤에 깔리는 잎. */
    @param:DrawableRes val back: Int,
    /** 얼굴 위를 덮는 잎. */
    @param:DrawableRes val front: Int,
    /**
     * 얼굴이 앉을 자리의 반지름. **짧은 변 대비 비율**이라 카드가 커지든 작아지든
     * 같은 자리에 앉는다. `Immersive.kt` 의 `Fit` 이 같은 방식이다.
     */
    val hole: Float,
    /** 카드 뒤 글로우에 쓸 색. 저쪽 `accent` 와 같은 뜻이다. */
    val accent: Color,
)

val CABBAGE = VeggieSpec(
    id = "cabbage",
    label = "배추",
    back = R.drawable.veggie_cabbage_back,
    front = R.drawable.veggie_cabbage_front,
    hole = 0.30f,
    accent = Color(0xFF8FD94A),
)

/**
 * 채소를 그리고 구멍에 [face] 를 끼운다.
 *
 * @param face 얼굴 누끼. null 이면 구멍이 빈 채로 그려진다 — 그림만 볼 때 쓴다.
 */
@Composable
fun VeggieWithFace(
    spec: VeggieSpec,
    face: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(spec.back),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        if (face != null) {
            // 구멍보다 조금 크게 둔다. 딱 맞추면 얼굴과 잎 사이에 틈이 보이는데,
            // 넘치는 만큼은 어차피 앞잎이 덮는다.
            BoxWithFace(spec.hole * 2.2f, face)
        }
        Image(
            painter = painterResource(spec.front),
            contentDescription = spec.label,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BoxWithFace(fraction: Float, face: ImageBitmap) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight) * fraction
        Image(
            bitmap = face,
            contentDescription = null,
            // **Crop 이다.** 얼굴이 원 안을 꽉 채워야 한다 — Fit 이면 원 안에 여백이
            // 생겨서 채소에 얼굴을 얹은 게 아니라 구멍에 사진을 띄운 것으로 보인다.
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .size(side)
                .clip(androidx.compose.foundation.shape.CircleShape),
        )
    }
}

// -- 프리뷰 ------------------------------------------------------------------

/** 채소만. 구멍이 빈 채로 — 잎이 제대로 앉았는지는 이걸로 본다. */
@Preview(name = "배추 · 벡터", widthDp = 240, heightDp = 240)
@Composable
private fun CabbagePreview() {
    VeggieWithFace(CABBAGE, face = null, modifier = Modifier.size(220.dp))
}

/** 작게 줄였을 때. 도감 그리드에서 이만해진다. */
@Preview(name = "배추 · 작게", widthDp = 140, heightDp = 140)
@Composable
private fun CabbageSmallPreview() {
    VeggieWithFace(CABBAGE, face = null, modifier = Modifier.size(120.dp))
}
