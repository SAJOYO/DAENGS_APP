package com.daengs.app.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * 대화·요약·저장 질의가 실패했을 때 앱이 받는 것.
 *
 * `PetApi`·`WalkApi` 는 실패를 **문장 하나**로 접는다 — 그쪽은 화면이 문장을 띄우면
 * 끝이라서다. 대화 저장은 다르다. 저쪽(`routers/chat.py` · `routers/assistant.py`)이
 * 409 하나에도 `detail.code` 를 여섯 갈래로 나눠 주고, 그 갈래마다 화면이 할 일이
 * 다르다 — 같은 UUID 로 **기다렸다 다시**(`TURN_PROCESSING`), **새 UUID 로**
 * (`TURN_FAILED`), **이미 있는 요약으로 이동**(`SUMMARY_ALREADY_EXISTS`), **다시
 * 로그인**(401). 문장으로 접어 버리면 그 판단을 화면이 문자열을 뒤져서 해야 한다.
 *
 * 그래서 상태 코드·코드·동봉 데이터를 그대로 든다. [message] 는 여전히 사용자에게
 * 보여 줄 한 문장이라, 코드를 모르는 화면은 지금처럼 `it.message` 만 띄워도 된다.
 *
 * [IllegalStateException] 인 이유: 기존 클라이언트들이 "서버가 준 문장은 그대로
 * 통과시킨다" 를 `cause is IllegalStateException` 으로 가르고 있어서다. 그 배관을
 * 그대로 타야 [com.daengs.app.assistant.AssistantApi] 의 무상태 경로가 안 바뀐다.
 *
 * ⚠️ **응답 본문을 담지 않는다.** 저장된 대화가 오류 본문에 섞여 올 수 있고, 예외는
 * 로그로 새기 쉽다 (저쪽 D-048 — 관측 계층에 원문 금지). 여기 있는 것은 상태·코드·
 * 동봉된 id·숫자·불리언뿐이다.
 */
class ChatApiError(
    /** HTTP 상태. **0 은 서버에 닿지 못한 것**이다 (DNS·연결·타임아웃). */
    val status: Int,
    /** 저쪽 `detail.code`. 문장만 온 오류(401·404·502)는 null 이다. */
    val code: String?,
    message: String,
    /** `detail` 객체의 나머지 칸. 값은 전부 문자열로 옮겼다 (`"true"`, `"2000"`). */
    val data: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : IllegalStateException(message, cause) {

    // -- 화면이 갈래를 정할 때 보는 것 ----------------------------------------

    /** 토큰이 죽었거나 탈퇴한 회원이다. **재발급이 아니라 다시 로그인**이다. */
    val needsReauth: Boolean get() = status == 401

    /** 내 것이 아니거나 없다. 남의 것도 404 라 둘을 가를 수 없다 — 목록을 다시 받는다. */
    val notFound: Boolean get() = status == 404

    /** 본문 모양이 틀렸거나([QUESTION_TOO_LONG]) FastAPI 검증에 걸렸다. 앱 버그다. */
    val invalidRequest: Boolean get() = status == 422

    /**
     * 요약 공급자가 실패했다 (`POST …/summary` 의 502). **아무것도 저장되지 않았다** —
     * 저쪽이 부분 저장을 안 한다. 그 예약은 실패로 닫혀서 같은 `client_request_id` 는
     * 다음에 [SUMMARY_REQUEST_ALREADY_FAILED] 가 되므로 새 id 로 다시 시도한다.
     */
    val providerFailed: Boolean get() = status == 502

    /**
     * 게이트웨이(nginx)가 끊었거나 서버가 못 받는다 (502~504, 코드 없음). **닿지
     * 못한 것이 아니라 오래 걸린 것**일 수 있고, 서버는 아직 답을 만들고 있을 수 있다 —
     * 같은 UUID 로 다시 보내면 저장된 답을 그대로 받거나 [TURN_PROCESSING] 을 받는다.
     *
     * 요약의 502 와 겹친다 — 그쪽은 [providerFailed] 로 먼저 가른다.
     */
    val gatewayTimeout: Boolean get() = status in 502..504 && code == null

    /** 닿지 못했다. 서버가 요청을 못 받았을 수도, 받았는데 응답만 잃었을 수도 있다. */
    val unreachable: Boolean get() = status == 0

    /** 같은 요청이 아직 답하는 중이다. **새 요청을 만들지 말고** 잠시 뒤 같은 것으로. */
    val stillProcessing: Boolean get() = code == TURN_PROCESSING || code == SUMMARY_PROCESSING

    /**
     * 그 `client_message_id` 는 탄 것이다. 다시 보내려면 **새 UUID** 여야 한다.
     * `TURN_FAILED` 와 503 `TURN_PERSISTENCE_FAILED` 의 `retry_with_fresh_client_message_id`.
     */
    val retryWithFreshClientMessageId: Boolean
        get() = code == TURN_FAILED || data["retry_with_fresh_client_message_id"] == "true"

    /** 요약 쪽 같은 규칙. `SUMMARY_REQUEST_ALREADY_FAILED` 와 503 의 플래그. */
    val retryWithFreshClientRequestId: Boolean
        get() = code == SUMMARY_REQUEST_ALREADY_FAILED ||
            data["retry_with_fresh_client_request_id"] == "true"

    /**
     * 이미 같은 대화 상태를 요약해 뒀다. **실패가 아니다** — 그 요약으로 가면 된다.
     * `SUMMARY_ALREADY_EXISTS` 일 때만 값이 있다.
     */
    val existingSummaryId: String? get() = if (code == SUMMARY_ALREADY_EXISTS) summaryId else null

    /** 이 대화의 강아지. `ACTIVE_DOG_MISMATCH` 일 때 온다 — 고른 강아지와 대화가 어긋났다. */
    val sessionPetId: String? get() = data["session_pet_id"]

    val turnId: String? get() = data["turn_id"]
    val summaryId: String? get() = data["summary_id"]

    /** `QUESTION_TOO_LONG` 의 글자 상한, `TURN_LIMIT_EXCEEDED` 의 개수 상한. */
    val limit: Int? get() = data["limit"]?.toIntOrNull()

    /** 저쪽이 왜 못 저장했는지 (`COMPLETION_CONFLICT` 같은). 사용자에게 보여 줄 말은 아니다. */
    val persistenceErrorCode: String? get() = data["persistence_error_code"]

    /** `TURN_FAILED` 에 실린, 그 turn 이 실패한 이유 코드. */
    val turnErrorCode: String? get() = data["error_code"]

    override fun toString(): String = "ChatApiError(status=$status, code=$code, keys=${data.keys})"

    companion object {
        const val ACTIVE_DOG_MISMATCH = "ACTIVE_DOG_MISMATCH"
        const val CLIENT_MESSAGE_ID_REUSED = "CLIENT_MESSAGE_ID_REUSED"
        const val TURN_PROCESSING = "TURN_PROCESSING"
        const val TURN_FAILED = "TURN_FAILED"
        const val TURN_LIMIT_EXCEEDED = "TURN_LIMIT_EXCEEDED"
        const val TRANSCRIPT_LIMIT_EXCEEDED = "TRANSCRIPT_LIMIT_EXCEEDED"
        const val TURN_PERSISTENCE_FAILED = "TURN_PERSISTENCE_FAILED"
        const val QUESTION_TOO_LONG = "QUESTION_TOO_LONG"
        const val CHAT_PERSISTENCE_APP_USER_ONLY = "CHAT_PERSISTENCE_APP_USER_ONLY"
        const val SUMMARY_ALREADY_EXISTS = "SUMMARY_ALREADY_EXISTS"
        const val SUMMARY_PROCESSING = "SUMMARY_PROCESSING"
        const val SUMMARY_REQUEST_ALREADY_FAILED = "SUMMARY_REQUEST_ALREADY_FAILED"
        const val SUMMARY_SOURCE_LIMIT_EXCEEDED = "SUMMARY_SOURCE_LIMIT_EXCEEDED"
        const val SUMMARY_PERSISTENCE_FAILED = "SUMMARY_PERSISTENCE_FAILED"

        /** 서버에 닿지 못했을 때. 연결 예외 메시지는 영어 한 줄이라 화면에 못 띄운다. */
        fun unreachable(message: String, cause: Throwable?): ChatApiError =
            ChatApiError(status = 0, code = null, message = message, cause = cause)

        /**
         * 200 대가 아닌 응답에서 만든다.
         *
         * 저쪽 봉투는 셋 중 하나다 — `detail` 이 **문장**(401·404·502·409 빈 대화),
         * **객체**(`{"code": ..., ...}`), **배열**(FastAPI 검증 422). 그리고 nginx 가
         * 60초에 끊으면 **HTML** 이 온다. 넷 다 여기서 받는다.
         *
         * @param fallback 문장도 코드도 없을 때 앱이 대신 하는 말. 호출하는 API 마다
         *   다르다 — 챗봇은 "오래 걸리고 있어요", 목록은 "서버 오류".
         */
        fun from(status: Int, body: String?, fallback: (Int) -> String): ChatApiError {
            val detail = runCatching { JSONObject(body.orEmpty()).opt("detail") }.getOrNull()
            return when (detail) {
                is String -> ChatApiError(status, code = null, message = detail.ifBlank { fallback(status) })
                is JSONObject -> {
                    val code = detail.optString("code").ifBlank { null }
                    val data = detail.keys().asSequence()
                        .filter { it != "code" }
                        .associateWith { detail.opt(it).toString() }
                    ChatApiError(status, code, sentenceFor(code, data) ?: fallback(status), data)
                }
                is JSONArray -> ChatApiError(status, code = null, message = VALIDATION)
                else -> ChatApiError(status, code = null, message = fallback(status))
            }
        }

        /**
         * 코드마다 사용자에게 할 말. **본문의 다른 문자열은 섞지 않는다** — 숫자 상한만 쓴다.
         *
         * 저쪽은 코드가 있는 오류에 문장을 안 준다 (앱이 갈래를 정하라는 뜻). 모르는
         * 코드면 null 이라 호출하는 쪽의 기본 문장으로 떨어진다 — 저쪽이 코드를 하나
         * 더 만드는 날 크래시가 아니라 일반 문구여야 한다.
         */
        private fun sentenceFor(code: String?, data: Map<String, String>): String? = when (code) {
            ACTIVE_DOG_MISMATCH -> "지금 고른 강아지와 이 대화의 강아지가 달라요. 대화를 다시 열어 주세요."
            CLIENT_MESSAGE_ID_REUSED -> "요청이 꼬였어요. 질문을 다시 보내 주세요."
            TURN_PROCESSING -> "아직 답을 만드는 중이에요. 잠시만 기다려 주세요."
            TURN_FAILED -> "지난 요청이 실패로 끝났어요. 질문을 다시 보내 주세요."
            TURN_LIMIT_EXCEEDED, TRANSCRIPT_LIMIT_EXCEEDED -> "이 대화는 가득 찼어요. 새 대화를 열어 주세요."
            TURN_PERSISTENCE_FAILED -> "답을 만들었지만 저장하지 못했어요. 질문을 다시 보내 주세요."
            QUESTION_TOO_LONG -> {
                val limit = data["limit"]?.toIntOrNull()
                if (limit != null) "질문이 너무 길어요. ${limit}자 안으로 줄여 주세요." else "질문이 너무 길어요."
            }
            CHAT_PERSISTENCE_APP_USER_ONLY -> "이 계정으로는 대화를 저장할 수 없어요."
            SUMMARY_ALREADY_EXISTS -> "이미 저장된 요약이 있어요."
            SUMMARY_PROCESSING -> "요약을 만드는 중이에요. 잠시만 기다려 주세요."
            SUMMARY_REQUEST_ALREADY_FAILED -> "지난 요약 요청이 실패로 끝났어요. 다시 시도해 주세요."
            SUMMARY_SOURCE_LIMIT_EXCEEDED -> "대화가 너무 길어 요약할 수 없어요."
            SUMMARY_PERSISTENCE_FAILED -> "요약을 만들었지만 저장하지 못했어요. 다시 시도해 주세요."
            else -> null
        }

        private const val VALIDATION = "요청 형식이 맞지 않아요. 앱을 업데이트해 주세요."
    }
}
