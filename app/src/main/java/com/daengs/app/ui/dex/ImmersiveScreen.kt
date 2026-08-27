package com.daengs.app.ui.dex

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.art.rememberAssetImage
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------
// 이머시브 무대 그리기
//
// 평면 일곱 장을 시차를 두고 겹친다. 구조와 숫자는 [Immersive] 에 적어 뒀다.
//
// 진입 연출이 이 뷰의 요점이다 — 카드가 서고, **그림이 녹아 틀만 남고**, 그 틀의
// 창이 벌어지면서 화면을 삼킨다. 창을 통과해 안으로 들어가는 것으로 읽힌다.
// 저쪽 `immersive.mjs` 의 `setWindow` 를 옮긴 것이다 (SAJOYO/DAENGS_dev PR #21).
//
// 캐릭터가 한 픽셀도 안 움직여야 "안으로 들어갔다"가 된다. 그래서 누끼를 카드 안
// 제자리([ImmersiveScene.fit])에 놓고 시작해서, **창이 벌어지는 동안에만** 무대
// 크기로 키운다. 카드가 녹는 구간에는 가만히 있는다.
// ---------------------------------------------------------------------------

private const val ENTER_MS = 2100

// 진입의 세 구간. 저쪽 `plate-in` 키프레임의 비율을 그대로 쓴다.
//
// 카드가 **다 서고 나서** 창을 열어야 한다. 서는 중에 열면 창 자리가 매 프레임
// 달라져서 틀과 창이 어긋난다 (저쪽이 판을 44% 에 세워 두는 이유가 이것이다).

/** 카드가 제자리에 서기까지. */
private const val SETTLE_END = 0.44f

/** 카드 그림이 녹아 틀이 드러나는 구간. */
private const val MELT_FROM = 0.30f
private const val MELT_TO = 0.62f

/** 창이 벌어지는 구간. */
private const val OPEN_FROM = 0.62f

/**
 * 화면을 다 덮고 나서도 이만큼 더 벌어진다.
 *
 * 딱 덮는 데서 멈추면 "가려졌다"이지 "통과했다"가 아니다. 여기서부터는 이미 화면
 * 밖이라 틀이 어디로 가든 안 보인다. 저쪽 `--win-k-end` 와 같은 1.22 배다.
 */
private const val WIN_OVERSHOOT = 1.22f

/**
 * 배경을 화면보다 이만큼 크게 깐다.
 *
 * 시차로 밀리는 층이라 딱 맞게 깔면 밀리는 순간 가장자리에 빈 데가 생긴다.
 * [Par.AMBIENT] 가 12px 이므로 여유가 조금만 있으면 된다. 저쪽 `--k: 1.06` 과 같다.
 */
private const val AMBIENT_OVERSCAN = 1.06f

@Composable
fun ImmersiveScreen(
    scene: ImmersiveScene = CABBAGE_SCENE,
    onClose: () -> Unit,
) {
    val back = rememberAssetImage(scene.back)
    val subject = rememberAssetImage(scene.subject)
    val card = rememberAssetImage(scene.card)
    val frame = scene.frame?.let { rememberAssetImage(it) }
    val parts = remember(scene) { buildScene(scene, seedOf("cabbage")) }

    BackHandler(onBack = onClose)

    // 진입. 0 = 카드 그대로, 1 = 무대
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(ENTER_MS, easing = LinearEasing),
        label = "immersive-enter",
    )

    // 시선. 손가락이 없으면 자이로가 맡는다.
    val rub = rememberRubState()
    val tracker = remember { TiltTracker() }
    DeviceTilt { b, g -> tracker.feed(b, g) }
    val aim = if (rub.input.intensity > 0f) rub.input.p else (tracker.input?.p ?: Offset(0.5f, 0.5f))

    // 먼지·잎이 떠다니는 시계
    val clock = rememberInfiniteTransition(label = "immersive-clock")
    val t by clock.animateFloat(
        initialValue = 0f,
        targetValue = 6000f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1408))
            .rubbable(rub)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawStage(scene, parts, back, subject, card, frame, aim, enter, t.toLong())
        }

        // 글자는 창이 벌어진 뒤에 뜬다. 진입 내내 떠 있으면 카드 위에 겹쳐서,
        // 아직 들어가지도 않았는데 도착한 것처럼 보인다.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 28.dp)
                .graphicsLayer { alpha = ((enter - OPEN_FROM) / (1f - OPEN_FROM)).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CABBAGE NEO", color = Color(0xFFEFFBE2), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(scene.place, color = scene.accent2.copy(alpha = 0.8f), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text("아무 데나 누르면 나갑니다", color = Color(0x88FFFFFF), fontSize = 11.sp)
        }
    }
}

/** 평면 일곱 장. 뒤에서 앞으로. */
private fun DrawScope.drawStage(
    scene: ImmersiveScene,
    parts: SceneParts,
    back: ImageBitmap?,
    subject: ImageBitmap?,
    card: ImageBitmap?,
    frame: ImageBitmap?,
    aim: Offset,
    enter: Float,
    timeMs: Long,
) {
    val settle = (enter / SETTLE_END).coerceIn(0f, 1f)
    val melt = ((enter - MELT_FROM) / (MELT_TO - MELT_FROM)).coerceIn(0f, 1f)
    val open = ((enter - OPEN_FROM) / (1f - OPEN_FROM)).coerceIn(0f, 1f)

    // 틀이 없는 카드는 예전 연출로 돈다 — 카드가 커지면서 통째로 녹는다.
    if (card == null || frame == null) {
        drawScene(scene, parts, back, subject, aim, enter, timeMs)
        if (card != null && enter < 1f) {
            val alpha = (1f - enter * 1.6f).coerceIn(0f, 1f)
            if (alpha > 0.001f) {
                val (pos, sz) = grownCardRect(card, size, enter)
                drawImage(
                    image = card,
                    dstOffset = androidx.compose.ui.unit.IntOffset(pos.x.roundToInt(), pos.y.roundToInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(sz.width.roundToInt(), sz.height.roundToInt()),
                    alpha = alpha,
                    filterQuality = FilterQuality.High,
                )
            }
        }
        return
    }

    val (cardPos, cardSize) = settledCardRect(card, size, settle)
    val win = windowOf(scene, cardPos, cardSize, size)

    // 창이 벌어지는 배율. **끝을 향해 빨라진다** — 등속으로 벌리면 통과가 아니라
    // 그냥 확대로 읽힌다. 화면을 다 덮는 건 이 곡선의 8할쯤이고, 나머지는 화면 밖이다.
    val k = 1f + (win.kEnd - 1f) * (open * open)

    // 무대는 **창 안에만** 그린다. 창이 화면을 넘어서면 이 자르기는 아무것도 안 한다.
    clipRect(
        left = win.cx - win.hw * k,
        top = win.cy - win.hh * k,
        right = win.cx + win.hw * k,
        bottom = win.cy + win.hh * k,
    ) {
        drawScene(scene, parts, back, subject, aim, open, timeMs, cardPos, cardSize)
    }

    // 틀. **창과 같은 중심에서 같은 배율로** 커진다. 둘이 같은 값을 읽으므로 어긋날
    // 수가 없다 — 창은 중심에서 반너비 x 배율로, 틀은 창 중심 기준 확대로 커지는데
    // 기준점이 같아서 결과가 맞는다.
    if (k < win.kEnd || open < 1f) {
        val fp = Offset(
            win.cx + (cardPos.x - win.cx) * k,
            win.cy + (cardPos.y - win.cy) * k,
        )
        drawImage(
            image = frame,
            dstOffset = androidx.compose.ui.unit.IntOffset(fp.x.roundToInt(), fp.y.roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(
                (cardSize.width * k).roundToInt(),
                (cardSize.height * k).roundToInt(),
            ),
            filterQuality = FilterQuality.High,
        )
    }

    // 카드 그림. 틀 위에 같은 자리로 얹혀 있다가 녹는다. 녹고 나면 아래의 틀이 드러나
    // 창틀이 된다 — 그림이 지워진 자리가 곧 창이다.
    if (melt < 1f) {
        drawImage(
            image = card,
            dstOffset = androidx.compose.ui.unit.IntOffset(cardPos.x.roundToInt(), cardPos.y.roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(
                cardSize.width.roundToInt(),
                cardSize.height.roundToInt(),
            ),
            alpha = 1f - melt,
            filterQuality = FilterQuality.High,
        )
    }
}

/** 창의 자리와, 화면을 다 덮고도 남게 벌어지는 배율. */
private class WindowGeom(
    val cx: Float,
    val cy: Float,
    val hw: Float,
    val hh: Float,
    val kEnd: Float,
)

/**
 * 창이 화면 어디인지, 얼마나 벌어져야 화면을 넘는지.
 *
 * 필요한 배율을 **고정값으로 박으면 안 된다.** 창은 카드 그림 영역이라 거의 정사각인데
 * 화면은 세로로 길어서, 같은 배율로 벌리면 한쪽이 먼저 끝나고 다른 쪽이 한참 남는다.
 * 창은 자기 중심에서 벌어지므로 **중심에서 먼 쪽 변까지의 거리를 반너비로 나눈 값**이
 * 곧 필요한 배율이다.
 */
private fun windowOf(
    scene: ImmersiveScene,
    cardPos: Offset,
    cardSize: Size,
    stage: Size,
): WindowGeom {
    val w = scene.window
    val cx = cardPos.x + cardSize.width * (w.x + w.w / 2f) / 100f
    val cy = cardPos.y + cardSize.height * (w.y + w.h / 2f) / 100f
    val hw = cardSize.width * w.w / 200f
    val hh = cardSize.height * w.h / 200f
    val cover = maxOf(
        maxOf(cx, stage.width - cx) / hw,
        maxOf(cy, stage.height - cy) / hh,
    )
    return WindowGeom(cx, cy, hw, hh, cover * WIN_OVERSHOOT)
}

/** 평면 일곱 장. 뒤에서 앞으로. */
private fun DrawScope.drawScene(
    scene: ImmersiveScene,
    parts: SceneParts,
    back: ImageBitmap?,
    subject: ImageBitmap?,
    aim: Offset,
    grow: Float,
    timeMs: Long,
    cardPos: Offset? = null,
    cardSize: Size? = null,
) {
    // 1. 하늘 — 해 뜨기 직전의 텃밭
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF16240F),
            0.55f to Color(0xFF24380F),
            1f to Color(0xFF3C4E14),
        ),
    )

    // 2. 색 환경 — 배경 그림을 크게 깔아 색만 남긴다.
    //
    // **화면을 덮어야 한다(cover).** 원화가 1339x670 으로 가로가 긴데 가로만 맞추면
    // 세로가 화면의 절반밖에 안 돼서 밭이 띠처럼 남는다. 긴 쪽이 아니라 **모자란 쪽**을
    // 기준으로 키우고 넘치는 만큼은 잘라 낸다.
    //
    // 시차로 움직이는 층이라 화면보다 [AMBIENT_OVERSCAN] 만큼 더 키운다 — 딱 맞게
    // 깔면 밀리는 순간 가장자리에 빈 데가 생긴다.
    if (back != null) {
        val d = parallax(aim, Par.AMBIENT)
        translate(d.x, d.y) {
            val scale = maxOf(size.width / back.width, size.height / back.height) * AMBIENT_OVERSCAN
            val w = back.width * scale
            val h = back.height * scale
            drawImage(
                image = back,
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    ((size.width - w) / 2f).roundToInt(),
                    ((size.height - h) / 2f).roundToInt(),
                ),
                dstSize = androidx.compose.ui.unit.IntSize(w.roundToInt(), h.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
        // 늘어나 흐려진 걸 가리고 주인공을 띄운다.
        //
        // **위아래를 다르게 덮는다.** 사방을 고르게 어둡게 하면 하늘까지 뭉개져서
        // 장면이 텃밭 한 덩어리로 보인다 — 흙(아래)은 깊게 눌러 주인공을 띄우고,
        // 하늘(위)은 살린다. 저쪽 `.dio-back::after` 값 그대로다.
        //
        // 이 층은 **시차를 안 탄다.** 같이 밀리면 화면 한쪽 구석이 밝아진다.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.42f to Color(0x38020603),
                0.66f to Color(0x94020603),
                1f to Color(0xD6020603),
            ),
        )
        drawRect(
            Brush.radialGradient(
                0.46f to Color.Transparent,
                1f to Color(0x80020603),
                center = Offset(size.width * 0.5f, size.height * 0.54f),
                radius = size.width * 0.92f,
            ),
        )
    }

    // 3. 빛줄기 — 위에서 비스듬히 내려온다
    run {
        val d = parallax(aim, Par.RAYS)
        translate(d.x, d.y) {
            drawRect(
                Brush.linearGradient(
                    0f to Color(0x33FFF6C4),
                    0.35f to Color(0x11FFF6C4),
                    1f to Color.Transparent,
                    start = Offset(size.width * 0.75f, -size.height * 0.1f),
                    end = Offset(size.width * 0.1f, size.height),
                ),
                blendMode = BlendMode.Screen,
                // 배경에 잎사귀가 가득해서 세게 걸면 다 씻겨 나간다.
                // 있는 듯 없는 듯한 정도로만 남긴다 (저쪽 `.dio-rays { opacity: .3 }`).
                alpha = 0.3f,
            )
        }
    }

    // 4. 먼지 — 카드 뒤에서 느리게 떠다닌다
    run {
        val d = parallax(aim, Par.MOTES)
        translate(d.x, d.y) {
            parts.motes.forEach { m ->
                val p = m.drift(timeMs, size)
                drawCircle(scene.accent.copy(alpha = m.alpha * 0.8f), m.r, p, blendMode = BlendMode.Screen)
            }
        }
    }

    // 5. 주인공 — 카드 안 제자리에서 시작해 무대 크기로 자란다.
    //    **여기가 진입 연출의 핵심이다.** 카드가 녹는 동안 캐릭터가 안 움직여야 한다.
    if (subject != null) {
        val d = parallax(aim, Par.SUBJECT)
        translate(d.x, d.y) {
            val r = subjectRect(scene, subject, size, grow, cardPos, cardSize)
            drawImage(
                image = subject,
                dstOffset = androidx.compose.ui.unit.IntOffset(r.first.x.roundToInt(), r.first.y.roundToInt()),
                dstSize = androidx.compose.ui.unit.IntSize(r.second.width.roundToInt(), r.second.height.roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
    }

    // 카드는 여기서 안 그린다. 창 연출에서는 무대가 창 안으로 잘리는데, 카드와 틀은
    // 그 바깥에도 보여야 하기 때문이다 — [drawStage] 가 자르기 밖에서 그린다.

    // 7. 앞잎사귀 — 크고 흐리게. 초점이 안쪽에 맞은 것처럼 보이게 하는 층이다
    run {
        val d = parallax(aim, Par.FORE)
        translate(d.x, d.y) {
            parts.leaves.forEach { l ->
                val sway = sin(timeMs / 1400f + l.phase) * 6f
                val w = size.width * 0.42f * l.scale
                val h = w * 0.62f
                drawOval(
                    color = Color(0xFF2E4A12).copy(alpha = l.alpha),
                    topLeft = Offset(l.at.x * size.width - w / 2f + sway, l.at.y * size.height - h / 2f),
                    size = Size(w, h),
                )
            }
        }
    }

    // 8. 이슬 — 카메라 유리에 맺힌 방울. **이 층만 시차가 0 이다.**
    parts.dew.forEach { dw ->
        val run = if (dw.runs) ((timeMs / 30f) % (size.height * 1.2f)) else 0f
        val p = Offset(dw.at.x * size.width, dw.at.y * size.height + run)
        drawCircle(Color.White.copy(alpha = dw.alpha * 0.5f), dw.r, p, blendMode = BlendMode.Screen)
        drawCircle(Color.White.copy(alpha = dw.alpha), dw.r * 0.35f, p - Offset(dw.r * 0.3f, dw.r * 0.3f))
    }

}

/**
 * 누끼가 놓일 자리. [enter] 0 이면 **카드 안 제자리**, 1 이면 무대 가득.
 *
 * 카드 안 제자리는 [ImmersiveScene.fit] 이 준다 — 원본 카드 그림에서 누끼가
 * 차지하던 사각형이다. 여기서 출발해야 틀이 녹는 동안 캐릭터가 안 움직인다.
 */
private fun subjectRect(
    scene: ImmersiveScene,
    subject: ImageBitmap,
    stage: Size,
    enter: Float,
    atPos: Offset? = null,
    atSize: Size? = null,
): Pair<Offset, Size> {
    // 창 연출에서는 카드가 선 자리를 그대로 받는다. 예전 연출에서는 카드가 커지는
    // 중이라 매번 다시 잰다.
    val (cardPos, cardSize) =
        if (atPos != null && atSize != null) atPos to atSize
        else grownCardRect(subject, stage, enter)
    // 카드 안에서의 자리 (카드 크기 대비 %)
    val from = Offset(
        cardPos.x + cardSize.width * scene.fit.x / 100f,
        cardPos.y + cardSize.height * scene.fit.y / 100f,
    )
    val fromSize = Size(cardSize.width * scene.fit.w / 100f, cardSize.height * scene.fit.h / 100f)

    // 무대 가득. 가로를 채우고 아래쪽에 앉힌다.
    val toW = stage.width * 1.05f
    val toH = toW * subject.height / subject.width
    val to = Offset((stage.width - toW) / 2f, stage.height * 0.52f - toH / 2f)
    val toSize = Size(toW, toH)

    val e = enter.coerceIn(0f, 1f)
    return Offset(
        from.x + (to.x - from.x) * e,
        from.y + (to.y - from.y) * e,
    ) to Size(
        fromSize.width + (toSize.width - fromSize.width) * e,
        fromSize.height + (toSize.height - fromSize.height) * e,
    )
}

/**
 * 창 연출에서 카드가 **서는** 자리. [settle] 1 이면 다 선 것이다.
 *
 * 다 섰을 때 화면 안에 들어와야 한다. 저쪽은 판 배율 1 이 화면보다 큰 크기라 따로
 * 줄여 앉히는데(`--set-s`), 여기서는 처음부터 그 크기로 잡는다 — 가로 86% 와
 * 세로 80% 중 **작은 쪽**을 따른다. 창이 벌어지는 구간에는 이 자리가 고정이라야
 * 창과 틀이 안 어긋난다.
 */
private fun settledCardRect(card: ImageBitmap, stage: Size, settle: Float): Pair<Offset, Size> {
    val ratio = card.width.toFloat() / card.height
    val endW = minOf(stage.width * 0.86f, stage.height * 0.80f * ratio)
    // 확대 뷰에서 이어지도록, 시작은 그 크기에서 조금 작게.
    val startW = endW * 0.88f
    val w = startW + (endW - startW) * settle.coerceIn(0f, 1f)
    val h = w / ratio
    return Offset((stage.width - w) / 2f, (stage.height - h) / 2f) to Size(w, h)
}

/** 틀이 없는 카드의 예전 연출. 들어오는 동안 화면만 하게 커진다. */
private fun grownCardRect(card: ImageBitmap, stage: Size, enter: Float): Pair<Offset, Size> {
    val ratio = card.width.toFloat() / card.height
    // 시작은 확대 뷰와 같은 크기, 끝은 화면보다 살짝 크게
    val startW = stage.width * 0.92f
    val endW = stage.width * 1.25f
    val w = startW + (endW - startW) * enter.coerceIn(0f, 1f)
    val h = w / ratio
    return Offset((stage.width - w) / 2f, (stage.height - h) / 2f) to Size(w, h)
}
