package com.daengs.app.ui.chat

import com.daengs.app.assistant.AssistantResponse

/**
 * [AssistantResponse] → 화면이 할 일. 순수 함수로 빼 둔 이유는 이 매핑 자체가
 * "로컬 키워드 라우팅을 안 한다"는 약속이라서다 — 여기서 보는 것은 서버가 준
 * `status`·`handoffs` 뿐이고, 사용자 원문 텍스트는 안 본다.
 */

/** 말풍선에 띄울 한 줄. CLARIFY 는 질문을 우선하고, 없으면 [AssistantResponse.message] 로 물러선다. */
internal fun AssistantResponse.bubbleMessage(): String =
    if (status == AssistantResponse.Status.CLARIFY) {
        clarify?.question ?: message
    } else {
        message
    }

/** 화면이 실제로 재사용할 수 있는 handoff 대상. 그 밖의 target 은 서버 메시지만 보여주고 끝난다. */
internal enum class KnownHandoff { GAIT, SKIN }

internal fun AssistantResponse.knownHandoff(): KnownHandoff? =
    handoffs.firstNotNullOfOrNull { handoff ->
        when (handoff.target) {
            "gait" -> KnownHandoff.GAIT
            "skin" -> KnownHandoff.SKIN
            else -> null
        }
    }
