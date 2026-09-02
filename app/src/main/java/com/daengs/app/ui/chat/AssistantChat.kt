package com.daengs.app.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import com.daengs.app.assistant.AssistantResponse
import com.daengs.app.assistant.WalkVerdict

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

/**
 * 말풍선 아래 얹을 산책 카드. 없으면 null 이다.
 *
 * **등급을 모를 때는 카드를 안 띄운다.** 저쪽이 관측 자료가 모자라면 `ABSTAINED` 로
 * "현재 관측 자료만으로 산책 조건을 판단할 수 없습니다." 를 보내는데, 그건 판정이
 * 아니라 못 하겠다는 말이라 등급 칸과 시간대 칸이 있는 카드에 담을 것이 없다.
 * 그때는 저쪽 문장을 그대로 말풍선으로 보여주는 편이 정직하다.
 */
internal fun AssistantResponse.walkCard(): WalkVerdict? =
    walk?.takeIf { it.grade != WalkVerdict.Grade.UNKNOWN }

/**
 * 산책만 물었을 때 저쪽 한 줄을 갈음할 말풍선. 그 밖에는 null 이고, 그때는
 * [bubbleMessage] 를 그대로 쓴다.
 *
 * **여기는 챗봇이다.** 저쪽 `message` 는 `"현재 산책 판단: GOOD"` 인데 이건 사람이
 * 대화에서 들을 말이 아니다. 등급에 붙은 이름으로 바꿔 띄우고, 근거와 시간대는
 * 아래 카드가 맡는다.
 *
 * **문장을 지어내는 게 아니라 등급의 이름을 붙이는 것이다.** 매핑은 [walkSentenceOf]
 * 하나뿐이고 등급마다 한 줄씩 고정이다 — 값을 보고 말을 만들지 않는다.
 *
 * 능력이 둘 이상이면 갈음하지 않는다. 그때 `message` 는 `"[산책]\n…\n\n[훈련]\n…"`
 * 처럼 라벨을 붙여 이어붙은 것이라, 갈아 끼우면 **산책이 아닌 답변까지 사라진다.**
 */
internal fun AssistantResponse.walkSentence(): String? =
    walkCard()?.takeIf { resultCount <= 1 }?.let { walkSentenceOf(it.grade) }

internal fun walkSentenceOf(grade: WalkVerdict.Grade): String = when (grade) {
    WalkVerdict.Grade.GOOD -> "지금은 산책하기 좋아요."
    WalkVerdict.Grade.CAUTION -> "나가도 되지만 조심하는 게 좋아요."
    WalkVerdict.Grade.UNSAFE -> "지금은 안 나가는 게 좋겠어요."
    // [walkCard] 가 막아서 여기까지 오지 않는다. 그래도 말할 것은 둔다.
    WalkVerdict.Grade.UNKNOWN -> "지금 자료로는 판단하기 어려워요."
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

// 들여쓰기를 첫 번째 그룹으로 잡는다 — 안 그러면 중첩 항목("    * 세부")이 줄
// 앞머리부터 시작하지 않아 매치가 안 되고 별표가 그대로 보인다.
private val LIST_ITEM = Regex("^(\\s*)[*-]\\s+(.*)$")
private val HEADING = Regex("^#{1,6}\\s+(.*)$")

/** 입력 공백 몇 칸을 한 단계 들여쓰기로 볼지. 4칸 들여쓰기가 실제 관찰된 값이다. */
private const val INDENT_WIDTH = 4

/** 한 단계 들여쓰기의 출력 폭. 전체 중첩 엔진을 만들지 않고 시각적 위계만 준다. */
private const val NEST_INDENT = "    "

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
                val level = listItem.groupValues[1].length / INDENT_WIDTH
                append(NEST_INDENT.repeat(level))
                append("• ")
                appendWithBold(listItem.groupValues[2])
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
