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
 * @property activeDogId 지금 고른 강아지. 대화의 강아지와 다르면 저쪽이 행을 쓰기 전에
 *   409 `ACTIVE_DOG_MISMATCH` 로 막는다 — **같은 값을 실어야 그 검사가 산다.** 안 실으면
 *   저쪽이 대화의 강아지를 그냥 쓰고, 고른 강아지와 어긋난 채 남의 대화에 쌓여도 모른다.
 */
@Immutable
data class ChatPersistence(
    val sessionId: String,
    val clientMessageId: String,
    val activeDogId: String?,
) {
    init {
        require(sessionId.isUuid()) { "chat_session_id 가 UUID 가 아닙니다" }
        require(clientMessageId.isUuid()) { "client_message_id 가 UUID 가 아닙니다" }
        // 빈 문자열은 저쪽 `_metadata_not_blank` 가 422 로 막는다. 없으면 칸을 뺀다.
        require(activeDogId == null || activeDogId.isNotBlank()) { "active_dog_id 가 비어 있습니다" }
    }

    private companion object {
        fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess
    }
}
