package com.daengs.app.ui.my

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.daengs.app.ui.theme.TextMuted

/** GitHub Pages API와 공개 페이지에서 확인한 개인정보처리방침 정본 주소. */
internal const val PRIVACY_POLICY_URL =
    "https://sajoyo.github.io/daengs-legal/privacy.html"

/** 화면에 보이는 링크 글자. 테스트가 같은 글자로 찾습니다. */
internal const val PRIVACY_POLICY_LABEL = "개인정보처리방침"

internal fun privacyPolicyIntent(): Intent =
    Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())

/** 브라우저가 없는 기기에서도 My 화면이 죽지 않고 실패를 알려 줍니다. */
internal fun openPrivacyPolicy(context: Context) {
    try {
        context.startActivity(privacyPolicyIntent())
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "개인정보처리방침을 열 수 없어요. $PRIVACY_POLICY_URL",
            Toast.LENGTH_LONG,
        ).show()
    }
}

/**
 * My 화면에 못 들어가는 자리에 두는 글자 링크 — 로그인 전(Landing)과 첫 강아지
 * 등록(취소가 없는 온보딩). 그 두 자리를 지나기 전에는 My 탭이 없어서, 여기 없으면
 * 앱 안 어디서도 방침을 못 찾습니다.
 */
@Composable
internal fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Text(
        PRIVACY_POLICY_LABEL,
        color = TextMuted,
        fontSize = 12.sp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { openPrivacyPolicy(context) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
