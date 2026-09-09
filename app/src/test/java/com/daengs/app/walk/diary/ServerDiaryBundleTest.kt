package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

fun diaryFixture(): JSONObject = JSONObject(ServerDiaryBundleTest::class.java.getResource("/storyboard/diary-v1.json")!!.readText())

class ServerDiaryBundleTest {
    @Test fun `equal timestamp scenes retain server sequence after local editing`() {
        val base = GeoStoryboardBundle.parse(diaryFixture().toString()).scenes.first()
        val first = base.copy(id = "z", diary = base.diary!!.copy(order = 1))
        val second = base.copy(id = "a", diary = base.diary!!.copy(order = 2))
        assertEquals(listOf("z", "a"), applyStoryboardEdits(listOf(first, second), StoryboardDraft()).map { it.id })
    }

    @Test fun `server-validated output preserves exact note and separates background from originals`() {
        val bundle = GeoStoryboardBundle.parse(diaryFixture().toString())
        assertEquals("두부와 함께 남긴 아침", bundle.title)
        assertEquals(listOf("entry:note", "entry:action", "observation:dwell", "photo:photo"), bundle.scenes.map { it.id })
        val note = bundle.scenes.first()
        assertEquals("  두부와 사진을 찍었다.\n다음 기록도 남김  ", note.diary!!.recordText)
        assertEquals("등록된 카페가 가까이에 있었다.", note.body)
        assertEquals(note.diary, applyStoryboardEdits(bundle.scenes, StoryboardDraft().edit(note, body = "내가 고친 배경")).first().diary)
        assertEquals(1, bundle.diary!!.missingScenes)
        assertEquals(20, bundle.scenes[2].observation!!.clientSeq)
        assertEquals("photo", bundle.scenes[3].diary!!.photoId)
    }

    @Test fun `bad order wrong session unsupported kind and invalid coordinates are rejected`() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("session_id", "other") },
            { it.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(0).put("order", 2) },
            { it.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(1).getJSONObject("user_record").put("code", "running") },
            { it.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(0).getJSONObject("anchor").getJSONObject("point").put("lat", 120) },
            { it.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(2).getJSONObject("observation").put("subject", "dog") },
            { it.getJSONObject("bundle").put("title_origin", "system") },
        )
        mutations.forEach { mutate -> assertTrue(runCatching { GeoStoryboardBundle.parse(diaryFixture().also(mutate).toString()) }.isFailure) }
    }

    @Test fun `photo card is not duplicated and its hidden state survives local photo merging`() {
        val bundle = GeoStoryboardBundle.parse(diaryFixture().toString())
        val at = Instant.parse("2026-09-09T00:00:00Z").toEpochMilli()
        val walk = WalkSummary(bundle.sessionId, listOf("dog"), at, at + 2_400_000, null, 100.0, 2_400_000, emptyList(), null)
        val image = WalkPhoto("photo", walk.sessionId, at + 1_800_000, GeoPoint(37.5, 127.0), java.io.File("photo.jpg"))
        val view = StoryboardAnalysisView(bundle, true, "ready")
        val diary = diaryWalk(walk, emptyList(), listOf(image), StoryboardDraft(), view)
        assertEquals(4, diary.scenes.size)
        assertEquals(image, diary.scenes.last().photo)
        assertNull(diary.scenes[2].point) // No matching local route fix: do not invent an observation marker.
        assertEquals(GeoPoint(37.5, 127.0), diary.scenes.first().point)
        val hidden = diaryWalk(walk, emptyList(), listOf(image), StoryboardDraft().edit(bundle.scenes.last(), hidden = true), view)
        assertEquals(3, hidden.scenes.size)
        assertFalse(hidden.scenes.any { it.photo != null })
    }

    @Test fun `empty bundle remains a valid diary without fabricated scenes`() {
        val fixture = diaryFixture()
        fixture.getJSONObject("bundle").put("scenes", org.json.JSONArray())
        assertTrue(GeoStoryboardBundle.parse(fixture.toString()).scenes.isEmpty())
    }
}
