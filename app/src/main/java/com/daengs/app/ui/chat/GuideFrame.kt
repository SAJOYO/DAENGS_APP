package com.daengs.app.ui.chat

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.TextDark

/**
 * 병변에 맞추는 가이드 프레임.
 *
 * **이 네모가 그대로 서버의 `bbox` 가 된다.** 안 보내면 저쪽이 화면 중앙을 자르는데,
 * 1단계는 중심만 쓰므로 큰 차이가 없지만 **2단계는 네모 크기로 배율이 정해져서**
 * 학습 크롭과 어긋난다 (`src/agent.py` 의 `crop_for`). 병변이 어디에 얼마나 크게
 * 찍혔는지는 사람만 안다 — 그래서 고정 프레임이 아니라 끌고 늘릴 수 있어야 한다.
 *
 * 밴드 값 [Band] 는 저쪽이 STEP 10 에서 **실측한 것**이다. 여기서 임의로 고치면
 * 앱은 "딱 좋아요"라고 하는데 서버는 다시 찍으라고 하는 상태가 된다.
 */
@Composable
fun GuideFrameScreen(
    photo: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (FloatArray) -> Unit,
    /**
     * 맨 위 제목. 이 네모를 쓰는 곳이 진단만은 아니다.
     *
     * **"병변" 이라고 쓰지 않는다.** 반려인이 쓰는 말이 아니라, 무엇을 맞추라는
     * 것인지가 안 읽힌다. 서버에 물어보는 것은 결국 "이 부위가 어떤지" 라서,
     * 화면도 그렇게 부른다.
     */
    title: String = "진단하고 싶은 부위가 잘 보이게 맞춰 주세요",
    /** 확인 버튼 글자. */
    confirmLabel: String = "이 자리로 진단",
    /**
     * 네모 대신 **원**을 보여 준다.
     *
     * 자르는 범위는 그대로 네모다 — 바뀌는 건 어디를 맞추라고 알려 주는가뿐이다.
     * 얼굴만 담고 싶을 때 네모는 **모서리로 어깨가 딸려 들어온다.** 원이면 그
     * 모서리가 눈에 보이게 빠져서, 같은 상자를 줘도 사람이 얼굴에 맞춘다.
     */
    circle: Boolean = false,
    /**
     * 네모 아래 안내. null 이면 [Band] 의 병변 밴드 안내를 쓴다.
     *
     * **밴드는 진단 전용이다** — 저쪽이 STEP 10 에서 실측한 값이라 "너무 작아요"
     * 같은 문장이 병변 크기를 기준으로 나온다. 강아지 얼굴을 고르는 자리에서
     * 그 문장이 뜨면 사용자는 무엇이 잘못됐는지 알 수가 없다.
     */
    guidance: String? = null,
    /**
     * 앱 안 카메라의 가이드에 맞춰 찍었으면 **그 네모에서 시작한다.**
     *
     * 맞춰 찍었는데 확인 화면이 자기 기본값에서 시작하면 다시 맞춰야 한다 — 두 번
     * 일하는 셈이고, 무엇보다 "가이드가 소용없었다" 로 읽힌다. 갤러리에서 고른
     * 사진은 맞춰 찍은 적이 없으므로 null 이고, 그때만 기본값에서 시작한다.
     */
    guided: Boolean = false,
) {
    // 뒤로가기는 **이 화면만 닫는다.** 없으면 [ChatScreen] 의 핸들러까지 흘러가
    // 대화가 통째로 닫히고, 방금 찍은 사진이 말없이 버려진다.
    BackHandler(onBack = onCancel)

    // 정규화 [x, y, w, h]. 저쪽 데모의 시작값과 같다.
    //
    // ⚠️ w 와 h 는 **각 축 기준**이라 w = h 로 두면 16:9 사진에서 납작해진다.
    //    원본 픽셀 기준 정사각이 되려면 세로에 가로/세로 비를 곱해야 한다.
    val aspect = photo.width.toFloat() / photo.height.toFloat()
    var w by remember {
        mutableStateOf(
            if (guided) Band.CAPTURE_WIDTH else minOf(0.44f, 0.95f / aspect),
        )
    }
    var box by remember {
        val width = if (guided) Band.CAPTURE_WIDTH else minOf(0.44f, 0.95f / aspect)
        val height = (width * aspect).coerceAtMost(1f)
        // 찍을 때의 네모는 화면 한가운데였다. 같은 자리에서 시작한다.
        mutableStateOf(
            if (guided) Offset((1f - width) / 2f, (1f - height) / 2f) else Offset(0.28f, 0.28f),
        )
    }
    val h = (w * aspect).coerceAtMost(1f)

    val centerOff = centerOffset(CropBox(box.x, box.y, w), aspect)
    val hint = Band.hintFor(w, centerOff)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF2B2320))
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                title,
                color = CardWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )

            // 사진을 **비율 그대로** 채운다. 그래야 화면 좌표와 정규화 좌표가
            // 1:1 이라, 네모를 옮긴 만큼이 그대로 bbox 가 된다.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(aspect) {
                        // ⚠️ 바깥의 `w` 와 `box` 를 그대로 읽지 않는다. 이 블록은
                        //    aspect 가 바뀔 때만 다시 만들어지므로, 처음 조합될 때의
                        //    값을 끝까지 들고 있게 된다. **매번 지금 값에서 시작한다.**
                        //
                        // 손짓이 둘이다.
                        //   두 손가락  →  핀치로 크기 (zoom)
                        //   한 손가락  →  **네 모서리 중 아무 곳**이면 크기, 아니면 이동
                        //
                        // 예전에는 오른쪽 아래 한 곳만 손잡이였다. 왼쪽 위를 아무리
                        // 끌어도 네모가 움직이기만 해서, 크기를 바꾸려면 매번 반대편으로
                        // 손을 옮겨야 했다.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = CropBox(box.x, box.y, w)
                            // 실제 첫 transform 콜백에는 이미 pan 이 생겨 있다. 따라서
                            // 모서리는 콜백이 아니라 DOWN 좌표에서 한 번만 정한다.
                            val corner = grabbedCorner(
                                down.position.x / size.width,
                                down.position.y / size.height,
                                start,
                                aspect,
                                HANDLE_GRAB,
                            )
                            var pinching = false
                            do {
                                val event = awaitPointerEvent()
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                if (event.changes.count { it.pressed } > 1) pinching = true
                                val cur = CropBox(box.x, box.y, w)
                                val next = when {
                                    // 핀치를 시작했으면 한 손가락이 먼저 떨어져도 그
                                    // 제스처가 끝날 때까지 이동으로 바꾸지 않는다.
                                    pinching && zoom != 1f ->
                                        resizeAroundCenter(cur, aspect, cur.w * zoom)
                                    pinching -> cur
                                    corner != null -> {
                                        val d = cornerResizeDelta(
                                            corner,
                                            pan.x / size.width,
                                            pan.y / size.height,
                                        )
                                        resizeAroundCenter(cur, aspect, cur.w + 2f * d)
                                    }
                                    else -> moveBy(
                                        cur,
                                        aspect,
                                        pan.x / size.width,
                                        pan.y / size.height,
                                    )
                                }
                                box = Offset(next.x, next.y)
                                w = next.w
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "고른 사진",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                GuideOverlay(box, w, h, bad = guidance == null && hint.bad, circle = circle)
            }

            Text(
                guidance ?: hint.text,
                color = if (guidance == null && hint.bad) Color(0xFFFFD9D9) else CardWhite,
                fontSize = 13.sp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GuideButton("다시 고르기", CardWhite.copy(alpha = 0.14f), CardWhite, onCancel)
                // **밴드 밖이어도 보낼 수 있다.** 막아 버리면 저쪽이 왜 다시 찍어야
                // 하는지 문장으로 돌려주는 길이 막힌다 — 판단은 서버가 한다.
                GuideButton(confirmLabel, DaengPink, TextDark) {
                    onConfirm(floatArrayOf(box.x, box.y, w, h))
                }
            }
        }
    }
}

/** 네모 밖을 어둡게 덮고 테두리와 손잡이를 그린다. */
@Composable
private fun GuideOverlay(box: Offset, w: Float, h: Float, bad: Boolean, circle: Boolean = false) {
    val edge = if (bad) Color(0xFFFFD9D9) else DaengPink
    Canvas(Modifier.fillMaxSize()) {
        val x = box.x * size.width
        val y = box.y * size.height
        val bw = w * size.width
        val bh = h * size.height
        val scrim = Color(0xFF4A3B36).copy(alpha = if (bad) 0.55f else 0.5f)
        if (circle) {
            // 원일 때는 **경로 하나를 짝수-홀수 규칙으로** 칠한다. 바깥을 네 조각으로
            // 나눠 칠하는 방법이 원에는 안 통하고(모서리가 남는다), 그렇다고 레이어를
            // 떠서 뚫으면 아래 주석이 피하려던 그 비용이 든다.
            val hole = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addOval(Rect(x, y, x + bw, y + bh))
            }
            drawPath(hole, scrim)
            drawOval(edge, Offset(x, y), Size(bw, bh), style = Stroke(3.dp.toPx()))
        } else {
            // 구멍은 블렌드 모드 없이 **주변 넷을 칠해서** 만든다. 레이어를 따로
            // 뜨지 않아도 되고, 사진 위에서 결과가 같다.
            drawRect(scrim, Offset.Zero, Size(size.width, y))
            drawRect(scrim, Offset(0f, y + bh), Size(size.width, size.height - y - bh))
            drawRect(scrim, Offset(0f, y), Size(x, bh))
            drawRect(scrim, Offset(x + bw, y), Size(size.width - x - bw, bh))
            drawRect(edge, Offset(x, y), Size(bw, bh), style = Stroke(3.dp.toPx()))
        }
        // 손잡이는 **원일 때도 상자 모서리에 둔다.** 잡는 판정이 상자 모서리로
        // 되어 있어서(위 `sizing`), 테두리로 옮기면 보이는 곳과 잡히는 곳이 어긋난다.
        drawCircle(CardWhite, 13.dp.toPx(), Offset(x + bw, y + bh))
        drawCircle(edge, 10.dp.toPx(), Offset(x + bw, y + bh))
    }
}

@Composable
private fun GuideButton(label: String, fill: Color, ink: Color, onClick: () -> Unit) {
    Surface(color = fill, shape = RoundedCornerShape(24.dp)) {
        Box(
            Modifier.width(150.dp).height(48.dp).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

/**
 * 촬영 가이드 밴드. **저쪽이 STEP 10 에서 실측한 값이다** (`src/agent.py` 의
 * `GUIDE_RECOMMEND` · `GUIDE_ALLOW` · `GUIDE_CENTER_MAX`).
 *
 * 화면 **가로** 대비 병변의 비율이고, 밴드 밖에서는 성능이 떨어지는 걸 이미
 * 재 뒀다. 그래서 서버는 추론 **전에** 이걸 보고 재촬영으로 돌려보낸다.
 * 여기 숫자는 그 판단을 화면에서 미리 보여 주려고 옮겨 온 사본이라,
 * 저쪽이 바꾸면 같이 바꿔야 한다.
 */
internal object Band {
    private val RECOMMEND = 0.34f..0.56f
    private val ALLOW = 0.28f..0.68f
    private const val CENTER_MAX = 0.10f

    /**
     * 찍을 때 보여 줄 네모의 가로 비율. 권장 밴드의 한가운데다.
     *
     * **여기서 따로 정하지 않는다.** 찍을 때의 안내와 찍고 나서의 판정이 다른 숫자를
     * 쓰면, 가이드에 맞춰 찍었는데 "너무 작아요" 가 뜬다.
     */
    val CAPTURE_WIDTH = (RECOMMEND.start + RECOMMEND.endInclusive) / 2f

    /**
     * **사진을 고르기 전에** 보여 주는 안내.
     *
     * 갤러리에서 멀리 찍은 사진을 가져오면 네모를 아무리 맞춰도 [ALLOW] 아래라
     * "너무 작아요" 에 걸려 막다른 길이 된다. 앱만 풀어줘도 소용없다 — 이 밴드는
     * 서버 판정의 사본이라 서버가 재촬영으로 돌려보낸다. 그래서 **찍는 시점에**
     * 알려 주는 것이 유일한 길이다. 보행 쪽이 같은 이유로 시트에 안내 줄을 달고 있다.
     *
     * ⚠️ **비율 숫자를 문장에 안 쓴다.** 예전엔 "화면 가로의 45%쯤" 을 붙였는데,
     * 그 숫자는 읽어도 무엇을 해야 하는지로 안 바뀐다. 네모를 이미 그려 주고 있고,
     * 크기가 틀리면 [hintFor] 가 바로 말해 준다 — 숫자는 그 둘을 되풀이할 뿐이다.
     */
    const val CAPTURE_HINT: String =
        "💡 물어보고 싶은 곳을 가까이 찍어주세요"

    data class Hint(val text: String, val bad: Boolean)

    fun hintFor(w: Float, centerOff: Float): Hint =
        when {
            w < ALLOW.start -> Hint("너무 작아요 — 더 가까이 찍어 주세요", true)
            w > ALLOW.endInclusive -> Hint("너무 커요 — 주변 피부도 보이게", true)
            centerOff > CENTER_MAX -> Hint("가운데에서 벗어났어요", true)
            w !in RECOMMEND -> Hint("괜찮아요", false)
            else -> Hint("딱 좋아요", false)
        }
}

/** 손잡이로 인정하는 거리. 저쪽 데모와 같다. */
private const val HANDLE_GRAB = 0.07f


