package com.daengs.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** 판정 말풍선 아래 칩의 글자. */
internal const val REPORT_FOLLOW_UP_LABEL = "이 결과 물어보기"

/**
 * 칩을 누르면 보내지는 질문. 대화에 **내 말풍선으로 그대로 남는다** — 나중에 이력에서
 * 봐도 무엇을 물었는지 알 수 있어야 해서, "버튼 눌림" 같은 표시가 아니라 사람 말이다.
 */
internal const val REPORT_FOLLOW_UP_QUESTION = "이 결과가 무슨 뜻이고, 이제 뭘 하면 좋을까요?"

/**
 * 피부 판정 말풍선 아래 "이 결과 물어보기" (백엔드 D-079 — 피부 판정 해설 서브에이전트).
 *
 * 누르면 판정 기록 id 를 실은 질문이 가고, 서버가 그 판정이 무슨 뜻인지와 다음 행동
 * (다시 찍기 · 진료 · 지켜보기)을 답한다. 답을 기다리는 동안([enabled] = false)에는
 * 눌리지 않는다 — 입력칸과 같은 규칙이다.
 */
@Composable
internal fun ReportFollowUpChip(enabled: Boolean, onClick: () -> Unit) {
    FollowUpChip(REPORT_FOLLOW_UP_LABEL, enabled = enabled, onClick = onClick)
}

@Preview(showBackground = true)
@Composable
private fun ReportFollowUpChipPreview() {
    ReportFollowUpChip(enabled = true) {}
}

@Preview(showBackground = true)
@Composable
private fun ReportFollowUpChipWaitingPreview() {
    ReportFollowUpChip(enabled = false) {}
}
