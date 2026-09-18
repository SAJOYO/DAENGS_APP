package com.daengs.app.ui.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted

/**
 * 저장소 탭에서 접힌 섹션을 여는 줄.
 *
 * **진료비가 쓰던 모양을 그대로 쓴다** (`VetVisitSection` 의 「전체보기 ›」). 저장소 탭의
 * 섹션 셋이 같은 몸짓으로 열려야, 사용자가 한 번 배우면 나머지도 안다.
 *
 * [hint] 는 몇 개가 접혔는지다 — 없으면 "전체보기" 가 무엇을 더 보여 줄지 알 수 없어서
 * 누를 이유가 안 생긴다.
 */
@Composable
fun StorageSeeAllRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            color = DaengsColors.BrandPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
        )
        hint?.let { Text(it, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f)) }
        Text("›", color = DaengsColors.BrandPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun StorageSeeAllRowPreview() {
    DaengsTheme {
        StorageSeeAllRow(label = "전체보기", onClick = {}, hint = "+5개 더", modifier = Modifier.padding(16.dp))
    }
}
