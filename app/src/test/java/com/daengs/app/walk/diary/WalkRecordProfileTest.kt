package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.location.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WalkRecordProfileTest {
    private fun response(): JSONObject {
        val content = WalkEntry(id = "entry", sessionId = "walk", type = WalkMomentType.SNIFFING,
            recordedAtMillis = 1000, point = GeoPoint(37.5, 127.0), locationCapturedAtMillis = 900,
            accuracyMeters = 5f, petId = "pet").toJson().apply {
            put("entry_id", "entry"); put("walk_id", "walk"); put("entry_revision", 2)
            put("context_status", "not_requested"); put("context_refs", JSONArray())
        }
        return JSONObject().apply {
            put("profile_version", "walk-record-profile-v0"); put("vocabulary_version", "walk-behavior-v1")
            put("pet_id", "pet"); put("source_revision", "revision")
            put("walk_count", 3); put("unassigned_entry_count", 1)
            put("generated_at", "2026-09-05T09:00:00Z")
            put("period", JSONObject().put("since", JSONObject.NULL).put("until", JSONObject.NULL))
            put("behaviors", JSONObject().apply {
                put("sniffing", JSONObject().put("entry_count", 1).put("walks_with_entries", 1))
                for (code in listOf("excretion", "barking")) put(code,
                    JSONObject().put("entry_count", 0).put("walks_with_entries", 0))
            })
            put("evidence", JSONArray().put(content))
        }
    }

    @Test fun `기록이 없는 산책도 분모에 남고 근거 위치와 측정 시각이 보존된다`() {
        val profile = WalkRecordProfile.parse(response())
        assertEquals(3, profile.walkCount)
        assertEquals(1, profile.behaviors.getValue("sniffing").entryCount)
        assertEquals(1, profile.unassignedEntryCount)
        assertEquals(900L, profile.evidence.single().content.locationCapturedAtMillis)
        assertEquals(GeoPoint(37.5, 127.0), profile.evidence.single().content.point)
        assertEquals("not_requested", profile.evidence.single().contextStatus)
        assertNull(profile.since)
    }

    @Test fun `모르는 계약 버전을 기존 프로필처럼 해석하지 않는다`() {
        assertTrue(runCatching { WalkRecordProfile.parse(response().put("profile_version", "future")) }.isFailure)
    }
}
