package com.daengs.app.assistant

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * 산책 판정. 저쪽 `SAJOYO/DAENGS_dev` 의 `daengs_life/app/dto/walk.py` `WalkOut` 중
 * **화면이 그리는 것만** 옮긴 것이다.
 *
 * ### 왜 [AssistantResponse.message] 로 안 되나
 *
 * 저쪽 `orchestration/aggregate.py` 의 `_result_message()` 는 능력이 준 `answer`
 * 문자열을 그대로 통과시키는데, **산책만 그 키가 없다** — 구조화된 판정이라서다.
 * 그래서 그 자리에서 `f"현재 산책 판단: {grade}"` 로 한 줄을 지어낸다. 등급 말고는
 * 아무것도 안 남아서 왜 그런지도, 언제 나가면 좋은지도 화면이 그릴 수가 없다.
 *
 * ### 이건 집계를 두 번 하는 게 아니다
 *
 * [AssistantResponse] 가 `results` 를 안 옮긴 이유는 "저쪽 결정론적 집계를 앱에서
 * 두 번째로 만들면 둘이 갈라진다" 였다. 그 원칙은 그대로다 — **판정은 여전히
 * 저쪽 것이다.** 등급을 매기는 규칙도, 어느 축이 깎았는지도, 추천 시간대를 고르는
 * 것도 서버가 하고, 여기서는 이미 나온 결론을 **읽어서 그리기만 한다.** 우리가
 * 계산하는 값은 하나도 없다.
 *
 * ### 저쪽 스키마에 묶인다
 *
 * 그 대가로 이 파일은 저쪽 DTO 를 따라간다. 그래서 [parse] 는 **모르는 값과 빠진
 * 필드를 전부 견딘다** — 등급이 처음 보는 문자열이면 [Grade.UNKNOWN] 이고, 시간대가
 * 이상하면 그 줄만 빠진다. 저쪽이 필드 하나 바꿨을 때 카드가 비는 정도로 끝나야지
 * 크래시가 나면 안 된다 ([AssistantResponse.Status] 가 `UNKNOWN` 을 두는 것과 같은 이유).
 */
@Immutable
data class WalkVerdict(
    val grade: Grade,
    /** 등급을 깎은 축의 한 줄들. `"체감온도 33.3℃ (여름식)"` 처럼 저쪽이 지은 문장이다. */
    val reasons: List<String>,
    /** 모르는 축이 있어 GOOD 에서 한 단계 깎였다는 표시. */
    val capped: Boolean,
    val windows: List<Window>,
    /** `"○○동 (측정소: △△) 기준"`. 저쪽이 만든 출처 표기라 그대로 쓴다. */
    val locationLabel: String,
    val notes: List<String>,
) {
    /**
     * `GradeName`. 저쪽은 `"unknown"` 만 소문자다 — 등급이 아니라 "판단 못 함" 이라서
     * 일부러 다르게 적어 둔 것이고, 여기서는 [UNKNOWN] 하나로 받는다.
     */
    enum class Grade { GOOD, CAUTION, UNSAFE, UNKNOWN }

    @Immutable
    data class Window(val from: LocalDateTime, val to: LocalDateTime, val grade: Grade)

    companion object {
        /** 저쪽 `CapabilityName.WALK`. */
        const val CAPABILITY = "walk"

        /**
         * `results` 배열에서 산책 결과를 찾아 옮긴다. 없으면 null 이다.
         *
         * **`results[0]` 이 산책이라고 가정하지 않는다.** 한 질문에 능력이 둘 이상
         * 돌면 배열에 여럿이 담기고 순서는 약속돼 있지 않다.
         */
        fun from(results: JSONArray?): WalkVerdict? {
            for (i in 0 until (results?.length() ?: 0)) {
                val result = results!!.optJSONObject(i) ?: continue
                if (result.optString("capability") != CAPABILITY) continue
                val data = result.optJSONObject("data") ?: continue
                return parse(data)
            }
            return null
        }

        /** `WalkOut` 한 덩어리. */
        fun parse(data: JSONObject): WalkVerdict {
            val now = data.optJSONObject("now")
            val axes = now?.optJSONObject("axes")
            return WalkVerdict(
                grade = now?.optString("grade").toGrade(),
                reasons = axes.notesFor(now?.optJSONArray("dominant").toStringList()),
                capped = now?.optBoolean("capped") == true,
                windows = data.optJSONArray("windows").toWindows(),
                locationLabel = data.optJSONObject("location")?.optString("label").orEmpty(),
                notes = data.optJSONArray("notes").toStringList(),
            )
        }

        private fun String?.toGrade(): Grade = when (this?.uppercase()) {
            "GOOD" -> Grade.GOOD
            "CAUTION" -> Grade.CAUTION
            "UNSAFE" -> Grade.UNSAFE
            else -> Grade.UNKNOWN
        }

        /**
         * 등급을 깎은 축의 `note` 들.
         *
         * **`dominant` 가 비면 축 전부를 쓴다.** 깎을 것이 없는 좋은 날에는 저쪽이
         * `dominant` 를 안 채우는데, 그때 이유 칸이 통째로 비면 카드가 등급만 있는
         * 지금과 같아진다. 온도·미세먼지·강수 한 줄씩은 좋은 날에도 볼 값이다.
         */
        private fun JSONObject?.notesFor(dominant: List<String>): List<String> {
            if (this == null) return emptyList()
            val keys = dominant.ifEmpty { keys().asSequence().toList() }
            return keys.mapNotNull { key ->
                optJSONObject(key)?.optString("note")?.takeIf { it.isNotBlank() }
            }
        }

        private fun JSONArray?.toWindows(): List<Window> = buildList {
            for (i in 0 until (this@toWindows?.length() ?: 0)) {
                val w = this@toWindows!!.optJSONObject(i) ?: continue
                val from = w.optString("from").toLocalDateTime() ?: continue
                val to = w.optString("to").toLocalDateTime() ?: continue
                add(Window(from = from, to = to, grade = w.optString("grade").toGrade()))
            }
        }

        /**
         * 저쪽은 오프셋이 붙은 ISO 로 준다(`2026-09-02T10:00:00+09:00`). 오프셋이 없는
         * 모양도 견디고, 그래도 안 되면 **그 줄만 버린다** — 시간 하나 때문에 카드가
         * 통째로 사라지면 안 된다.
         */
        private fun String?.toLocalDateTime(): LocalDateTime? {
            if (this.isNullOrBlank()) return null
            return try {
                OffsetDateTime.parse(this).toLocalDateTime()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(this)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }

        private fun JSONArray?.toStringList(): List<String> = buildList {
            for (i in 0 until (this@toStringList?.length() ?: 0)) {
                this@toStringList!!.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }
}
