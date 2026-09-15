package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.R
import com.daengs.app.map.layers.moments.*
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.boundaryKind

/** Stable source identities only; an editable title or the first/last ordinal is not an endpoint. */
internal fun DiaryScene.isWalkBoundary(): Boolean = boundaryKind() != null

internal fun diaryActionKey(entry: WalkEntry) = "diary-action:${entry.id}"
internal fun diaryActionObjects(entries: List<WalkEntry>, sessionId: String, selected: Set<String>): List<MomentMarkerState> =
    entries.filter { it.sessionId == sessionId && it.type != WalkMomentType.NOTE }.mapNotNull { entry ->
        // A v2 unlocated pin must never fall back to the old content coordinate.
        val point = (if (entry.pin != null) entry.pin.point else entry.point)?.takeIf { it.isDiaryLocation() } ?: return@mapNotNull null
        MomentMarkerState(diaryActionKey(entry), point, entry.type.label, selected = diaryActionKey(entry) in selected,
            behaviors = setOf(entry.type), recordPin = RecordPinAppearance(1))
    }

/** Explicit list access also covers records that cannot fit on the map or have no coordinate. */
@Composable
internal fun DiaryActionObjectsReading(entries: List<WalkEntry>, selected: Set<String>, unplacedCount: Int,
    onSelect: (String) -> Unit, onClear: () -> Unit) {
    if (entries.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val chosen=entries.filter { diaryActionKey(it) in selected }
    TextButton(onClick = { expanded = !expanded; if (selected.isNotEmpty()) onClear() }, contentPadding = PaddingValues(0.dp)) {
        Text(if (unplacedCount>0) "행동 기록 ${entries.size} · 지도에 못 놓은 ${unplacedCount}건" else "행동 기록 ${entries.size}", color=TextMuted)
    }
    val shown=if (chosen.isNotEmpty()) chosen else if (expanded) entries else emptyList()
    shown.forEach { entry ->
        TextButton(onClick={onSelect(diaryActionKey(entry))}, modifier=Modifier.fillMaxWidth(), contentPadding=PaddingValues(0.dp)) {
            com.daengs.app.ui.walk.reading.DiaryOriginalActionReading(entry, Modifier.fillMaxWidth())
        }
    }
}

@Preview(showBackground=true, widthDp=320)
@Composable
private fun DiaryActionReadingPreview() { DaengsTheme {
    val entry=WalkEntry("a","preview",WalkMomentType.SNIFFING,0)
    DiaryActionObjectsReading(listOf(entry),setOf(diaryActionKey(entry)),0,{},{})
} }
