package com.daengs.app.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.features.places.categoryLabel
import com.daengs.app.place.*
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

/** 검색에 들어간 조건은, 지금 둘러보는 대분류가 바뀌어도 같은 자리에 남는다. */
@Composable
internal fun PlaceSearchQueue(
    selection: PlaceCategorySelection,
    filterSummary: String = "",
    nameQuery: String = "",
    onOpenFilters: () -> Unit = {},
    onSelect: (PlaceCategorySelection) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val kinds = selection.kinds.takeUnless { selection == PlaceCategorySelection.All }.orEmpty()
    val extra = (if (filterSummary.isNotBlank()) 1 else 0) + (if (nameQuery.isNotBlank()) 1 else 0)
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(44.dp).testTag("place-search-queue"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("검색 중", fontSize = 11.sp, color = DaengsColors.TextSecondary)
            if (selection == PlaceCategorySelection.All) QueueWord("전체") { onSelect(PlaceCategorySelection.None) }
            else if (kinds.isEmpty()) Text("검색할 카테고리를 골라 주세요", fontSize = 11.sp, color = DaengsColors.TextSecondary,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            else kinds.take(2).forEach { kind ->
                QueueWord(categoryLabel(kind)) {
                    onSelect(PlaceCategorySelection.fromKinds(kinds.filterNot { it == kind }))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(if (kinds.size + extra > 2) "+${kinds.size + extra - 2} ▾" else "조건 ▾", fontSize = 11.sp)
            }
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.widthIn(max = 330.dp),
            containerColor = DaengsColors.Surface) {
            Column(Modifier.padding(horizontal = 12.dp).widthIn(min = 240.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("현재 검색 조건", style = MaterialTheme.typography.titleSmall)
                Text("카테고리 이름을 누르면 검색에서 빠져요.", fontSize = 11.sp, color = DaengsColors.TextSecondary)
                if (selection == PlaceCategorySelection.All) QueueWord("전체") { onSelect(PlaceCategorySelection.None) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEach { kind -> QueueWord(categoryLabel(kind)) {
                        onSelect(PlaceCategorySelection.fromKinds(kinds.filterNot { it == kind }))
                    } }
                }
                if (nameQuery.isNotBlank()) Text("장소명 · $nameQuery", fontSize = 12.sp)
                if (filterSummary.isNotBlank()) TextButton(onClick = { expanded = false; onOpenFilters() }) {
                    Text(filterSummary, fontSize = 12.sp)
                }
                if (kinds.isEmpty()) Text("선택한 카테고리가 없어요.", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun QueueWord(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
        .padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(label, Modifier.background(DaengsColors.BrandPrimarySoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 11.sp, color = DaengsColors.TextPrimary)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SearchQueuePreview() {
    DaengsTheme { PlaceSearchQueue(PlaceCategorySelection.Multiple(listOf(PlaceKind.CAFE, PlaceKind.HOTEL, PlaceKind.TRAVEL)),
        filterSummary = "주차 가능") {} }
}
