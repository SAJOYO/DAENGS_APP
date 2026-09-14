package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import org.json.JSONObject
import java.time.Instant

/** Four source kinds, one existing card/editor. The body is already a complete scene. */
object ServerDiaryBoard {
    const val FORMAT = "walk-diary-board-v1"
    const val RESPONSE = "walk-diary-board-response-v1"

    fun parse(text: String): GeoStoryboardBundle {
        val response = JSONObject(text)
        require(response.getString("format") == RESPONSE && response.getString("status") == "ready")
        val board = response.getJSONObject("bundle")
        require(board.getString("format") == FORMAT)
        val session = board.requiredText("client_session_id", 200)
        require(session == response.getString("session_id"))
        response.digestText("input_revision")
        val revision = board.digestText("input_revision")
        board.digestText("plan_revision")
        val status = board.getString("model_status")
        require(status in setOf("accepted", "not_requested", "unavailable"))
        require((status == "unavailable") == !board.isNull("failure_code"))
        val items = board.getJSONArray("scenes")
        require(items.length() in 2..602)
        val scenes = (0 until items.length()).map { scene(items.getJSONObject(it), it + 1) }
        require(scenes.map { it.id }.distinct().size == scenes.size)
        require(scenes.first().id == "start" && scenes.last().id == "end")
        require(scenes.drop(1).dropLast(1).zipWithNext().all { (a, b) -> a.atMillis <= b.atMillis })
        // Fallback provenance stays internal; it is not a warning above the reader's scene.
        return GeoStoryboardBundle(session, revision, false, scenes, text,
            title = board.requiredText("title", 80), diary = DiaryGenerationInfo(status,
                response.getJSONObject("preparation_counts").getInt("remaining_deficit"), showFailureNotice = false))
    }

    private fun scene(obj: JSONObject, order: Int): StoryboardScene {
        require(obj.strictLong("order", 1, 602).toInt() == order)
        obj.requiredText("id", 200)
        val core = obj.getJSONObject("core")
        val identity = core.requiredText("identity", 220)
        core.digestText("version")
        val kind = obj.getString("kind")
        val fields = mapOf("user_record" to "user_record", "movement_observation" to "observation",
            "route_checkpoint" to "checkpoint", "session_boundary" to "boundary")
        require(fields.filterValues { !obj.isNull(it) }.keys == setOf(kind))
        val anchor = obj.getJSONObject("anchor")
        val at = Instant.parse(anchor.getString("event_at")).toEpochMilli()
        val method = anchor.getString("method")
        val state = anchor.getString("position_state")
        require(method in setOf("none", "observed", "last_known", "estimated"))
        require(state in setOf("unlocated", "legacy", "resolved", "provisional"))
        val point = anchor.optJSONObject("point")?.let {
            val lat = it.getDouble("lat"); val lng = it.getDouble("lng")
            require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0)
            GeoPoint(lat, lng)
        }
        require((point == null) == (method == "none"))
        require(state != "unlocated" || point == null)
        require(state !in setOf("legacy", "resolved") || point != null)
        val locationAt = if (anchor.isNull("location_at")) null else Instant.parse(anchor.getString("location_at")).toEpochMilli()
        if (method in setOf("observed", "last_known")) require(locationAt != null && locationAt <= at)
        if (point == null) require(locationAt == null && anchor.getJSONArray("source_fixes").length() == 0)
        var entry: StoryboardEntryReference? = null
        var photoId: String? = null
        var observation: StoryboardObservation? = null
        var recordKind = kind
        var original = ""
        val id = when (kind) {
            "user_record" -> {
                val record = obj.getJSONObject("user_record")
                recordKind = record.getString("kind")
                when (recordKind) {
                    "photo" -> {
                        require(identity.startsWith("walk_photo:"))
                        photoId = identity.removePrefix("walk_photo:").also { require(it.isNotBlank()) }
                        require(record.getString("media_ref") == "app-private-photo:$photoId")
                        require(anchor.getString("time_basis") == "photo_capture")
                        "photo:$photoId"
                    }
                    "note", "behavior" -> {
                        require(identity.startsWith("walk_entry:"))
                        val entryId = identity.removePrefix("walk_entry:").also { require(it.isNotBlank()) }
                        entry = StoryboardEntryReference(entryId, null,
                            if (record.isNull("pet_id")) null else record.getString("pet_id"), recordKind == "note")
                        original = if (recordKind == "note") record.requiredText("text", 2000) else record.getString("code").also {
                            require(it in setOf("sniffing", "excretion", "barking"))
                        }
                        "entry:$entryId"
                    }
                    else -> error("지원하지 않는 기록")
                }
            }
            "movement_observation" -> {
                val item = obj.getJSONObject("observation")
                require(identity == "observation:${item.getString("id")}")
                require(item.getString("subject") == "recording_device" && item.getString("action_meaning") == "not_inferred")
                require(item.getString("kind") in setOf("observed_dwell", "observed_slow", "observed_fast"))
                require(at in Instant.parse(item.getString("started_at")).toEpochMilli()..Instant.parse(item.getString("ended_at")).toEpochMilli())
                observation = exactObservation(anchor, at, point, singleton = false)
                identity
            }
            "route_checkpoint" -> {
                require(identity.startsWith("checkpoint:"))
                val checkpoint = obj.getJSONObject("checkpoint")
                checkpoint.requiredText("analysis_id", 200)
                checkpoint.strictLong("block", 0, Int.MAX_VALUE.toLong())
                require(checkpoint.getDouble("route_m").let { it.isFinite() && it >= 0 })
                observation = exactObservation(anchor, at, point)
                identity
            }
            "session_boundary" -> {
                require(identity.startsWith("boundary:"))
                if (point == null) require(anchor.getString("time_basis") == "session_fallback")
                else observation = exactObservation(anchor, at, point)
                obj.getString("boundary").also { require(it in setOf("start", "end")) }
            }
            else -> error("지원하지 않는 장면")
        }
        val places = obj.getJSONArray("place_reference")
        val address = (0 until places.length()).mapNotNull {
            val piece = places.getJSONObject(it)
            require(piece.getString("kind") == "place_reference")
            piece.getJSONObject("facts").optString("dong").takeIf(String::isNotBlank)
        }.distinct().joinToString(" · ").ifBlank { null }
        return StoryboardScene(id, at, obj.requiredText("title", 80), obj.requiredText("body", 2400),
            "", storyboardHash(canonicalJson(obj)), sourcePayload = obj.toString(),
            entryReference = entry, observation = observation,
            diary = DiarySceneContent(original, recordKind, photoId, point.takeUnless { state == "provisional" }, "", address, order,
                publishedWriting = publishedCardWriting(obj)),
            bodyScope = SceneBodyScope.SCENE)
    }

    private fun exactObservation(anchor: JSONObject, at: Long, point: GeoPoint?, singleton: Boolean = true): StoryboardObservation {
        require(anchor.getString("method") == "observed" && anchor.getString("position_state") == "resolved")
        require(anchor.getString("time_basis") == "route_observation")
        require(Instant.parse(anchor.getString("location_at")).toEpochMilli() == at)
        val fixes = anchor.getJSONArray("source_fixes")
        require(if (singleton) fixes.length() == 1 else fixes.length() in 1..256)
        val fix = (0 until fixes.length()).map { fixes.getJSONObject(it) }
            .single { Instant.parse(it.getString("at")).toEpochMilli() == at }
        return StoryboardObservation(fix.strictLong("client_seq", 0, Int.MAX_VALUE.toLong()).toInt(),
            fix.strictLong("chain_index", 0, Int.MAX_VALUE.toLong()).toInt(), at, requireNotNull(point))
    }
}
