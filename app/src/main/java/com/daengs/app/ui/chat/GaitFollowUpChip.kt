package com.daengs.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** 보행 비교 말풍선 아래 칩의 글자. */
internal const val GAIT_FOLLOW_UP_LABEL = "이 변화 물어보기"

/**
 * 칩을 누르면 보내지는 질문. 대화에 **내 말풍선으로 그대로 남는다** —
 * [REPORT_FOLLOW_UP_QUESTION] 과 같은 이유다.
 *
 * "결과" 가 아니라 **"변화"** 라고 묻는다. 보행은 한 번 찍은 것으로 상태를 말하는
 * 기능이 아니라 **같은 아이의 두 기록을 견주는** 기능이고, 서버 해설도 거기에 맞춰
 * 무엇이 달라졌는지만 말한다 (백엔드 D-058 · D-080).
 */
internal const val GAIT_FOLLOW_UP_QUESTION = "이 변화가 무슨 뜻이고, 이제 뭘 하면 좋을까요?"

/**
 * 보행 비교 말풍선 아래 "이 변화 물어보기" (백엔드 D-080 — 보행 변화 관찰 해설 서브에이전트).
 *
 * 누르면 **기록 id 둘**을 실은 질문이 가고, 서버가 두 기록을 다시 견줘 무엇이 달라졌는지와
 * 다음 행동(같은 조건으로 다시 찍기 · 조건 확인 · 지켜보기)을 답한다. 앱이 지은 판정
 * 문장이나 관절 수치는 보내지 않는다 ([com.daengs.app.assistant.GaitFollowUp]).
 */
@Composable
internal fun GaitFollowUpChip(enabled: Boolean, onClick: () -> Unit) {
    FollowUpChip(GAIT_FOLLOW_UP_LABEL, enabled = enabled, onClick = onClick)
}

@Preview(showBackground = true)
@Composable
private fun GaitFollowUpChipPreview() {
    GaitFollowUpChip(enabled = true) {}
}

@Preview(showBackground = true)
@Composable
private fun GaitFollowUpChipWaitingPreview() {
    GaitFollowUpChip(enabled = false) {}
}
