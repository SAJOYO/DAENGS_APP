package com.daengs.app.chat

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * `POST /assistant/query` 를 **대화의 turn 으로 남기라는** 표시.
 *
 * 저쪽(`schemas/assistant.py`)은 `chat_session_id` 와 `client_message_id` 가 **함께**
 * 와야 저장하고, 둘 다 없으면 v0.0.0 그대로 무상태, 한쪽만 오면 422 다. 앱에서 그
 * 한쪽짜리를 만들 수 없게 **둘을 한 값에 묶었다** — 이 객체가 있으면 둘 다 있고,
 * 없으면(null) 둘 다 없다. 그래서 `AssistantApi.query` 에 인자를 둘 추가하지 않고
 * 하나만 추가했다.
 *
 * 둘 다 UUID 여야 한다. 아니면 서버가 422 로 질문을 통째로 버리므로 **보내기 전에**
 * 여기서 막는다 (`PetDraft.valid` 와 같은 자리).
 *
 * ⚠️ **`active_dog_id` 는 여기 없다.** 대표 강아지는 저장하든 안 하든 매 질의에 싣는
 * 값이라(PR #112 — 저쪽이 그 id 로 견종·나이를 읽는다) 저장 전용 id 와 생애가 다르다.
 * 여기 얹어 두면 무상태 질문은 그 값을 실을 자리가 없어져서, 같은 칸을 두 경로가 서로
 * 다르게 싣게 된다. 그래서 `AssistantApi.query` 의 `activeDogId` 로 따로 받는다.
 */
@Immutable
data class ChatPersistence(
    val sessionId: String,
    val clientMessageId: String,
) {
    init {
        require(sessionId.isUuid()) { "chat_session_id 가 UUID 가 아닙니다" }
        require(clientMessageId.isUuid()) { "client_message_id 가 UUID 가 아닙니다" }
    }

    private companion object {
        fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess
    }
}
