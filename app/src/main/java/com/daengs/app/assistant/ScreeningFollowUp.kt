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
 * @param recordId 판정이 남은 기록의 id (`ScreeningRun.Outcome.Screened.recordId`).
 */
data class ScreeningFollowUp(val recordId: String)
