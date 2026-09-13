package com.daengs.app.map.provider.naver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.daengs.app.R
import com.daengs.app.walk.WalkMomentType
import kotlin.math.roundToInt

/** The SDK receives one centered bitmap. Co-located behaviors remain visible together. */
internal fun actionMarkerBitmap(
    context: Context,
    behaviors: Set<WalkMomentType>,
    selected: Boolean,
    countLabel: String?,
    density: Float,
    recordFocusRing: Boolean = false,
): Bitmap {
    require(behaviors.isNotEmpty())
    val ordered = WalkMomentType.entries.filter(behaviors::contains)
    val iconDp = if (selected) 36 else 32
    val width = ((iconDp * ordered.size + 8) * density).roundToInt().coerceAtLeast(1)
    val height = ((iconDp + 8) * density).roundToInt().coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    if (selected && recordFocusRing) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(density, density, width - density, height - density, 20*density, 20*density, paint)
    }
    ordered.forEachIndexed { index, type ->
        val drawable = requireNotNull(context.getDrawable(when (type) {
            WalkMomentType.SNIFFING -> R.drawable.ic_walk_sniffing
            WalkMomentType.EXCRETION -> R.drawable.ic_walk_excretion
            WalkMomentType.BARKING -> R.drawable.ic_walk_barking
            WalkMomentType.NOTE -> R.drawable.ic_walk_note
        }))
        drawable.setBounds(
            ((4 + index * iconDp) * density).roundToInt(), (4 * density).roundToInt(),
            ((4 + (index + 1) * iconDp) * density).roundToInt(), ((4 + iconDp) * density).roundToInt(),
        )
        drawable.draw(canvas)
    }
    // A count supplements the silhouette; it never replaces it with a numbered generic pin.
    countLabel?.let { label ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10 * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val badgeWidth = maxOf(16 * density, paint.measureText(label) + 6 * density)
        val right = width.toFloat()
        val bottom = 16 * density
        paint.color = Color.WHITE
        canvas.drawRoundRect(right - badgeWidth, 0f, right, bottom, 8 * density, 8 * density, paint)
        paint.color = Color.rgb(91, 63, 54)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, right - badgeWidth / 2, bottom / 2 - (paint.ascent() + paint.descent()) / 2, paint)
    }
    return bitmap
}

/** Uses the same vectors and bitmap composition as the real SDK marker. */
@Preview(showBackground = true, backgroundColor = 0xFFFFFAF4, widthDp = 340)
@Composable
internal fun ActionMarkersPreview() {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WalkMomentType.entries.forEach { type ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val bitmap = remember(type, density) { actionMarkerBitmap(context, setOf(type), false, null, density) }
                    Image(bitmap.asImageBitmap(), type.label)
                    Text(type.label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val selected = remember(density) { actionMarkerBitmap(context, setOf(WalkMomentType.BARKING), true, null, density) }
            Image(selected.asImageBitmap(), "선택한 짖기")
            val grouped = remember(density) {
                actionMarkerBitmap(context, setOf(WalkMomentType.SNIFFING, WalkMomentType.BARKING), false, "3", density)
            }
            Image(grouped.asImageBitmap(), "같은 위치의 킁킁과 짖기, 기록 3건")
        }
    }
}
