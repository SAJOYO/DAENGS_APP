package com.daengs.app.ui.walk.reading

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.R
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.*

/** Membership comes from the recorded walk, never the current primary pet. */
internal fun diaryParticipants(dogIds: List<String>, pets: List<Pet>): List<Pair<String, Pet?>> =
    dogIds.distinct().map { id -> id to pets.singleOrNull { it.id == id } }

@Composable
internal fun DiaryReadingHeader(title: String, dogIds: List<String>, pets: List<Pet>) {
    val participants = diaryParticipants(dogIds, pets)
    Row(Modifier.fillMaxWidth().padding(horizontal = DiaryReadingChrome.Gutter).padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (participants.isNotEmpty()) {
            val pet = participants.singleOrNull()?.second
            val portrait = pet?.breedArt?.portraitRes
            Image(painterResource(portrait ?: R.drawable.ic_location_paw),
                contentDescription = if (participants.size > 1) "함께 산책한 강아지 ${participants.size}마리"
                    else if (portrait != null) "${pet.name} 견종 그림" else "산책한 강아지",
                modifier = Modifier.size(36.dp).clip(CircleShape))
            Spacer(Modifier.width(11.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextDark, fontSize = DiaryReadingChrome.Title, lineHeight = 26.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (participants.isNotEmpty()) {
                val names = participants.mapNotNull { it.second?.name }
                val missing = participants.count { it.second == null }
                val label = (names + listOfNotNull(if (missing > 0) "이름 미확인 ${missing}마리" else null)).joinToString(" · ")
                Text(label, Modifier.padding(top = 3.dp), fontSize = 12.sp, lineHeight = 16.sp,
                    color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun DiaryReadingHeaderPreview() { DiaryReviewTheme {
    DiaryReadingHeader("함께 걸었던 긴 저녁 산책의 기록", listOf("a", "b"), emptyList())
} }
