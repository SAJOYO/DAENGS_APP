package com.daengs.app.legal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/**
 * 공개 법적 문서의 주소. **원본은 `SAJOYO/daengs-legal` 저장소**(GitHub Pages)다.
 *
 * 앱 저장소가 비공개라 문서만 따로 공개 저장소에 두고 있다. Play Console 의
 * 개인정보처리방침 URL 과 데이터 삭제 URL 에도 **같은 주소**가 들어간다 — 여기를 바꾸면
 * 콘솔도 같이 바꿔야 한다.
 *
 * 플레이스토어는 방침 링크를 스토어 등록정보 **와 앱 안** 양쪽에 두라고 요구한다.
 * 앱 안의 자리는 저장소 탭([com.daengs.app.ui.my.MyScreen])과 랜딩 화면이다 —
 * 심사자는 카카오 계정이 없어 로그인 전에도 찾을 수 있어야 한다.
 */
object LegalLinks {
    const val PRIVACY_POLICY = "https://sajoyo.github.io/daengs-legal/privacy.html"
    const val ACCOUNT_DELETION = "https://sajoyo.github.io/daengs-legal/delete.html"
}

/** 개인정보처리방침을 브라우저로 연다. */
fun openPrivacyPolicy(context: Context) = openWebPage(context, LegalLinks.PRIVACY_POLICY)

/**
 * 웹 주소를 기본 브라우저로 던진다.
 *
 * 브라우저가 하나도 없는 기기(있다 — 관리형 기기, 일부 태블릿)에서는 `ActivityNotFoundException`
 * 이 나고 앱이 죽는다. 그때는 주소를 토스트로 보여 준다 — 사용자가 다른 기기에서
 * 열 수 있게, 그리고 "눌렀는데 아무 일도 없다"가 되지 않게.
 */
private fun openWebPage(context: Context, url: String) {
    // NEW_TASK: 화면(Activity)이 아닌 컨텍스트에서 불러도 죽지 않게. 브라우저는 어차피
    // 제 태스크에서 뜨므로 Activity 컨텍스트에서도 해로울 것이 없다.
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "브라우저를 열 수 없어요.\n$url", Toast.LENGTH_LONG).show()
    }
}
