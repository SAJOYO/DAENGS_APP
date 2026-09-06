package com.daengs.app.map.features.places

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.daengs.app.place.PlaceKind
import com.daengs.app.ui.theme.DaengsTheme

/** 지도 검색에만 쓰는 색. 앱 전체/미니룸 팔레트는 변경하지 않는다. */
internal object PlaceSearchColors {
    val Background = Color(0xFFFFFAF7)
    val Field = Color(0xFFF3EFEC)
    val IconBackground = Color(0xFFF2EBE6)
    val Ink = Color(0xFF493D39)
    val Muted = Color(0xFF79645E)
    val Accent = Color(0xFFBC5D49)
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val visible = visiblePlaceCategories(selected)
        visible.chunked(4).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { category ->
                    CategoryTile(category, selected == category.kind, enabled,
                        { onSelect(category.kind) }, Modifier.weight(1f))
                }
                if (rowIndex == 1) {
                    CategoryTile(null, false, true, { showAll = true }, Modifier.weight(1f))
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

@Composable
private fun CategoryTile(
    category: PlaceCategory?, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).selectable(
            selected = selected, enabled = enabled,
            role = if (category == null) Role.Button else Role.RadioButton, onClick = onClick,
        ).padding(vertical = 3.dp).heightIn(min = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(42.dp).background(
            if (selected) PlaceSearchColors.Accent else PlaceSearchColors.IconBackground,
            RoundedCornerShape(15.dp),
        ), contentAlignment = Alignment.Center) {
            PlaceCategoryIcon(category?.kind, Modifier.size(23.dp),
                if (selected) PlaceSearchColors.Background else PlaceSearchColors.Muted)
        }
        Text(category?.label ?: "전체 보기", color = if (selected) PlaceSearchColors.Accent
            else PlaceSearchColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
