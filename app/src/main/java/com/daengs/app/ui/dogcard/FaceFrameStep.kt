package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 얼굴을 **원 안에 맞추는 자리.** 여기서 보이는 그대로 카드 구멍에 들어간다.
 *
 * 예전에는 목선을 끄는 화면이었다. 목 아래를 지우는 것이었는데, **그게 카드에서
 * 어떻게 보일지는 뽑고 나서야 알았다** — 무엇을 하는 단계인지도 안 읽혔다.
 * 원 안에 놓고 보면 목 아래는 밀어내면 그만이라 목선이 따로 필요 없다.
 *
 * 무슨 야채가 나올지는 아직 정해지지 않았다(`drawNow()` 에서 뽑는다). 그래서 카드
 * 모양이 아니라 **원 하나만** 둔다 — 어차피 구멍은 열두 장 다 타원이다.
 */
/**
 * 얼굴 맞추는 상자의 최대 폭.
 *
 * 411dp 폭 기기에서 바깥 여백 20dp 를 빼면 371dp 가 남는데, 그때도 이 값이 걸려
 * 조금 작아진다 — 사진이 너무 크다는 이야기가 그 전에도 있었다. 넓은 화면에서는
 * 이 값이 그대로 상한이 된다.
 */
internal const val FACE_FRAME_TAG = "face-frame"

private val MAX_FRAME = 300.dp

@Composable
fun FaceFrameStep(
    face: Bitmap,
    frame: FaceFrame,
    onChange: (FaceFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image: ImageBitmap = face.asImageBitmap()
    Box(
        modifier
            // **넓은 화면에서 무한정 커지지 않는다.** 이 상자는 폭만 한 정사각형이라
            // 폭이 넓어지면 그만큼 키도 커진다. 펼친 폴드에서는 화면을 다 먹어서
            // 아래 `이 얼굴로 뽑기` · `다시 자르기` 가 밀려났고, **카드를 못 뽑았다.**
            //
            // ⚠️ **"스크롤 되니까 괜찮다" 가 여기서는 성립하지 않는다.** 이 상자는
            // 크롭 손짓(핀치·이동)을 받는 자리라 세로 끌기를 가져간다. 상자가 화면을
            // 거의 덮으면 스크롤을 시작할 자리 자체가 없다 — 버튼이 **첫 화면에**
            // 보여야 한다. 이 상한을 키우려는 사람은 이 줄을 먼저 읽을 것.
            //
            // 폭만 묶는다. 감싼 쪽이 세로로 스크롤되는 `Column` 이라 거기서는 남은
            // 높이를 잴 수 없다(`BoxWithConstraints` 의 maxHeight 가 무한이다).
            .testTag(FACE_FRAME_TAG)
            .widthIn(max = MAX_FRAME)
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(PinkFaint)
            // **상자 밖으로 안 그린다.** 원을 채우려면 그림이 상자보다 커지는데,
            // 안 자르면 아래 안내 글씨와 버튼 위에 사진이 덮인다. 누끼는 둘레가
            // 투명해서 안 보이던 것이 **프로필 사진에서 드러났다.**
            .clipToBounds()
            // **손짓은 이 블록 안에서 쌓는다.**
            //
            // 예전에는 `pointerInput(Unit)` 안에서 `frame` 을 그냥 읽었다. 그 블록은
            // 처음 한 번만 만들어지므로 첫 조합 때의 값이 박제되고, 그러면 손가락을
            // 움직이는 내내 "처음 값 + 이번 델타"만 계산돼서 누적이 안 된다 — 확대도
            // 이동도 손을 떼면 제자리라 **"그냥 보여주기만 하는 화면"으로 읽혔다.**
            // `NeckPicker.kt:53` 이 같은 함정을 적어 둔 그 자리다.
            //
            // 거기서 쓴 `rememberUpdatedState` 로는 **모자랐다.** 그 값은 조합될 때만
            // 갱신되므로, 한 프레임에 포인터 이벤트가 여럿 들어오면 중간 값이 버려진다.
            // 빠른 핀치가 한 번밖에 안 먹는 것이 그 증상이고, `FaceFrameStepTest` 의
            // 핀치가 그걸 잡는다 (테스트는 이벤트 사이에 프레임을 안 그린다).
            //
            // 그래서 조합을 기다리지 않고 `acc` 에 직접 쌓는다. **키를 `face` 로 두는
            // 이유**는 새 사진이 오면 그 사진의 첫 틀(`initialFrame`)에서 다시 쌓아야
            // 하기 때문이다 — 사진과 `frame` 은 같은 자리에서 함께 바뀐다
            // (`CardDrawScreen` 의 `Cutout.of` 성공 갈래).
            .pointerInput(face) {
                var acc = frame
                detectTransformGestures { _, pan, zoom, _ ->
                    acc = acc.nudged(
                        dScale = zoom,
                        dx = pan.x / size.width,
                        dy = pan.y / size.height,
                    )
                    onChange(acc)
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val side = size.minDimension
            val r = side / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val circle = Path().apply {
                addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
            }
            // 원 밖은 흐리게 남겨 둔다. 통째로 가리면 **무엇을 밀어내고 있는지**
            // 안 보여서, 목 아래를 빼려는 손이 갈 곳을 잃는다.
            drawFace(image, frame, side, c, alpha = 0.18f)
            clipPath(circle) { drawFace(image, frame, side, c, alpha = 1f) }
            drawPath(circle, DaengPink, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFace(
    image: ImageBitmap,
    frame: FaceFrame,
    side: Float,
    center: Offset,
    alpha: Float,
) {
    val long = max(image.width, image.height).toFloat()
    val w = image.width / long * frame.scale * side
    val h = image.height / long * frame.scale * side
    // 정규화 좌표는 **원의 외접 정사각형** 기준이다 (`FaceFrame`). 화면에서도 같은
    // 정사각형을 기준으로 풀어야 구운 결과와 어긋나지 않는다.
    val squareLeft = center.x - side / 2f
    val squareTop = center.y - side / 2f
    drawImage(
        image = image,
        dstOffset = IntOffset(
            (squareLeft + frame.cx * side - w / 2f).roundToInt(),
            (squareTop + frame.cy * side - h / 2f).roundToInt(),
        ),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.High,
    )
}

// -- 프리뷰 ------------------------------------------------------------------
//
// 진짜 누끼는 사진과 ML Kit 가 있어야 해서 여기서는 못 만든다. 대신 **원과 글자가
// 앉는 자리**만 본다 — 원이 가운데인지, 흐린 바깥이 얼마나 남는지.

@Preview(name = "원형 틀 · 가운데", widthDp = 360, heightDp = 380)
@Composable
private fun FaceFrameStepPreview() {
    DaengsTheme {
        FaceFrameStep(face = previewFace(), frame = FaceFrame.CENTER, onChange = {})
    }
}

/** 작게 잡으면 원 밖으로 밀려난 부분이 얼마나 흐린지가 보인다. */
@Preview(name = "원형 틀 · 작게 잡았을 때", widthDp = 360, heightDp = 380)
@Composable
private fun FaceFrameStepSmallPreview() {
    DaengsTheme {
        FaceFrameStep(face = previewFace(), frame = FaceFrame(0.6f, 0.5f, 0.42f), onChange = {})
    }
}

/** 얼굴 대신 색 덩어리. 자리만 보는 프리뷰라 이것으로 충분하다. */
private fun previewFace(): Bitmap {
    val size = 240
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.rgb(226, 200, 165)
    canvas.drawOval(android.graphics.RectF(30f, 20f, 210f, 220f), paint)
    paint.color = android.graphics.Color.rgb(80, 60, 50)
    canvas.drawCircle(95f, 100f, 12f, paint)
    canvas.drawCircle(145f, 100f, 12f, paint)
    canvas.drawOval(android.graphics.RectF(105f, 130f, 135f, 152f), paint)
    return bmp
}
