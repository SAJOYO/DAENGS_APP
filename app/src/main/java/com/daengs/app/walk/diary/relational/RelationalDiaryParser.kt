package com.daengs.app.walk.diary.relational

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.diary.DiarySceneAddress
import com.daengs.app.walk.diary.strictLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Parses the public contract only. Unknown additive fields survive in rawJson. */
internal object RelationalDiaryParser {
    fun parse(raw: String): RelationalDiaryResponse {
        require(raw.toByteArray(Charsets.UTF_8).size <= 4_000_000) { "일기 응답이 너무 커요." }
        val obj = JSONObject(raw)
        require(obj.text("format") == RelationalDiaryResponse.RESPONSE)
        val session = obj.text("session_id").also { UUID.fromString(it) }
        val status = obj.enumValue<RelationalStatus>("status")
        val bundle = obj.objectOrNull("bundle")?.let { bundle(it, session) }
        require((status == RelationalStatus.READY) == (bundle != null))
        val revisions = obj.getJSONObject("entry_revisions").let { values ->
            values.keys().asSequence().associateWith { values.strictLong(it, 0, Long.MAX_VALUE) }
        }
        bundle?.cards?.forEach { card ->
            card.originals.filter { !it.deleted && it.content is RelationalRecordContent.Behavior }.forEach {
                require(it.ref.versionKind == "revision" && it.ref.version.toLongOrNull() == revisions[it.ref.id])
            }
        }
        val photosStatus = obj.choice("photos_status", "complete", "not_available")
        val manifest = obj.objectOrNull("photo_manifest")?.let {
            RelationalPhotoManifest(it.text("publisher_id"), it.strictLong("revision", 1, Long.MAX_VALUE))
        }
        require(manifest == null || photosStatus == "complete")
        return RelationalDiaryResponse(session, obj.strictLong("generation", 0, Long.MAX_VALUE),
            obj.text("input_revision", blank = true), status, revisions, photosStatus, manifest,
            obj.strictLong("target_scene_count", 1, 50).toInt(), bundle, obj.optionalText("error_code"),
            obj.getJSONObject("execution_limits").jsonValue(), raw)
    }

    private fun bundle(obj: JSONObject, session: String): RelationalDiaryBundle {
        require(obj.text("format") == RelationalDiaryResponse.FORMAT)
        require(obj.text("client_session_id") == session)
        val title = obj.optionalText("title")
        val titleStatus = obj.enumValue<RelationalPartStatus>("title_status")
        require((titleStatus == RelationalPartStatus.RETURNED) == (title != null))
        val cards = obj.getJSONArray("cards").objects(::card)
        require(cards.size <= 600)
        require(cards.map { it.sceneId }.distinct().size == cards.size)
        require(cards.zipWithNext().all { (a, b) -> a.anchor.eventAt <= b.anchor.eventAt })
        val seen = mutableSetOf<String>()
        cards.forEach { card ->
            require(card.comparisonSceneId == null || card.comparisonSceneId in seen)
            seen.add(card.sceneId)
        }
        require(cards.map { it.currentContext.walkId }.distinct().size <= 1)
        return RelationalDiaryBundle(session, title, titleStatus, cards)
    }

    private fun card(obj: JSONObject): RelationalDiaryCard {
        val id = obj.text("scene_id")
        val anchor = anchor(obj.getJSONObject("anchor"))
        val header = obj.getJSONObject("header").let {
            require(it.text("scene_id") == id)
            val dong = it.optionalText("dong")
            val address = it.objectOrNull("administrative_address")?.let { value ->
                DiarySceneAddress(value.optionalText("sido"), value.optionalText("sigungu"),
                    value.text("dong"), value.optionalText("address_type"))
                    .also { parsed -> require(parsed.dong == dong) }
            }
            RelationalHeader(id, dong, it.objectOrNull("weather")?.jsonValue(), address)
        }
        val context = snapshot(obj.getJSONObject("current_context"))
        require(context.sceneId == id && context.recordedAt == anchor.eventAt)
        val space = part(obj.getJSONObject("space"))
        val action = part(obj.getJSONObject("action"))
        val body = obj.text("body", blank = true)
        // Check the server's part/body contract; keep its original string rather than replacing it.
        require(body == listOf(space.text, action.text).filter { it.isNotEmpty() }.joinToString("\n"))
        val originals = obj.getJSONArray("originals").objects(::original)
        val behaviors = originals.filter { !it.deleted && it.content is RelationalRecordContent.Behavior }
        require(behaviors.size <= 1 && behaviors.all { it.anchor == anchor })
        return RelationalDiaryCard(id, anchor, header, space, action, body, context,
            obj.optionalText("comparison_scene_id"), originals)
    }

    private fun part(obj: JSONObject): RelationalDiaryPart {
        val status = obj.enumValue<RelationalPartStatus>("status")
        val semantic = obj.enumValue<RelationalSemanticStatus>("semantic_status")
        val text = obj.text("text", blank = true)
        if (status == RelationalPartStatus.RETURNED) {
            require(text.isNotBlank() && semantic != RelationalSemanticStatus.NOT_PUBLISHED)
        } else require(text.isEmpty() && semantic == RelationalSemanticStatus.NOT_PUBLISHED)
        return RelationalDiaryPart(status, text, semantic)
    }

    private fun anchor(obj: JSONObject): RelationalAnchor {
        val event = obj.instant("event_at")
        val basis = obj.choice("time_basis", "recorded_at", "photo_capture", "session_fallback", "route_observation")
        val point = obj.point()
        val at = obj.optionalInstant("location_at")
        val state = obj.choice("position_state", "legacy", "provisional", "resolved", "unlocated")
        val method = obj.enumValue<RelationalPositionMethod>("method")
        val fixes = obj.getJSONArray("source_fixes").objects {
            RelationalFixRef(it.strictLong("client_seq", 0, Long.MAX_VALUE),
                it.strictLong("chain_index", 0, Long.MAX_VALUE), it.instant("at"))
        }
        require((point == null) == (method == RelationalPositionMethod.NONE))
        require(state != "unlocated" || point == null)
        require(state !in setOf("resolved", "legacy") || point != null)
        require(point != null || (at == null && fixes.isEmpty()))
        if (method in setOf(RelationalPositionMethod.OBSERVED, RelationalPositionMethod.LAST_KNOWN))
            require(at != null && at <= event)
        require(basis != "session_fallback" || point == null)
        require(fixes.size <= 256 && fixes.map { it.clientSeq }.distinct().size == fixes.size)
        if (method == RelationalPositionMethod.ESTIMATED) require(fixes.map { it.chainIndex }.distinct().size <= 1)
        return RelationalAnchor(event, basis, point, at, obj.nonnegativeOrNull("accuracy_m"), state, method, fixes)
    }

    private fun snapshot(obj: JSONObject): RelationalSceneSnapshot {
        val point = obj.point()
        val basis = obj.enumValue<RelationalPositionMethod>("position_basis")
        require((point == null) == (basis == RelationalPositionMethod.NONE))
        val facts = obj.getJSONArray("facts").objects { fact ->
            val scope = fact.getJSONObject("scope")
            RelationalSceneFact(fact.text("id"), fact.enumValue("family"), fact.getJSONObject("value").jsonValue(),
                RelationalFactScope(scope.choice("kind", "record_point", "registered_point", "query_area"),
                    scope.text("description"), scope.optionalText("coverage_key")),
                fact.optionalText("subject_key"), fact.getJSONArray("source_refs").texts().also { require(it.isNotEmpty()) },
                fact.optionalInstant("observed_at"), fact.optionalInstant("retrieved_at"),
                fact.optionalText("reference_date"), fact.text("time_meaning"))
        }
        val collection = obj.getJSONObject("collection").let { values ->
            values.keys().asSequence().associate { wireEnum<RelationalFamily>(it) to values.enumValue<RelationalCollectionStatus>(it) }
        }
        val reasons = obj.optJSONObject("collection_reasons")?.let { values ->
            values.keys().asSequence().associate { wireEnum<RelationalFamily>(it) to values.getJSONArray(it).texts() }
        }.orEmpty()
        require(facts.map { it.id }.distinct().size == facts.size && facts.all { it.family in collection })
        return RelationalSceneSnapshot(obj.text("scene_id"), obj.text("walk_id"), obj.instant("recorded_at"),
            point, obj.nonnegativeOrNull("accuracy_m"), basis, facts, collection, reasons)
    }

    private fun original(obj: JSONObject): RelationalOriginal {
        val ref = obj.getJSONObject("ref").let {
            val kind = it.choice("version_kind", "revision", "sha256")
            val version = it.text("version")
            require(if (kind == "sha256") version.matches(Regex("[0-9a-f]{64}"))
                else version.toLongOrNull()?.let { n -> n > 0 && n.toString() == version } == true)
            RelationalRecordRef(it.choice("store", "walk_entry", "walk_photo"), it.text("id"), version, kind,
                if (it.isNull("pin_revision")) null else it.strictLong("pin_revision", 0, Long.MAX_VALUE))
        }
        require(obj.get("deleted") is Boolean)
        val deleted = obj.getBoolean("deleted")
        val content = obj.objectOrNull("content")?.let {
            when (it.choice("kind", "behavior", "note", "photo")) {
                "behavior" -> RelationalRecordContent.Behavior(it.choice("code", "sniffing", "excretion", "barking"), it.optionalText("pet_id"))
                "note" -> RelationalRecordContent.Note(it.text("text").also { note -> require(note.length <= 2000) })
                else -> RelationalRecordContent.Photo(it.text("media_ref"))
            }
        }
        val anchor = obj.objectOrNull("anchor")?.let(::anchor)
        val pin = obj.objectOrNull("pin_payload")?.jsonValue()
        if (deleted) require(content == null && anchor == null && pin == null)
        else {
            require(content != null && anchor != null)
            val photo = content is RelationalRecordContent.Photo
            require(photo == (ref.store == "walk_photo") && photo == (anchor.timeBasis == "photo_capture"))
        }
        return RelationalOriginal(ref, deleted, content, anchor, pin)
    }
}

private fun JSONObject.text(key: String, blank: Boolean = false): String =
    (get(key) as? String ?: error("Invalid text: $key")).also { require(blank || it.isNotBlank()) }
private fun JSONObject.optionalText(key: String): String? = if (isNull(key)) null else text(key)
private fun JSONObject.objectOrNull(key: String): JSONObject? = if (isNull(key)) null else getJSONObject(key)
private fun JSONObject.choice(key: String, vararg allowed: String): String = text(key).also { require(it in allowed) }
private inline fun <reified T : Enum<T>> wireEnum(value: String): T =
    enumValues<T>().firstOrNull { it.name.lowercase(java.util.Locale.ROOT) == value } ?: error("Unsupported contract value: $value")
private inline fun <reified T : Enum<T>> JSONObject.enumValue(key: String): T = wireEnum(text(key))
private fun JSONObject.instant(key: String): Instant = Instant.parse(text(key))
private fun JSONObject.optionalInstant(key: String): Instant? = optionalText(key)?.let(Instant::parse)
private fun JSONObject.number(key: String): Double = (get(key) as? Number)?.toDouble()
    ?.also { require(it.isFinite()) } ?: error("Invalid number: $key")
private fun JSONObject.nonnegativeOrNull(key: String): Double? = if (isNull(key)) null else number(key).also { require(it >= 0) }
private fun JSONObject.point(): GeoPoint? = objectOrNull("point")?.let {
    val lat = it.number("lat"); val lng = it.number("lng")
    require(lat in -90.0..90.0 && lng in -180.0..180.0)
    GeoPoint(lat, lng)
}
private fun JSONObject.jsonValue(): JsonObject = Json.parseToJsonElement(toString()).jsonObject
private fun <T> JSONArray.objects(parse: (JSONObject) -> T): List<T> = (0 until length()).map { parse(getJSONObject(it)) }
private fun JSONArray.texts(): List<String> = (0 until length()).map { (get(it) as? String ?: error("Invalid text array")) }
