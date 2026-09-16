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
 * **피부([ScreeningFollowUp])와 달리 `explicit` 가 없다.** 이 값은 비교 말풍선 아래
 * 칩에서만 만들어져서 언제나 지목이다. 이어서 친 질문에 비교 참조를 얹는 길은 아직
 * 없다 — 그때가 오면 여기에 같은 칸이 생긴다.
 *
 * @param recentId 최근 기록의 id ([com.daengs.app.gait.GaitComparison.recent]).
 * @param pastId 견준 예전 기록의 id ([com.daengs.app.gait.GaitComparison.past]).
 */
data class GaitFollowUp(val recentId: String, val pastId: String)
