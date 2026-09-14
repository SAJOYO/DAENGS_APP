package com.daengs.app.ui.walk.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*

/** Shared spacing for the approved reading shell; content styles follow in later stages. */
internal object DiaryReadingChrome {
    val Gutter = 20.dp
    val Corner = 24.dp
    val Title = 20.sp
}

@Composable
internal fun DiaryReadingTabs(count: Int, explorer: Boolean, onScenes: () -> Unit,
    onExplorer: () -> Unit, canLower: Boolean, canRaise: Boolean,
    onLower: () -> Unit, onRaise: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = DiaryReadingChrome.Gutter),
            verticalAlignment = Alignment.CenterVertically) {
            DiaryReadingTab("장면 $count", !explorer, onScenes)
            Spacer(Modifier.width(20.dp))
            DiaryReadingTab("동선 탐색", explorer, onExplorer)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onLower, enabled = canLower, modifier = Modifier.size(48.dp)
                .semantics { contentDescription = "서랍 접기" }) { DiaryReadingChevron(false, canLower) }
            IconButton(onClick = onRaise, enabled = canRaise, modifier = Modifier.size(48.dp)
                .semantics { contentDescription = "서랍 펼치기" }) { DiaryReadingChevron(true, canRaise) }
        }
        HorizontalDivider(color = PinkFaint)
    }
}

@Composable
private fun DiaryReadingTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.width(IntrinsicSize.Max).heightIn(min = 48.dp)
        .selectable(selected, role = Role.Tab, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, Modifier.padding(vertical = 14.dp), fontSize = 14.sp,
            color = if (selected) TextDark else TextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(DaengPink))
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun ReadingTabsPreview() { DiaryReviewTheme {
    DiaryReadingTabs(5, false, {}, {}, true, true, {}, {})
} }

@Composable
private fun DiaryReadingChevron(up: Boolean, enabled: Boolean) {
    val color = if (enabled) TextMuted else PinkSoft
    Canvas(Modifier.size(18.dp)) {
        val edge = size.height * if (up) .62f else .38f
        val center = size.height * if (up) .38f else .62f
        drawLine(color, Offset(size.width * .25f, edge), Offset(size.width * .5f, center), 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width * .5f, center), Offset(size.width * .75f, edge), 1.5.dp.toPx(), StrokeCap.Round)
    }
}
