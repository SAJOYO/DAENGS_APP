package com.daengs.app.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.features.places.PlaceCategoryIcon
import com.daengs.app.map.features.places.categoryLabel
import com.daengs.app.place.PlaceCategorySelection
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlacePurpose
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
        is PlaceCategorySelection.Purpose -> purposePresentation.getValue(purpose).label
        is PlaceCategorySelection.Kind -> categoryLabel(kind)
    }

@Composable
internal fun PlacePurposeMenu(selection: PlaceCategorySelection, onSelect: (PlaceCategorySelection) -> Unit) {
    val options = remember {
        listOf(PlaceCategorySelection.All) + PlacePurpose.entries.map(PlaceCategorySelection::Purpose) +
            PlaceCategorySelection.Kind(PlaceKind.ETC)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().selectableGroup().testTag("place-purpose-grid"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { option ->
                        val selected = option == selection ||
                            (option is PlaceCategorySelection.Purpose && option.purpose == selection.parentPurpose)
                        val icon = when (option) {
                            PlaceCategorySelection.All -> null
                            is PlaceCategorySelection.Purpose -> purposePresentation.getValue(option.purpose).icon
                            is PlaceCategorySelection.Kind -> option.kind
                        }
                        Column(
                            Modifier.weight(1f).heightIn(min = 64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) DaengsColors.BrandPrimarySoft else DaengsColors.Surface, RoundedCornerShape(12.dp))
                                .border(1.dp, if (selected) DaengsColors.BrandPrimary else DaengsColors.BorderNeutral, RoundedCornerShape(12.dp))
                                .selectable(selected, role = Role.Tab, onClick = { onSelect(option) })
                                .padding(horizontal = 2.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        ) {
                            PlaceCategoryIcon(icon, Modifier.size(23.dp), DaengsColors.TextPrimary)
                            Text(option.label, color = DaengsColors.TextPrimary, fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        selection.parentPurpose?.let { purpose ->
            // 대분류가 바뀌면 스크롤도 초기화하여 '분류 전체'가 항상 첫 선택으로 보인다.
            key(purpose) {
                LazyRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val group = PlaceCategorySelection.Purpose(purpose)
                    items(listOf(group) + purpose.kinds.map(PlaceCategorySelection::Kind), key = { it.label }) { option ->
                        FilterChip(
                            selected = selection == option,
                            onClick = { onSelect(option) },
                            label = { Text(if (option == group) "${group.label} 전체" else option.label, fontSize = 12.sp) },
                            border = BorderStroke(1.dp, if (selection == option) DaengsColors.BrandPrimarySoft else DaengsColors.BorderNeutral),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DaengsColors.BrandPrimarySoft,
                                selectedLabelColor = DaengsColors.TextPrimary,
                                labelColor = DaengsColors.TextPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun PurposeMenuNarrowPreview() {
    DaengsTheme {
        var selected by remember { mutableStateOf<PlaceCategorySelection>(PlaceCategorySelection.Purpose(PlacePurpose.DINING)) }
        PlacePurposeMenu(selected) { selected = it }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PurposeMenuAllPreview() {
    DaengsTheme { PlacePurposeMenu(PlaceCategorySelection.All) {} }
}
