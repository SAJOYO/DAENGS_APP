package com.daengs.app.walk.diary

import org.json.JSONArray
import org.json.JSONObject

/** A read-only experiment result; never converted into the published diary cache. */
data class DiarySlotPreview(
    val clientSessionId: String,
    val title: String,
    val revision: String,
    val modelStatus: String,
    val failureCode: String?,
    val contextPending: Boolean,
    val excludedBackgroundCount: Int,
    val scenes: List<DiarySlotScene>,
) {
    companion object {
        fun parse(response: JSONObject): DiarySlotPreview {
            val preview = response.getJSONObject("preview")
            require(preview.getString("format") == "walk-diary-slots-preview-v1")
            val board = preview.getJSONObject("base_board")
            val originals = board.getJSONArray("scenes").objects()
            val stamps = preview.getJSONArray("stamps").objects()
            val scenes = preview.getJSONArray("scenes").objects()
            val ids = scenes.map { it.getString("id") }
            require(ids.isNotEmpty() && ids.distinct().size == ids.size)
            require(originals.map { it.getString("id") } == ids)
            require(stamps.map { it.getString("scene_id") }.toSet() == ids.toSet() && stamps.size == ids.size)
            val status = preview.getString("model_status")
            require(status in setOf("not_requested", "accepted", "unavailable"))
            val failure = preview.nullableText("failure_code")
            require((status == "unavailable") == (failure != null))
            val citations = preview.getJSONObject("citations")
            require(citations.keys().asSequence().all { it in ids })
            return DiarySlotPreview(
                board.getString("client_session_id"), board.getString("title"), preview.getString("revision"),
                status, failure, response.getBoolean("context_pending"),
                response.getJSONArray("excluded_backgrounds").length(),
                scenes.mapIndexed { index, scene ->
                    require(scene.getInt("order") == index + 1)
                    val id = scene.getString("id")
                    val stamp = stamps.single { it.getString("scene_id") == id }
                    val evidence = stamp.getJSONArray("evidence").objects().map(DiarySlotEvidence::parse)
                    val location = stamp.optJSONObject("location_reference")?.let(DiarySlotEvidence::parse)
                    val cited = citations.optJSONArray(id)?.let { array ->
                        (0 until array.length()).map { array.getString(it) }.toSet()
                    }.orEmpty()
                    require(cited.all { citation -> (evidence + listOfNotNull(location)).any { it.id == citation } })
                    DiarySlotScene(id, scene.getString("title"), scene.getString("body"),
                        originals[index].getString("body"), evidence, location, cited)
                },
            )
        }
    }
}

data class DiarySlotScene(
    val id: String,
    val title: String,
    val body: String,
    val baseBody: String,
    val evidence: List<DiarySlotEvidence> = emptyList(),
    val locationReference: DiarySlotEvidence? = null,
    val citations: Set<String> = emptySet(),
)

data class DiarySlotEvidence(val id: String, val part: String, val role: String, val facts: String) {
    companion object {
        fun parse(json: JSONObject): DiarySlotEvidence {
            val part = json.getString("part")
            require(part in setOf("space", "environment", "motion"))
            return DiarySlotEvidence(json.getString("id"), part, json.getString("role"),
                json.getJSONObject("facts").toString())
        }
    }
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.nullableText(key: String) = if (isNull(key)) null else getString(key)
