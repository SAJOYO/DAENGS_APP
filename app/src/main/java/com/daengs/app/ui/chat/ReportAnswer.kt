package com.daengs.app.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** 공개 법적 문서에 게시된 운영 문의 주소. */
internal const val REPORT_EMAIL = "sajoyodaengs@gmail.com"

private const val REPORT_SUBJECT = "댕스 AI 답변 신고"

/** 저쪽 `POST /app/reports` 의 `reason` 상한. 넘으면 422 라 입력에서 막는다. */
internal const val REPORT_REASON_MAX = 500

/**
 * 신고 사유 선택지.
 *
 * 폰에서 타이핑 없이 신고가 끝나야 하고, 운영자도 갈래별로 셀 수 있어야 해서 목록을
 * 둔다. [OTHER] 만 직접 적는다 — 목록에 없는 문제를 못 적으면 신고가 막힌다.
 */
internal enum class ReportReason(val label: String) {
    WRONG("사실과 달라요"),
    DANGEROUS("위험한 조언이에요"),
    OFFENSIVE("불쾌해요"),
    OTHER("직접 적을게요"),
}

/**
 * 서버에 보낼 사유 한 줄. **보낼 수 없으면 null** 이다 — 직접 적기를 골라 놓고 아무것도
 * 안 썼을 때. 서버가 422 로 버리기 전에 여기서 막는다 (`ChatPersistence` 와 같은 자리).
 */
internal fun reportReasonText(choice: ReportReason, written: String): String? = when (choice) {
    ReportReason.OTHER -> written.trim().takeIf { it.isNotEmpty() }
    else -> choice.label
}

/**
 * 메일 앱으로 가는 신고.
 *
 * **서버 신고가 안 되는 답변의 길이다.** 저장 안 된 대화(무상태 질문)의 답변에는
 * `turn_id` 가 없어서 `POST /app/reports` 가 받아 주지 않는다. 서버 신고가 실패했을
 * 때도 이리로 물러선다 — 그때는 [turnId] 와 [reason] 이 있으므로 같이 싣는다.
 * 운영자가 콘솔에서 그 답변을 찾을 수 있는 유일한 실마리다.
 */
internal fun reportAnswerIntent(
    answer: String,
    turnId: String? = null,
    reason: String? = null,
): Intent {
    val body = buildString {
        if (reason == null) append("신고 이유를 적어 주세요:\n\n\n") else append("신고 이유: $reason\n\n")
        if (turnId != null) append("답변 번호: $turnId\n")
        append("--- 신고하는 AI 답변 ---\n")
        append(answer)
    }
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

internal fun openReportEmail(
    context: Context,
    answer: String,
    turnId: String? = null,
    reason: String? = null,
): Boolean =
    deliverReport(
        reportAnswerIntent(answer, turnId, reason).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    ) {
        context.startActivity(it)
    }
