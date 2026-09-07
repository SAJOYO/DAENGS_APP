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
import com.daengs.app.map.style.rememberWalkStyle
import com.daengs.app.map.style.speedScaleStops
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The host chooses when to show this; colors follow the same preference as the native path. */
@Composable
internal fun WalkSpeedLegend(modifier: Modifier = Modifier, showColorSettings: Boolean = false) {
    val selection by rememberWalkStyle()
    val policy = selection.policy
    val theme = policy.theme(selection.themeId)
    val stops = remember(policy, theme.id) {
        policy.speedScaleStops(theme.id).map { (offset, color) -> offset to Color(color) }.toTypedArray()
    }
    val number = remember { DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT)) }
    val max = number.format(policy.speedMax)
    val middle = number.format(policy.speedMax / 2)
    Surface(modifier.width(236.dp), shape = RoundedCornerShape(12.dp), color = CardWhite,
        shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("이동 속도 · m/s", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                if (showColorSettings) WalkColorSettingsButton()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(Color(policy.unknownColor)))
                Spacer(Modifier.width(4.dp))
                Text("속도 미확인", style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(*stops))
                .semantics {
                    contentDescription = "${theme.label} 속도 스펙트럼. 왼쪽 0 m/s에서 오른쪽 $max m/s 이상으로 빨라져요."
                })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 · 느리게", style = MaterialTheme.typography.labelSmall)
                Text(middle, style = MaterialTheme.typography.labelSmall)
                Text("$max+ · 빠르게", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WalkSpeedLegendPreview() { DaengsTheme { WalkSpeedLegend() } }

@Preview(showBackground = true)
@Composable
private fun WalkSpeedLegendSettingsPreview() { DaengsTheme { WalkSpeedLegend(showColorSettings = true) } }
