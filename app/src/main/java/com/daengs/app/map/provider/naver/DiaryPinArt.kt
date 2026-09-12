package com.daengs.app.map.provider.naver

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

internal fun diaryPinSize(label: String, density: Float, endpoint: Boolean = false): Pair<Int, Int> {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (endpoint) 11f else 14f) * density
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    val width = ceil(maxOf(if (endpoint) 36f else 32f, paint.measureText(label) / density + 20f) * density).toInt()
    return width to ceil(((if (endpoint) 22f else 30f) + 7f) * density).toInt()
}

/** The tail anchors the true coordinate. Endpoints sit below it; scene numbers sit above it. */
internal fun diaryPinBitmap(label: String, selected: Boolean, density: Float, endpoint: Boolean = false): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (endpoint) 11f else 14f) * density
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    val (width, height) = diaryPinSize(label, density, endpoint)
    val bodyHeight = (if (endpoint) 22f else 30f) * density
    val tail = 5f * density
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val left = density
    val right = width - density
    val top = density + if (endpoint) tail else 0f
    val bottom = top + bodyHeight
    val center = width / 2f
    val fill = if (selected) Color.rgb(128, 48, 92) else Color.WHITE
    val ink = if (selected) Color.WHITE else Color.rgb(107, 48, 81)
    val shape = Path().apply {
        addRoundRect(left, top, right, bottom, 10f * density, 10f * density, Path.Direction.CW)
        if (endpoint) { moveTo(center - tail, top); lineTo(center, 0f); lineTo(center + tail, top) }
        else { moveTo(center - tail, bottom); lineTo(center, bitmap.height.toFloat()); lineTo(center + tail, bottom) }
        close()
    }
    paint.color = fill
    canvas.drawPath(shape, paint)
    paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density
    paint.color = Color.rgb(128, 48, 92)
    canvas.drawPath(shape, paint)
    paint.style = Paint.Style.FILL; paint.color = ink; paint.textAlign = Paint.Align.CENTER
    val baseline = (top + bottom) / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    canvas.drawText(label, center, baseline, paint)
    return bitmap
}

/** Fixed gray minus, matching the diary list. Its tail sits below the true coordinate. */
internal fun diaryGapPinBitmap(selected: Boolean, density: Float): Bitmap {
    val width = ceil(34f * density).toInt()
    val bitmap = Bitmap.createBitmap(width, ceil(39f * density).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centerX = width / 2f
    val centerY = 22f * density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val shape = Path().apply {
        addCircle(centerX, centerY, 16f * density, Path.Direction.CW)
        moveTo(centerX - 4f * density, 7f * density)
        lineTo(centerX, 0f)
        lineTo(centerX + 4f * density, 7f * density)
        close()
    }
    paint.color = if (selected) Color.rgb(103, 99, 108) else Color.rgb(239, 237, 241)
    canvas.drawPath(shape, paint)
    paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density
    paint.color = Color.rgb(119, 113, 125)
    canvas.drawPath(shape, paint)
    paint.color = if (selected) Color.WHITE else Color.rgb(103, 99, 108)
    paint.strokeWidth = 2f * density; paint.strokeCap = Paint.Cap.ROUND
    canvas.drawLine(centerX - 5f * density, centerY, centerX + 5f * density, centerY, paint)
    return bitmap
}

@Preview(showBackground = true)
@Composable
private fun DiaryPinPreview() {
    val density = LocalDensity.current.density
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(diaryPinBitmap("1", false, density).asImageBitmap(), "첫 장면")
        Image(diaryPinBitmap("2 · 3", true, density).asImageBitmap(), "두 번째와 세 번째 장면")
        Image(diaryPinBitmap("출발", false, density, endpoint = true).asImageBitmap(), "출발")
        Image(diaryGapPinBitmap(false, density).asImageBitmap(), "경로 공백")
        Image(diaryGapPinBitmap(true, density).asImageBitmap(), "선택한 경로 공백")
    }
}
