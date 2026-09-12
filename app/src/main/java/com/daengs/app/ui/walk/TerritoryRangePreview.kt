package com.daengs.app.ui.walk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.map.layers.territory.*
import com.daengs.app.territory.TerritoryProximityRange
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.CreamBg

/** Shared ring colors and actual pole art. SDK projection/zoom still require device validation. */
@Preview(showBackground = true, widthDp = 320, heightDp = 620)
@Composable
internal fun TerritoryRangePreview() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pole = remember(context) { territoryMarkerIcon(context, TerritoryMarkerOccupancy.NEUTRAL).asImageBitmap() }
    val size = TerritoryPoleArt.size()
    DaengsTheme {
        Column(Modifier.fillMaxSize().background(CreamBg).padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TerritoryProximityRange.entries.forEach { proximity ->
                val style = territoryRangeStyle(proximity)
                val color = Color(style.outlineArgb)
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(136.dp)) {
                        drawCircle(color.copy(alpha = 20 / 255f))
                        drawCircle(color, style = Stroke(2.dp.toPx()))
                    }
                    Image(pole, "전봇대", modifier = with(density) {
                        Modifier.offset(y = (size.second * (.5f - TerritoryPoleArt.ANCHOR_Y)).toDp())
                            .size(size.first.toDp(), size.second.toDp())
                    })
                    Text(style.label, color = color, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}
