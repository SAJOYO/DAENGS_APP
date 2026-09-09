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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DaengsLogo
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.PetAvatar
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted

/** 홈의 상단은 로고와 실제 진입점만 둔다. 화면 내비게이션은 하단 바가 맡는다. */
@Composable
fun DaengsTopBar(
    onBell: () -> Unit,
    onProfile: () -> Unit,
    /**
     * 대표 강아지의 견종. **null 이면 발자국이다.**
     *
     * 예전에는 데모 강아지로 떨어졌다. 마이·산책·장소는 같은 경우 발자국을 세우는데
     * (아무 얼굴이나 갖다 쓰면 자기 개가 아닌 얼굴을 보게 된다) 상단바만 그 규칙에서
     * 빠져 있었다.
     */
    avatar: DogBreed? = null,
    /** 사용자가 올린 프로필 사진. 있으면 견종 그림 대신 이게 뜬다. */
    photo: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    territoryContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(CreamBg)
            .padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaengsLogo()
        if (territoryContent == null) {
            Spacer(Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { territoryContent() }
        }
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
            PetAvatar(photo, avatar, 34.dp)
            DaengsIconView(DaengsIcon.CaretDown, Modifier.size(15.dp), tint = TextMuted)
        }
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun DaengsTopBarPreview() {
    DaengsTheme { DaengsTopBar({}, {}, avatar = HomeDemoData.DOG_BREED,
        territoryContent = { HomeGameCard("보리의 이번 시즌", "3곳 · 320점 · 순위 —", {}) }) }
}
