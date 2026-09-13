package com.daengs.app.walk.diary

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PublishedCardWritingTest {
    private fun response() = JSONObject(javaClass.getResource("/storyboard/diary-card-orchestration-v1.json")!!.readText())

    @Test fun serverTitleRepresentsWholeCardAndAddressRemainsDongOnly() {
        val board = GeoStoryboardBundle.parse(response().toString())
        assertTrue(board.scenes.all { it.diary?.address == "도곡1동" })
        val behavior = board.scenes.single { it.diary?.recordKind == "behavior" }
        assertEquals("산책길에서 남긴 기록", behavior.title)
        assertNotEquals("킁킁", behavior.title)
        val writing = requireNotNull(behavior.diary?.publishedWriting)
        assertEquals("generated", writing.titleOrigin)
        assertEquals("보리가 냄새를 맡았다.", writing.actions.single().text)
        assertFalse(writing.space.text.contains("보리"))
        assertTrue(board.scenes.filter { it.diary?.recordKind != "behavior" }
            .all { it.diary?.publishedWriting?.actions?.isEmpty() == true })
    }

    @Test fun noteAndUserTitleBodyEditsSurviveWithoutRegeneratingParts() {
        val board = GeoStoryboardBundle.parse(response().toString())
        val note = board.scenes.single { it.diary?.recordKind == "note" }
        assertEquals("  있는 그대로\n  ", note.diary!!.publishedWriting!!.originalText)
        assertTrue(note.body.endsWith("  있는 그대로\n  "))
        val draft = StoryboardDraft().edit(note, title = "내가 정한 제목", body = "직접 수정한 본문")
        val reopened = GeoStoryboardBundle.parse(board.rawJson)
        val edited = applyStoryboardEdits(reopened.scenes, StoryboardDraft.parse(draft.toJson())).single { it.id == note.id }
        assertEquals("내가 정한 제목", edited.title)
        assertEquals("직접 수정한 본문", edited.body)
        assertEquals(note.diary.publishedWriting, edited.diary!!.publishedWriting)
    }

    @Test fun mismatchedTitleRevisionOrRewrittenBodyIsRejected() {
        for (change in listOf("revision", "body", "actor")) {
            val value = response()
            val cards = value.getJSONObject("bundle").getJSONArray("scenes")
            val card = (0 until cards.length()).map { cards.getJSONObject(it) }
                .single { it.optJSONObject("user_record")?.optString("kind") == "behavior" }
            val writing = card.getJSONObject("writing")
            when (change) {
                "revision" -> writing.put("title_based_on_content_revision", "f".repeat(64))
                "body" -> card.put("body", "두 본문을 임의로 다시 합친 결과")
                "actor" -> writing.getJSONObject("space").put("actor_id", "invented")
            }
            assertTrue(runCatching { GeoStoryboardBundle.parse(value.toString()) }.isFailure)
        }
    }

    @Test fun oneFallbackTitleKeepsOtherTitlesAndAllAdoptedBodiesOnReopen() {
        val value = response()
        val cards = value.getJSONObject("bundle").getJSONArray("scenes")
        val first = cards.getJSONObject(0)
        val body = first.getString("body")
        first.put("title", "산책 기록")
        first.getJSONObject("writing").put("title_origin", "fallback")
        val board = GeoStoryboardBundle.parse(value.toString())
        val reopened = GeoStoryboardBundle.parse(board.rawJson)
        assertEquals("산책 기록", reopened.scenes.first().title)
        assertEquals(body, reopened.scenes.first().body)
        assertEquals("fallback", reopened.scenes.first().diary!!.publishedWriting!!.titleOrigin)
        assertTrue(reopened.scenes.drop(1).all {
            it.diary!!.publishedWriting!!.titleOrigin == "generated"
        })
        assertEquals(board.scenes.map { it.body }, reopened.scenes.map { it.body })
    }
}
