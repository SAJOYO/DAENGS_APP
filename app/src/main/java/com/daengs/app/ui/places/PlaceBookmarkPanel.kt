package com.daengs.app.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.place.PlaceKey
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

enum class PlaceBookmarkPhase { LOADING, READY, FAILED }

/** Values supplied by the caller. Null means the bookmark total has not been obtained. */
data class PlaceBookmarkPanelState(
    val tab: PlaceBrowseTab = PlaceBrowseTab.SEARCH,
    val savedKeys: Set<PlaceKey> = emptySet(),
    val totalSaved: Int? = null,
    val phase: PlaceBookmarkPhase = PlaceBookmarkPhase.LOADING,
    val hasFilters: Boolean = true,
    val allRegions: Boolean = false,
)

/** Tabs share the handle row, preserving the map rather than adding a second navigation strip. */
@Composable
internal fun PlaceBookmarkHandle(tab: PlaceBrowseTab, expanded: Boolean,
    onTab: (PlaceBrowseTab) -> Unit, onExpand: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("place-results-handle"), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(36.dp).height(4.dp).background(PlaceSearchStyle.Border, RoundedCornerShape(4.dp)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            PlaceBrowseTab.entries.forEach { item ->
                val selected = tab == item
                Column(Modifier.weight(1f).height(48.dp)
                    .testTag("place-tab-${item.name}")
                    .selectable(selected, role = Role.Tab, onClick = { onTab(item) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (item == PlaceBrowseTab.SEARCH) "검색 결과" else "찜한 시설", fontSize = 13.sp,
                        color = if (selected) DaengsColors.BrandPrimary else DaengsColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(32.dp).height(2.dp).background(
                        if (selected) DaengsColors.BrandPrimary else DaengsColors.Surface, RoundedCornerShape(2.dp)))
                }
            }
            TextButton(onClick = onExpand, modifier = Modifier.height(48.dp).semantics {
                contentDescription = if (expanded) "지도 보기" else "목록 보기"
            }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (expanded) "지도 ↓" else "목록 ↑", fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun PlaceBookmarkSummary(state: PlaceBookmarkPanelState, visibleCount: Int, onShowAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("조건에 맞는 ${visibleCount}곳 · 저장 ${state.totalSaved ?: "—"}곳",
            Modifier.weight(1f), fontSize = 12.sp, color = DaengsColors.TextSecondary)
        if (state.hasFilters && (state.totalSaved ?: 0) > 0)
            TextButton(onClick = onShowAll) { Text("전체 찜 보기", fontSize = 12.sp) }
    }
}

@Composable
internal fun PlaceBookmarkEmpty(state: PlaceBookmarkPanelState, onSearch: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(when {
            state.phase == PlaceBookmarkPhase.LOADING -> "찜한 시설을 불러오는 중…"
            state.phase == PlaceBookmarkPhase.FAILED -> "찜한 시설을 불러오지 못했어요."
            state.totalSaved == 0 -> "아직 찜한 시설이 없어요."
            else -> "이 조건에 맞는 찜이 없어요."
        }, fontSize = 13.sp)
        when {
            state.phase == PlaceBookmarkPhase.FAILED -> TextButton(onClick = onRetry) { Text("다시 불러오기") }
            state.phase == PlaceBookmarkPhase.READY && state.totalSaved == 0 -> {
                Text("시설 카드의 하트를 눌러 저장해 보세요.", fontSize = 12.sp, color = DaengsColors.TextSecondary)
                TextButton(onClick = onSearch) { Text("검색 결과 보기") }
            }
            state.phase == PlaceBookmarkPhase.READY -> Text("카테고리나 지역 범위를 바꿔 보세요.",
                fontSize = 12.sp, color = DaengsColors.TextSecondary)
        }
    }
}

@Composable
internal fun PlaceBookmarkButton(name: String, saved: Boolean, onToggle: () -> Unit) {
    IconToggleButton(checked = saved, onCheckedChange = { onToggle() }, modifier = Modifier.size(48.dp)
        .semantics { contentDescription = "$name ${if (saved) "찜 해제" else "찜하기"}" }) {
        Text(if (saved) "♥" else "♡", fontSize = 25.sp,
            color = if (saved) DaengsColors.BrandPrimary else DaengsColors.TextSecondary)
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun BookmarkPanelPreview() { DaengsTheme { Column {
    PlaceBookmarkHandle(PlaceBrowseTab.BOOKMARKS, false, {}, {})
    PlaceBookmarkSummary(PlaceBookmarkPanelState(totalSaved = 3), 1, {})
    PlaceBookmarkEmpty(PlaceBookmarkPanelState(totalSaved = 3, phase = PlaceBookmarkPhase.READY), {}, {})
    PlaceBookmarkButton("검토 카페", true, {})
} } }
