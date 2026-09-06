package com.daengs.app.walk.diary

import com.daengs.app.walk.sync.WalkApi
import org.json.JSONObject

/** 성향/추천 정책 없이 기록 수와 근거를 읽는 v0 계약. */
data class WalkBehaviorCount(val entryCount: Int, val walksWithEntries: Int)

data class WalkRecordEvidence(val entryId: String, val walkId: String, val revision: Int,
    val behaviorCode: String, val recordedAt: String, val contextStatus: String,
    val content: com.daengs.app.walk.WalkEntry, val contextRefs: List<String>)

data class WalkRecordProfile(
    val petId: String,
    val sourceRevision: String,
    val walkCount: Int,
    val unassignedEntryCount: Int,
    val behaviors: Map<String, WalkBehaviorCount>,
    val evidence: List<WalkRecordEvidence>,
    val generatedAt: java.time.Instant,
    val since: java.time.Instant?,
    val until: java.time.Instant?,
) {
    companion object {
        fun parse(json: JSONObject): WalkRecordProfile {
            require(json.getString("profile_version") == "walk-record-profile-v0")
            require(json.getString("vocabulary_version") == "walk-behavior-v1")
            val counts = json.getJSONObject("behaviors")
            val evidence = json.getJSONArray("evidence")
            return WalkRecordProfile(json.getString("pet_id"), json.getString("source_revision"),
                json.getInt("walk_count"), json.getInt("unassigned_entry_count"),
                listOf("sniffing", "excretion", "barking").associateWith { code ->
                    counts.getJSONObject(code).let { WalkBehaviorCount(it.getInt("entry_count"), it.getInt("walks_with_entries")) }
                }, (0 until evidence.length()).map { index ->
                    evidence.getJSONObject(index).let { WalkRecordEvidence(it.getString("entry_id"),
                        it.getString("walk_id"), it.getInt("entry_revision"), it.getString("behavior_code"),
                        it.getString("recorded_at"), it.getString("context_status"),
                        com.daengs.app.walk.WalkEntry.parse(it.getString("entry_id"), it.getString("walk_id"), it),
                        it.getJSONArray("context_refs").let { refs -> (0 until refs.length()).map { n -> refs.getString(n) } }) }
                }, java.time.Instant.parse(json.getString("generated_at")),
                json.getJSONObject("period").let { if (it.isNull("since")) null else java.time.Instant.parse(it.getString("since")) },
                json.getJSONObject("period").let { if (it.isNull("until")) null else java.time.Instant.parse(it.getString("until")) })
        }
    }
}

object WalkRecordProfileApi {
    suspend fun query(token: String, petId: String, since: java.time.Instant? = null,
        until: java.time.Instant? = null): Result<WalkRecordProfile> =
        WalkApi.call(token, "/record-profile/query", "POST", JSONObject().apply {
            put("pet_id", petId)
            since?.let { put("since", it.toString()) }
            until?.let { put("until", it.toString()) }
        }) { WalkRecordProfile.parse(JSONObject(it)) }
}
