package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.map.features.territory.*
import com.daengs.app.ui.theme.*

@Composable
internal fun TerritoryCardActions(
    game: TerritoryGameState, onMark: (String) -> Unit, onPhotograph: (String) -> Unit,
    onSelectPet: (String, String) -> Unit, onPrepareWalk: (() -> Unit)?,
) {
    val siteId = game.targetId ?: return
    var choosingPet by remember(siteId) { mutableStateOf(false) }
    when {
        game.phase == TerritoryWalkPhase.BROWSING -> {
            val guidance = territoryBrowsingGuidance(game)
            if (onPrepareWalk == null || guidance != "산책을 시작하고 가까이 가면 점령할 수 있어요")
                Text(guidance, color = TextDark, fontSize = 12.sp, lineHeight = 17.sp)
            onPrepareWalk?.let { action ->
                Button(onClick = action, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DaengPink)) {
                    Text("산책 준비하기", fontWeight = FontWeight.Bold)
                }
            }
        }
        game.readOnly || game.target?.occupancyKnown != true -> Text(game.guidance, color = TextDark, fontSize = 12.sp, lineHeight = 17.sp)
        game.phase == TerritoryWalkPhase.PAUSED ->
            Text("산책을 재개하면 영역표시할 수 있어요", color = TextDark, fontSize = 12.sp, lineHeight = 17.sp)
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("함께 점령할 강아지", Modifier.weight(1f), color = TextDark, fontSize = 12.sp, lineHeight = 17.sp)
                Box {
                    TextButton(onClick = { choosingPet = true }, enabled = !game.petLocked && game.eligiblePets.isNotEmpty()) {
                        Text(game.representativeLabel ?: "참여견 없음", fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    DropdownMenu(expanded = choosingPet, onDismissRequest = { choosingPet = false }) {
                        game.eligiblePets.forEach { (id, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                choosingPet = false; onSelectPet(siteId, id)
                            })
                        }
                    }
                }
            }
            TerritoryFeedbackLine(game)
            if (game.canPhotograph) Button(onClick = { onPhotograph(siteId) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DaengPink)) {
                Text(territoryPhotoButtonLabel(game), fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (game.canMark) {
                if (game.canPhotograph) TextButton(onClick = { onMark(siteId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (game.actionLabel == "영역표시") "사진 없이 일반 점령" else game.actionLabel,
                        color = TextDark, fontSize = 12.sp, lineHeight = 17.sp)
                } else Button(onClick = { onMark(siteId) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DaengPink)) {
                    Text(if (game.actionLabel == "영역표시") "일반 점령하기" else game.actionLabel, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TerritoryCardActionsPreview() = DaengsTheme {
    Column(Modifier.padding(16.dp)) {
        TerritoryCardActions(TerritoryGameState(targetId = "preview", phase = TerritoryWalkPhase.WALKING,
            canMark = true, canPhotograph = true, onlinePhotos = true, representativeLabel = "두부"), {}, {}, { _, _ -> }, {})
    }
}
