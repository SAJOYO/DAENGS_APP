package com.daengs.app.ui.walk

import com.daengs.app.ui.walk.reading.DiaryReviewTheme

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.daengs.app.R
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.*
import com.daengs.app.walk.diary.DiaryScene
import com.daengs.app.walk.diary.DiarySceneKind

/** Existing action silhouettes keep the same meaning as the map pins. */
@Composable
internal fun DiarySceneBadge(kind: DiarySceneKind, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val background = when (kind) {
        DiarySceneKind.SNIFFING, DiarySceneKind.EXCRETION, DiarySceneKind.BARKING -> CreamBg
        DiarySceneKind.NOTE -> PinkSoft
        else -> PinkFaint
    }
    Surface(modifier.size(size).semantics { contentDescription = kind.label },
        color = background, shape = RoundedCornerShape(10.dp)) {
        Box(contentAlignment = Alignment.Center) {
            val drawable = when (kind) {
                DiarySceneKind.SNIFFING -> R.drawable.ic_walk_sniffing
                DiarySceneKind.EXCRETION -> R.drawable.ic_walk_excretion
                DiarySceneKind.BARKING -> R.drawable.ic_walk_barking
                DiarySceneKind.NOTE -> R.drawable.ic_walk_note
                else -> null
            }
            if (drawable != null) Image(painterResource(drawable), null, Modifier.size(24.dp))
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
    Row(modifier, verticalAlignment = if (detail) Alignment.Top else Alignment.CenterVertically) {
        DiarySceneBadge(kind, size = if (detail) 36.dp else 32.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(scene.title, color = TextDark, fontSize = if (detail) 19.sp else 15.sp,
                lineHeight = if (detail) 27.sp else 22.sp, fontWeight = FontWeight.SemiBold,
                maxLines = if (detail) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatWalkClock(scene.atMillis), fontSize = 12.sp, color = TextMuted)
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
    onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
) {
    var menu by remember(scene.id) { mutableStateOf(false) }
    Row(modifier.heightIn(min = 66.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ordinal?.let { Text(it.toString(), Modifier.widthIn(min = 18.dp).padding(end = 6.dp),
                color = TextMuted, fontSize = 11.sp) }
            DiarySceneHeading(scene, kind, Modifier.weight(1f))
            if (onEdit == null && onDelete == null) Text("›", Modifier.padding(start = 8.dp), fontSize = 20.sp, color = TextMuted)
        }
        if (onEdit != null || onDelete != null) Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "장면 $ordinal 메뉴" }) {
                Text("⋯", fontSize = 20.sp, color = TextMuted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                onEdit?.let { edit -> DropdownMenuItem(text = { Text("내용 수정") },
                    modifier = Modifier.semantics { contentDescription = "장면 $ordinal 수정" },
                    onClick = { menu = false; edit() }) }
                onDelete?.let { remove -> DropdownMenuItem(text = { Text("장면 삭제") },
                    modifier = Modifier.semantics { contentDescription = "장면 $ordinal 삭제" },
                    onClick = { menu = false; remove() }) }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
internal fun DiarySceneKindsPreview() { DiaryReviewTheme {
    Column(Modifier.fillMaxWidth()) {
        DiarySceneKind.entries.forEachIndexed { index, kind ->
            DiarySceneListButton(DiaryScene("s/$index", "s", 0, kind.label, "", null, ""), kind, {},
                Modifier.fillMaxWidth(), index + 1)
        }
    }
} }

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun DiarySceneHeadingPreview() { DiaryReviewTheme {
    DiarySceneHeading(DiaryScene("s/n", "s", 0, "물 한 모금, 잠깐의 쉼", "", null, ""),
        DiarySceneKind.NOTE, Modifier.fillMaxWidth().padding(20.dp), detail = true)
} }
