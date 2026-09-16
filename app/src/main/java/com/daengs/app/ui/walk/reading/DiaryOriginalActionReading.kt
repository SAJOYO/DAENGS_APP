package com.daengs.app.ui.walk.reading

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.R
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.walk.formatWalkClock
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType

/** Original action presentation shared with the diary's route reading. */
@Composable
internal fun DiaryOriginalActionReading(entry: WalkEntry, modifier: Modifier = Modifier) {
    Row(modifier) {
        Icon(painterResource(when(entry.type) {
            WalkMomentType.SNIFFING -> R.drawable.ic_walk_sniffing
            WalkMomentType.EXCRETION -> R.drawable.ic_walk_excretion
            WalkMomentType.BARKING -> R.drawable.ic_walk_barking
            WalkMomentType.NOTE -> R.drawable.ic_walk_note
        }), null, Modifier.size(32.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(entry.type.label, color = TextDark)
            Text(formatWalkClock(entry.recordedAtMillis), color = TextMuted)
            entry.pin?.label?.let { Text(it, color = TextMuted) }
            if ((if (entry.pin != null) entry.pin.point else entry.point) == null)
                Text("위치 없이 남긴 행동", color = TextMuted)
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun DiaryOriginalActionPreview() { DiaryReviewTheme {
    DiaryOriginalActionReading(WalkEntry("a", "s", WalkMomentType.SNIFFING, 0))
} }
