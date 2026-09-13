package com.daengs.app.map.provider.naver

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import com.daengs.app.ui.theme.*
import kotlin.math.ceil

/** Badge is included in the bitmap and in the layout footprint; the tail remains at the anchor. */
internal fun diaryGroupPinBitmap(ordinal: Int, count: Int, selected: Boolean, density: Float): android.graphics.Bitmap {
    val base = diaryPinBitmap(ordinal.toString(), selected, density)
    if (count <= 1) return base
    val label = "+${count-1}"
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10*density; typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    val extra = ceil(maxOf(16*density, paint.measureText(label)+8*density)).toInt()
    // Symmetric horizontal padding retains a .5,1 anchor for the original tail.
    val inset = extra/2
    val top = ceil(9*density).toInt()
    val bitmap = createBitmap(base.width+2*inset, base.height+top)
    val canvas = Canvas(bitmap)
    canvas.drawBitmap(base, inset.toFloat(), top.toFloat(), null); base.recycle()
    val right = bitmap.width-density; val bottom = 19*density
    paint.color = CreamBg.toArgb()
    canvas.drawRoundRect(right-extra, density, right, bottom, 9*density, 9*density, paint)
    paint.style = Paint.Style.STROKE; paint.strokeWidth = density; paint.color = DaengPinkDeep.toArgb()
    canvas.drawRoundRect(right-extra, density, right, bottom, 9*density, 9*density, paint)
    paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
    canvas.drawText(label, right-extra/2f, (bottom+density)/2-(paint.ascent()+paint.descent())/2, paint)
    return bitmap
}

@Preview(showBackground = true)
@Composable
private fun DiaryGroupPinPreview() { Row {
    val density = LocalDensity.current.density
    Image(diaryGroupPinBitmap(2, 8, false, density).asImageBitmap(), "2번 외 7개")
    Image(diaryGroupPinBitmap(17, 121, true, density).asImageBitmap(), "17번 외 120개")
} }
