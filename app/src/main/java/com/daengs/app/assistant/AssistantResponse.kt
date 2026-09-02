package com.daengs.app.assistant

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * `POST /assistant/query` 의 응답. 계약은 저쪽 `SAJOYO/DAENGS_dev` 의
 * `orchestration/contracts.py` `AssistantResponse` 다.
 *
 * **필드를 전부 옮기지 않는다.** `results`(능력별 실행 결과)는 대개 이미 `message` 에
 * 집계돼 있어 화면이 다시 조립할 필요가 없다 — 저쪽 결정론적 집계를 앱에서
 * 두 번째로 만들면 둘이 갈라진다.
 *
 * **산책만 예외다.** 훈련·생활은 `data.answer` 에 완성된 한국어 답변이 들어 있고
 * 저쪽이 그걸 그대로 `message` 로 통과시키지만, 산책에는 그 키가 없어서 등급 한 줄로
 * 뭉개진다. 그래서 산책 결과만 [walk] 로 옮긴다 — 이유는 [WalkVerdict] 에 적었다.
 * `results` 자체는 여전히 안 들고 있고, [walk] 도 집계가 아니라 **이미 나온 결론**이다.
 */
@Immutable
data class AssistantResponse(
    val requestId: String,
    val status: Status,
    val message: String,
    val handoffs: List<Handoff>,
    val clarify: Clarify?,
    /** 산책 판정. 산책 능력이 안 돌았으면 null 이다. */
    val walk: WalkVerdict? = null,
    /** 이번 응답에 담긴 능력 수. 하나뿐일 때만 [message] 를 카드로 갈음할 수 있다. */
    val resultCount: Int = 0,
) {
    /**
     * `AssistantStatus`. 모르는 값이 와도 앱이 죽지 않게 [UNKNOWN] 으로 떨어진다 —
     * 저쪽이 상태를 하나 더 추가하는 날 화면이 크래시하면 안 된다.
     */
    enum class Status {
        ANSWERED, PARTIAL, CLARIFY, HANDOFF, UNCERTAIN, REFUSED, PENDING, FAILED, UNKNOWN
    }

    @Immutable
    data class Handoff(val target: String, val reason: String)

    @Immutable
    data class Clarify(val question: String, val missing: List<String>)

    companion object {
        fun parse(json: JSONObject): AssistantResponse {
            val results = json.optJSONArray("results")
            return AssistantResponse(
                requestId = json.optString("request_id"),
                status = json.optString("status").toStatus(),
                message = json.optString("message"),
                handoffs = json.optJSONArray("handoffs").toHandoffs(),
                clarify = json.optJSONObject("clarify")?.toClarify(),
                walk = WalkVerdict.from(results),
                resultCount = results?.length() ?: 0,
            )
        }

        private fun String.toStatus(): Status =
            runCatching { Status.valueOf(this) }.getOrDefault(Status.UNKNOWN)

        private fun org.json.JSONArray?.toHandoffs(): List<Handoff> = buildList {
            for (i in 0 until (this@toHandoffs?.length() ?: 0)) {
                val h = this@toHandoffs!!.getJSONObject(i)
                add(Handoff(target = h.optString("target"), reason = h.optString("reason")))
            }
        }

        private fun JSONObject.toClarify(): Clarify = Clarify(
            question = optString("question"),
            missing = optJSONArray("missing").toStringList(),
        )

        private fun org.json.JSONArray?.toStringList(): List<String> = buildList {
            for (i in 0 until (this@toStringList?.length() ?: 0)) add(this@toStringList!!.optString(i))
        }
    }
}
