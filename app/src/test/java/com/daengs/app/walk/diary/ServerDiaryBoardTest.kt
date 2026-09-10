package com.daengs.app.walk.diary

import com.daengs.app.walk.RecordedFix
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.support.diaryBoardFixture
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerDiaryBoardTest {
    private fun bundle() = GeoStoryboardBundle.parse(diaryBoardFixture().toString())
    private fun walk(b: GeoStoryboardBundle) = WalkSummary(b.sessionId, emptyList(), b.scenes.first().atMillis,
        b.scenes.last().atMillis, null, 2000.0, 1_000_000L, emptyList(), null)

    @Test fun `real DEV output exposes four kinds as one editable body per card`() {
        val b = bundle()
        assertEquals(7, b.scenes.size)
        assertEquals("start", b.scenes.first().id)
        assertEquals("end", b.scenes.last().id)
        val note = b.scenes.single { it.id == "entry:note" }
        assertTrue(note.body.startsWith("등록된 카페가 가까이에 있었다.\n"))
        assertTrue(note.body.endsWith(note.diary!!.recordText))
        assertEquals(SceneBodyScope.SCENE, note.bodyScope)
        assertEquals(note.body, note.sceneBody())
        assertEquals("", b.diary!!.description())
        assertEquals("", b.diary!!.copy(modelStatus = "unavailable").description())
        val prose = "내가 고친 장면 전체."
        val restored = StoryboardDraft.parse(StoryboardDraft().edit(note, body = prose,
            bodyScope = SceneBodyScope.SCENE).toJson())
        val projected = diaryWalk(walk(b), emptyList(), emptyList(), restored, StoryboardAnalysisView(b, true, ""))
        assertEquals(prose, projected.scenes.single { it.entryId == "note" }.body)
        assertEquals(b.scenes.map { it.id }, projected.scenes.map { it.source!!.id })
    }

    @Test fun `boundaries checkpoints and movement resolve only their exact uploaded fix`() {
        val b = bundle()
        val observations = b.scenes.mapNotNull { it.observation }
        assertEquals(4, observations.size)
        val fixes = observations.map { RecordedFix(it.clientSeq, it.chainIndex, it.atMillis,
            it.point.latitude, it.point.longitude, 5f, false) }
        val view = StoryboardAnalysisView(b, true, "")
        val matching = diaryWalk(walk(b), emptyList(), emptyList(), StoryboardDraft(), view, fixes)
        assertTrue(matching.scenes.all { it.point != null })
        for (wrong in listOf(emptyList(), fixes.map { it.copy(chainIndex = 99) },
            fixes.map { it.copy(atMillis = it.atMillis + 1) }, fixes.map { it.copy(isMock = true) })) {
            val result = diaryWalk(walk(b), emptyList(), emptyList(), StoryboardDraft(), view, wrong)
            assertTrue(result.scenes.filter { it.source!!.observation != null }.all { it.point == null })
            assertEquals(7, result.scenes.size)
        }
    }

    @Test fun `no GPS boundaries stay readable cards without invented pins`() {
        val json = diaryBoardFixture()
        val items = json.getJSONObject("bundle").getJSONArray("scenes")
        for (index in listOf(0, items.length() - 1)) {
            items.getJSONObject(index).getJSONObject("anchor")
                .put("point", JSONObject.NULL).put("location_at", JSONObject.NULL)
                .put("method", "none").put("position_state", "unlocated")
                .put("time_basis", "session_fallback").put("source_fixes", JSONArray())
        }
        val b = GeoStoryboardBundle.parse(json.toString())
        assertNull(b.scenes.first().diary!!.point)
        assertNull(b.scenes.last().observation)
        assertFalse(b.scenes.last().body.isBlank())
    }

    @Test fun `invalid source kind missing fix and mismatched session never enter the reader`() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("session_id", "other") },
            { it.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(1).put("order", 1) },
            { it.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(1).put("boundary", "end") },
            { it.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(1).getJSONObject("anchor").put("source_fixes", JSONArray()) },
            { it.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(1).getJSONObject("anchor").getJSONObject("point").put("lat", 100) },
            { it.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(5).getJSONObject("observation").put("action_meaning", "sniffing") },
        )
        mutations.forEach { mutate ->
            assertTrue(runCatching { GeoStoryboardBundle.parse(diaryBoardFixture().also(mutate).toString()) }.isFailure)
        }
    }
}
