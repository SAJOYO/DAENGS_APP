package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkSummary
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiarySceneBodyTest {
    private val record = DiarySceneContent("  두부와 사진 한 장!\n다시 걸었다.  ", "note", locationLabel = "마지막으로 확인된 위치")
    private val source = StoryboardScene("entry:n", 1000, "메모", "공원 옆이었다.", "내부 근거", "v1", diary = record)
    private val walk = WalkSummary("s", emptyList(), 0, 10000, null, 100.0, 10000, emptyList(), null)
    private fun projected(scene: StoryboardScene, draft: StoryboardDraft) = diaryWalk(walk, emptyList(), emptyList(), draft,
        StoryboardAnalysisView(GeoStoryboardBundle("s", "revision", false, listOf(scene), "{}"), true, "")).scenes.single()

    @Test fun `reader receives one body and no location provenance while the source note stays exact`() {
        val scene = projected(source, StoryboardDraft())
        assertEquals("공원 옆이었다. " + record.recordText, scene.body)
        assertFalse(scene.body.contains(record.locationLabel))
        assertEquals(record, scene.content)
        assertEquals(source, scene.source)
    }

    @Test fun `legacy JSON edits only the background and keeps the note in the assembled scene`() {
        val json = JSONObject(StoryboardDraft().edit(source, body = "예전에 고친 배경.").toJson())
        json.getJSONArray("edits").getJSONObject(0).remove("body_scope")
        val restored = StoryboardDraft.parse(json.toString())
        assertEquals("예전에 고친 배경. " + record.recordText, projected(source, restored).body)
    }

    @Test fun `whole scene survives serialization regeneration and hidden toggles without reappending sources`() {
        val prose = "두부랑 쉬다가 다시 나섰다.\n내가 완성한 장면."
        val draft = StoryboardDraft().edit(source, body = prose, bodyScope = SceneBodyScope.SCENE)
        val updated = source.copy(body = "새 생성 문장", fingerprint = "v2", diary = record.copy(recordText = "수정된 원본"))
        val restored = StoryboardDraft.parse(draft.toJson())
        val scene = projected(updated, restored)
        assertEquals(prose, scene.body)
        assertTrue(scene.needsReview)
        val hidden = restored.edit(requireNotNull(scene.source), hidden = true)
        val unhidden = hidden.edit(requireNotNull(scene.source), hidden = false)
        assertEquals(prose, projected(updated, unhidden).body)
        assertEquals("수정된 원본", scene.content!!.recordText)
    }

    @Test fun `intentionally emptied scene does not regenerate a record paragraph`() {
        val draft = StoryboardDraft().edit(source, body = "", bodyScope = SceneBodyScope.SCENE)
        assertEquals("", projected(source, StoryboardDraft.parse(draft.toJson())).body)
    }

    @Test fun `behavior photo and measured movement become prose without diagnostic blocks`() {
        val examples = listOf(
            "behavior" to "킁킁", "photo" to "이때 남긴 사진", "observed_dwell" to "기기 이동 기록 · 90초 동안의 관측")
        val expected = listOf("냄새를 맡았다.", "사진을 남겼다.", "한곳에 머문 구간이 있었다.")
        examples.zip(expected).forEach { (input, text) ->
            val item = source.copy(diary = record.copy(recordKind = input.first, recordText = input.second))
            assertEquals("공원 옆이었다. $text", item.sceneBody())
            assertEquals(input.second, item.diary!!.recordText)
        }
    }

    @Test fun `missing generated prose leaves the note readable and legacy scenes keep their body`() {
        assertEquals(record.recordText, source.copy(body = "").sceneBody())
        assertEquals("옛 장면", source.copy(body = "옛 장면", diary = null).sceneBody())
        assertEquals(record.recordText, source.copy(body = record.recordText).sceneBody())
    }
}
