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
 * ### `notes` 는 안 옮긴다
 *
 * 저쪽 `_notes()` 는 "사용자가 알아야 할 한계" 라고 적혀 있지만 실제로 나오는 문구는
 * `"대기질 미래 구간은 권역 일 예보 기준이다 (③-b — 관측값의 지속 가정은 기각했다)"`,
 * `"특보구역 매핑표가 아직 없어 시도 단위로만 특보를 찾는다"` 처럼 **명세 절 번호가
 * 박힌 방법론 각주**다. 판정을 어떻게 냈는지에 대한 기록이지 강아지를 데리고 나갈지
 * 정하는 데 쓸 말이 아니라, 대화창에 띄우면 읽을 수 없는 줄만 늘어난다.
 *
 * 같은 성격의 한계 중 **사용자가 실제로 쓸 수 있는 것은 이미 다른 자리에 있다** —
 * 모르는 축 때문에 등급이 깎였다는 사실은 [capped] 로, 어느 측정소 값인지는
 * [locationLabel] 로 온다.
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
    /**
     * 축별 판정. 기온 · 미세먼지 · 비 순으로, **좋은 축도 빠지지 않는다.**
     *
     * 예전에는 `dominant`(등급을 깎은 축)만 옮겼는데, 그러면 좋은 날에 줄 수가 들쭉날쭉
     * 하고 "비는 어떤데?" 를 알 수가 없다. 어느 축이 문제인지는 [Axis.grade] 가 이미 말한다.
     */
    val axes: List<Axis>,
    /** 모르는 축이 있어 GOOD 에서 한 단계 깎였다는 표시. */
    val capped: Boolean,
    val windows: List<Window>,
    /**
     * 앞으로 24시간의 시간별 등급.
     *
     * **되묻기를 대신한다.** v1 오케스트레이션은 무상태라 "3시간 후에는?" 이 앞 문답을
     * 모르는 새 질문으로 가고, 서버는 다시 "지금" 을 판정해 같은 답을 준다. 그 값이
     * 이미 이 배열에 들어 있으니 화면에 펴 두면 사용자가 되묻지 않아도 된다.
     */
    val timeline: List<Point>,
    /** `"○○동 (측정소: △△) 기준"`. 저쪽이 만든 출처 표기라 그대로 쓴다. */
    val locationLabel: String,
) {
    /**
     * `GradeName`. 저쪽은 `"unknown"` 만 소문자다 — 등급이 아니라 "판단 못 함" 이라서
     * 일부러 다르게 적어 둔 것이고, 여기서는 [UNKNOWN] 하나로 받는다.
     */
    enum class Grade { GOOD, CAUTION, UNSAFE, UNKNOWN }

    /**
     * 좋은 시간이 이어지는 구간.
     *
     * **[to] 는 "마지막으로 좋은 시각" 이지 구간의 끝이 아니다.** 저쪽 `windows()` 가
     * 그다음 판정까지 좋다고 말하지 않으려고 그렇게 준다. 그래서 좋은 시점이 하나뿐이면
     * [from] 과 [to] 가 같고, 그건 범위가 아니라 **한 시점**이다 ([isPoint]).
     */
    @Immutable
    data class Window(val from: LocalDateTime, val to: LocalDateTime, val grade: Grade) {
        val isPoint: Boolean get() = from == to
    }

    /** 시간별 판정 한 점. */
    @Immutable
    data class Point(val at: LocalDateTime, val grade: Grade)

    /**
     * 축 하나의 판정.
     *
     * **[grade] 를 [note] 보다 앞에 둔다.** `"PM10 42㎍/㎥"` 는 아는 사람에게만 근거고
     * 대부분은 그 숫자로 판단을 못 한다. 그런데 그 해석은 저쪽이 이미 해서 축마다
     * 등급을 붙여 놨다 — 우리가 기준을 새로 만들 필요 없이 그걸 앞세우면 된다.
     *
     * @param label 사람이 읽을 축 이름. `heat` 를 "더위" 로 안 적는다 — 그 축은 한파도
     *   같이 보는 자리라 겨울에 틀린 말이 된다.
     * @param note 저쪽이 지은 한 줄. 그대로 쓴다.
     */
    @Immutable
    data class Axis(val key: String, val label: String, val grade: Grade, val note: String)

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
                axes = axes.toAxes(),
                capped = now?.optBoolean("capped") == true,
                windows = data.optJSONArray("windows").toWindows(),
                timeline = data.optJSONArray("timeline").toTimeline(),
                locationLabel = data.optJSONObject("location")?.optString("label").orEmpty(),
            )
        }

        private fun String?.toGrade(): Grade = when (this?.uppercase()) {
            "GOOD" -> Grade.GOOD
            "CAUTION" -> Grade.CAUTION
            "UNSAFE" -> Grade.UNSAFE
            else -> Grade.UNKNOWN
        }

        /**
         * 사람이 읽을 축 이름. 저쪽 `Axis` 는 `heat`·`air`·`rain` 셋이다.
         *
         * 모르는 키가 오면 키를 그대로 이름으로 쓴다 — 저쪽이 축을 하나 더 만드는 날
         * 그 줄이 사라지는 것보다 영어로라도 보이는 편이 낫다.
         */
        private val AXIS_LABELS = mapOf(
            "heat" to "기온",
            "air" to "미세먼지",
            "rain" to "비",
        )

        /**
         * 축별 판정. **좋은 축도 뺀 것 없이 [AXIS_LABELS] 순서로** 돌려준다.
         *
         * 저쪽이 준 순서(JSON 객체 키 순서)를 따르지 않는다 — 그건 약속된 것이 아니라
         * 매번 달라지면 같은 카드가 볼 때마다 다르게 보인다.
         */
        private fun JSONObject?.toAxes(): List<Axis> {
            if (this == null) return emptyList()
            val known = AXIS_LABELS.keys
            val rest = keys().asSequence().filterNot { it in known }.sorted()
            return (known.asSequence() + rest).mapNotNull { key ->
                val axis = optJSONObject(key) ?: return@mapNotNull null
                Axis(
                    key = key,
                    label = AXIS_LABELS[key] ?: key,
                    grade = axis.optString("grade").toGrade(),
                    note = axis.optString("note").orEmpty(),
                )
            }.toList()
        }

        private fun JSONArray?.toWindows(): List<Window> = buildList {
            for (i in 0 until (this@toWindows?.length() ?: 0)) {
                val w = this@toWindows!!.optJSONObject(i) ?: continue
                val from = w.optString("from").toLocalDateTime() ?: continue
                val to = w.optString("to").toLocalDateTime() ?: continue
                add(Window(from = from, to = to, grade = w.optString("grade").toGrade()))
            }
        }

        private fun JSONArray?.toTimeline(): List<Point> = buildList {
            for (i in 0 until (this@toTimeline?.length() ?: 0)) {
                val p = this@toTimeline!!.optJSONObject(i) ?: continue
                val at = p.optString("at").toLocalDateTime() ?: continue
                add(Point(at = at, grade = p.optString("grade").toGrade()))
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
