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

/** The tail anchors the true coordinate. Endpoints sit below it; scene numbers sit above it. */
internal fun diaryPinBitmap(label: String, selected: Boolean, density: Float, endpoint: Boolean = false): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (endpoint) 11f else 14f) * density
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    val width = ceil(maxOf(if (endpoint) 36f else 32f, paint.measureText(label) / density + 20f) * density).toInt()
    val bodyHeight = (if (endpoint) 22f else 30f) * density
    val tail = 5f * density
    val bitmap = Bitmap.createBitmap(width, ceil(bodyHeight + tail + 2f * density).toInt(), Bitmap.Config.ARGB_8888)
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

@Preview(showBackground = true)
@Composable
private fun DiaryPinPreview() {
    val density = LocalDensity.current.density
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(diaryPinBitmap("1", false, density).asImageBitmap(), "첫 장면")
        Image(diaryPinBitmap("2 · 3", true, density).asImageBitmap(), "두 번째와 세 번째 장면")
        Image(diaryPinBitmap("출발", false, density, endpoint = true).asImageBitmap(), "출발")
    }
}
