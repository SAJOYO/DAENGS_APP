package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.R
import com.daengs.app.map.features.territory.TerritoryGameState
import com.daengs.app.map.layers.territory.*
import com.daengs.app.territory.ClaimPhotoStatus
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.theme.DaengsColors

/** 기존 산책 UI 호출 계약은 유지하고 애니메이션 구현은 지도 공용 계층에 위임한다. */
@Composable
internal fun rememberTerritoryFeedbackProgress(feedback: TerritoryFeedback?, nowNanos: () -> Long = System::nanoTime): Float =
    rememberTerritoryFeedbackAnimationProgress(feedback, nowNanos)

internal fun territoryFeedbackLabel(game: TerritoryGameState, feedback: TerritoryFeedback?): String =
    if (game.onlinePhotos) game.guidance else when (game.photoStatus) {
    ClaimPhotoStatus.PENDING -> "사진 확인 중 · 산책을 계속해도 돼요"
    ClaimPhotoStatus.RETRY_PENDING -> "사진 보관 중 · 판정을 다시 시도해 주세요"
    ClaimPhotoStatus.REJECTED -> "인증되지 않았어요 · 현장에서 다시 촬영해 주세요"
    ClaimPhotoStatus.VERIFIED -> "사진 인증 완료"
    else -> if (feedback?.kind == TerritoryFeedbackKind.READY && !game.canMark) "인증 촬영 준비 · 사진으로 영역표시해요"
        else feedback?.kind?.label ?: game.guidance
}

@Composable
internal fun TerritoryFeedbackLine(game: TerritoryGameState, nowNanos: () -> Long = System::nanoTime) {
    val progress = rememberTerritoryFeedbackProgress(game.feedback, nowNanos)
    val feedback = game.feedback?.takeIf { progress < 1f }
    val frame = territoryFeedbackFrame(feedback?.kind, progress)
    val success = feedback?.takeIf { it.kind != TerritoryFeedbackKind.READY }
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (success != null) Icon(painterResource(R.drawable.ic_territory_paw), contentDescription = null,
            tint = if (success.kind == TerritoryFeedbackKind.MARKED) DaengsColors.Warning else DaengsColors.Success,
            modifier = Modifier.size(16.dp).graphicsLayer { scaleX = frame.markerScale; scaleY = frame.markerScale })
        if (success != null) Spacer(Modifier.width(5.dp))
        Text(territoryFeedbackLabel(game, feedback), color = TextMuted, fontSize = 11.sp)
    }
}

@Preview(showBackground = true) @Composable
private fun TerritoryFeedbackLinePreview() { MaterialTheme {
    Column {
        ClaimPhotoStatus.entries.forEach { TerritoryFeedbackLine(TerritoryGameState(photoStatus = it)) }
        TerritoryFeedbackLine(TerritoryGameState(onlinePhotos = true, photoStatus = ClaimPhotoStatus.RETRY_PENDING,
            guidance = "사진 판정을 마치지 못했어요 · 현장에서 새 사진을 찍어 주세요"))
    }
} }
