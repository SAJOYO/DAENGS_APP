package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.features.territory.TerritoryGameState
import com.daengs.app.territory.ClaimAccess
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

@Composable
internal fun TerritoryActionCard(
    game: TerritoryGameState,
    onMark: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val ready = game.target?.interaction?.access == ClaimAccess.READY
    LaunchedEffect(game.targetId, ready) {
        if (ready) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Surface(modifier.widthIn(max = 320.dp), shape = RoundedCornerShape(18.dp), color = CardWhite) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("점령 연습 · 임시 영역표시", color = TextMuted, fontSize = 11.sp)
            Text(game.target?.occupancyLabel ?: "점령지를 선택해 주세요", color = TextDark)
            game.representativeLabel?.let { Text("영역표시 주체 · $it", color = TextMuted, fontSize = 12.sp) }
            Text(game.guidance, color = TextDark, fontSize = 12.sp)
            Button(
                onClick = { game.targetId?.let(onMark) },
                enabled = game.canMark,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DaengsIconView(DaengsIcon.Pin, Modifier.size(18.dp), tint = LocalContentColor.current)
                    Text(game.actionLabel)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TerritoryActionCardPreview() {
    DaengsTheme {
        TerritoryActionCard(
            TerritoryGameState(enabled = true, representativeLabel = "보리", guidance = "점령 준비 · 영역표시할 수 있어요", canMark = true),
            onMark = {},
        )
    }
}
