package com.daengs.app.map.provider.naver

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.TextDark
import kotlin.math.ceil

/** Same canvas in both states, so selection cannot move the true route coordinate. */
internal fun diaryRouteAnchorBitmap(selected: Boolean, density: Float): Bitmap {
    val size = ceil(26f * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun circle(radius: Float) = canvas.drawCircle(center, center, radius * density, paint)
    if (selected) {
        paint.color = DaengPinkDeep.copy(alpha = .15f).toArgb()
        circle(11f)
    }
    paint.color = CardWhite.toArgb()
    circle(if (selected) 8f else 6.5f)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = (if (selected) 1.5f else 1.2f) * density
    paint.color = TextDark.toArgb()
    circle(if (selected) 8f else 6.5f)
    paint.style = Paint.Style.FILL
    paint.color = (if (selected) DaengPinkDeep else TextDark).toArgb()
    circle(if (selected) 4.5f else 3.5f)
    return bitmap
}

@Preview(showBackground = true)
@Composable
private fun DiaryRouteAnchorPreview() {
    val density = LocalDensity.current.density
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(diaryRouteAnchorBitmap(false, density).asImageBitmap(), "기록 위치")
        Image(diaryRouteAnchorBitmap(true, density).asImageBitmap(), "선택한 기록 위치")
    }
}
