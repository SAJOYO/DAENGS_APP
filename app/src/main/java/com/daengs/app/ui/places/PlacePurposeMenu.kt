package com.daengs.app.ui.places

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
            // 🔒 **잠긴 디자인 — `docs/design-locks.md` 2절.** 화면 폭을 다 쓰는 **아래 시트**에
            //    **세 칸씩 크게** 연다. 전에는 막대 밑 작은 드롭다운(최대 360dp)에 다섯 칸을 넣어
            //    11sp 글자 · 22dp 아이콘이었고, 사용자가 실기기에서 "옹졸하다" 고 했다.
            //    ⚠️ **다른 창(Dialog)으로 연다.** 펼쳐도 지도와 버튼의 자리를 밀지 않는다 —
            //       `ConnectedPlaceSearchUiTest` 가 그걸 잡는다.
            //    잠금 테스트: `PlacePurposeSheetLockTest`.
            if (expanded) Dialog(
                onDismissRequest = { expanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                // 바깥(어두운 자리)을 누르면 닫힌다. ⚠️ `clickable` 로 하지 않는다 — 자식
                // semantics 를 한 덩어리로 합쳐 시트 전체가 버튼 하나로 읽힌다.
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { expanded = false } },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        Modifier.fillMaxWidth().testTag("place-purpose-sheet")
                            .pointerInput(Unit) { detectTapGestures { } },
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = DaengsColors.AppBackground,
                    ) {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp)) {
                            Box(
                                Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)).background(DaengsColors.BorderNeutral),
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("어떤 곳을 찾을까요?", Modifier.weight(1f), color = DaengsColors.TextPrimary,
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("닫기", Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = false }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                    color = DaengsColors.TextSecondary, fontSize = 14.sp)
                            }
                            val options = listOf("ALL") + PlacePurpose.entries.map { it.name } + "ETC"
                            Column(Modifier.testTag("place-purpose-grid"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                options.chunked(3).forEachIndexed { rowIndex, row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        }
        Row(Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState())
            .testTag("place-subcategory-panel"), verticalAlignment = Alignment.CenterVertically) {
            if (purpose != null) CategoryWord("전체", leaves.all { it in selection.kinds }, "$label 전체") { toggle(leaves) }
            leaves.forEach { kind -> CategoryWord(categoryLabel(kind), kind in selection.kinds) { toggle(listOf(kind)) } }
        }
    }
}

/**
 * 시트의 한 칸. 🔒 **잠긴 디자인 — `docs/design-locks.md` 2절.** 줄이지 않는다
 * (최소 88dp · 아이콘 30dp · 글자 14sp).
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
    Box(modifier.heightIn(min = 88.dp)) {
        AnimatedVisibility(arrived, enter = fadeIn(tween(180, order * 20)) +
            slideInVertically(tween(180, order * 20)) { it / 4 }) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 88.dp).testTag("place-purpose-tile").clip(shape)
                    .background(if (chosen) DaengsColors.BrandPrimarySoft else DaengsColors.Surface)
                    .border(if (chosen) 2.dp else 1.dp,
                        if (chosen) DaengsColors.BrandPrimary else DaengsColors.BorderNeutral, shape)
                    .selectable(chosen, enabled = !muted, role = Role.Tab, onClick = onClick)
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                PlaceCategoryIcon(groupIcon, Modifier.size(30.dp),
                    if (muted) DaengsColors.TextSecondary else DaengsColors.TextPrimary)
                Text(title, fontSize = 14.sp, maxLines = 1,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    color = if (muted) DaengsColors.TextSecondary else DaengsColors.TextPrimary)
            }
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
