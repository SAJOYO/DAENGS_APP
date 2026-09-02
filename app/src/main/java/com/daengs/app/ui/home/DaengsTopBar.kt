package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DaengsLogo
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted

/** 홈의 상단은 로고와 실제 진입점만 둔다. 화면 내비게이션은 하단 바가 맡는다. */
@Composable
fun DaengsTopBar(
    onBell: () -> Unit,
    onProfile: () -> Unit,
    avatar: DogBreed = HomeDemoData.DOG_BREED,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(CreamBg)
            .padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaengsLogo()
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBell),
            contentAlignment = Alignment.Center,
        ) {
            DaengsIconView(DaengsIcon.Bell, Modifier.size(22.dp), tint = TextMuted)
        }
        Spacer(Modifier.width(4.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onProfile)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DogAvatar(avatar, Modifier.size(34.dp))
            DaengsIconView(DaengsIcon.CaretDown, Modifier.size(15.dp), tint = TextMuted)
        }
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun DaengsTopBarPreview() {
    DaengsTheme { DaengsTopBar({}, {}) }
}
