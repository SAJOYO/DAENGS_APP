package com.daengs.app.ui.places

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
        is PlaceCategorySelection.Custom -> "업종 ${kinds.size}개"
    }

@Composable
internal fun PlacePurposeMenu(selection: PlaceCategorySelection, onSelect: (PlaceCategorySelection) -> Unit) {
    val options = remember {
        listOf(PlaceCategorySelection.All) + PlacePurpose.entries.map(PlaceCategorySelection::Purpose) +
            PlaceCategorySelection.Kind(PlaceKind.ETC)
    }
    // **기본은 접혀 있다.** 이 화면이 고쳐진 이유가 "지도를 볼 수 없는 지도 화면"
    // 이라, 아홉 칸을 늘 펴 두면 격자만으로 화면의 15% 를 쓴다. 접어 두면 그만큼이
    // 지도로 간다 — 카테고리는 고를 때 한 번만 필요하다.
    //
    // 고르면 도로 접는다. 고른 뒤에 보고 싶은 것은 격자가 아니라 지도다.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceCategoryBar(selection, expanded) { expanded = !expanded }
        if (expanded) Column(Modifier.fillMaxWidth().selectableGroup().testTag("place-purpose-grid"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // **다섯씩 끊는다.** 아홉 칸이라 3×3 이 더 반듯하지만 펼쳤을 때가 크다
            // (실기기에서 364px → 544px). 지도 자리는 접는 것으로 이미 벌었으므로,
            // 펼친 판은 작은 쪽이 낫다. 오른쪽 아래 한 칸은 빈다.
            options.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        val selected = option == selection ||
                            (option is PlaceCategorySelection.Purpose && option.purpose == selection.parentPurpose)
                        val icon = when (option) {
                            PlaceCategorySelection.All -> null
                            is PlaceCategorySelection.Custom -> null
                            is PlaceCategorySelection.Purpose -> purposePresentation.getValue(option.purpose).icon
                            is PlaceCategorySelection.Kind -> option.kind
                        }
                        Column(
                            Modifier.weight(1f).heightIn(min = 64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) DaengsColors.BrandPrimarySoft else DaengsColors.Surface, RoundedCornerShape(12.dp))
                                .border(1.dp, if (selected) DaengsColors.BrandPrimary else PlaceSearchStyle.Border, RoundedCornerShape(12.dp))
                                .selectable(selected, role = Role.Tab, onClick = { onSelect(option); expanded = false })
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

/**
 * 접혀 있을 때 지금 무엇으로 보고 있는지 알려 주고, 눌러서 격자를 펴는 자리.
 *
 * **접어 두는 것이 요점이다.** 아홉 칸을 늘 펴 두면 지도가 그만큼 줄어드는데, 이
 * 화면은 지도를 보라고 있는 화면이다. 대신 **무엇으로 보고 있는지는 접혀 있어도
 * 보여야 한다** — 안 그러면 "왜 카페만 나오지" 를 알 길이 없다.
 */
@Composable
private fun PlaceCategoryBar(
    selection: PlaceCategorySelection,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val purpose = selection.parentPurpose
    val icon = purpose?.let { purposePresentation.getValue(it).icon }
        ?: (selection as? PlaceCategorySelection.Kind)?.kind
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(DaengsColors.Surface, shape)
            .border(1.dp, PlaceSearchStyle.Border, shape)
            .testTag("place-category-bar")
            .clickable(onClick = onToggle)
            .semantics { contentDescription = if (expanded) "카테고리 접기" else "카테고리 펴기" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlaceCategoryIcon(icon, Modifier.size(20.dp), DaengsColors.BrandPrimary)
        // **하위가 아니라 상위 갈래를 적는다.** 하위 이름을 적으면 바로 아래 칩 줄과
        // 같은 말이 두 번 나오고("음식점" 막대 + "음식점" 칩), 글자로 찾는 쪽에서는
        // 둘이 구분이 안 된다.
        Text(
            purpose?.let { PlaceCategorySelection.Purpose(it).label } ?: selection.label,
            color = DaengsColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        // 화살표는 옆 필터 칩(`반경 3km ▾`)과 같은 글자를 쓴다. 아이콘을 따로 두면
        // 같은 뜻을 두 가지 모양으로 말하게 된다.
        Text(if (expanded) "▴" else "▾", color = DaengsColors.TextPrimary, fontSize = 13.sp)
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
        // 바탕이 위 격자 카드와 같아야 한 덩어리로 읽힌다. 앱 배경색(크림)을 쓰면
        // 이 줄만 재질이 달라 보여서 딴 데서 온 것처럼 보인다.
        Modifier.fillMaxWidth().clip(shape)
            .background(DaengsColors.Surface)
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
                // **위 격자와 같은 방식으로 고른 것을 표시한다.** 여기만 진한 색으로
                // 채우면 하위가 상위보다 세게 보여서, 눈이 "카페" 로 먼저 가고
                // "식사·카페" 가 나중에 읽힌다 — 위계가 뒤집힌다.
                Box(
                    Modifier.heightIn(min = 48.dp).clip(tabShape)
                        .background(
                            if (selected) DaengsColors.BrandPrimarySoft
                            else androidx.compose.ui.graphics.Color.Transparent,
                            tabShape,
                        )
                        .border(
                            1.dp,
                            if (selected) DaengsColors.BrandPrimary
                            else androidx.compose.ui.graphics.Color.Transparent,
                            tabShape,
                        )
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
