package com.daengs.app.map.features.places

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val categoryState = rememberLazyListState()

    // 선택된 종류가 18개 칩 중 화면 밖에 있으면, 무엇으로 찾은 결과인지 보이지 않는다.
    LaunchedEffect(selectedKind) {
        val index = PLACE_CATEGORIES.indexOfFirst { it.kind == selectedKind }
        if (index >= 0) categoryState.animateScrollToItem(index)
    }

    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 210.dp, max = 430.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = CardWhite,
        shadowElevation = 12.dp,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                    Box(
                        Modifier.width(42.dp).height(4.dp)
                            .background(DaengsColors.BorderNeutral, RoundedCornerShape(4.dp))
                            .align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(10.dp))
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

            item {
                LazyRow(
                    state = categoryState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(PLACE_CATEGORIES, key = { it.kind.wire }) { category ->
                        DaengsChip(
                            label = category.label,
                            selected = category.kind == selectedKind,
                            enabled = !state.loading,
                            onClick = { onSearch(category.kind, state.preferParking) },
                        )
                    }
                }
            }

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
