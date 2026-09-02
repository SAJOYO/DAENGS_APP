package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * 내보낼 카드의 가로 픽셀.
 *
 * 자리를 비운 원화가 1080x1440 이라 그 크기다. 더 키우면 원화를 늘리는 것뿐이고,
 * 줄이면 이름 글자가 뭉갠다.
 */
const val CARD_EXPORT_WIDTH = 1080

/**
 * 합쳐진 카드 한 장을 화면 밖에서 그린다.
 *
 * **화면이 쓰는 [drawPersonalCardAt] 을 그대로 부른다.** 처음 계획은 좌표 산술을
 * `holeFit` 으로 떼어내고 `android.graphics.Canvas` 로 다시 그리는 것이었는데,
 * 그러면 **합치는 규칙이 두 벌**이 된다 — 턱을 구멍 아래에 거는 식이나
 * `CORE_OVERFILL` 을 한쪽만 고치는 날 "화면에서 본 것과 파일이 다르다" 가 난다.
 *
 * 그럴 필요가 없었다. `CanvasDrawScope().draw(...)` 는 화면에 안 붙은 [ImageBitmap]
 * 위에 **똑같은 `DrawScope`** 를 열어 준다. 그리는 코드도 글자를 재는 [TextMeasurer]
 * 도 화면과 같은 것을 쓴다.
 *
 * 글자 크기가 화면과 어긋나지 않는 이유: 글자 크기는 `카드 높이 x 비율` 로 구해서
 * sp 로 바꾸고, 그 sp 를 px 로 되돌리는 것은 [measurer] 가 든 밀도다. 두 곳이 같은
 * [measurer] 를 쓰므로 **카드 높이 대비 글자 높이**가 화면과 파일에서 같다.
 *
 * @param face null 이면 구멍이 빈 채로 나온다. 뽑은 카드는 항상 있다
 * @return 새로 만든 비트맵. **부른 쪽이 다 쓰고 `recycle()` 한다**
 */
fun renderCard(
    art: ImageBitmap,
    template: CardTemplate,
    face: CardFace?,
    measurer: TextMeasurer,
    name: String,
    code: String,
    width: Int = CARD_EXPORT_WIDTH,
): Bitmap {
    val height = (width / template.ratio).roundToInt()
    val target = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(target),
        size = size,
    ) {
        drawPersonalCardAt(
            art = art,
            template = template,
            face = face,
            measurer = measurer,
            name = name,
            code = code,
            at = Offset.Zero,
            box = size,
        )
    }
    return target.asAndroidBitmap()
}
