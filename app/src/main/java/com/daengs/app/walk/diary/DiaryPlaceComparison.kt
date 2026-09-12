package com.daengs.app.walk.diary

import org.json.JSONArray
import org.json.JSONObject

/** Exact visible scenes, including user edits and their order. No credentials or photo bytes. */
internal data class DiaryComparisonSnapshot(
    val json: String, val digest: String, val scenes: List<DiaryScene>, val ownerId: String, val sessionId: String,
) {
    companion object {
        fun create(owner: String, sessionId: String, scenes: List<DiaryScene>): DiaryComparisonSnapshot {
            require(owner.isNotBlank() && sessionId.isNotBlank() && scenes.isNotEmpty())
            require(scenes.size <= 12 && scenes.all { it.sessionId == sessionId })
            require(scenes.map { it.id }.distinct().size == scenes.size)
            val payload = JSONObject().put("format", "diary-scene-comparison-input-v2")
                .put("owner_id", owner).put("session_id", sessionId)
                .put("scenes", JSONArray(scenes.map { scene ->
                    JSONObject().put("id", scene.id).put("at_millis", scene.atMillis)
                        .put("title", scene.title).put("body", scene.body)
                        .put("point", scene.point?.let { JSONArray(listOf(it.latitude, it.longitude)) } ?: JSONObject.NULL)
                        .put("entry_id", scene.entryId ?: JSONObject.NULL)
                        .put("photo_id", scene.photo?.id ?: JSONObject.NULL)
                        .put("location_basis", scene.evidence)
                        .put("source_scene", scene.source?.sourcePayload?.let { JSONObject(it) } ?: JSONObject.NULL)
                }))
            val text = canonicalJson(payload)
            return DiaryComparisonSnapshot(text, storyboardHash(text), scenes.toList(), owner, sessionId)
        }
    }
}

internal data class DiaryPlaceNarration(
    val background: String, val evidence: List<String>, val coverage: String = "",
)

/** Temporary display only. It has no conversion to the published diary or user-edit store. */
internal data class DiaryPlaceComparison(
    val snapshotDigest: String,
    val model: String,
    val retrievedAt: String,
    val narrations: Map<String, DiaryPlaceNarration>,
) {
    fun project(snapshot: DiaryComparisonSnapshot, usePlaces: Boolean): List<DiaryScene> {
        require(snapshot.digest == snapshotDigest) { "장면이 바뀌어 비교 자료를 다시 준비해야 해요." }
        return snapshot.scenes.map { scene ->
            val background = narrations.getValue(scene.id).background
            if (!usePlaces || background.isBlank()) scene
            else scene.copy(body = background + if (scene.body.isEmpty()) "" else "\n\n${scene.body}")
        }
    }

    companion object {
        fun parse(text: String, snapshot: DiaryComparisonSnapshot): DiaryPlaceComparison {
            require(text.toByteArray(Charsets.UTF_8).size <= 256_000)
            val root = JSONObject(text)
            require(root.getString("format") == "diary-scene-comparison-result-v2")
            require(root.getString("snapshot_sha256") == snapshot.digest) {
                "현재 계정·산책·장면과 다른 비교 결과예요. 장면을 다시 준비해 주세요."
            }
            require(root.getString("model_status") == "accepted") { "장면 설명 생성이 완료되지 않았어요." }
            val items = root.getJSONArray("scenes")
            require(items.length() == snapshot.scenes.size)
            val narrations = snapshot.scenes.mapIndexed { index, scene ->
                val item = items.getJSONObject(index)
                require(item.getString("id") == scene.id) { "비교 결과의 장면이나 순서가 달라요." }
                val background = item.getString("background").trim()
                require(background.length <= 220)
                val evidence = item.getJSONArray("evidence")
                require(evidence.length() <= 17)
                val facts = (0 until evidence.length()).associate { i ->
                    val fact = evidence.getJSONObject(i)
                    fact.getString("id") to fact.getString("description").also { require(it.length <= 2000) }
                }
                require(facts.size == evidence.length())
                val refs = item.getJSONArray("evidence_ids")
                val ids = (0 until refs.length()).map { refs.getString(it) }
                require(ids.distinct().size == ids.size && ids.all { it in facts })
                require(background.isNotBlank() == ids.isNotEmpty())
                val coverage = item.getString("coverage").also { require(it.length <= 2000) }
                scene.id to DiaryPlaceNarration(background, ids.map(facts::getValue), coverage)
            }.toMap()
            return DiaryPlaceComparison(snapshot.digest, root.getString("model"),
                root.getString("retrieved_at"), narrations)
        }
    }
}
