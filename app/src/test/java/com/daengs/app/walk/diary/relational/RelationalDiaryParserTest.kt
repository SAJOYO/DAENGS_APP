package com.daengs.app.walk.diary.relational

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Public response from DEV 574daf10: synthetic walk, actual SGIS/EGIS/Gemini output. */
fun relationalFixture(): JSONObject = JSONObject(
    RelationalDiaryParserTest::class.java.getResource("/storyboard/relational-diary-v1.json")!!.readText(),
)

fun relationalBehaviorFixture(): JSONObject = relationalFixture().apply {
    val card = getJSONObject("bundle").getJSONArray("cards").getJSONObject(1)
    val id = getJSONObject("entry_revisions").keys().next()
    card.put("originals", JSONArray().put(JSONObject()
        .put("ref", JSONObject().put("store", "walk_entry").put("id", id)
            .put("version", getJSONObject("entry_revisions").getLong(id).toString())
            .put("version_kind", "revision").put("pin_revision", JSONObject.NULL))
        .put("deleted", false).put("content", JSONObject().put("kind", "behavior")
            .put("code", "sniffing").put("pet_id", JSONObject.NULL))
        .put("anchor", JSONObject(card.getJSONObject("anchor").toString()))
        .put("pin_payload", JSONObject.NULL)))
}

class RelationalDiaryParserTest {
    @Test fun `behavior references preserve identity and reject mismatched revision anchor or duplicates`() {
        val value = RelationalDiaryResponse.parse(relationalBehaviorFixture().toString())
        val original = value.bundle!!.cards[1].originals.single()
        assertEquals(value.entryRevisions.getValue(original.ref.id).toString(), original.ref.version)
        assertEquals(RelationalRecordContent.Behavior("sniffing", null), original.content)
        for (change in listOf<(JSONObject, JSONObject) -> Unit>(
            { _, record -> record.getJSONObject("ref").put("id", "unknown") },
            { _, record -> record.getJSONObject("ref").put("version", "999") },
            { _, record -> record.getJSONObject("anchor").put("accuracy_m", 987.0) },
            { card, record -> card.getJSONArray("originals").put(JSONObject(record.toString())) },
        )) {
            val json = relationalBehaviorFixture()
            val card = json.getJSONObject("bundle").getJSONArray("cards").getJSONObject(1)
            change(card, card.getJSONArray("originals").getJSONObject(0))
            assertThrows(IllegalArgumentException::class.java) { RelationalDiaryResponse.parse(json.toString()) }
        }
    }
    @Test fun `administrative address cannot disagree with the same cards dong`() {
        val json = relationalFixture()
        json.firstCard().getJSONObject("header").put("administrative_address", JSONObject()
            .put("sido", "서울특별시").put("sigungu", "강남구").put("dong", "역삼동")
            .put("address_type", "administrative_dong"))
        assertThrows(IllegalArgumentException::class.java) { RelationalDiaryResponse.parse(json.toString()) }
    }

    @Test fun `actual response keeps accepted action beside failed space and empty final card`() {
        val raw = relationalFixture().toString()
        val response = RelationalDiaryResponse.parse(raw)
        assertEquals(raw, response.rawJson)
        assertEquals(RelationalStatus.READY, response.status)
        val cards = response.bundle!!.cards
        assertEquals(3, cards.size)
        assertEquals("사평대로28길과 반포대로 산책", response.bundle.title)
        assertEquals("반포4동", cards[0].header.dong)
        assertNull(cards[0].header.weather)
        assertEquals("\"사평대로28길\"", cards[0].currentContext.facts.single().value["name"].toString())
        assertEquals(RelationalPartStatus.FAILED, cards[1].space.status)
        assertEquals(RelationalPartStatus.RETURNED, cards[1].action.status)
        assertEquals(cards[1].action.text, cards[1].body)
        assertEquals(RelationalPartStatus.NOT_REQUESTED, cards[2].action.status)
        assertEquals("", cards[2].body)
        assertEquals(cards[0].sceneId, cards[1].comparisonSceneId)
        assertEquals(RelationalCollectionStatus.UNKNOWN, cards[0].currentContext.collection[RelationalFamily.LAND_COVER])
    }

    @Test fun `original whitespace photo reference and missing environment survive without entering body`() {
        val obj = relationalFixture()
        val card = obj.getJSONObject("bundle").getJSONArray("cards").getJSONObject(1)
        val note = "  내 메모\n그대로 남겨!  "
        fun original(id: String, photo: Boolean): JSONObject = JSONObject()
            .put("ref", JSONObject().put("store", if (photo) "walk_photo" else "walk_entry")
                .put("id", id).put("version", "1").put("version_kind", "revision").put("pin_revision", JSONObject.NULL))
            .put("deleted", false)
            .put("content", if (photo) JSONObject().put("kind", "photo").put("media_ref", "app-private-photo:$id")
                else JSONObject().put("kind", "note").put("text", note))
            .put("anchor", JSONObject(card.getJSONObject("anchor").toString()).put("time_basis", if (photo) "photo_capture" else "recorded_at"))
            .put("pin_payload", JSONObject.NULL)
        card.put("originals", JSONArray().put(original("note-1", false)).put(original("photo-1", true)))
        card.getJSONObject("header").put("weather", JSONObject().put("temperature_c", 0).put("precipitation", JSONObject.NULL))
        val parsed = RelationalDiaryResponse.parse(obj.toString()).bundle!!.cards[1]
        assertEquals(note, (parsed.originals[0].content as RelationalRecordContent.Note).text)
        assertEquals("app-private-photo:photo-1", (parsed.originals[1].content as RelationalRecordContent.Photo).mediaRef)
        assertFalse(parsed.body.contains(note))
        assertEquals("0", parsed.header.weather!!["temperature_c"].toString())
        assertEquals("null", parsed.header.weather["precipitation"].toString())
    }

    @Test fun `pending failed and stale responses keep server limits without a publication`() {
        listOf("pending", "running", "failed", "stale").forEach { status ->
            val obj = relationalFixture().put("status", status).put("bundle", JSONObject.NULL)
                .put("execution_limits", JSONObject().put("generation_timeout_s", 180))
            val response = RelationalDiaryResponse.parse(obj.toString())
            assertNull(response.bundle)
            assertEquals("180", response.executionLimits["generation_timeout_s"].toString())
        }
    }

    @Test fun `bad session comparison anchor and unpublished text are rejected`() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("session_id", "00000000-0000-0000-0000-000000000001") },
            { it.firstCard().put("comparison_scene_id", it.firstCard().getString("scene_id")) },
            { it.firstCard().getJSONObject("anchor").getJSONObject("point").put("lat", 99) },
            { it.firstCard().getJSONObject("header").put("scene_id", "other") },
            { it.firstCard().getJSONObject("current_context").put("recorded_at", "2026-09-09T02:00:00Z") },
            { it.firstCard().getJSONObject("action").put("text", "거절된 문장") },
            { it.firstCard().put("body", "본문에만 섞인 미발행 후보") },
            { it.firstCard().getJSONObject("space").put("semantic_status", "not_published") },
            { it.getJSONObject("bundle").getJSONArray("cards").put(it.firstCard()) },
        )
        mutations.forEachIndexed { i, mutate ->
            assertTrue("mutation $i", runCatching { RelationalDiaryResponse.parse(relationalFixture().also(mutate).toString()) }.isFailure)
        }
    }

    @Test fun `same-time cards retain their server order and facts are never turned into prose`() {
        val obj = relationalFixture()
        val cards = obj.getJSONObject("bundle").getJSONArray("cards")
        val first = cards.getJSONObject(0)
        val second = cards.getJSONObject(1)
        second.put("anchor", JSONObject(first.getJSONObject("anchor").toString()))
        second.getJSONObject("current_context").put("recorded_at", first.getJSONObject("anchor").getString("event_at"))
        val response = RelationalDiaryResponse.parse(obj.toString())
        assertEquals(first.getString("scene_id"), response.bundle!!.cards[0].sceneId)
        assertEquals(second.getString("scene_id"), response.bundle.cards[1].sceneId)
        assertEquals("", response.bundle.cards[2].body)
    }
}

private fun JSONObject.firstCard(): JSONObject = getJSONObject("bundle").getJSONArray("cards").getJSONObject(0)
