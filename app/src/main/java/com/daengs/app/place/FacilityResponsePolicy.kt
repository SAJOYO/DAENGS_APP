package com.daengs.app.place

import com.daengs.app.place.bookmarks.BookmarkCompletion
import com.daengs.app.place.bookmarks.BookmarkOutcome
import java.text.Normalizer
import kotlinx.serialization.json.*

/** Last display boundary. Structured codes and identities are never changed by wording. */
object FacilityResponsePolicy {
    const val OUT_OF_SCOPE = "멍, 그건 잘 몰라요. 장소 찾는 건 맡겨줘요 🐾"
    const val PROCESSING_FAILED = "앗, 요청을 처리하지 못했어요. 다시 시도해 주세요 🐾"
    const val RESTORED = "검색을 다시 불러왔어요. 원하는 요청을 다시 말해 주세요."
    const val VIEW_CHANGED = "목록이 바뀌었어요. 지금 보이는 목록에서 다시 말해 주세요."
    const val UNKNOWN = "처리 결과를 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요."
    const val LOCATION = "주변을 찾으려면 현재 위치가 필요해요. 위치를 확인한 뒤 다시 요청해 주세요."
    private val internal = Regex(
        "(?<![a-z])(session|revision|receipt|snapshot|traceback|timeout|http|json|sql|llm|api)(?![a-z])" +
            "|[a-z]+_[a-z_]+|[0-9a-f]{8}-[0-9a-f-]{27,}" +
            "|세션|리비전|스냅샷|오케스트레|클라이언트|토큰|서버|원천\\s*정보|위도|경도|[{}<>`]",
        RegexOption.IGNORE_CASE,
    )
    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace(Regex("\\s+"), " ").trim()

    fun allowed(text: String, limit: Int = 160): Boolean {
        val value = normalized(text)
        return value.isNotBlank() && value.length <= limit && !internal.containsMatchIn(value) &&
            value.split(Regex("(?<=[.!?])\\s+")).size <= 2
    }

    fun text(value: String, fallback: String = UNKNOWN, limit: Int = 160) =
        if (allowed(value, limit)) normalized(value) else fallback

    fun failure(status: Int): String = when (status) {
        0 -> "지금은 장소를 찾을 수 없어요. 잠시 뒤 다시 시도해 주세요."
        401, 403 -> "로그인한 뒤 다시 말해 주세요."
        409 -> VIEW_CHANGED
        410 -> "보던 검색이 오래됐어요. 다시 불러와 주세요."
        422 -> "원하는 장소나 조건을 다시 알려주세요."
        429 -> "요청이 몰렸어요. 잠시 뒤 다시 시도해 주세요."
        504 -> "찾는 데 시간이 걸리고 있어요. 잠시 뒤 다시 시도해 주세요."
        else -> UNKNOWN
    }

    fun answer(result: ConversationResult): String? {
        val receipt = result.receipt
        // A rejected proposal needs failure wording even with an old or absent answer.
        if (!result.failed && receipt["code"]?.jsonPrimitive?.content == "invalid_plan") return PROCESSING_FAILED
        val raw = result.answer ?: return null
        if (result.failed) return if (receipt["known_places"]?.jsonArray?.isNotEmpty() == true)
            "이미 아는 곳으로 반영했어요. 다시 찾지 못해 목록은 그대로예요."
        else "다시 찾지 못했어요. 보던 목록은 그대로예요."
        if (receipt["code"]?.jsonPrimitive?.content == "facility_out_of_scope") return OUT_OF_SCOPE
        val fallback = when {
            receipt["bookmark_command"]?.let { it != JsonNull } == true -> "찜 처리 결과를 확인하고 있어요."
            receipt["question"]?.jsonPrimitive?.content.orEmpty().isNotBlank() -> "원하는 조건을 짧게 나눠서 알려주세요."
            receipt["goal"]?.jsonPrimitive?.content == "edit_only" ->
                if (receipt["filters_changed"]?.jsonPrimitive?.boolean == true) "조건을 바꿨고 목록은 그대로예요." else "이미 적용된 조건이에요."
            receipt["goal"]?.jsonPrimitive?.content == "pick_one" && result.selected != null -> "한 곳 골라뒀어요!"
            else -> "장소 정보는 카드에서 확인해 주세요."
        }
        return text(raw, fallback, if (receipt["pending_id"]?.let { it != JsonNull } == true) 300 else 160)
    }

    fun bookmark(outcome: BookmarkOutcome, saved: Boolean): String = when (outcome.completion) {
        BookmarkCompletion.CONFIRMED -> {
            val current = if (saved) "현재 찜에 저장돼 있어요." else "현재 찜에서 해제돼 있어요."
            val applied = if (saved) "여기 찜해뒀어요!" else "찜에서 빼뒀어요."
            outcome.message.takeIf { it == current || it == applied } ?: current
        }
        BookmarkCompletion.SUPERSEDED -> "이전 찜 요청은 멈췄어요. 현재 찜 상태를 확인해 주세요."
        BookmarkCompletion.UNKNOWN -> "찜 처리 결과를 확인하지 못했어요. 찜 목록을 새로고침해 주세요."
        BookmarkCompletion.FAILED -> if (outcome.message.contains(Regex("찜해뒀|저장돼|저장했|해제됐|해제했|빼뒀")))
            "찜을 변경하지 못했어요. 잠시 뒤 다시 시도해 주세요."
        else text(outcome.message, "찜을 변경하지 못했어요. 잠시 뒤 다시 시도해 주세요.")
    }
}
