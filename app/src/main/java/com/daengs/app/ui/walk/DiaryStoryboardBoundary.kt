package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*
import com.daengs.app.ui.walk.reading.DiaryReadingChrome
import com.daengs.app.ui.walk.reading.DiaryReviewTheme

/** Storyboard bookends use session time, without fabricating a numbered/editable scene. */
@Composable
internal fun DiaryStoryboardBoundary(start: Boolean, atMillis: Long) {
    val key = if (start) "start" else "end"
    Row(Modifier.fillMaxWidth().testTag("storyboard-$key")
        .padding(horizontal=DiaryReadingChrome.Gutter).heightIn(min=66.dp).padding(vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=RoundedCornerShape(10.dp), color=PinkFaint) {
            Box(Modifier.size(32.dp), contentAlignment=Alignment.Center) {
                Text(if(start) "○" else "●", color=TextMuted, fontSize=14.sp)
            }
        }
        Column {
            Text(if(start) "산책 시작" else "산책 끝", color=TextDark, fontSize=14.sp,
                fontWeight=FontWeight.SemiBold, lineHeight=20.sp)
            Text(formatRouteExplorerClock(atMillis), Modifier.testTag("storyboard-$key-time"),
                color=TextMuted, fontSize=12.sp, lineHeight=18.sp)
        }
    }
}

@Preview(showBackground=true, widthDp=320, fontScale=1.3f)
@Composable
private fun DiaryStoryboardBoundaryPreview() { DiaryReviewTheme {
    Column { DiaryStoryboardBoundary(true,0); DiaryStoryboardBoundary(false,720_000) }
} }
