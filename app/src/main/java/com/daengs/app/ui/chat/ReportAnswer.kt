package com.daengs.app.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** 공개 법적 문서에 게시된 운영 문의 주소. */
internal const val REPORT_EMAIL = "sajoyodaengs@gmail.com"

private const val REPORT_SUBJECT = "댕스 AI 답변 신고"

/**
 * 메일 앱에만 전달되는 신고 Intent입니다.
 *
 * 서버 대화 저장은 v0.0.0 범위가 아니므로 사용자 ID나 토큰을 싣지 않습니다. 사용자가
 * 실제로 본 AI 답변과 직접 적을 신고 이유만 메일로 보냅니다.
 */
internal fun reportAnswerIntent(answer: String): Intent {
    val body = "신고 이유를 적어 주세요:\n\n\n--- 신고하는 AI 답변 ---\n$answer"
    val uri = (
        "mailto:$REPORT_EMAIL" +
            "?subject=${Uri.encode(REPORT_SUBJECT)}" +
            "&body=${Uri.encode(body)}"
        ).toUri()
    return Intent(Intent.ACTION_SENDTO, uri)
}

/** 메일 앱 handoff 성공 여부. 테스트가 실패 경로까지 확인할 수 있게 launcher를 분리합니다. */
internal fun deliverReport(intent: Intent, launcher: (Intent) -> Unit): Boolean =
    try {
        launcher(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

internal fun openReportEmail(context: Context, answer: String): Boolean =
    deliverReport(reportAnswerIntent(answer).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) {
        context.startActivity(it)
    }
