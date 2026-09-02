package com.daengs.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 앱 화풍의 누르는 것들.
 *
 * **Material3 `Button`·`FilterChip` 을 쓰지 않는다.** 이 저장소는 `Surface`·`Box` 에
 * `.clickable` 을 붙여 직접 짠다 (`LandingScreen`·`MyScreen` 과 같은 결). M3 기본
 * 부품은 자기 색과 자기 모서리를 들고 와서, 한 화면만 다른 앱처럼 보이게 만든다 —
 * 장소 화면이 정확히 그랬다.
 *
 * 여기 모아 둔 이유는 **장소 화면과 패널 두 파일이 같은 부품을 쓰기 때문**이다.
 * 한 파일에만 필요했으면 그 파일 안에 뒀을 것이다.
 */

/** 카테고리·필터 칩. 고르면 분홍으로 찬다. */
@Composable
fun DaengsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg = if (selected) PinkSoft else CardWhite
    val fg = when {
        !enabled -> TextMuted.copy(alpha = 0.5f)
        selected -> DaengPinkDeep
        else -> TextDark
    }
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, if (selected) DaengPink.copy(alpha = 0.45f) else DaengsColors.BorderNeutral, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

/**
 * 지도 위에 떠 있는 둥근 버튼.
 *
 * 지도가 배경이라 **흰 바탕을 깔아야 글자가 읽힌다** — 투명하게 두면 위성 사진이나
 * 짙은 도로 위에서 글자가 사라진다.
 */
@Composable
fun DaengsFloatingButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) CardWhite else CardWhite.copy(alpha = 0.6f))
            .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) TextDark else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * 카드 안의 가로로 꽉 찬 버튼 (길찾기·전화).
 *
 * @param busy 도는 동안 누를 수 없고, 글자 앞에 작은 원이 돈다
 */
@Composable
fun DaengsWideButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    accent: Boolean = false,
) {
    val on = enabled && !busy
    val fg = when {
        !on -> TextMuted
        accent -> DaengPinkDeep
        else -> TextDark
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) PinkSoft else CardWhite)
            .border(1.dp, if (accent) DaengPink.copy(alpha = 0.35f) else DaengsColors.BorderNeutral, RoundedCornerShape(12.dp))
            .clickable(enabled = on, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), color = DaengPink, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 글자만 있는 작은 동작 (다시 시도). */
@Composable
fun DaengsTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = DaengPinkDeep,
) {
    Text(
        label,
        color = tint,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun DaengsControlsPreview() {
    DaengsTheme {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DaengsChip("카페", selected = true, onClick = {})
                DaengsChip("병원", selected = false, onClick = {})
                DaengsChip("펫샵", selected = false, enabled = false, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DaengsFloatingButton("← 홈", {})
                DaengsFloatingButton("내 위치", {})
                DaengsFloatingButton("찾는 중", {}, enabled = false)
            }
            DaengsWideButton("길찾기", {}, accent = true)
            DaengsWideButton("가는 길 확인 중", {}, busy = true)
            DaengsWideButton("전화 02-1234-5678", {})
            DaengsTextAction("다시 시도", {})
        }
    }
}
