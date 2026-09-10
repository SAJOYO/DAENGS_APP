package com.daengs.app.walk.diary

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Versioned geo candidate contract. User prose and visibility are stored separately. */
data class GeoStoryboardBundle(
    val sessionId: String, val sourceRevision: String, val synthetic: Boolean,
    val scenes: List<StoryboardScene>, val rawJson: String,
    val selection: StoryboardSelection? = null,
    val title: String? = null,
    val diary: DiaryGenerationInfo? = null,
) {
    companion object {
        const val FORMAT = "walk-storyboard-candidates-v1"
        const val FORMAT_V2 = "walk-storyboard-candidates-v2"
        const val FORMAT_V3 = "walk-storyboard-candidates-v3"
        const val FORMAT_V4 = "walk-storyboard-candidates-v4"
        const val FORMAT_V5 = "walk-storyboard-candidates-v5"
        fun parse(text: String): GeoStoryboardBundle {
            require(text.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "장면 파일이 너무 커요." }
            val obj = JSONObject(text)
            if (obj.optString("format") == ServerDiaryBundle.RESPONSE) return ServerDiaryBundle.parse(text)
            val v5 = obj.getString("format") == FORMAT_V5
            val v4 = v5 || obj.getString("format") == FORMAT_V4
            val v3 = v4 || obj.getString("format") == FORMAT_V3
            val v2 = v3 || obj.getString("format") == FORMAT_V2
            require(v2 || obj.getString("format") == FORMAT) { "지원하지 않는 장면 형식이에요." }
            obj.exactKeys(*(listOf("format", "session_id", "source_revision", "synthetic", "scenes") +
                (if (v2) listOf("selection") else emptyList()) +
                (if (v3) listOf("title", "title_fact_ids") else emptyList())).toTypedArray())
            val session = obj.requiredText("session_id", 128)
            val revision = obj.requiredText("source_revision", 100)
            require(obj.get("synthetic") is Boolean)
            val array = obj.getJSONArray("scenes")
            require(array.length() in 1..250)
            val scenes = (0 until array.length()).map { i -> parseScene(array.getJSONObject(i), v2, v4, v5, obj.getBoolean("synthetic")) }
            require(scenes.map { it.id }.distinct().size == scenes.size) { "장면 ID가 중복됐어요." }
            require(scenes.zipWithNext().all { (a, b) -> a.atMillis <= b.atMillis }) {
                "장면이 시간순으로 정렬되지 않았어요."
            }
            val title = if (!v3 || obj.isNull("title")) null else obj.requiredText("title", 40).also {
                require(it == it.trim() && it.none { c -> c in "\n\r\t" })
            }
            if (v4) {
                val end = (0 until array.length()).maxOf { Instant.parse(array.getJSONObject(it).getString("ended_at")).toEpochMilli() }
                require(scenes.all { it.observation == null || it.observation.atMillis in scenes.first().atMillis..end })
            }
            if (v3) {
                val refs = obj.getJSONArray("title_fact_ids").strings()
                val facts = (0 until array.length()).flatMap { i ->
                    val items = array.getJSONObject(i).getJSONArray("facts")
                    (0 until items.length()).map { items.getJSONObject(it) }
                        .filter { it.getString("kind") != "coverage" }.map { it.getString("id") }
                }.toSet()
                require(refs.size <= 8 && refs.all { it in facts } && (title != null) == refs.isNotEmpty())
            }
            return GeoStoryboardBundle(session, revision, obj.getBoolean("synthetic"), scenes, text,
                if (v2) StoryboardSelection.parse(obj.getJSONObject("selection")) else null, title)
        }

        private fun parseScene(obj: JSONObject, v2: Boolean, v4: Boolean, v5: Boolean, synthetic: Boolean): StoryboardScene {
            obj.exactKeys(*(listOf("id", "revision", "started_at", "ended_at", "route", "reasons", "title", "facts", "sources") +
                (if (v2) listOf("entry") else emptyList()) +
                (if (v4) listOf("observation") else emptyList()) +
                (if (v5) listOf("pin") else emptyList())).toTypedArray())
            val id = obj.requiredText("id", 100)
            val revision = obj.requiredText("revision", 100)
            val start = Instant.parse(obj.getString("started_at")).toEpochMilli()
            val end = Instant.parse(obj.getString("ended_at")).toEpochMilli()
            require(end >= start) { "장면 시간 범위가 뒤집혔어요." }
            val reasons = obj.getJSONArray("reasons").strings()
            require(reasons.isNotEmpty() && reasons.all { it in REASONS })
            val entry = if (!v2 || obj.isNull("entry")) null else obj.getJSONObject("entry").let {
                it.exactKeys("entry_id", "revision", "pet_id")
                val entryRevision = if (it.isNull("revision")) null else it.strictLong("revision", 1, Long.MAX_VALUE)
                require(synthetic || entryRevision != null)
                StoryboardEntryReference(it.requiredText("entry_id", 100), entryRevision,
                    if (it.isNull("pet_id")) null else it.requiredText("pet_id", 128), "note" in reasons)
            }
            val route = if (obj.isNull("route")) null else obj.getJSONObject("route").also {
                it.exactKeys("start_m", "end_m", "block_id")
                val from = it.getDouble("start_m"); val to = it.getDouble("end_m")
                require(from.isFinite() && to.isFinite() && from >= 0 && to >= from)
                if (!it.isNull("block_id")) {
                    val block = it.getDouble("block_id")
                    require(block.isFinite() && block >= 0 && block % 1.0 == 0.0)
                }
            }
            val observation = if (!v4 || obj.isNull("observation")) null else
                StoryboardObservation.parse(obj.getJSONObject("observation")).also {
                    require(entry == null && "observation_gap" !in reasons)
                    require("session_boundary" in reasons || it.atMillis in start..end)
                }
            val pin = if (!v5 || obj.isNull("pin")) null else StoryboardPin.parse(obj.getJSONObject("pin")).also {
                require(entry != null && observation == null && it.targetAtMillis == start)
            }
            val sources = obj.getJSONArray("sources")
            require(sources.length() in 1..20)
            val sourceIds = mutableSetOf<String>()
            val sourceTexts = (0 until sources.length()).map { i ->
                val source = sources.getJSONObject(i)
                source.exactKeys("id", "provider", "status", "captured_at", "url")
                require(sourceIds.add(source.requiredText("id", 100)))
                val provider = source.requiredText("provider", 100)
                val status = source.requiredText("status", 80)
                val captured = if (source.isNull("captured_at")) null else
                    source.getString("captured_at").also { Instant.parse(it) }
                val url = if (source.isNull("url")) null else source.requiredText("url", 500)
                listOfNotNull("$provider · $status", captured?.let { "조회 시각 $it" }, url).joinToString("\n")
            }
            val facts = obj.getJSONArray("facts")
            require(facts.length() in 1..40)
            if (v2) require((entry != null) == (0 until facts.length()).any {
                facts.getJSONObject(it).getString("kind") in setOf("action", "note")
            })
            val factIds = mutableSetOf<String>()
            val texts = (0 until facts.length()).map { i ->
                val fact = facts.getJSONObject(i)
                fact.exactKeys("id", "kind", "text", "source_ids")
                require(factIds.add(fact.requiredText("id", 120)))
                require(fact.getString("kind") in setOf("walk", "action", "note", "movement", "environment", "coverage"))
                val refs = fact.getJSONArray("source_ids").strings()
                require(refs.size <= 20 && refs.all { it in sourceIds }) { "사실에 연결된 출처가 없어요." }
                fact.requiredText("text", 2000)
            }
            val evidence = buildString {
                append("선택 이유: "+reasons.joinToString(" · ") { REASONS.getValue(it) })
                append("\n시간: ${obj.getString("started_at")} ~ ${obj.getString("ended_at")}")
                route?.let { append("\n수용 경로: ${it.getDouble("start_m").toInt()}~${it.getDouble("end_m").toInt()}m") }
                append("\n\n관측 사실\n"+texts.joinToString("\n"))
                append("\n\n출처\n"+sourceTexts.joinToString("\n\n"))
                observation?.let { append("\n\n지도 위치: 원본 관측 ${it.clientSeq} · ${Instant.ofEpochMilli(it.atMillis)}") }
                pin?.let { append("\n\n행동 위치: ${it.label} · 핀 버전 ${it.revision}") }
            }
            // Include actual payload as well as producer revision to detect incorrectly reused revisions.
            return StoryboardScene("geo:$id", start, obj.requiredText("title", 80),
                texts.joinToString("\n"), evidence, storyboardHash(revision+canonicalJson(obj)),
                sourcePayload = canonicalJson(obj), entryReference = entry, observation = observation)
        }
    }
}

private val REASONS = mapOf("session_boundary" to "산책 시작·종료", "action" to "액션 핀",
    "note" to "메모", "profile_change" to "과거 대비 이동 변화", "session_speed" to "세션 속도 변화",
    "distance_fill" to "거리 보충", "observation_gap" to "관측 공백")

internal fun JSONObject.requiredText(key: String, max: Int): String {
    require(get(key) is String)
    return getString(key).also { require(it.isNotBlank() && it.length <= max) }
}
internal fun JSONObject.exactKeys(vararg expected: String) {
    require(keys().asSequence().toSet() == expected.toSet()) { "장면 필드 구성이 계약과 달라요." }
}

internal fun JSONObject.strictLong(key: String, min: Long, max: Long): Long {
    require(get(key) is Number)
    val number = java.math.BigDecimal(get(key).toString()).longValueExact()
    require(number in min..max)
    return number
}
private fun JSONArray.strings(): List<String> = (0 until length()).map {
    require(get(it) is String); getString(it)
}
internal fun canonicalJson(value: Any?): String = when (value) {
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it)+":"+canonicalJson(value.get(it))
    }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalJson(value.get(it)) }
    is String -> JSONObject.quote(value)
    is Number -> java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
    null, JSONObject.NULL -> "null"
    else -> value.toString()
}
