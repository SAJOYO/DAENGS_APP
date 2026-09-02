package com.daengs.app.ui.my

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/** GitHub Pages API와 공개 페이지에서 확인한 개인정보처리방침 정본 주소. */
internal const val PRIVACY_POLICY_URL =
    "https://sajoyo.github.io/daengs-legal/privacy.html"

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
