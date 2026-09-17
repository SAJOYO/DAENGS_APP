package com.daengs.app.assistant

/**
 * 보행 비교 결과를 보고 이어 묻는 질문 (백엔드 `SAJOYO/DAENGS_dev#568`, D-080).
 *
 * 이 값이 있으면 [AssistantApi.requestBody] 가 `requested_capability="gait"` 와
 * `gait_compare{recent_record_id, past_record_id}` 를 **함께** 싣고, 서버의 보행 변화
 * 관찰 해설 서브에이전트가 무엇이 달라졌는지와 다음 행동(같은 조건으로 다시 찍기 ·
 * 조건 확인 · 지켜보기)을 답한다.
 *
 * **비교 내용은 싣지 않는다 — 기록 id 둘뿐이다.** 관절별 이동범위도, 앱이 화면에서
 * 지어 붙인 판정 문장도 보내지 않는다. 서버가 두 기록의 소유를 확인해 DB 에서 읽고
 * 그 자리에서 다시 비교한다. 답이 대화로 저장되므로, 앱이 보낸 비교를 믿으면 지난
 * 대화에서 되돌릴 수 없다.
 *
 * **[explicit] 가 가르는 것은 "누가 답할지를 앱이 정하는가" 다** (백엔드 `#577`, D-081).
 * [ScreeningFollowUp.explicit] 과 같은 규칙이고, 같은 이유로 생겼다.
 *
 * - `true` — 비교 말풍선의 "이 변화 물어보기". `requested_capability="gait"` 를 함께 보내
 *   서버가 묻지도 따지지도 않고 해설로 보낸다. 사용자가 그 칩을 눌러 뜻을 밝혔기 때문이다.
 * - `false` — 비교를 보고 사용자가 **직접 친 질문**. 기록 id 둘만 보낸다. 누가 답할지는
 *   서버의 의미 라우터가 정하고, 라우터가 보행으로 보냈을 때만 해설이 받는다
 *   (D-081 의 HANDOFF 전환). **매 턴 신호까지 보내면 "산책 언제 가?" 까지 보행이
 *   가로챈다.**
 *
 * @param recentId 최근 기록의 id ([com.daengs.app.gait.GaitComparison.recent]).
 * @param pastId 견준 예전 기록의 id ([com.daengs.app.gait.GaitComparison.past]).
 * @param explicit 보행 해설을 **지목**하는가. 칩은 true, 이어서 친 질문은 false.
 */
data class GaitFollowUp(val recentId: String, val pastId: String, val explicit: Boolean)
