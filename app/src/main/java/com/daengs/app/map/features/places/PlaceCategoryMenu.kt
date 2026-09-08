package com.daengs.app.map.features.places

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.daengs.app.place.PlaceKind
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

/**
 * 지도 검색의 색. **앱 테마에서 가져온다.**
 *
 * 전에는 이 화면만 따로 잡은 갈색 여섯 개를 썼다 — 강조가 벽돌색(`#BC5D49`)이라
 * 다른 탭의 분홍(`BrandPrimary`)과 나란히 놓으면 다른 앱처럼 보였다. 값만 테마 쪽으로
 * 돌리고 이름은 그대로 둔다. 이 이름을 쓰는 자리가 네 파일에 흩어져 있어서다.
 */
internal object PlaceSearchColors {
    val Background = DaengsColors.AppBackground
    val Field = DaengsColors.SurfaceMuted
    val IconBackground = DaengsColors.SurfaceMuted
    val Ink = DaengsColors.TextPrimary
    /**
     * **이 하나만 테마 밖 값이다.** 제자리인 `DaengsColors.TextSecondary` 는 크림
     * 배경에서 2.77:1 이라 본문에 못 쓴다. 이 갈색은 같은 배경에서 5.10:1 이다.
     */
    val Muted = Color(0xFF79645E)
    val Accent = DaengsColors.BrandPrimary
    // AI 검색은 일부러 다른 갈래로 보이게 두는 자리라 보라 계열을 그대로 둔다.
    val AiBackground = Color(0xFFF7F2FB)
    val AiInk = Color(0xFF695087)
}

/** 7개 바로가기 + 전체 보기. 추가 종류를 고르면 마지막 자리에 현재 선택을 유지한다. */
internal fun visiblePlaceCategories(selected: PlaceKind): List<PlaceCategory> {
    val primary = PLACE_CATEGORIES.take(7)
    return if (primary.any { it.kind == selected }) primary
    else primary.take(6) + PLACE_CATEGORIES.single { it.kind == selected }
}

@Composable
fun PlaceCategoryMenu(
    selected: PlaceKind,
    enabled: Boolean,
    onSelect: (PlaceKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    // **기본은 접혀 있다.** 이 화면은 지도를 보라고 있는 화면인데, 여덟 칸을 늘 펴 두면
    // 격자만으로 150dp 를 쓴다. 위의 머리줄·이름칸까지 더하면 지도가 시작되기 전에
    // 270dp 가 나간다 — 411dp 폭 기기에서 세로의 3분의 1이 넘는다. 접으면 그 150dp 가
    // 지도로 간다. 카테고리는 고를 때 한 번만 필요하다.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PlaceKindBar(selected, expanded) { expanded = !expanded }
        // 고르면 도로 접는다. 고른 뒤에 보고 싶은 것은 격자가 아니라 지도다.
        if (expanded) {
            val visible = visiblePlaceCategories(selected)
            visible.chunked(4).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { category ->
                        CategoryTile(category, selected == category.kind, enabled,
                            { onSelect(category.kind); expanded = false }, Modifier.weight(1f))
                    }
                    if (rowIndex == 1) {
                        CategoryTile(null, false, true, { showAll = true }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (showAll) {
        Dialog(onDismissRequest = { showAll = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = PlaceSearchColors.Background) {
                Column(Modifier.padding(16.dp)) {
                    Text("전체 카테고리", color = PlaceSearchColors.Ink,
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("시설 종류를 하나 선택하세요", color = PlaceSearchColors.Muted,
                        fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    LazyColumn(Modifier.weight(1f, fill = false).testTag("place-category-list"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(PLACE_CATEGORIES.chunked(3)) { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { category ->
                                    CategoryTile(category, selected == category.kind, enabled, {
                                        onSelect(category.kind)
                                        showAll = false
                                        expanded = false
                                    }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showAll = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("닫기", color = PlaceSearchColors.Muted)
                    }
                }
            }
        }
    }
}

/**
 * 접혀 있을 때 지금 무엇으로 보고 있는지 알려 주고, 눌러서 격자를 펴는 자리.
 *
 * **접어 두는 것이 요점이지만, 무엇으로 보고 있는지는 접혀 있어도 보여야 한다** —
 * 안 그러면 "왜 카페만 나오지" 를 알 길이 없다.
 */
@Composable
private fun PlaceKindBar(selected: PlaceKind, expanded: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(DaengsColors.Surface, shape)
            .border(1.dp, DaengsColors.BorderNeutral, shape)
            .testTag("place-kind-bar")
            .clickable(onClick = onToggle)
            .semantics { contentDescription = if (expanded) "카테고리 접기" else "카테고리 펴기" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlaceCategoryIcon(selected, Modifier.size(20.dp), PlaceSearchColors.Accent)
        Text(
            PLACE_CATEGORIES.firstOrNull { it.kind == selected }?.label ?: "전체",
            color = PlaceSearchColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(if (expanded) "▴" else "▾", color = PlaceSearchColors.Ink, fontSize = 13.sp)
    }
}

/**
 * **고른 것을 색으로 말하지 않는다.** 바탕·테두리·굵기로 말한다.
 *
 * 라벨을 강조색으로 칠하는 게 자연스러워 보이지만, 이 앱의 분홍
 * (`BrandPrimary`)은 크림 배경에서 2.24:1 이고 분홍 위 흰 글자는 2.43:1 이다.
 * 진한 갈색을 그대로 두면 연분홍 바탕에서도 8.77:1 이다.
 */
@Composable
private fun CategoryTile(
    category: PlaceCategory?, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape)
            .background(if (selected) DaengsColors.BrandPrimarySoft else DaengsColors.Surface, shape)
            .border(1.dp, if (selected) PlaceSearchColors.Accent else DaengsColors.BorderNeutral, shape)
            .selectable(
                selected = selected, enabled = enabled,
                role = if (category == null) Role.Button else Role.RadioButton, onClick = onClick,
            ).heightIn(min = 62.dp).padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        PlaceCategoryIcon(category?.kind, Modifier.size(22.dp), PlaceSearchColors.Ink)
        Text(category?.label ?: "전체 보기", color = PlaceSearchColors.Ink, fontSize = 11.sp,
            lineHeight = 16.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Preview(widthDp = 360, showBackground = true)
@Composable
private fun PlaceCategoryMenuPreview() {
    DaengsTheme {
        var selected by remember { mutableStateOf(PlaceKind.CAFE) }
        PlaceCategoryMenu(selected, true, { selected = it }, Modifier.padding(16.dp))
    }
}

@Preview(widthDp = 320, showBackground = true)
@Composable
private fun PlaceCategoryExtraSelectionPreview() {
    DaengsTheme { PlaceCategoryMenu(PlaceKind.ARTS_CENTER, false, {}, Modifier.padding(16.dp)) }
}

@Preview(name = "카테고리 막대 · 접힘/펴짐", widthDp = 360, showBackground = true)
@Composable
private fun PlaceKindBarPreview() {
    DaengsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PlaceKindBar(PlaceKind.CAFE, expanded = false) {}
            PlaceKindBar(PlaceKind.ARTS_CENTER, expanded = true) {}
        }
    }
}
