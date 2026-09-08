package com.daengs.app.ui.places

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
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
        Column(Modifier.fillMaxWidth().selectableGroup().testTag("place-purpose-grid"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                .border(1.dp, if (selected) DaengsColors.BrandPrimary else PlaceSearchStyle.Border, RoundedCornerShape(12.dp))
                                .selectable(selected, role = Role.Tab, onClick = { onSelect(option) })
                                .padding(horizontal = 2.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        ) {
                            PlaceCategoryIcon(icon, Modifier.size(22.dp), DaengsColors.TextPrimary)
                            Text(option.label, color = DaengsColors.TextPrimary, fontSize = 11.sp, lineHeight = 16.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        AnimatedContent(
            targetState = selection.parentPurpose,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
            label = "place-subcategories",
        ) { purpose ->
            if (purpose != null) {
                PlaceSubcategoryMenu(purpose, selection, onSelect)
            }
        }
    }
}

@Composable
private fun PlaceSubcategoryMenu(
    purpose: PlacePurpose,
    selection: PlaceCategorySelection,
    onSelect: (PlaceCategorySelection) -> Unit,
) {
    val group = PlaceCategorySelection.Purpose(purpose)
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(DaengsColors.AppBackground)
            .border(1.dp, DaengsColors.BrandPrimarySoft, shape)
            .testTag("place-subcategory-panel")
            .semantics { isTraversalGroup = true; contentDescription = "${group.label} 하위 카테고리" }
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceCategoryIcon(purposePresentation.getValue(purpose).icon, Modifier.size(20.dp), DaengsColors.BrandPrimary)
        Spacer(Modifier.padding(horizontal = 10.dp).width(1.dp).height(24.dp).background(DaengsColors.BrandPrimarySoft))
        FlowRow(
            Modifier.weight(1f).selectableGroup().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (listOf(group) + purpose.kinds.map(PlaceCategorySelection::Kind)).forEach { option ->
                val selected = selection == option
                val tabShape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.heightIn(min = 48.dp).clip(tabShape)
                        .background(if (selected) DaengsColors.BrandPrimary else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(selected, role = Role.Tab, onClick = { onSelect(option) })
                        .semantics { if (option == group) contentDescription = "${group.label} 전체" }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (option == group) "전체" else option.label, fontSize = 12.sp,
                        color = DaengsColors.TextPrimary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SubcategoryMenuPreview() {
    DaengsTheme {
        PlaceSubcategoryMenu(PlacePurpose.CULTURE, PlaceCategorySelection.Kind(PlaceKind.MUSEUM)) {}
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
