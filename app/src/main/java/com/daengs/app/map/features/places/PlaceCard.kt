package com.daengs.app.map.features.places

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.map.features.journey.PlaceJourneyState
import com.daengs.app.place.PlaceKey
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/** 장소 한 곳의 표현 모델만 그린다. 시설 사실 해석은 [PlaceCardPresentation] 생성 단계에 있다. */
@Composable
internal fun PlaceCard(
    presentation: PlaceCardPresentation,
    selected: Boolean,
    journey: PlaceJourneyState?,
    onSelect: () -> Unit,
    onJourney: () -> Unit,
    onRetryJourney: () -> Unit,
    onOpenHandoff: (String) -> Unit,
    onCall: (String) -> Unit,
) {
    val border = if (selected) DaengPink else DaengsColors.BorderNeutral
    Surface(
        modifier = Modifier.width(292.dp).border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        color = CardWhite,
    ) {
        PlaceCardContent(
            presentation = presentation,
            journey = journey,
            onJourney = onJourney,
            onRetryJourney = onRetryJourney,
            onOpenHandoff = onOpenHandoff,
            onCall = onCall,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun PlaceCardContent(
    presentation: PlaceCardPresentation,
    journey: PlaceJourneyState?,
    onJourney: () -> Unit,
    onRetryJourney: () -> Unit,
    onOpenHandoff: (String) -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            presentation.name,
            color = TextDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(presentation.meta, color = TextMuted, fontSize = 13.sp)
        presentation.parking?.let { fact ->
            Text(
                fact.text,
                color = fact.tone.color(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        presentation.dogAccess?.let { fact ->
            Text(
                fact.text,
                color = fact.tone.color(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        presentation.operation?.let { fact ->
            Text(
                fact.text,
                color = fact.tone.color(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        presentation.todayHours?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        presentation.sourceDate?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        presentation.hours?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 2,
            )
        }
        presentation.address?.let {
            Text(
                it,
                color = TextDark,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlaceJourneyAction(
            presentation = journey.toActionPresentation(),
            onJourney = onJourney,
            onRetry = onRetryJourney,
            onOpenHandoff = onOpenHandoff,
        )
        presentation.phoneAction?.let { action ->
            DaengsWideButton(action.label, { onCall(action.phone) })
        }
    }
}

private fun PlaceTextTone.color(): Color = when (this) {
    PlaceTextTone.DEFAULT -> TextDark
    PlaceTextTone.MUTED -> TextMuted
    PlaceTextTone.SUCCESS -> DaengsColors.Success
    PlaceTextTone.WARNING -> DaengsColors.Warning
    PlaceTextTone.ERROR -> DaengsColors.Error
}

@Preview(showBackground = true)
@Composable
private fun PlaceCardPreview() {
    DaengsTheme {
        Surface(color = CardWhite) {
            PlaceCard(
                presentation = PlaceCardPresentation(
                    key = PlaceKey("preview", "cafe"),
                    name = "댕스 카페",
                    meta = "카페 · 320m",
                    parking = PlaceTextPresentation("주차 가능", PlaceTextTone.SUCCESS),
                    dogAccess = PlaceTextPresentation(
                        "입장 조건상 가능 · 무게 제한 허용",
                        PlaceTextTone.SUCCESS,
                    ),
                    address = "서울시 강남구",
                ),
                selected = true,
                journey = null,
                onSelect = {},
                onJourney = {},
                onRetryJourney = {},
                onOpenHandoff = {},
                onCall = {},
            )
        }
    }
}
