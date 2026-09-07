package com.daengs.app.assistant

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Place 능력의 `place-capability-v1` projection. 저쪽 `SAJOYO/DAENGS_dev` 의
 * `orchestration/adapters/_place_projection.py` `project_place_capability_data` 가 만드는
 * 것 중 **화면이 그리는 것만** 옮긴다 ([WalkVerdict] 와 같은 이유·같은 규칙).
 *
 * ### `place/PlaceModels.kt` 를 재사용하지 않는다
 *
 * 그건 `/v2/places/search` 계약을 파싱하고 `PlaceKind.fromWire` 가 `requireNotNull` 로
 * 죽는다. 여긴 다른 계약이고(사전 렌더링된 `facts[].text`, `groups[].lens_id`) 챗봇은
 * 모르는 값에 죽으면 안 된다. 재사용하는 것은 [com.daengs.app.map.features.places.formatPlaceMeters]
 * 하나뿐이다 (같은 모듈이라 `internal` 로도 접근된다).
 *
 * ### 저쪽이 이미 해석해 준 것만 옮긴다
 *
 * `source_state`·`evaluation_state`·`provenance` 는 안 옮긴다. 저쪽 `_project_candidate`
 * 가 이미 그 값들을 사람이 읽을 [Fact.text] 문장으로 접어 넣었다 — "확인되지 않았어요."
 * 처럼. 여기서 `evaluation_state == "unknown"` 을 보고 우리가 다시 "가능/불가능" 을
 * 판단하면, 안 물어본 것과 아니라고 답한 것을 섞어 긍정으로 잘못 뒤집을 길이 생긴다.
 * [Fact.severity] 만 톤을 고르는 데 쓴다 — 그건 저쪽이 이미 매긴 요약 신호다.
 *
 * ### 최대 3장 표시는 여기 없다
 *
 * 서버가 이미 lens 당 3개·전체 9개로 줄여 보낸다 (`_MAX_CANDIDATES_PER_GROUP`,
 * `_MAX_TOTAL_CANDIDATES`). 화면에 3장만 놓는 것은 **앱의 표시 제약**이라 여기서
 * 자르지 않는다 — 그건 카드를 고르는 [com.daengs.app.ui.chat.placeCards] 가 한다.
 */
@Immutable
data class PlaceSuggestions(
    /** 저쪽이 지은 답변 한 줄. [AssistantResponse.message] 에 이미 실려 오므로 카드는
     *  이 값을 다시 그리지 않는다 — 파서가 계약을 놓치지 않았는지 테스트에서만 본다. */
    val answer: String,
    val groups: List<Group>,
    /** 값 선택이 더 필요한 기준. **탭 가능한 액션으로 만드는 것은 이 카드 범위 밖이다.** */
    val refinements: List<Refinement>,
    /** `place.searched_around_current_location` 처럼 저쪽이 지은 고지 문장 그대로.
     *  앱이 다시 쓰거나 요약하지 않는다 — Option B 고지는 조건 없이 그대로 보여야 한다. */
    val notices: List<String>,
) {
    @Immutable
    data class PlaceId(val source: String, val ref: String)

    @Immutable
    data class Group(
        val lensId: String,
        val label: String,
        val supportNote: String,
        val candidates: List<Candidate>,
    )

    @Immutable
    data class Candidate(
        val placeId: PlaceId,
        val title: String,
        val summary: String,
        val kindLabel: String,
        /** 범위를 벗어나면(위도 ±90, 경도 ±180) null — 지도 액션이 이 값으로 [geo] intent 를 만든다. */
        val lat: Double?,
        val lon: Double?,
        val distanceMeters: Int?,
        val address: String,
        /** 저쪽이 이미 `promoted`→`detail`→`core` 순으로 정렬해 최대 5개까지 준다. 다시 정렬하지 않는다. */
        val facts: List<Fact>,
        val notices: List<Notice>,
        /** 이 후보가 왜 뽑혔는지. 저쪽 문장 그대로, 최대 2개. */
        val whyMatched: List<String>,
    )

    @Immutable
    data class Fact(val label: String, val text: String, val severity: Severity)

    @Immutable
    data class Notice(val message: String, val severity: Severity)

    @Immutable
    data class Refinement(
        val label: String,
        val required: Boolean,
        val supportNote: String,
        val options: List<Option>,
    )

    @Immutable
    data class Option(val label: String, val availability: String, val supportNote: String)

    /**
     * 저쪽 `severity`. 모르는 값이 오면 [UNKNOWN] 이다 — [WalkVerdict.Grade.UNKNOWN] 과
     * 같은 규칙으로, 새 등급이 하나 늘어도 카드가 죽으면 안 된다.
     */
    enum class Severity { INFO, WARNING, CRITICAL, UNKNOWN }

    companion object {
        /**
         * 저쪽 `CapabilityName.PLACE`. 배지 라벨 쪽에도 같은 값이 따로 적혀 있다
         * (`chat/ChatModels.kt` 의 `ChatCapability.PLACE`) — 뜻이 달라 안 묶었고,
         * 대신 `ChatModelsTest` 가 둘이 갈라지는지 지킨다. **한쪽만 고치지 말 것.**
         */
        const val CAPABILITY = "place"

        /**
         * `results` 배열에서 place 결과를 찾아 옮긴다. 없으면 null 이다.
         *
         * **`data` 가 없으면 null 이다** (`ERROR` 상태). `OK`·`ABSTAINED` 는 둘 다
         * `data` 를 들고 온다 — 후자도 `refinements`·`notices` 는 있을 수 있어서다.
         */
        fun from(results: JSONArray?): PlaceSuggestions? {
            for (i in 0 until (results?.length() ?: 0)) {
                val result = results!!.optJSONObject(i) ?: continue
                if (result.optString("capability") != CAPABILITY) continue
                val data = result.optJSONObject("data") ?: return null
                return parse(data)
            }
            return null
        }

        /** `place-capability-v1` 한 덩어리. */
        fun parse(data: JSONObject): PlaceSuggestions = PlaceSuggestions(
            answer = data.optString("answer"),
            groups = data.optJSONArray("groups").toGroups(),
            refinements = data.optJSONArray("refinements").toRefinements(),
            notices = data.optJSONArray("notices").toNoticeMessages(),
        )

        private fun String?.toSeverity(): Severity = when (this?.lowercase()) {
            "info" -> Severity.INFO
            "warning" -> Severity.WARNING
            "critical" -> Severity.CRITICAL
            else -> Severity.UNKNOWN
        }

        private fun JSONArray?.toGroups(): List<Group> = buildList {
            for (i in 0 until (this@toGroups?.length() ?: 0)) {
                val g = this@toGroups!!.optJSONObject(i) ?: continue
                add(
                    Group(
                        lensId = g.optString("lens_id"),
                        label = g.optString("label"),
                        supportNote = g.optString("support_note"),
                        candidates = g.optJSONArray("candidates").toCandidates(),
                    ),
                )
            }
        }

        private fun JSONArray?.toCandidates(): List<Candidate> = buildList {
            for (i in 0 until (this@toCandidates?.length() ?: 0)) {
                val c = this@toCandidates!!.optJSONObject(i) ?: continue
                val placeId = c.optJSONObject("place_id")
                val location = c.optJSONObject("location")
                add(
                    Candidate(
                        placeId = PlaceId(
                            source = placeId?.optString("source").orEmpty(),
                            ref = placeId?.optString("ref").orEmpty(),
                        ),
                        title = c.optString("title"),
                        summary = c.optString("summary"),
                        kindLabel = c.optJSONObject("kind")?.optString("label").orEmpty(),
                        lat = location?.optDoubleOrNull("lat")?.takeIf { it in -90.0..90.0 },
                        lon = location?.optDoubleOrNull("lon")?.takeIf { it in -180.0..180.0 },
                        distanceMeters = location?.optIntOrNull("distance_m"),
                        address = c.optString("address"),
                        facts = c.optJSONArray("facts").toFacts(),
                        notices = c.optJSONArray("notices").toNotices(),
                        whyMatched = c.optJSONArray("why_matched").toMessages(),
                    ),
                )
            }
        }

        private fun JSONArray?.toFacts(): List<Fact> = buildList {
            for (i in 0 until (this@toFacts?.length() ?: 0)) {
                val f = this@toFacts!!.optJSONObject(i) ?: continue
                add(Fact(label = f.optString("label"), text = f.optString("text"), severity = f.optString("severity").toSeverity()))
            }
        }

        private fun JSONArray?.toNotices(): List<Notice> = buildList {
            for (i in 0 until (this@toNotices?.length() ?: 0)) {
                val n = this@toNotices!!.optJSONObject(i) ?: continue
                add(Notice(message = n.optString("message"), severity = n.optString("severity").toSeverity()))
            }
        }

        private fun JSONArray?.toNoticeMessages(): List<String> = buildList {
            for (i in 0 until (this@toNoticeMessages?.length() ?: 0)) {
                val n = this@toNoticeMessages!!.optJSONObject(i) ?: continue
                n.optString("message").takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }

        private fun JSONArray?.toMessages(): List<String> = buildList {
            for (i in 0 until (this@toMessages?.length() ?: 0)) {
                val m = this@toMessages!!.optJSONObject(i) ?: continue
                m.optString("message").takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }

        private fun JSONArray?.toRefinements(): List<Refinement> = buildList {
            for (i in 0 until (this@toRefinements?.length() ?: 0)) {
                val r = this@toRefinements!!.optJSONObject(i) ?: continue
                add(
                    Refinement(
                        label = r.optString("label"),
                        required = r.optBoolean("required"),
                        supportNote = r.optString("support_note"),
                        options = r.optJSONArray("options").toOptions(),
                    ),
                )
            }
        }

        private fun JSONArray?.toOptions(): List<Option> = buildList {
            for (i in 0 until (this@toOptions?.length() ?: 0)) {
                val o = this@toOptions!!.optJSONObject(i) ?: continue
                add(
                    Option(
                        label = o.optString("label"),
                        availability = o.optString("availability"),
                        supportNote = o.optString("support_note"),
                    ),
                )
            }
        }

        private fun JSONObject.optDoubleOrNull(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            return optDouble(key, Double.NaN).takeUnless { it.isNaN() }
        }

        private fun JSONObject.optIntOrNull(key: String): Int? {
            if (!has(key) || isNull(key)) return null
            return optInt(key)
        }
    }
}
