package com.daengs.app.ui.walk

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.distanceTo
import kotlin.math.cos
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.DaengPinkDeep
import androidx.compose.ui.graphics.toArgb
import com.daengs.app.ui.theme.CardWhite

/** Canvas, no map SDK/tile request per row. Points already passed the shared route filter. */
@Composable
internal fun WalkRouteThumbnail(walk: WalkSummary, modifier: Modifier = Modifier) {
    val points = remember(walk.segments) { walk.segments.flatten().map { it.point } }
    val merged = points.size > 1 && points.first().distanceTo(points.last()) <= 12
    val description = if (!walk.hasRoute) "경로 미기록" else if (merged) "산책 동선, 출발·도착 같은 자리" else "산책 동선, 출발과 도착"
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(PinkFaint)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        if (!walk.hasRoute) Text("경로 미기록", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        else Canvas(Modifier.matchParentSize()) {
            val cosLat = cos(Math.toRadians(points.first().latitude))
            val left = points.minOf { it.longitude }; val bottom = points.minOf { it.latitude }
            val width = (points.maxOf { it.longitude } - left) * cosLat
            val height = points.maxOf { it.latitude } - bottom
            val padding = 18.dp.toPx()
            val scale = minOf((size.width - padding * 2) / width.coerceAtLeast(1e-9),
                (size.height - padding * 2) / height.coerceAtLeast(1e-9))
            fun pos(p: GeoPoint) = Offset(((size.width - width * scale) / 2 + (p.longitude - left) * cosLat * scale).toFloat(),
                ((size.height + height * scale) / 2 - (p.latitude - bottom) * scale).toFloat())
            val ink = DaengPinkDeep
            walk.segments.filter { it.size > 1 }.forEach { segment ->
                val path = Path(); segment.forEachIndexed { i, sample ->
                    val xy = pos(sample.point); if (i == 0) path.moveTo(xy.x, xy.y) else path.lineTo(xy.x, xy.y)
                }; drawPath(path, ink, style = Stroke(2.dp.toPx()))
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.dp.toPx(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
            val ends = if (merged) listOf(points.first() to "출발·도착") else listOf(points.first() to "출발", points.last() to "도착")
            ends.forEachIndexed { i, (point, label) ->
                val xy = pos(point)
                drawCircle(ink, 3.dp.toPx(), xy)
                if (i == 0) drawCircle(Color.White, 1.6.dp.toPx(), xy)
                val half = paint.measureText(label) / 2 + 3.dp.toPx()
                val x = xy.x.coerceIn(half, size.width - half)
                val y = if (i == 0) xy.y - 8.dp.toPx() else xy.y + 15.dp.toPx()
                val baseline = y.coerceIn(12.dp.toPx(), size.height - 3.dp.toPx())
                paint.color = CardWhite.toArgb()
                drawContext.canvas.nativeCanvas.drawRoundRect(x - half, baseline - 10.dp.toPx(), x + half,
                    baseline + 3.dp.toPx(), 4.dp.toPx(), 4.dp.toPx(), paint)
                paint.color = DaengPinkDeep.toArgb(); drawContext.canvas.nativeCanvas.drawText(label, x, baseline, paint)
            }
        }
    }
}

internal fun previewDiarySummary(): WalkSummary {
    val coordinates = listOf(GeoPoint(37.54,127.03), GeoPoint(37.541,127.03), GeoPoint(37.541,127.032), GeoPoint(37.54,127.032), GeoPoint(37.54,127.03))
    return WalkSummary("preview", emptyList(), 1_788_324_720_000L, 1_788_326_220_000L, null, 750.0, 900_000,
        listOf(coordinates.mapIndexed { i, p -> LocationSample(p, 1_788_324_720_000L + i * 200_000) }), coordinates.first())
}

@Preview(showBackground = true)
@Composable
private fun ThumbnailPreview() { DaengsTheme { WalkRouteThumbnail(previewDiarySummary(), Modifier.size(88.dp)) } }
