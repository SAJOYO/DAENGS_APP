package com.daengs.app.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
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

// ── 마크다운 부분집합 ───────────────────────────────────────────────────────
//
// 이건 마크다운 엔진이 아니다. 실제로 관찰된 답변에 쓰인 것만 다룬다 — 굵게,
// 목록(`*`/`-`), 제목(`#`), 인용 표기(`[1]`)와 줄바꿈. 표·이미지·링크·코드
// 펜스는 지금 답변에 안 나온다.
//
// 서버 문자열은 손대지 않는다([AssistantApi] 는 원문 그대로 돌려준다) — 이
// 함수는 화면에 그릴 때만 부른다. 그래서 [ChatEntry.Mine]·구조화 카드
// (스크리닝·보행)에는 안 쓴다, [AssistantBubble] 에만 쓴다.

private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
private val LIST_ITEM = Regex("^[*-]\\s+(.*)$")
private val HEADING = Regex("^#{1,6}\\s+(.*)$")

/**
 * 서버가 준 답변 문자열을 [AssistantBubble] 이 그릴 [AnnotatedString] 으로 바꾼다.
 *
 * 줄 단위로 본다 — 제목·목록은 줄 앞머리에서만 뜻을 가지므로. 안 닫힌 `**` 처럼
 * 못 알아보는 표기는 **지우지 않고 그대로 남긴다** — 내용을 지우는 쪽보다
 * 마커가 한두 개 남는 쪽이 안전하다.
 */
internal fun assistantMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { index, line ->
        val heading = HEADING.matchEntire(line)
        val listItem = LIST_ITEM.matchEntire(line)
        when {
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendWithBold(heading.groupValues[1])
            }
            listItem != null -> {
                append("• ")
                appendWithBold(listItem.groupValues[1])
            }
            else -> appendWithBold(line)
        }
        if (index != lines.lastIndex) append("\n")
    }
}

/** `**강조**` 만 굵게 바꾸고 마커는 지운다. 짝이 안 맞으면 원문을 그대로 남긴다. */
private fun AnnotatedString.Builder.appendWithBold(line: String) {
    var cursor = 0
    for (match in BOLD.findAll(line)) {
        append(line.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        cursor = match.range.last + 1
    }
    append(line.substring(cursor))
}
