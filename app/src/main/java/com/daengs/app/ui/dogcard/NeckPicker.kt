package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 목선을 끌어 얼굴만 남기는 자리.
 *
 * **실험실과 뽑기 화면이 같은 것을 쓴다.** 원래 `CutoutLabScreen` 안에 있었는데,
 * 뽑기 화면이 같은 동작을 다시 만들면 한쪽만 고쳐지는 날이 온다. 배경만 갈아
 * 끼우게 열어 뒀다 — 실험실은 알파를 보려고 체커보드를 깔고, 사용자 화면은
 * 앱 색을 깐다.
 *
 * 선 아래는 **그리기로만** 지운다. 비트맵을 다시 만들면 손가락을 못 따라온다.
 * 실제로 굽는 것은 `Cutout.fadedBelow` 이고 자리가 정해진 뒤 한 번만 부른다.
 */
@Composable
internal fun NeckPicker(
    bitmap: Bitmap,
    neck: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    lineColor: Color = NeckLine,
    background: (@Composable () -> Unit)? = null,
) {
    // **`pointerInput(Unit)` 안에서 `neck` 을 그냥 읽으면 안 된다.** 그 블록은 처음
    // 한 번만 만들어지므로 첫 조합 때의 값이 박제되고, 한 번 끈 다음부터는 늘 처음
    // 자리를 기준으로 계산해서 선이 튄다. 갱신되는 참조를 따로 들고 읽는다.
    val latest by rememberUpdatedState(neck)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, drag ->
                    change.consume()
                    onChange((latest + drag / size.height).coerceIn(0.05f, 1f))
                }
            },
    ) {
        background?.invoke()
        FadedImage(bitmap, neck, "누끼 결과")
        // 선은 그림 위에 그린다. 사라지는 자리와 눈금이 어긋나면 못 맞춘다.
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * neck
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            drawCircle(lineColor, 9.dp.toPx(), Offset(size.width - 18.dp.toPx(), y))
        }
    }
}

/** [neck] 아래가 서서히 사라지게 그린다. 비트맵은 안 건드린다. */
@Composable
internal fun FadedImage(bitmap: Bitmap, neck: Float, label: String?) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = label,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
        modifier = Modifier
            .fillMaxSize()
            // 지우개가 그림하고만 섞여야 한다. 레이어를 안 뜨면 뒤 배경까지 지운다.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (neck >= 1f) return@drawWithContent
                val top = size.height * neck
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black,
                        startY = top,
                        endY = (top + size.height * 0.16f).coerceAtMost(size.height),
                    ),
                    blendMode = BlendMode.DstOut,
                )
            },
    )
}

/** 알파를 눈으로 보려면 뒤에 무늬가 있어야 한다. 단색이면 흰 털과 구분이 안 된다. */
@Composable
internal fun Checkered(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier.clip(RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = 12.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = 0f
                var col = 0
                while (x < size.width) {
                    val dark = (row + col) % 2 == 0
                    drawRect(
                        color = if (dark) Color(0xFF3A3F46) else Color(0xFF2A2E34),
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                    )
                    x += cell
                    col++
                }
                y += cell
                row++
            }
        }
        content()
    }
}

internal val NeckLine = Color(0xFF8FD94A)

@Preview(name = "체커보드", widthDp = 140, heightDp = 140)
@Composable
private fun CheckeredOnlyPreview() {
    Checkered(Modifier.size(120.dp))
}
