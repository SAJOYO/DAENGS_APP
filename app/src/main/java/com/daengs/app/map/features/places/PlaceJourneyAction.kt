package com.daengs.app.map.features.places

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted

/** 길찾기 상태를 그리며 외부 지도 실행은 callback으로만 올린다. */
@Composable
internal fun PlaceJourneyAction(
    presentation: JourneyActionPresentation,
    onJourney: () -> Unit,
    onRetry: () -> Unit,
    onOpenHandoff: (String) -> Unit,
) {
    when (presentation) {
        JourneyActionPresentation.Ready -> {
            DaengsWideButton("길찾기", onJourney, accent = true)
        }
        JourneyActionPresentation.Loading -> {
            DaengsWideButton("가는 길 확인 중", {}, busy = true)
        }
        is JourneyActionPresentation.Failed -> {
            Text(
                presentation.message,
                style = MaterialTheme.typography.bodySmall,
                color = DaengsColors.Error,
                maxLines = 2,
            )
            DaengsWideButton("길찾기 다시 시도", onRetry)
        }
        JourneyActionPresentation.Unavailable -> {
            Text(
                "사용할 수 있는 이동 경로가 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            DaengsWideButton("다시 계산", onJourney)
        }
        is JourneyActionPresentation.Handoff -> {
            Text(
                presentation.summary,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            DaengsWideButton(
                presentation.actionLabel,
                { onOpenHandoff(presentation.url) },
                accent = true,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceJourneyActionPreview() {
    DaengsTheme {
        Surface(color = CardWhite) {
            PlaceJourneyAction(
                presentation = JourneyActionPresentation.Ready,
                onJourney = {},
                onRetry = {},
                onOpenHandoff = {},
            )
        }
    }
}
