package com.daengs.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.ui.theme.DaengPink

/** The map pole's cap, brackets and safety band simplified for a small monochrome UI icon. */
internal fun DrawScope.iconTerritoryPole(tint: Color) {
    val stroke = Stroke(1.25f, cap = StrokeCap.Round)
    fun rounded(x: Float, y: Float, w: Float, h: Float, radius: Float, fill: Float) {
        drawRoundRect(tint.copy(alpha = tint.alpha * fill), Offset(x, y), Size(w, h), CornerRadius(radius))
        drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(radius), style = stroke)
    }
    rounded(8f, 1.5f, 8f, 21f, 2.2f, .12f)
    rounded(8f, 1.5f, 8f, 4.5f, 2f, .22f)
    // Two rounded insulators and short brackets, without small map-only hardware details.
    rounded(4.3f, 5f, 2.6f, 2.7f, .8f, .10f)
    rounded(17.1f, 5f, 2.6f, 2.7f, .8f, .10f)
    drawLine(tint, Offset(5f, 9f), Offset(8f, 11.5f), 1.25f, StrokeCap.Round)
    drawLine(tint, Offset(19f, 9f), Offset(16f, 11.5f), 1.25f, StrokeCap.Round)
    rounded(3.4f, 7.2f, 5.8f, 1.9f, .6f, .22f)
    rounded(14.8f, 7.2f, 5.8f, 1.9f, .6f, .22f)
    drawRoundRect(tint.copy(alpha = tint.alpha * .28f), Offset(9.3f, 12f), Size(2.6f, 3.5f), CornerRadius(.6f))
    drawLine(tint, Offset(10f, 13f), Offset(11.1f, 13f), .55f, StrokeCap.Round)
    drawLine(tint, Offset(10f, 14.1f), Offset(11.1f, 14.1f), .55f, StrokeCap.Round)
    clipRect(8.7f, 17.3f, 15.3f, 20.3f) {
        drawRect(tint.copy(alpha = tint.alpha * .16f), Offset(8.7f, 17.3f), Size(6.6f, 3f))
        for (x in listOf(7f, 12f)) drawPath(Path().apply {
            moveTo(x, 20.3f); lineTo(x + 2.3f, 20.3f)
            lineTo(x + 5.3f, 17.3f); lineTo(x + 3f, 17.3f); close()
        }, tint)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun TerritorySummaryIconsPreview() {
    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DaengsIconView(DaengsIcon.TerritoryPole, Modifier.size(28.dp), DaengPink)
        DaengsIconView(DaengsIcon.TerritoryPole, Modifier.size(48.dp), DaengPink)
    }
}
