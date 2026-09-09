package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import org.json.JSONObject
import java.time.Instant

/** Original record and background stay separate, including after local prose edits. */
data class DiarySceneContent(
    val recordText: String,
    val recordKind: String,
    val photoId: String? = null,
    val point: GeoPoint? = null,
    val locationLabel: String,
    val address: String? = null,
    val order: Int = 0,
)

data class DiaryGenerationInfo(val modelStatus: String, val missingScenes: Int) {
    fun description(): String = when (modelStatus) {
        "unavailable" -> "배경 문장을 만들지 못했어요. 남긴 기록은 그대로 볼 수 있고 다시 생성할 수 있어요."
        "not_requested" -> "남긴 기록을 모았어요. 배경 자료가 있는 장면부터 일기 문장을 붙여요."
        else -> "장면 배경과 직접 남긴 기록을 함께 모았어요."
    } + if (missingScenes > 0) " 채울 근거가 부족한 장면은 억지로 만들지 않았어요." else ""
}

object ServerDiaryBundle {
    const val FORMAT = "walk-diary-bundle-v1"
    const val RESPONSE = "walk-diary-response-v1"
    const val TARGET_SCENES = 5 // Supplement target, never a cap on user records.

    fun parse(text: String): GeoStoryboardBundle {
        val response = JSONObject(text)
        require(response.getString("format") == RESPONSE)
        require(response.getString("status") == "ready")
        val obj = response.getJSONObject("bundle")
        require(obj.getString("format") == FORMAT)
        val session = obj.requiredText("client_session_id", 200)
        require(response.getString("session_id") == session)
        val revision = obj.digestText("input_revision")
        obj.digestText("plan_revision")
        response.digestText("input_revision")
        val title = obj.requiredText("title", 80)
        val status = obj.getString("model_status")
        require(status in setOf("accepted", "not_requested", "unavailable"))
        require(obj.getString("title_origin") == if (status == "accepted") "model" else "system")
        require(obj.getString("semantic_status") == "not_evaluated")
        require((status == "unavailable") == !obj.isNull("failure_code"))
        require(obj.getString("photos_status") in setOf("complete", "not_available"))
        require(obj.getString("photos_status") == response.getString("photos_status"))
        val items = obj.getJSONArray("scenes")
        require(items.length() <= 600)
        val scenes = (0 until items.length()).map { i -> scene(items.getJSONObject(i), i + 1, status) }
        require(scenes.map { it.id }.distinct().size == scenes.size)
        require(scenes.zipWithNext().all { (a, b) -> a.atMillis <= b.atMillis })
        return GeoStoryboardBundle(session, revision, false, scenes, text, title = title,
            diary = DiaryGenerationInfo(status, response.getJSONObject("preparation_counts").getInt("remaining_deficit")))
    }

    private fun scene(obj: JSONObject, order: Int, modelStatus: String): StoryboardScene {
        require(obj.strictLong("order", 1, 600).toInt() == order)
        obj.requiredText("id", 200)
        val core = obj.getJSONObject("core")
        val identity = core.requiredText("identity", 220)
        core.digestText("version")
        val anchor = obj.getJSONObject("anchor")
        val at = Instant.parse(anchor.getString("event_at")).toEpochMilli()
        val method = anchor.getString("method")
        val state = anchor.getString("position_state")
        require(method in setOf("none", "observed", "estimated", "last_known"))
        require(state in setOf("unlocated", "legacy", "resolved", "provisional"))
        val point = anchor.optJSONObject("point")?.let {
            val lat = it.getDouble("lat"); val lng = it.getDouble("lng")
            require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0)
            GeoPoint(lat, lng)
        }
        require((point == null) == (method == "none"))
        require(state != "unlocated" || point == null)
        val locationAt = if (anchor.isNull("location_at")) null else Instant.parse(anchor.getString("location_at")).toEpochMilli()
        if (method in setOf("observed", "last_known")) require(locationAt != null && locationAt <= at)
        val narration = obj.getJSONObject("narration")
        val narrationStatus = narration.getString("status")
        require(narrationStatus in setOf("generated", "omitted", "no_background", "not_requested", "unavailable"))
        val body = if (narrationStatus == "generated") {
            require(modelStatus == "accepted" && narration.getJSONArray("evidence_ids").length() in 1..8)
            narration.requiredText("text", 180)
        } else {
            require(narration.isNull("text") && narration.getJSONArray("evidence_ids").length() == 0)
            ""
        }
        val record = obj.optJSONObject("user_record")
        val observed = obj.optJSONObject("observation")
        require((record == null) != (observed == null))
        var entry: StoryboardEntryReference? = null
        var photoId: String? = null
        var observation: StoryboardObservation? = null
        val title: String
        val original: String
        val kind: String
        val id: String
        if (record != null) {
            kind = record.getString("kind")
            require(kind in setOf("behavior", "note", "photo"))
            if (kind == "photo") {
                require(identity.startsWith("walk_photo:"))
                photoId = identity.removePrefix("walk_photo:").also { require(it.isNotBlank()) }
                require(record.getString("media_ref") == "app-private-photo:$photoId")
                require(anchor.getString("time_basis") == "photo_capture")
                id = "photo:$photoId"; title = "산책 사진"; original = "이때 남긴 사진"
            } else {
                require(identity.startsWith("walk_entry:"))
                val entryId = identity.removePrefix("walk_entry:").also { require(it.isNotBlank()) }
                id = "entry:$entryId"
                val pet = if (record.isNull("pet_id")) null else record.getString("pet_id")
                entry = StoryboardEntryReference(entryId, null, pet, kind == "note")
                title = if (kind == "note") "남긴 메모" else when (record.getString("code")) {
                    "sniffing" -> "킁킁"; "excretion" -> "배변"; "barking" -> "짖음"
                    else -> error("지원하지 않는 행동")
                }
                original = if (kind == "note") record.requiredText("text", 2000) else title
            }
        } else {
            require(identity.startsWith("observation:"))
            val item = requireNotNull(observed)
            require(identity == "observation:${item.getString("id")}")
            require(item.getString("subject") == "recording_device" && item.getString("action_meaning") == "not_inferred")
            kind = item.getString("kind")
            title = when (kind) {
                "observed_dwell" -> "머무른 구간"
                "observed_fast" -> "이동이 빨라진 구간"
                "observed_slow" -> "이동이 느려진 구간"
                else -> error("지원하지 않는 이동 관측")
            }
            val from = Instant.parse(item.getString("started_at")).toEpochMilli()
            val to = Instant.parse(item.getString("ended_at")).toEpochMilli()
            require(at in from..to && locationAt == at && state == "resolved" && method == "observed")
            val fixes = anchor.getJSONArray("source_fixes")
            val fix = (0 until fixes.length()).map { fixes.getJSONObject(it) }
                .first { Instant.parse(it.getString("at")).toEpochMilli() == at }
            observation = StoryboardObservation(fix.strictLong("client_seq", 0, Int.MAX_VALUE.toLong()).toInt(),
                fix.strictLong("chain_index", 0, Int.MAX_VALUE.toLong()).toInt(), at, requireNotNull(point))
            original = "기기 이동 기록 · ${(to - from) / 1000}초 동안의 관측"
            id = identity
        }
        // Only explicitly named address fields are displayed; never stringify arbitrary dictionaries.
        val places = obj.getJSONArray("place_reference")
        val address = (0 until places.length()).mapNotNull {
            val piece = places.getJSONObject(it)
            require(piece.getString("kind") == "place_reference")
            piece.getJSONObject("facts").optString("dong").takeIf(String::isNotBlank)
        }.distinct().joinToString(" · ").ifBlank { null }
        val label = when {
            state == "provisional" -> "위치 확인 중"
            method == "estimated" -> "동선에서 추정한 위치"
            method == "last_known" -> "마지막으로 확인된 위치"
            point == null -> "확인된 위치 없음"
            else -> "기록할 때 확인된 위치"
        }
        return StoryboardScene(id, at, title, body, "$label\n$original", storyboardHash(canonicalJson(obj)),
            sourcePayload = obj.toString(), entryReference = entry, observation = observation,
            diary = DiarySceneContent(original, kind, photoId, point.takeUnless { state == "provisional" }, label, address, order))
    }
}

internal fun JSONObject.digestText(key: String): String = requiredText(key, 64).also {
    require(it.matches(Regex("[0-9a-f]{64}")))
}
