package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import kotlin.math.max
import kotlin.math.min

/**
 * 얼굴을 **원 안에 어떻게 놓을지**. 정규화 값이라 화면 크기와 상관없다.
 *
 * 원의 외접 정사각형을 1x1 로 본다. [scale] 은 그 정사각형 대비 누끼의 긴 변 배율이고,
 * [cx]·[cy] 는 누끼의 한가운데가 놓일 자리다 (0.5, 0.5 가 정중앙).
 *
 * **화면과 떼어 둔다.** 손짓은 기기에서 봐야 알지만 이 셈은 눈으로 못 잡는다.
 */
data class FaceFrame(val scale: Float, val cx: Float, val cy: Float) {
    companion object {
        val CENTER = FaceFrame(1f, 0.5f, 0.5f)
    }
}

/** 원 안에서 더 줄이거나 키울 수 있는 한계. 너무 작으면 얼굴을 잃고, 너무 크면 화소가 뭉갠다. */
const val FRAME_MIN_SCALE = 0.35f
const val FRAME_MAX_SCALE = 3.5f

/**
 * 누끼의 **또렷한 얼굴이 원을 채우도록** 시작 값을 잡는다.
 *
 * 처음부터 사용자가 다 맞추게 하면 대부분은 손도 안 대고 넘긴다. 예전에 앱이 알아서
 * 하던 그 자리에서 시작해서, 마음에 안 들 때만 만지게 한다.
 *
 * @param core 또렷한 얼굴의 자리 (`Cutout.faceFor` 가 재어 준다)
 */
fun initialFrame(
    imageWidth: Int,
    imageHeight: Int,
    core: AndroidRect,
): FaceFrame {
    if (imageWidth <= 0 || imageHeight <= 0) return FaceFrame.CENTER
    val long = max(imageWidth, imageHeight).toFloat()
    val coreW = core.width().coerceAtLeast(1).toFloat()
    val coreH = core.height().coerceAtLeast(1).toFloat()
    // 또렷한 얼굴의 **모자란 쪽**이 원을 덮게 키운다. 짧은 쪽에 맞추면 원이 빈다.
    val scale = (long / min(coreW, coreH)).coerceIn(FRAME_MIN_SCALE, FRAME_MAX_SCALE)
    // 누끼 한가운데가 아니라 **또렷한 얼굴의 한가운데**가 원 복판에 오게 민다.
    val faceCx = (core.left + core.width() / 2f) / imageWidth
    val faceCy = (core.top + core.height() / 2f) / imageHeight
    return FaceFrame(
        scale = scale,
        cx = 0.5f + (0.5f - faceCx) * scale * imageWidth / long,
        cy = 0.5f + (0.5f - faceCy) * scale * imageHeight / long,
    )
}

/** 손짓으로 바뀐 값을 범위 안으로. 원 밖으로 아주 나가 버리면 되돌릴 길이 없다. */
fun FaceFrame.nudged(dScale: Float, dx: Float, dy: Float): FaceFrame = FaceFrame(
    scale = (scale * dScale).coerceIn(FRAME_MIN_SCALE, FRAME_MAX_SCALE),
    cx = (cx + dx).coerceIn(-0.5f, 1.5f),
    cy = (cy + dy).coerceIn(-0.5f, 1.5f),
)

/**
 * 맞춘 대로 **정사각형에 굽는다.** 원은 이 정사각형에 내접한다.
 *
 * 굽는 이유는 그리는 쪽을 단순하게 두려는 것이다. 맞춘 값을 그대로 들고 다니면
 * `drawInHoleOf` 가 카드마다 다른 구멍 비율에 그 값을 다시 풀어야 하는데, 구멍은
 * 카드마다 3.4배까지 차이 난다. **한 번 구워 두면 그리는 쪽은 가운데 정렬 한 번이다.**
 *
 * @param side 결과 정사각형의 한 변. 카드 구멍이 화면에서 200px 남짓이라 그 두 배면 넉넉하다
 */
fun bakeFramed(source: Bitmap, frame: FaceFrame, side: Int = FRAMED_SIDE): Bitmap {
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val long = max(source.width, source.height).toFloat()
    val w = source.width / long * frame.scale * side
    val h = source.height / long * frame.scale * side
    val left = frame.cx * side - w / 2f
    val top = frame.cy * side - h / 2f
    canvas.drawBitmap(
        source,
        null,
        android.graphics.RectF(left, top, left + w, top + h),
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
    )
    return out
}

/** 구운 정사각형의 한 변. */
const val FRAMED_SIDE = 512
