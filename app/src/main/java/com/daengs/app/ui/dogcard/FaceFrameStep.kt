package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengPink
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
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(PinkFaint)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onChange(
                        frame.nudged(
                            dScale = zoom,
                            dx = pan.x / size.width,
                            dy = pan.y / size.height,
                        ),
                    )
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
