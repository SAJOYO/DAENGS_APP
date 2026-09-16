package com.daengs.app.assistant

/**
 * 피부 판정 결과를 보고 이어 묻는 질문 (백엔드 `SAJOYO/DAENGS_dev#556`, D-079).
 *
 * 이 값이 있으면 [AssistantApi.requestBody] 가 `requested_capability="skin"` 과
 * `screening_record_id` 를 **함께** 싣고, 서버의 피부 판정 해설 서브에이전트가 그 판정이
 * 무슨 뜻인지와 다음 행동(다시 찍기 · 진료 · 지켜보기)을 답한다.
 *
 * **판정 내용은 싣지 않는다** — 기록 id 만 보내고 판정은 서버가 소유를 확인해 DB 에서
 * 읽는다. 응답이 대화로 저장되므로, 앱이 보낸 판정을 믿으면 지난 대화에서 되돌릴 수 없다.
 *
 * **[explicit] 가 가르는 것은 "누가 답할지를 앱이 정하는가" 다** (백엔드 `#569`).
 *
 * - `true` — 판정 말풍선의 "이 결과 물어보기". `requested_capability="skin"` 을 함께 보내
 *   서버가 묻지도 따지지도 않고 해설로 보낸다. 사용자가 그 버튼을 눌러 뜻을 밝혔기 때문이다.
 * - `false` — 판정 뒤에 사용자가 **직접 친 질문**. 기록 id 만 보낸다. 누가 답할지는 서버의
 *   의미 라우터가 정하고, 피부 이야기일 때만 해설이 받는다. 신호까지 보내면 "산책 언제 가?"
 *   까지 피부가 가로챈다.
 *
 * @param recordId 판정이 남은 기록의 id (`ScreeningRun.Outcome.Screened.recordId`).
 * @param explicit 피부 해설을 **지목**하는가. 칩은 true, 이어서 친 질문은 false.
 */
data class ScreeningFollowUp(val recordId: String, val explicit: Boolean)
