package com.daengs.app.ui.places

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.features.places.PlaceCategoryIcon
import com.daengs.app.map.features.places.categoryLabel
import com.daengs.app.place.*
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme

private data class PurposePresentation(val label: String, val icon: PlaceKind)
private val purposePresentation = mapOf(
    PlacePurpose.HEALTHCARE to PurposePresentation("진료", PlaceKind.HOSPITAL),
    PlacePurpose.PET_CARE to PurposePresentation("돌봄", PlaceKind.GROOMING),
    PlacePurpose.SHOPPING to PurposePresentation("쇼핑", PlaceKind.PET_SHOP),
    PlacePurpose.DINING to PurposePresentation("식사·카페", PlaceKind.CAFE),
    PlacePurpose.OUTING to PurposePresentation("나들이", PlaceKind.TRAVEL),
    PlacePurpose.CULTURE to PurposePresentation("문화", PlaceKind.MUSEUM),
    PlacePurpose.LODGING to PurposePresentation("숙박", PlaceKind.HOTEL),
)

internal val PlaceCategorySelection.label: String
    get() = when (this) {
        PlaceCategorySelection.All -> "전체"
        PlaceCategorySelection.None -> "카테고리 선택"
        is PlaceCategorySelection.Purpose -> purposePresentation.getValue(purpose).label
        is PlaceCategorySelection.Kind -> categoryLabel(kind)
        is PlaceCategorySelection.Multiple -> "${kinds.size}개 카테고리"
    }

/** 둘러보는 갈래는 검색 조건과 별개다. 팝업은 지도의 측정 높이에 참여하지 않는다. */
@Composable
internal fun PlacePurposeMenu(
    selection: PlaceCategorySelection,
    onLimit: () -> Unit = {},
    onSelect: (PlaceCategorySelection) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var browsing by rememberSaveable {
        mutableStateOf(selection.parentPurpose?.name ?: if (selection == PlaceCategorySelection.Kind(PlaceKind.ETC)) "ETC" else PlacePurpose.DINING.name)
    }
    val purpose = PlacePurpose.entries.firstOrNull { it.name == browsing }
    val label = purpose?.let { purposePresentation.getValue(it).label } ?: "기타"
    val icon = purpose?.let { purposePresentation.getValue(it).icon } ?: PlaceKind.ETC
    val leaves = purpose?.kinds ?: listOf(PlaceKind.ETC)
    fun toggle(kinds: List<PlaceKind>) {
        selection.toggleKinds(kinds)?.let(onSelect) ?: onLimit()
    }
    Column {
        Box {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("place-category-bar")
                .clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded }
                .semantics { contentDescription = if (expanded) "카테고리 접기" else "카테고리 펴기" }
                .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaceCategoryIcon(icon, Modifier.size(20.dp), DaengsColors.BrandPrimary)
                Text(label, Modifier.padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(if (expanded) " ▴" else " ▾", fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("카테고리", color = DaengsColors.TextSecondary, fontSize = 11.sp)
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.widthIn(max = 360.dp)
                .testTag("place-purpose-grid"), containerColor = DaengsColors.Surface) {
                val options = listOf("ALL") + PlacePurpose.entries.map { it.name } + "ETC"
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.chunked(5).forEachIndexed { rowIndex, row ->
                        Row {
                            row.forEachIndexed { columnIndex, id ->
                                val group = PlacePurpose.entries.firstOrNull { it.name == id }
                                val title = group?.let { purposePresentation.getValue(it).label } ?: if (id == "ALL") "전체" else "기타"
                                val groupIcon = group?.let { purposePresentation.getValue(it).icon } ?: PlaceKind.ETC.takeIf { id != "ALL" }
                                var arrived by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { arrived = true }
                                Box(Modifier.weight(1f).heightIn(min = 64.dp)) {
                                    androidx.compose.animation.AnimatedVisibility(arrived, enter = fadeIn(tween(180, (rowIndex * 5 + columnIndex) * 20)) +
                                        slideInVertically(tween(180, (rowIndex * 5 + columnIndex) * 20)) { it / 4 }) {
                                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                            .background(if (browsing == id) DaengsColors.BrandPrimarySoft else DaengsColors.Surface)
                                            .selectable(browsing == id, enabled = id != "ALL", role = Role.Tab,
                                                onClick = { browsing = id; expanded = false })
                                            .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            PlaceCategoryIcon(groupIcon, Modifier.size(22.dp),
                                                if (id == "ALL") DaengsColors.TextSecondary else DaengsColors.TextPrimary)
                                            Text(title, fontSize = 11.sp, maxLines = 1,
                                                color = if (id == "ALL") DaengsColors.TextSecondary else DaengsColors.TextPrimary)
                                        }
                                    }
                                }
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState())
            .testTag("place-subcategory-panel"), verticalAlignment = Alignment.CenterVertically) {
            if (purpose != null) CategoryWord("전체", leaves.all { it in selection.kinds }, "$label 전체") { toggle(leaves) }
            leaves.forEach { kind -> CategoryWord(categoryLabel(kind), kind in selection.kinds) { toggle(listOf(kind)) } }
        }
    }
}

@Composable
private fun CategoryWord(label: String, selected: Boolean, description: String = label, onClick: () -> Unit) {
    val strength by animateFloatAsState(if (selected) 1f else 0f, label = "category-selection")
    Column(Modifier.height(44.dp).clip(RoundedCornerShape(8.dp))
        .selectable(selected, role = Role.Checkbox, onClick = onClick)
        .semantics { contentDescription = description }.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(label, color = if (selected) DaengsColors.BrandPrimary else DaengsColors.TextSecondary,
            fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(16.dp).height(2.dp).background(DaengsColors.BrandPrimary.copy(alpha = strength)))
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun PurposeMenuPreview() {
    DaengsTheme {
        var selected by remember { mutableStateOf<PlaceCategorySelection>(PlaceCategorySelection.Kind(PlaceKind.CAFE)) }
        PlacePurposeMenu(selected) { selected = it }
    }
}
