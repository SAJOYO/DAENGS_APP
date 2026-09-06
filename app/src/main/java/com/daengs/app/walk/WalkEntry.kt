package com.daengs.app.walk

import com.daengs.app.location.GeoPoint
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** 지도 묶음과 독립적인 기록 원본. 메모는 행동 프로필의 입력이 아니다. */
data class WalkEntry(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val type: WalkMomentType,
    val recordedAtMillis: Long,
    val point: GeoPoint? = null,
    val locationCapturedAtMillis: Long? = null,
    val accuracyMeters: Float? = null,
    val note: String? = null,
    val petId: String? = null,
    val syncError: String? = null,
    /** 로컬 편집 시작 버전. 서버 content에는 직렬화하지 않는다. */
    val baseVersion: WalkEntryVersion? = null,
) {
    fun validate(): WalkEntry {
        require(type != WalkMomentType.NOTE || (!note.isNullOrBlank() && note.length <= 2000))
        require(type == WalkMomentType.NOTE || (point != null && locationCapturedAtMillis != null && note == null))
        return this
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("vocabulary_version", "walk-behavior-v1")
        put("kind", if (type == WalkMomentType.NOTE) "note" else "behavior")
        put("behavior_code", if (type == WalkMomentType.NOTE) JSONObject.NULL else type.behaviorCode)
        put("note", note ?: JSONObject.NULL)
        put("pet_id", if (type == WalkMomentType.NOTE) JSONObject.NULL else petId ?: JSONObject.NULL)
        put("recorded_at", Instant.ofEpochMilli(recordedAtMillis).toString())
        put("location", point?.let { p -> JSONObject().apply {
            put("lat", p.latitude); put("lng", p.longitude)
            put("captured_at", Instant.ofEpochMilli(requireNotNull(locationCapturedAtMillis)).toString())
            put("accuracy_m", accuracyMeters ?: JSONObject.NULL)
        } } ?: JSONObject.NULL)
    }

    companion object {
        fun parse(id: String, sessionId: String, json: JSONObject): WalkEntry = WalkEntry(
            id = id, sessionId = sessionId,
            type = if (json.getString("kind") == "note") WalkMomentType.NOTE else
                WalkMomentType.entries.single { it.behaviorCode == json.getString("behavior_code") },
            recordedAtMillis = Instant.parse(json.getString("recorded_at")).toEpochMilli(),
            point = json.optJSONObject("location")?.let { GeoPoint(it.getDouble("lat"), it.getDouble("lng")) },
            locationCapturedAtMillis = json.optJSONObject("location")?.let {
                Instant.parse(it.getString("captured_at")).toEpochMilli()
            },
            accuracyMeters = json.optJSONObject("location")?.let {
                if (it.isNull("accuracy_m")) null else it.getDouble("accuracy_m").toFloat()
            },
            note = if (json.isNull("note")) null else json.getString("note"),
            petId = if (json.isNull("pet_id")) null else json.getString("pet_id"),
        ).validate()
    }
}


/** ACK, 원격 정정, 다른 로컬 편집 모두 열린 편집창과 대조한다. */
data class WalkEntryVersion(val revision: Int, val mutationId: String)
