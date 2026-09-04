package com.daengs.app.chat

import java.util.UUID

/**
 * `client_message_id` · `client_request_id` 를 **언제 같은 값으로, 언제 새 값으로** 보낼지.
 *
 * 저쪽(`services/chat.py`, `docs/chat-transaction-flow.md`)은 두 UUID 를 멱등 키로 쓴다.
 * 같은 키 + 같은 질문이면 저장된 답을 그대로 돌려주고 모델을 안 부른다 — 그래서
 * **응답을 잃은 뒤의 재시도는 같은 키여야** 한다 (안 그러면 같은 질문이 두 번 저장되고
 * 모델이 두 번 돈다). 반대로 같은 키를 **다른 질문에** 쓰면 409 `CLIENT_MESSAGE_ID_REUSED`
 * 로 조용히 합쳐 주지 않고, 실패로 끝난 키는 탄 것이라 `TURN_FAILED` 다.
 *
 * 이 규칙을 화면 코드에 흩어 두면 하나쯤 빠진다. 여기 한 곳에 두고 화면은 두 함수만
 * 부른다 — 보내기 전에 [clientMessageId], 결과를 받고 [turnDelivered] 또는 [turnFailed].
 *
 * **기기에 저장하지 않는다.** 앱이 죽으면 같은 키를 잃고 새 키로 보내는데, 그러면
 * 최악이 "같은 질문이 두 번 남는 것" 이다. 반대로 키를 저장했다가 다른 질문에 잘못
 * 붙이면 409 로 질문이 막힌다. 앞쪽이 낫다. 세션 한 번 안의 재시도만 맡는다.
 *
 * @param newId 테스트가 UUID 를 예측 가능하게 바꿔 끼우는 자리. 기본은 무작위 v4.
 */
class ChatRequestIds(private val newId: () -> String = { UUID.randomUUID().toString() }) {

    private data class RetainedTurn(val sessionId: String, val text: String, val id: String)

    /** 마지막으로 보낸(아직 결론이 안 난) 질문. 한 번에 하나만 묻는다 (`ChatScreen` 의 `asking`). */
    private var turn: RetainedTurn? = null

    /** 대화별로 진행 중인 요약 요청. */
    private val summaries = mutableMapOf<String, String>()

    // -- 질문 --------------------------------------------------------------------

    /**
     * 이 대화에 이 질문을 보낼 때 쓸 `client_message_id`.
     *
     * **같은 대화 + 글자 그대로 같은 질문**을 다시 보내는 것만 같은 값이다 — 응답을
     * 잃었거나 `TURN_PROCESSING` 을 받고 기다렸다 다시 보내는 경우. 질문이 한 글자라도
     * 다르거나 대화가 다르면 새 값이고, 들고 있던 것은 버린다.
     */
    fun clientMessageId(sessionId: String, text: String): String {
        val kept = turn
        if (kept != null && kept.sessionId == sessionId && kept.text == text) return kept.id
        return newId().also { turn = RetainedTurn(sessionId, text, it) }
    }

    /**
     * 답이 왔다 (HTTP 200 — `FAILED` 상태도 여기다, 저쪽이 그 turn 을 실패로 닫으므로).
     *
     * 키를 버린다. **같은 질문을 또 하면 새 turn 이어야** 한다 — 같은 키를 쓰면 저쪽이
     * 저장된 답을 다시 줄 뿐 모델을 안 부른다.
     */
    fun turnDelivered() {
        turn = null
    }

    /**
     * 실패했다. **같은 키로 그대로 다시 보내도 되는가**를 돌려준다.
     *
     * - 닿지 못함·게이트웨이 끊김·401(재로그인 뒤)·`TURN_PROCESSING` → **같은 키.**
     *   서버가 예약을 잡았을 수 있고, 같은 키가 그 예약을 찾아 준다.
     * - `TURN_FAILED`·503 의 `retry_with_fresh_client_message_id`·`CLIENT_MESSAGE_ID_REUSED`·
     *   `ACTIVE_DOG_MISMATCH`·404·422 → **새 키.** 그 키는 탔거나, 그 대화로는 못 보낸다.
     */
    fun turnFailed(error: ChatApiError): Boolean {
        val keep = error.unreachable || error.gatewayTimeout || error.needsReauth || error.stillProcessing
        if (!keep) turn = null
        return keep
    }

    // -- 요약 --------------------------------------------------------------------

    /**
     * 이 대화를 요약하라고 보낼 때 쓸 `client_request_id`. 결론이 날 때까지 같은 값이다.
     *
     * 저쪽은 같은 `(대화, 문답 수)` 를 두 번 요약하지 않으므로 여기서 질문 텍스트 같은
     * 것을 볼 필요가 없다 — 대화 하나에 진행 중인 요약 요청 하나다.
     */
    fun clientRequestId(sessionId: String): String =
        summaries.getOrPut(sessionId) { newId() }

    /** 요약이 저장됐다 (201). 다음 요약 요청은 새 키다. */
    fun summaryDelivered(sessionId: String) {
        summaries.remove(sessionId)
    }

    /**
     * 요약이 실패했다. **같은 키로 다시 보내도 되는가**를 돌려준다.
     *
     * - 닿지 못함·게이트웨이 끊김·401·`SUMMARY_PROCESSING` → **같은 키.** 저쪽이 아직
     *   만드는 중이거나 만들어 뒀을 수 있다.
     * - `SUMMARY_ALREADY_EXISTS` → 실패가 아니다. 키를 버리고 **그 요약으로 간다**
     *   ([ChatApiError.existingSummaryId]).
     * - 502(공급자 실패)·`SUMMARY_REQUEST_ALREADY_FAILED`·503 의
     *   `retry_with_fresh_client_request_id`·그 밖의 409·404·422 → **새 키.** 그 예약은
     *   실패로 닫혔고 같은 키는 다음에 `SUMMARY_REQUEST_ALREADY_FAILED` 가 된다.
     */
    fun summaryFailed(sessionId: String, error: ChatApiError): Boolean {
        val keep = error.unreachable ||
            (error.gatewayTimeout && !error.providerFailed) ||
            error.needsReauth ||
            error.stillProcessing
        if (!keep) summaries.remove(sessionId)
        return keep
    }

    /** 로그아웃·탈퇴·강아지 바꿈. 다음 사람의 질문에 남의 키가 붙으면 안 된다. */
    fun forget() {
        turn = null
        summaries.clear()
    }
}
