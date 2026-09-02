package com.daengs.app.ui.landing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.TextDark

/**
 * 로그인을 건너뛰고 방까지 들어가는 길. **디버그 빌드에만 있다.**
 *
 * 출시 앱은 로그인이 필수라 릴리스에는 빈 껍데기가 들어간다
 * (`app/src/release/.../SkipBrowse.kt`).
 *
 * ## 디버그에는 왜 남기나
 *
 * **서버가 죽어 있어도 앱을 볼 수 있어야 한다.** 방·도감·진단·지도는 인증을 안
 * 쓰는데, 개발할 때까지 로그인을 강제하면 서버가 없을 때 아무것도 못 만진다.
 * 카카오 키가 안 채워진 새 체크아웃에서도 마찬가지다.
 *
 * 위 여백도 여기서 챙긴다 — 호출부에 두면 릴리스에서 그 `Spacer` 만 남는다.
 */
@Composable
fun SkipBrowse(enabled: Boolean, onSkip: () -> Unit) {
    Spacer(Modifier.height(6.dp))
    Text(
        "둘러보기",
        color = TextDark,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onSkip)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
