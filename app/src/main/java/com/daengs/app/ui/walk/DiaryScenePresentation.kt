package com.daengs.app.ui.walk

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.R
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.DiarySceneKind

/** Existing action silhouettes keep the same meaning as the map pins. */
@Composable
internal fun DiarySceneBadge(kind: DiarySceneKind, modifier: Modifier = Modifier) {
    val background = when (kind) {
        DiarySceneKind.SNIFFING, DiarySceneKind.EXCRETION, DiarySceneKind.BARKING -> CreamBg
        DiarySceneKind.NOTE -> PinkSoft
        else -> PinkFaint
    }
    Surface(modifier.size(36.dp).semantics { contentDescription = kind.label },
        color = background, shape = RoundedCornerShape(10.dp)) {
        Box(contentAlignment = Alignment.Center) {
            val drawable = when (kind) {
                DiarySceneKind.SNIFFING -> R.drawable.ic_walk_sniffing
                DiarySceneKind.EXCRETION -> R.drawable.ic_walk_excretion
                DiarySceneKind.BARKING -> R.drawable.ic_walk_barking
                DiarySceneKind.NOTE -> R.drawable.ic_walk_note
                else -> null
            }
            if (drawable != null) Image(painterResource(drawable), null, Modifier.size(28.dp))
            else DaengsIconView(when (kind) {
                DiarySceneKind.PHOTO -> DaengsIcon.Camera
                DiarySceneKind.DWELL -> DaengsIcon.Clock
                DiarySceneKind.FAST, DiarySceneKind.SLOW -> DaengsIcon.Chart
                else -> DaengsIcon.Book
            }, Modifier.size(22.dp), tint = TextDark)
        }
    }
}

@Composable
internal fun DiarySceneHeading(scene: DiaryScene, kind: DiarySceneKind, modifier: Modifier = Modifier,
    detail: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        DiarySceneBadge(kind)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(scene.title, color = TextDark, fontSize = if (detail) 22.sp else 18.sp,
                lineHeight = if (detail) 30.sp else 24.sp, fontWeight = FontWeight.SemiBold,
                maxLines = if (detail) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatWalkClock(scene.atMillis), fontSize = 13.sp, color = TextMuted)
                Text(" · ", fontSize = 12.sp, color = TextMuted)
                Text(kind.label, Modifier.weight(1f), fontSize = 12.sp, color = TextMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            scene.content?.address?.takeIf { detail && it.isNotBlank() }?.let {
                Text(it, fontSize = 13.sp, color = TextMuted)
            }
        }
    }
}

@Composable
internal fun DiarySceneListButton(scene: DiaryScene, kind: DiarySceneKind, onClick: () -> Unit,
    modifier: Modifier = Modifier, ordinal: Int? = null,
) {
    TextButton(onClick = onClick, modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
        DiarySceneHeading(scene, kind, Modifier.weight(1f))
        ordinal?.let { Text(it.toString(), Modifier.padding(start = 8.dp), color = TextMuted, fontSize = 12.sp) }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
internal fun DiarySceneKindsPreview() { DaengsTheme {
    Column(Modifier.fillMaxWidth()) {
        DiarySceneKind.entries.forEachIndexed { index, kind ->
            DiarySceneListButton(DiaryScene("s/$index", "s", 0, kind.label, "", null, ""), kind, {},
                Modifier.fillMaxWidth(), index + 1)
        }
    }
} }

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun DiarySceneHeadingPreview() { DaengsTheme {
    DiarySceneHeading(DiaryScene("s/n", "s", 0, "물 한 모금, 잠깐의 쉼", "", null, ""),
        DiarySceneKind.NOTE, Modifier.fillMaxWidth().padding(20.dp), detail = true)
} }
