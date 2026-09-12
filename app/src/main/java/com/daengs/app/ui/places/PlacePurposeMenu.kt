package com.daengs.app.ui.places

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
            // 🔒 **잠긴 디자인 — `docs/design-locks.md` 2절.** 막대 **바로 아래(위쪽)** 에 화면 폭으로
            //    뜨는 판에 **세 칸씩** 연다. 사용자와 두 번 맞춰 온 자리다 (2026-09-12):
            //    ① 막대 밑 드롭다운(최대 360dp)에 다섯 칸 · 11sp → "옹졸하다"
            //    ② 화면 아래 시트에 큰 칸(88dp) → "너무 크고, 아래보다 위가 낫다"
            //    지금은 그 사이다 — 위에서, 화면 폭으로, 칸은 64dp · 아이콘 24dp · 글자 13sp.
            //    ⚠️ 다른 창(Popup)으로 연다 — 펼쳐도 지도와 버튼 자리를 밀지 않는다
            //       (`ConnectedPlaceSearchUiTest`). 잠금 테스트: `PlacePurposeSheetLockTest`.
            if (expanded) {
                val density = LocalDensity.current
                val margin = with(density) { PURPOSE_PANEL_MARGIN.roundToPx() }
                val windowWidth = LocalWindowInfo.current.containerSize.width
                Popup(
                    popupPositionProvider = remember(margin) { PurposePanelPosition(margin) },
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Surface(
                        Modifier.width(with(density) { (windowWidth - 2 * margin).coerceAtLeast(0).toDp() })
                            .testTag("place-purpose-sheet"),
                        shape = RoundedCornerShape(18.dp), color = DaengsColors.Surface, shadowElevation = 8.dp,
                    ) {
                        val options = listOf("ALL") + PlacePurpose.entries.map { it.name } + "ETC"
                        Column(Modifier.padding(12.dp).testTag("place-purpose-grid"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.chunked(3).forEachIndexed { rowIndex, row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEachIndexed { columnIndex, id ->
                                        PurposeTile(id, browsing == id, rowIndex * 3 + columnIndex, Modifier.weight(1f)) {
                                            browsing = id
                                            expanded = false
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
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

/**
 * 판의 한 칸. 🔒 **잠긴 디자인 — `docs/design-locks.md` 2절.** 칸 최소 64dp · 아이콘 24dp ·
 * 글자 13sp. 더 줄이면 "옹졸" 로, 더 키우면 "너무 크다" 로 돌아간다 — 둘 다 겪었다.
 *
 * 고른 칸은 **바탕 · 테두리 · 굵기**로 말한다. 분홍 글자는 크림 위에서 2.24:1 이라
 * 색만으로 가르면 안 보인다 (`PlaceCategoryMenu` 의 같은 판단).
 */
@Composable
private fun PurposeTile(id: String, chosen: Boolean, order: Int, modifier: Modifier, onClick: () -> Unit) {
    val group = PlacePurpose.entries.firstOrNull { it.name == id }
    val title = group?.let { purposePresentation.getValue(it).label } ?: if (id == "ALL") "전체" else "기타"
    val groupIcon = group?.let { purposePresentation.getValue(it).icon } ?: PlaceKind.ETC.takeIf { id != "ALL" }
    val muted = id == "ALL"
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.heightIn(min = 64.dp)) {
        AnimatedVisibility(arrived, enter = fadeIn(tween(180, order * 20)) +
            slideInVertically(tween(180, order * 20)) { it / 4 }) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("place-purpose-tile").clip(shape)
                    .background(if (chosen) DaengsColors.BrandPrimarySoft else DaengsColors.Surface)
                    .border(if (chosen) 2.dp else 1.dp,
                        if (chosen) DaengsColors.BrandPrimary else DaengsColors.BorderNeutral, shape)
                    .selectable(chosen, enabled = !muted, role = Role.Tab, onClick = onClick)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                PlaceCategoryIcon(groupIcon, Modifier.size(24.dp),
                    if (muted) DaengsColors.TextSecondary else DaengsColors.TextPrimary)
                Text(title, fontSize = 13.sp, maxLines = 1,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    color = if (muted) DaengsColors.TextSecondary else DaengsColors.TextPrimary)
            }
        }
    }
}

private val PURPOSE_PANEL_MARGIN = 12.dp

/** 카테고리 판의 자리. 🔒 `docs/design-locks.md` 2절 — **막대 바로 아래**, 양옆을 조금 들여서. */
internal class PurposePanelPosition(private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
    ): IntOffset = purposePanelOffset(anchorBounds, windowSize, popupContentSize, margin)
}

/**
 * 판의 왼쪽 위. **막대 바로 아래에 붙인다** — 화면 아래에서 올라오는 시트로 했다가 사용자가
 * "아래보다 위가 낫다" 고 했다. 누른 자리 바로 밑에 떠야 무엇을 펼쳤는지 이어진다.
 * 화면 아래로 넘치면 넘치는 만큼만 올린다.
 */
internal fun purposePanelOffset(anchor: IntRect, window: IntSize, content: IntSize, margin: Int): IntOffset =
    IntOffset(margin, anchor.bottom.coerceAtMost((window.height - content.height).coerceAtLeast(0)))

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
