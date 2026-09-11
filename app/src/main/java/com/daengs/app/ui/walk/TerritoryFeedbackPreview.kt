package com.daengs.app.ui.walk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.R
import com.daengs.app.map.layers.territory.*
import com.daengs.app.ui.theme.DaengsTheme

/** SDK와 같은 픽셀 크기·바닥 기준점으로 그림과 효과를 검토한다. */
@Composable
internal fun TerritoryFeedbackSample(kind: TerritoryFeedbackKind, progress: Float) {
    val frame = territoryFeedbackFrame(kind, progress)
    val context = LocalContext.current
    val icon = remember(context, kind) { territoryMarkerIcon(context, when (kind) {
        TerritoryFeedbackKind.READY -> TerritoryMarkerOccupancy.NEUTRAL
        TerritoryFeedbackKind.MARKED -> TerritoryMarkerOccupancy.UNVERIFIED
        TerritoryFeedbackKind.VERIFIED -> TerritoryMarkerOccupancy.VERIFIED
    }, isMine = true).asImageBitmap() }
    val accent = if (kind == TerritoryFeedbackKind.MARKED) Color(0xffe3912d) else Color(0xff3c9673)
    val size = TerritoryPoleArt.size(scale = frame.markerScale)
    val density = LocalDensity.current
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(130.dp)) {
            drawCircle(accent.copy(alpha = (40 + 35 * frame.glow) / 255f))
            drawCircle(accent, style = Stroke((2 + frame.glow).dp.toPx()))
        }
        Image(icon, "전봇대",
            modifier = with(density) { Modifier
                .offset(y = (size.second * (.5f - TerritoryPoleArt.ANCHOR_Y)).toDp())
                .size(size.first.toDp(), size.second.toDp()) })
        if (frame.pawAlpha > 0f) Icon(painterResource(R.drawable.ic_territory_paw), "영역표시 성공 발자국",
            tint = accent.copy(alpha = frame.pawAlpha),
            modifier = with(density) {
                val pawSize = 36 * frame.pawScale
                Modifier.offset(y = (-1.1f * pawSize).toDp()).size(pawSize.toDp())
            })
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 620)
@Composable
private fun TerritoryFeedbackPreview() { DaengsTheme {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        TerritoryFeedbackKind.entries.forEach { Text(it.label); TerritoryFeedbackSample(it, .5f) }
    }
} }
