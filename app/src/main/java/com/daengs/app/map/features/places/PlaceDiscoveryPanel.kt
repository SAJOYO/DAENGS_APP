package com.daengs.app.map.features.places

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.common.DaengsChip
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.map.features.journey.PlaceJourneyState
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceResult
import com.daengs.app.ui.theme.DaengsTheme

@Composable
fun PlaceDiscoveryPanel(
    state: PlaceDiscoveryState,
    journey: PlaceJourneyState,
    onSearch: (PlaceKind, Boolean) -> Unit,
    onRetry: () -> Unit,
    onSelect: (PlaceKey) -> Unit,
    onJourney: (PlaceResult) -> Unit,
    onRetryJourney: () -> Unit,
    onOpenHandoff: (String) -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = state.toPanelPresentation()
    val selectedKind = presentation.selectedKind

    // **접었다 펼 수 있다.** 이 패널은 최대 430dp 를 쓰는데, 그동안 그만큼 지도가
    // 가려진 채였고 되돌릴 방법이 없었다. 위에 손잡이 모양이 있기는 했지만 그림일
    // 뿐이라 눌러도 아무 일이 없었다 — 손잡이처럼 생긴 것은 손잡이여야 한다.
    var expanded by rememberSaveable { mutableStateOf(true) }
    // **지도에서 하나를 고르면 저절로 펴진다.** 접어 둔 채로 마커를 누르면 고르기는
    // 골라지는데 보이는 것이 없어서 안 눌린 것처럼 된다.
    LaunchedEffect(state.selectedPlaceKey) {
        if (state.selectedPlaceKey != null) expanded = true
    }
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = 430.dp).animateContentSize(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = CardWhite,
        shadowElevation = 12.dp,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    PlacePanelHandle(expanded) { expanded = !expanded }
                    // 접어도 이 두 줄은 남긴다. 무엇을 어디서 찾은 목록인지가
                    // 사라지면 접힌 패널이 그냥 흰 띠가 된다.
                    Column(Modifier.padding(start = 16.dp, end = 16.dp)) {
                        Text(
                            presentation.title,
                            color = TextDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            presentation.originDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }
            }

            if (expanded) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (presentation.showParkingPreference) {
                            DaengsChip(
                                label = "주차 가능 우선",
                                selected = state.preferParking,
                                enabled = !state.loading,
                                onClick = { onSearch(selectedKind, !state.preferParking) },
                            )
                        }
                        Text(
                            presentation.sortDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }

                presentation.shoppingNotice?.let { notice ->
                    item {
                        Text(
                            notice,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = DaengsColors.Warning,
                        )
                    }
                }

                presentation.parkingCoverage?.let { coverage ->
                    item {
                        Text(
                            coverage,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }

                presentation.dogAccessCoverage?.let { coverage ->
                    item {
                        Text(
                            coverage,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }

                presentation.errorMessage?.let { error ->
                    item {
                        Surface(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = DaengsColors.ErrorSoft,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    error,
                                    modifier = Modifier.weight(1f),
                                    color = DaengsColors.Error,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                )
                                if (presentation.retryable) {
                                    DaengsTextAction("다시 시도", onRetry, tint = DaengsColors.Error)
                                }
                            }
                        }
                    }
                }

                when (val results = presentation.results) {
                    is PlaceResultsPresentation.Loading -> item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(22.dp),
                                color = DaengPink,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(results.message, color = TextMuted, fontSize = 13.sp)
                        }
                    }
                    PlaceResultsPresentation.Failed -> Unit
                    is PlaceResultsPresentation.Initial -> item {
                        PlaceResultMessage(results.message)
                    }
                    is PlaceResultsPresentation.Empty -> item {
                        PlaceResultMessage(results.message)
                    }
                    is PlaceResultsPresentation.Content -> {
                        item {
                            Text(
                                results.countLabel,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = TextDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(results.hits, key = { placeMarkerId(it.place.key) }) { hit ->
                                    PlaceCard(
                                        presentation = hit.toCardPresentation(),
                                        selected = hit.place.key == state.selectedPlaceKey,
                                        journey = journey.takeIf {
                                            it.destinationKey == hit.place.key
                                        },
                                        onSelect = { onSelect(hit.place.key) },
                                        onJourney = {
                                            onSelect(hit.place.key)
                                            onJourney(hit.place)
                                        },
                                        onRetryJourney = onRetryJourney,
                                        onOpenHandoff = onOpenHandoff,
                                        onCall = onCall,
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

/**
 * 패널을 접었다 펴는 손잡이.
 *
 * **가운데에 둔다.** 시트를 끄는 손잡이는 가운데 있는 것이 약속이고, 왼쪽으로
 * 치우쳐 있으면 아래 제목줄의 들여쓰기와 섞여서 그냥 선으로 읽힌다.
 *
 * **누르는 것과 끄는 것을 둘 다 받는다.** 손잡이 모양을 보면 끌게 되고, 작으면
 * 누르게 된다. 한쪽만 받으면 나머지 한쪽에서 "안 눌린다"가 된다. 누르는 자리는
 * 막대(42×4dp)가 아니라 그 위아래 여백까지 포함한 가로 전체 24dp 다.
 */
@Composable
private fun PlacePanelHandle(expanded: Boolean, onToggle: () -> Unit) {
    // 끌기는 손가락을 뗄 때 판정한다. 끄는 도중에 패널이 따라 늘었다 줄었다 하면
    // 목록이 다시 배치되면서 눈이 따라가지 못한다.
    var dragged by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    Box(
        Modifier.fillMaxWidth()
            .testTag("place-panel-handle")
            .clickable(onClick = onToggle)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dragged += it },
                onDragStopped = {
                    if (dragged > threshold && expanded) onToggle()
                    if (dragged < -threshold && !expanded) onToggle()
                    dragged = 0f
                },
            )
            .semantics { contentDescription = if (expanded) "장소 목록 접기" else "장소 목록 펴기" }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 옅은 테두리색(1.33:1)은 장식일 때나 통했다. 이제 눌러야 하는 것이라
        // 한 단계 진한 갈색으로 올린다 — 흰 바탕에서 5.53:1 이다.
        Box(
            Modifier.width(42.dp).height(4.dp)
                .background(PlaceSearchColors.Muted, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun PlaceResultMessage(message: String) {
    Text(
        message,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        color = TextMuted,
        fontSize = 13.sp,
    )
}

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun PlaceDiscoveryPanelPreview() {
    DaengsTheme {
        PlaceDiscoveryPanel(
            state = PlaceDiscoveryState(),
            journey = PlaceJourneyState(),
            onSearch = { _, _ -> },
            onRetry = {},
            onSelect = {},
            onJourney = {},
            onRetryJourney = {},
            onOpenHandoff = {},
            onCall = {},
        )
    }
}
