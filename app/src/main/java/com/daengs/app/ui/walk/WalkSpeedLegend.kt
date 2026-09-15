package com.daengs.app.ui.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.daengs.app.map.style.rememberWalkStyle
import com.daengs.app.map.style.speedScaleStops
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The host chooses when to show this; colors follow the same preference as the native path. */
@Composable
internal fun WalkSpeedLegend(modifier: Modifier = Modifier, compact: Boolean = false) {
    val selection by rememberWalkStyle()
    val policy = selection.policy
    val theme = policy.theme(selection.themeId)
    val stops = remember(policy, theme.id) {
        policy.speedScaleStops(theme.id).map { (offset, color) -> offset to Color(color) }.toTypedArray()
    }
    val number = remember { DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT)) }
    val max = number.format(policy.speedMax * if (compact) 3.6 else 1.0)
    val middle = number.format(policy.speedMax / 2)
    val unit = if (compact) "km/h" else "m/s"
    Surface(modifier.testTag("speedLegend"), shape = RoundedCornerShape(8.dp), color = CardWhite) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (compact) "이동 속도 km/h" else "속도 m/s", fontSize = 10.sp, lineHeight = 12.sp)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(*stops))
                    .semantics {
                        contentDescription = "${theme.label} 속도 스펙트럼. 왼쪽 0 ${unit}에서 오른쪽 $max $unit 이상으로 빨라져요."
                    })
                if (!compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (label in listOf("0", middle, "$max+")) Text(label, fontSize = 10.sp, lineHeight = 12.sp)
                }
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WalkSpeedLegendPreview() { DaengsTheme { WalkSpeedLegend() } }

@Preview(showBackground = true)
@Composable
private fun CompactWalkSpeedLegendPreview() { DaengsTheme { WalkSpeedLegend(compact = true) } }
