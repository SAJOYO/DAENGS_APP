package com.daengs.app.miniroom.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.daengs.app.miniroom.FloorQuad
import com.daengs.app.miniroom.RoomGeometry
import com.daengs.app.miniroom.RoomSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------
// 방 껍데기 — **그림 한 장**
//
// 예전에는 벽·바닥·창문·문·울타리를 전부 코드로 그렸다(500줄). 이제 그 전부가
// modular_empty_room_v1.png 안에 구워져 있어서 그림을 한 번 그리면 끝난다.
//
// 딱 하나 남은 예외가 **문**이다. 문은 눌러서 여는 물건이라 그림에 구워두면
// 열리지 않는다. 그렇다고 문만 벡터로 새로 그리면 픽셀 아트 방에 매끈한 도형이
// 얹혀 화풍이 깨진다.
//
// 그래서 **그림에서 문만 오려내 다시 그린다.** 같은 PNG 의 문 영역을 소스로 삼아
// 경첩 쪽으로 눌러 그리면, 새 아트 없이 원래 화풍 그대로 열리는 문이 된다.
// ---------------------------------------------------------------------------

/** 방 그림을 [RoomGeometry.stage] 에 채운다. 픽셀 아트라 보간을 끈다. */
fun DrawScope.drawRoomBackground(g: RoomGeometry, room: ImageBitmap) {
    drawImage(
        image = room,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(room.width, room.height),
        dstOffset = IntOffset(g.stage.left.roundToInt(), g.stage.top.roundToInt()),
        dstSize = IntSize(g.stage.width.roundToInt(), g.stage.height.roundToInt()),
        // 픽셀 아트를 확대할 때 기본값(Medium)이면 뿌옇게 번진다
        filterQuality = FilterQuality.None,
    )
}

/**
 * 창유리가 방 그림에서 차지하는 자리.
 *
 * **마스크는 여기 없다.** 창밖 그림이 이미 유리 모양으로 잘려 있어서(알파), 코드가
 * 아는 것은 얹을 사각형 하나뿐이다. 창살을 좌표로 잡아 오려내는 일이 없다.
 *
 * 값은 손으로 잰 게 아니라 **테마 여섯 장을 겹쳐 뽑았다.** 창틀·창살·벽은 테마마다
 * 색이 달라지고 유리 안 풍경은 안 변하므로, 전부 일치하는 화소가 곧 유리다
 * (`tools/make_outside.py`). 저쪽이 방 아트를 다시 그리면 그 스크립트를 다시 돌린다.
 */
object WindowSpec {
    val glass = Rect(left = 30.66f, top = 15.55f, right = 51.69f, bottom = 51.07f)

    /** 백분율 → 화면 px */
    fun rectOf(g: RoomGeometry): Rect = Rect(
        g.stage.left + glass.left / 100f * g.stage.width,
        g.stage.top + glass.top / 100f * g.stage.height,
        g.stage.left + glass.right / 100f * g.stage.width,
        g.stage.top + glass.bottom / 100f * g.stage.height,
    )
}

/**
 * 창밖. 방 그림 **위에** 유리 자리로 얹는다.
 *
 * 방 그림을 깔고 곧바로 부른다 — 소품·강아지보다 뒤이고, 창틀·창살은 방 그림에
 * 구워져 있으므로 이 그림이 그 위를 덮지 않는다(유리 모양으로 잘려 있다).
 */
fun DrawScope.drawWindowOutside(g: RoomGeometry, outside: ImageBitmap) {
    val dst = WindowSpec.rectOf(g)
    drawImage(
        image = outside,
        dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
        dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
        filterQuality = FilterQuality.None,
    )
}

/**
 * 문 규격 — **방 그림에 자로 재서 뽑은 값**이다.
 *
 * 예전엔 벽 평면 좌표계(u, h)로 문을 정의했다. 벽을 코드로 그리던 시절엔 그게 맞았지만
 * 이제 벽이 그림이라, 그림 안에서 문이 실제로 있는 자리를 백분율로 잡는 게 맞다.
 *
 * 값은 1122x1402 원본에서 올리브색 문짝의 경계를 찾아 재고 백분율로 환산했다.
 */
object DoorSpec {
    /**
     * 문짝을 감싸는 사각형. 여닫는 대상이자 터치 판정 영역이다.
     *
     * **사각형은 문짝보다 크다.** 문이 아치라 위 모서리가 남고, 밑변이 비스듬해
     * 오른쪽 아래도 남는다. 남는 자리는 벽이므로 그릴 때는 [TOP] · [BOTTOM] 으로
     * 잘라내야 한다.
     */
    val leaf = Rect(left = 6.33f, top = 39.02f, right = 18.54f, bottom = 66.26f)

    /** 문틀까지 포함한 범위. 터치를 조금 너그럽게 받으려고 쓴다. */
    val frame = Rect(left = 5.0f, top = 37.0f, right = 19.9f, bottom = 67.6f)

    /**
     * 문짝 윤곽 — **그림에서 떠 왔다.** [leaf] 안에서의 위·아래 가장자리를
     * 가로로 21등분해 잰 값이다. 0 이 [leaf] 위, 1 이 아래.
     *
     * 아치를 타원으로 어림잡았더니 문보다 볼록해서 양옆이 문 위까지 열렸다.
     * 실제 아치는 어깨가 완만하고 꼭대기가 살짝 오른쪽이며, 벽이 물러나는 만큼
     * 전체가 기울어 있다. 식으로 맞추느니 그림을 그대로 재는 편이 정확하다.
     *
     * 다시 뜨려면 `tools/trace_door.py` 를 쓴다. 문 색을 밝은 올리브로만 잡으면
     * 그늘진 아치 꼭대기를 놓친다 — r 과 g 가 비슷하고 파랑이 빠진 것으로 잡는다.
     */
    val TOP = floatArrayOf(
        0.2435f, 0.1675f, 0.1335f, 0.0995f, 0.0733f, 0.0576f, 0.0445f,
        0.0314f, 0.0236f, 0.0157f, 0.0079f, 0.0000f, 0.0000f, 0.0026f,
        0.0026f, 0.0079f, 0.0157f, 0.0288f, 0.0471f, 0.0707f, 0.1073f,
    )

    val BOTTOM = floatArrayOf(
        1.0000f, 0.9974f, 0.9921f, 0.9869f, 0.9791f, 0.9738f, 0.9660f,
        0.9555f, 0.9503f, 0.9450f, 0.9372f, 0.9346f, 0.9267f, 0.9188f,
        0.9110f, 0.9058f, 0.8979f, 0.8901f, 0.8848f, 0.8770f, 0.8717f,
    )

    /**
     * 밑변이 왼쪽에서 오른쪽으로 기운 정도. 문짝 높이 대비.
     *
     * 문 너머 지평선도 이만큼 기울어야 바닥과 나란해 보인다.
     * [BOTTOM] 에서 바로 뽑으므로 윤곽을 다시 떠도 저절로 따라온다.
     */
    val SHEAR: Float get() = BOTTOM.last() - BOTTOM.first()

    /** 경첩은 오른쪽. 그림에서 손잡이가 왼쪽에 있다. 열릴 때 이쪽으로 눌린다. */
    const val HINGE_RIGHT = true

    /** 활짝 열렸을 때 문짝이 남기는 폭의 비율. 0 이면 완전히 사라져 어색하다. */
    const val OPEN_MIN_W = 0.16f

    /** 백분율 → 화면 px */
    fun rectOf(g: RoomGeometry, r: Rect) = Rect(
        g.stage.left + r.left / 100f * g.stage.width,
        g.stage.top + r.top / 100f * g.stage.height,
        g.stage.left + r.right / 100f * g.stage.width,
        g.stage.top + r.bottom / 100f * g.stage.height,
    )

    /** 문을 눌렀는가. 문틀까지 받아준다 — 손가락은 정확하지 않다. */
    fun contains(g: RoomGeometry, pos: Offset): Boolean = rectOf(g, frame).contains(pos)

    /** 백분율 → 원본 PNG 픽셀 영역. 오려 그릴 때 소스가 된다. */
    fun sourcePx(room: ImageBitmap, r: Rect): IntRect = IntRect(
        (r.left / 100f * room.width).roundToInt(),
        (r.top / 100f * room.height).roundToInt(),
        (r.right / 100f * room.width).roundToInt(),
        (r.bottom / 100f * room.height).roundToInt(),
    )
}

/**
 * 문이 열리는 그림. [open] 0 이면 닫힘, 1 이면 활짝.
 *
 * 방 그림을 깔아둔 **뒤에**, 소품과 강아지보다 **앞서** 부른다. 문으로 든 볕이
 * 소품 밑으로 깔려야 하기 때문이다.
 *
 *  1. 문 너머 바깥 — **문 비율로 그린 [outside]** 를 깐다 (하늘·땅·길이 다 들어 있다)
 *  2. 문지방 볕과 인방 그늘 — 평평한 스티커로 안 보이게
 *  3. 눌린 문짝 — 방 그림에서 오려 경첩 쪽으로 누른다
 *  4. 바닥에 번지는 볕 — 이게 "나간다"를 만든다
 */
fun DrawScope.drawDoorOpening(
    g: RoomGeometry,
    room: ImageBitmap,
    outside: ImageBitmap,
    open: Float,
) {
    if (open <= 0.001f) return

    val dst = DoorSpec.rectOf(g, DoorSpec.leaf)
    // 밑변이 기운 만큼. 문지방 볕을 놓는 데 쓴다 (4번)
    val lift = DoorSpec.SHEAR * dst.height

    clipPath(doorPath(dst)) {
        // 1) 바깥. **문 비율로 따로 그린 그림**을 그대로 깐다.
        //
        // 예전에는 창유리 한 칸을 늘려 썼는데(`DoorSpec.outside`), 창은 236x498 이고
        // 문은 137x382 라 늘리면 뭉갰다. 게다가 창유리 아래쪽이 나뭇잎이라 문이
        // 아니라 "바닥까지 내려온 창" 으로 읽혀서, 잔디·흙길 띠를 코드로 덮어
        // 가리고 있었다. 그 띠 색이 낮·맑음으로 박혀 있어 밤이어도 문만 대낮이었다.
        //
        // 이제 하늘·땅·길이 그림 안에 다 있다. 지평선 기울기도 구워져 있어서
        // 여기서 기울일 것이 없다.
        drawImage(
            image = outside,
            dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
            dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }

    // 3) 눌린 문짝. 잘라내기도 이미지와 **똑같이** 눌러야 한다. 눌린 사각형에
    // 아치를 새로 그리면 어깨 높이까지 따라 올라가 실제 문짝보다 위까지 열린다.
    val squeeze = 1f - open * (1f - DoorSpec.OPEN_MIN_W)
    val w = dst.width * squeeze
    val left = if (DoorSpec.HINGE_RIGHT) dst.right - w else dst.left
    val leafSrc = DoorSpec.sourcePx(room, DoorSpec.leaf)
    clipPath(doorPath(dst, squeeze)) {
        drawImage(
            image = room,
            srcOffset = IntOffset(leafSrc.left, leafSrc.top),
            srcSize = IntSize(leafSrc.width, leafSrc.height),
            dstOffset = IntOffset(left.roundToInt(), dst.top.roundToInt()),
            dstSize = IntSize(w.roundToInt().coerceAtLeast(1), dst.height.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }

    // 4) 바닥에 번지는 볕. 문간만 밝으면 스티커고, 빛이 바닥에 닿아야 바깥이 된다.
    // 바닥 윤곽으로 잘라야 벽이나 방 밖으로 새지 않는다.
    val threshold = Offset(dst.left + dst.width * 0.5f, dst.bottom + lift * 0.5f)
    val reach = g.stage.width * 0.3f
    clipPath(floorPath(g)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Sunbeam.copy(alpha = 0.42f * open), Color.Transparent),
                center = threshold,
                radius = reach,
            ),
            radius = reach,
            center = threshold,
        )
    }
}

/**
 * 문짝 실루엣 — **그림에서 뜬 윤곽**([DoorSpec.TOP] · [DoorSpec.BOTTOM]) 을 잇는다.
 *
 * 문을 네모로 다루면 두 군데서 벽이 딸려온다. 위 모서리(아치 바깥)와 오른쪽
 * 아래(밑변이 비스듬해서). 그 벽 조각이 칠해지거나 문짝과 함께 움직이면
 * 스티커가 벗겨지는 것처럼 보인다.
 *
 * 타원으로 어림잡아도 안 된다. 문보다 볼록해서 양옆이 문 위까지 열린다.
 *
 * [squeeze] 는 열리며 눌린 가로 비율. 모양을 **원래 크기로 그린 다음** 경첩 쪽으로
 * x 만 줄인다 — 세로는 그대로다. 이미지도 그렇게 눌리므로 두 실루엣이 겹친다.
 */
private fun doorPath(r: Rect, squeeze: Float = 1f): Path {
    val n = DoorSpec.TOP.size
    val hinge = if (DoorSpec.HINGE_RIGHT) r.right else r.left
    fun x(i: Int): Float {
        val at = r.left + r.width * i / (n - 1f)
        return hinge + (at - hinge) * squeeze
    }
    fun y(v: Float) = r.top + r.height * v

    return Path().apply {
        moveTo(x(0), y(DoorSpec.TOP[0]))
        for (i in 1 until n) lineTo(x(i), y(DoorSpec.TOP[i]))
        for (i in n - 1 downTo 0) lineTo(x(i), y(DoorSpec.BOTTOM[i]))
        close()
    }
}

/** 칠해진 바닥의 윤곽. 볕이 벽으로 새지 않게 자르는 데 쓴다. */
private fun floorPath(g: RoomGeometry): Path = Path().apply {
    FloorQuad.outline.forEachIndexed { i, p ->
        val x = g.stage.left + p.x / 100f * g.stage.width
        val y = g.stage.top + p.y / 100f * g.stage.height
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * 닫힌 문을 누를 수 있다는 은은한 표시. [pulse] 0..1.
 *
 * 문짝 위에 옅은 흰빛을 얹는다. 문이 열리기 시작하면 호출부에서 pulse 를 0 으로
 * 줄이므로 여기서 따로 끄지 않는다.
 */
fun DrawScope.drawDoorHint(g: RoomGeometry, pulse: Float) {
    if (pulse <= 0.001f) return
    // 여기도 문 모양이다. 네모로 얹으면 문 위 벽이 같이 밝아져서 티가 난다.
    drawPath(doorPath(DoorSpec.rectOf(g, DoorSpec.leaf)), Color.White.copy(alpha = 0.10f * pulse))
}

// 문 너머 지평선(0.62)과 잔디·흙길 색은 여기 있었다. 이제 문밖 그림 안에 들어
// 있으므로 코드에서 걷어냈다. **값은 `tools/make_outside.py` 로 옮겨 갔다** —
// 그림을 다시 그릴 때 지평선 높이가 필요하면 거기를 본다.

/** 문으로 들어온 볕. 바닥에 번진다. */
private val Sunbeam = Color(0xFFFFE9B8)

/**
 * 발자국 도장. 방과 무관하게 아이콘·소품에서도 쓴다.
 *
 * [RoomSpec] 을 안 타므로 방 기하가 바뀌어도 영향이 없다.
 */
fun DrawScope.drawPawStamp(center: Offset, r: Float, color: Color) {
    drawOval(
        color,
        Offset(center.x - r * 0.62f, center.y - r * 0.12f),
        Size(r * 1.24f, r * 1.02f),
    )
    val toes = listOf(-0.74f to -0.7f, -0.26f to -1.02f, 0.26f to -1.02f, 0.74f to -0.7f)
    toes.forEach { (dx, dy) ->
        drawOval(
            color,
            Offset(center.x + dx * r - r * 0.18f, center.y + dy * r - r * 0.2f),
            Size(r * 0.37f, r * 0.45f),
        )
    }
}

// ---------------------------------------------------------------------------
// 벽에 건 액자 — 도감으로 들어가는 문
//
// 문과 같은 방식이다. 그림에 자로 재서 백분율로 잡고, 누르면 열린다.
//
// **문·창문이 있는 벽이 아니라 아무것도 없는 오른쪽 벽에 건다.** 가로로 겹치면
// 문을 누르려다 도감이 열린다.
//
//     문     6.8% ~ 17.8%
//     창문  32.3% ~ 49.1%
//     액자  67.4% ~ 96.6%   <- 여기가 빈 벽이다
// ---------------------------------------------------------------------------

/**
 * 액자 규격. 오른쪽 빈 벽에 걸린다.
 *
 * 벽이 물러나는 평면이라 **액자도 같이 기울어야** 벽에 붙어 보인다. 그림에서 벽의
 * 위 가장자리를 재보니 가로 1% 갈 때마다 세로로 0.35% 내려간다 ([SLOPE]).
 */
/**
 * 액자에 걸 때 보여줄 **카드 안의 그림창** (카드 크기 대비 %).
 *
 * `ImmersiveScene.fit` 과 같은 값이다. 거기서 참조하지 않고 여기 적어 둔 이유는
 * `miniroom` 이 `ui.dex` 를 모르게 두기 위해서다 — 방이 도감을 알면 의존이 거꾸로
 * 흐른다. 카드 그림이 바뀌면 두 곳을 같이 고친다.
 */
private object PictureRoi {
    const val x = 6.06f
    const val y = 14.15f
    const val w = 87.43f
    const val h = 62.70f
}

private val PICTURE_ROI = PictureRoi

/** 그림창을 액자 속에 넣을 때 남기는 여유. 1 이면 딱 맞고, 작을수록 물러난다. */
private const val PICTURE_FIT = 0.94f

object FrameSpec {
    /** 왼쪽 모서리 (스테이지 가로 %) */
    const val LEFT = 72f

    /** 오른쪽 모서리 */
    const val RIGHT = 91f

    /** 왼쪽 모서리에서의 액자 위 끝 (스테이지 세로 %) */
    const val TOP = 26f

    /** 액자 높이 (스테이지 세로 %) */
    const val HEIGHT = 17f

    /**
     * 벽 기울기. 가로 1%당 내려가는 세로 %.
     *
     * 오른쪽 벽의 위 가장자리를 x 70%~94% 구간에서 재서 얻었다 (12.9% -> 21.3%).
     * 바닥 쪽은 걸레받이·가구가 걸려서 값이 흔들리므로 위 가장자리를 썼다.
     */
    const val SLOPE = 0.35f

    /** [x] (가로 %) 에서 액자가 내려간 정도 (세로 %) */
    fun drop(x: Float) = SLOPE * (x - LEFT)

    /** 누르는 판정에 쓰는 사각형. 기울기를 무시한 외접 사각형이라 조금 너그럽다. */
    fun bounds(g: RoomGeometry): Rect {
        val left = g.stage.left + LEFT / 100f * g.stage.width
        val right = g.stage.left + RIGHT / 100f * g.stage.width
        val top = g.stage.top + TOP / 100f * g.stage.height
        val bottom = g.stage.top + (TOP + HEIGHT + drop(RIGHT)) / 100f * g.stage.height
        return Rect(left, top, right, bottom)
    }

    /** 액자를 눌렀는가. */
    fun contains(g: RoomGeometry, pos: Offset): Boolean = bounds(g).contains(pos)
}

/**
 * 액자를 그린다. [picture] 가 null 이면 액자만 그린다.
 *
 * 기울기는 **캔버스를 밀어서** 준다. 액자와 그림을 각각 기울여 그리면 둘이 어긋나는데,
 * 축에 나란한 사각형으로 그린 뒤 통째로 미는 편이 어긋날 자리가 없다.
 *
 * [pulse] 는 문과 같은 "누를 수 있다"는 표시다. 0..1.
 */
fun DrawScope.drawWallFrame(g: RoomGeometry, picture: ImageBitmap?, pulse: Float) {
    val left = g.stage.left + FrameSpec.LEFT / 100f * g.stage.width
    val right = g.stage.left + FrameSpec.RIGHT / 100f * g.stage.width
    val top = g.stage.top + FrameSpec.TOP / 100f * g.stage.height
    val height = FrameSpec.HEIGHT / 100f * g.stage.height
    val width = right - left

    // 가로 1px 갈 때 내려가는 세로 px. 백분율 기울기를 화면 비율로 환산한다.
    val k = FrameSpec.SLOPE * (g.stage.height / g.stage.width)

    withTransform({
        transform(
            Matrix().apply {
                values[Matrix.SkewY] = k
                // 기울임의 중심을 액자 왼쪽 모서리로 옮긴다. 안 그러면 화면 원점을
                // 축으로 돌아서 액자가 엉뚱한 데로 간다.
                values[Matrix.TranslateY] = -k * left
            },
        )
    }) {
        val border = height * 0.075f
        val mat = height * 0.045f

        // 액자 테두리. 평평한 사각형이라 픽셀 화풍과 안 부딪힌다.
        drawRect(FrameWood, Offset(left, top), Size(width, height))
        drawRect(
            FrameWoodLit,
            Offset(left, top),
            Size(width, border * 0.6f),
        )
        // 안쪽 대지(mat)
        drawRect(
            FrameMat,
            Offset(left + border, top + border),
            Size(width - border * 2f, height - border * 2f),
        )

        val artLeft = left + border + mat
        val artTop = top + border + mat
        val artW = width - (border + mat) * 2f
        val artH = height - (border + mat) * 2f
        if (picture != null) {
            clipRect(artLeft, artTop, artLeft + artW, artTop + artH) {
                // 카드는 세로로 길고(810x1125) 액자 속은 가로로 넓다. 예전에는 가로를
                // 채우고 **위쪽만** 보여줬는데, 그러면 강아지 아래가 잘렸다.
                //
                // 그래서 카드 전체가 아니라 **그림창만** 액자에 맞춘다. 그 자리는
                // [ImmersiveScene.fit] 이 이미 알고 있는 값이다 — 이머시브가 누끼를
                // 카드 안 제자리에 놓을 때 쓰는 사각형이라, 강아지가 그 안에 있다.
                //
                // 그림창이 **다 들어오게**(contain) 맞추고 가운데를 잡는다. 덮이게
                // 키웠더니 액자 속이 그림창보다 넓지 않아서 아래가 또 잘렸다.
                // 남는 자리는 카드의 홀로그램 테두리가 채우므로 빈 데가 안 생긴다.
                val roiW = picture.width * PICTURE_ROI.w / 100f
                val roiH = picture.height * PICTURE_ROI.h / 100f
                // 살짝 물러나 액자 테두리에 딱 붙지 않게. 붙으면 잘린 것처럼 보인다.
                val s = minOf(artW / roiW, artH / roiH) * PICTURE_FIT

                val drawW = picture.width * s
                val drawH = picture.height * s
                // 그림창의 중심이 액자 속 중심에 오도록 그림 전체를 민다.
                val roiCx = (PICTURE_ROI.x + PICTURE_ROI.w / 2f) / 100f * drawW
                val roiCy = (PICTURE_ROI.y + PICTURE_ROI.h / 2f) / 100f * drawH
                drawImage(
                    image = picture,
                    dstOffset = IntOffset(
                        (artLeft + artW / 2f - roiCx).roundToInt(),
                        (artTop + artH / 2f - roiCy).roundToInt(),
                    ),
                    dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }
        } else {
            drawRect(FrameEmpty, Offset(artLeft, artTop), Size(artW, artH))
        }

        if (pulse > 0.001f) {
            drawRect(
                Color.White.copy(alpha = 0.10f * pulse),
                Offset(left, top),
                Size(width, height),
            )
        }
    }
}

/** 액자 나무. 방 그림의 가구 톤에서 가져왔다. */
private val FrameWood = Color(0xFF6E5636)
private val FrameWoodLit = Color(0xFF8A6E48)

/** 그림 둘레 대지. */
private val FrameMat = Color(0xFFF3E7CE)

/** 그림이 아직 없을 때. */
private val FrameEmpty = Color(0xFF2B2118)
