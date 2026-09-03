package com.daengs.app.ui.chat

import kotlin.math.abs
import kotlin.math.hypot

/**
 * 자르는 네모의 셈. **화면과 떼어 둔다** — 손짓은 기기에서 봐야 알지만 좌표는
 * 눈으로 못 잡는다. 모서리를 하나만 받던 시절의 버그가 그런 종류였다.
 *
 * 좌표는 전부 **정규화**(0~1)다. 세로는 따로 안 들고 [heightOf] 로 그때그때 구한다 —
 * 들고 있으면 가로를 바꿀 때 같이 안 고쳐서 어긋난다.
 */
data class CropBox(val x: Float, val y: Float, val w: Float)

enum class CropCorner {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

/** 네모의 세로. 사진 비율([aspect] = 가로/세로)을 곱한다. 1을 넘지 않는다. */
fun heightOf(w: Float, aspect: Float): Float = (w * aspect).coerceAtMost(1f)

/** 네모의 최소 가로. 이보다 작으면 잡을 수가 없다. */
const val MIN_CROP_WIDTH = 0.10f

/**
 * 짚은 곳이 **네 모서리 중 하나**인가.
 *
 * 예전에는 오른쪽 아래 하나만 봤다. 그래서 왼쪽 위를 아무리 끌어도 네모가 움직이기만
 * 하고 커지지 않았다 — 크기를 바꾸려면 매번 반대편으로 손을 옮겨야 했다.
 */
fun grabsCorner(
    px: Float,
    py: Float,
    box: CropBox,
    aspect: Float,
    grab: Float,
): Boolean = grabbedCorner(px, py, box, aspect, grab) != null

fun grabbedCorner(
    px: Float,
    py: Float,
    box: CropBox,
    aspect: Float,
    grab: Float,
): CropCorner? {
    val h = heightOf(box.w, aspect)
    val corners = listOf(
        CropCorner.TopLeft to (box.x to box.y),
        CropCorner.TopRight to (box.x + box.w to box.y),
        CropCorner.BottomLeft to (box.x to box.y + h),
        CropCorner.BottomRight to (box.x + box.w to box.y + h),
    )
    return corners.minByOrNull { (_, point) -> hypot(px - point.first, py - point.second) }
        ?.takeIf { (_, point) -> hypot(px - point.first, py - point.second) < grab }
        ?.first
}

/** 양수면 해당 모서리를 바깥으로 끌어 자르기 영역이 커진 것이다. */
fun cornerResizeDelta(corner: CropCorner, dx: Float, dy: Float): Float {
    val horizontal = when (corner) {
        CropCorner.TopLeft, CropCorner.BottomLeft -> -dx
        CropCorner.TopRight, CropCorner.BottomRight -> dx
    }
    val vertical = when (corner) {
        CropCorner.TopLeft, CropCorner.TopRight -> -dy
        CropCorner.BottomLeft, CropCorner.BottomRight -> dy
    }
    return if (abs(dx) > abs(dy)) horizontal else vertical
}

/**
 * 가로를 [want] 로 바꾼다. **중심을 고정한 채** 늘린다.
 *
 * 모서리를 기준으로 늘리면 크기를 맞추는 동안 가운데 정렬이 풀려서, 가운데에 두라는
 * 안내에 계속 걸린다 — 저쪽이 겪고 적어 둔 것이다.
 *
 * 네모가 가장자리에 붙어 있으면 남은 여유가 최소 폭보다 작을 수 있다. 그때는 더 못
 * 늘리는 것뿐이라 범위를 뒤집지 않는다 — 뒤집힌 범위를 주면 `coerceIn` 이 던진다.
 */
fun resizeAroundCenter(box: CropBox, aspect: Float, want: Float): CropBox {
    val curH = heightOf(box.w, aspect)
    val cx = box.x + box.w / 2f
    val cy = box.y + curH / 2f
    val roomX = 2f * minOf(cx, 1f - cx)
    val roomY = 2f * minOf(cy, 1f - cy) / aspect
    val maxW = maxOf(minOf(roomX, roomY, 1f), MIN_CROP_WIDTH)
    val k = want.coerceIn(MIN_CROP_WIDTH, maxW)
    val nh = heightOf(k, aspect)
    return CropBox(
        x = (cx - k / 2f).coerceIn(0f, (1f - k).coerceAtLeast(0f)),
        y = (cy - nh / 2f).coerceIn(0f, (1f - nh).coerceAtLeast(0f)),
        w = k,
    )
}

/** 네모를 옮긴다. 사진 밖으로는 안 나간다. */
fun moveBy(box: CropBox, aspect: Float, dx: Float, dy: Float): CropBox {
    val h = heightOf(box.w, aspect)
    return box.copy(
        x = (box.x + dx).coerceIn(0f, (1f - box.w).coerceAtLeast(0f)),
        y = (box.y + dy).coerceIn(0f, (1f - h).coerceAtLeast(0f)),
    )
}

/** 네모가 사진 한가운데에서 얼마나 벗어났나. 안내 문구가 쓴다. */
fun centerOffset(box: CropBox, aspect: Float): Float {
    val h = heightOf(box.w, aspect)
    return maxOf(abs(box.x + box.w / 2f - 0.5f), abs(box.y + h / 2f - 0.5f))
}
