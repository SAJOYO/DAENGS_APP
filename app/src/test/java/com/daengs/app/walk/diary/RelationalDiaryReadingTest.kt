package com.daengs.app.walk.diary

import android.app.Application
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.relational.*
import com.daengs.app.walk.store.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelationalDiaryReadingTest {
    private val raw get() = javaClass.getResource("/storyboard/relational-diary-v1.json")!!.readText()
    private val response get() = RelationalDiaryResponse.parse(raw)
    private val walk get() = WalkSummary(response.sessionId, emptyList(), response.bundle!!.cards.first().anchor.eventAt.toEpochMilli(),
        response.bundle!!.cards.last().anchor.eventAt.toEpochMilli(), null, 600.0, 600000, emptyList(), null)
    private fun input(value: RelationalDiaryResponse = response, images: Set<String> = emptySet()) = DiaryBoardInput(emptyList(),
        DiaryBoardSource(StoryboardAnalysisView(null, false, ""), DiaryPublicationInput("invalid old base", null),
            true, RelationalDiaryCache(value, value), "ready"), images)
    private fun read(input: DiaryBoardInput = input(), photos: List<WalkPhoto> = emptyList(), draft: String? = null) =
        assembleDiary(walk, input, photos, draft, emptyList())

    @Test fun `saved cards supersede legacy preparing publication without inventing missing prose`() {
        val diary = read()
        assertTrue(diary.published)
        assertFalse(diary.preparing)
        assertEquals(response.bundle!!.title, diaryTitle(walk.sessionId, input().source))
        assertEquals(response.bundle!!.cards.map { it.body }, diary.scenes.map { it.body })
        assertEquals(3, diary.scenes.size)
        assertEquals("", diary.scenes.last().body)
        assertTrue(diary.scenes.last().notice.contains("공간"))
        assertEquals(response.bundle!!.cards[1].action.text, diary.scenes[1].body)
        assertTrue(diary.scenes.all { it.entryId == null }) // Matching a pin's time is not an identity.
        assertEquals(response.bundle!!.cards.map { it.anchor.point }, diary.scenes.map { it.point })
        assertEquals("반포4동", diary.scenes.first().content!!.address)
        assertEquals(response.bundle!!.cards[1].comparisonSceneId, diary.scenes[1].relational!!.comparisonSceneId)
    }

    @Test fun `failed and omitted parts have different presentation`() {
        val card = response.bundle!!.cards.last()
        assertTrue(relationalPartNotice(card).isNotBlank())
        assertEquals("", relationalPartNotice(card.copy(space = card.action)))
    }

    @Test fun `notes and multiple photos survive space failure and editing separately`() {
        val original = response.bundle!!.cards.last()
        val note = "  직접 남긴 메모\n두 번째 줄  "
        fun record(id: String, content: RelationalRecordContent) = RelationalOriginal(
            RelationalRecordRef(if (content is RelationalRecordContent.Photo) "walk_photo" else "walk_entry", id, "1", "revision", null),
            false, content, original.anchor, null)
        val card = original.copy(originals = listOf(record("n", RelationalRecordContent.Note(note)),
            record("p1", RelationalRecordContent.Photo("p1")), record("p2", RelationalRecordContent.Photo("p2"))))
        val value = response.copy(bundle = response.bundle!!.copy(cards = listOf(card)))
        val photo = WalkPhoto("p1", walk.sessionId, card.anchor.eventAt.toEpochMilli(), requireNotNull(card.anchor.point), File("unused.jpg"))
        val source = input(value, setOf("p1", "p2"))
        val first = read(source, listOf(photo)).scenes.single()
        val draft = StoryboardDraft().edit(requireNotNull(first.source), body = "내가 수정한 문장", acknowledge = true).toJson()
        val edited = read(source, listOf(photo), draft).scenes.single()
        assertEquals("내가 수정한 문장", edited.body)
        assertEquals(listOf(note), edited.originalNotes)
        assertSame(photo, edited.originalPhotos[0].photo)
        assertEquals("p2", edited.originalPhotos[1].id)
        assertNull(edited.originalPhotos[1].photo)
        assertFalse(edited.needsReview)
        assertTrue(read(source, listOf(photo), StoryboardDraft().hide(first.source!!).toJson()).scenes.isEmpty())
    }

    @Test fun `source edits or owner changes cannot resurrect old publication`() {
        val entries = response.entryRevisions.map { (id, version) -> WalkEntryRow(id, walk.sessionId, "{}", version.toInt(), "m", false) }
        val stamp = RelationalDiaryStorage.stamp(entries, null, emptyList())
        val json = JSONObject().put("format", "walk-relational-diary-cache-v1").put("server_walk_id", "remote")
            .put("latest", raw).put("published", raw).toString()
        val row = WalkSceneAnalysisRow(walk.sessionId, response.generation, stamp, response.inputRevision, "ready", json, null, stamp)
        val session = WalkSessionRow(walk.sessionId, walk.startedAtMillis, "owner", walk.endedAtMillis, serverWalkId = "remote")
        val old = WalkDiaryPublicationRow(walk.sessionId, 0, 20000, "invalid base", "invalid publication", 1)
        fun source(rows: List<WalkEntryRow> = entries, owner: String = "owner", bound: WalkSessionRow = session) =
            diaryBoardSource(rows, row, null, emptyList(), old, bound, owner)
        assertEquals(response.bundle!!.title, diaryTitle(walk.sessionId, source()))
        for (invalid in listOf(source(entries.map { it.copy(dirty = true) }), source(owner = "other"), source(bound = session.copy(serverWalkId = "other")))) {
            assertTrue(invalid.relationalSelected)
            assertNull(diaryTitle(walk.sessionId, invalid))
            assertTrue(read(DiaryBoardInput(emptyList(), invalid, emptySet())).scenes.isEmpty())
        }
    }

    @Test fun `refresh failure retains saved cards while stale has no old fallback`() {
        val base = input()
        val failure = response.copy(status = RelationalStatus.FAILED, bundle = null)
        val failed = base.copy(source = base.source.copy(relational = RelationalDiaryCache(failure, response)))
        assertEquals(3, read(failed).scenes.size)
        assertTrue(read(failed).notice.contains("저장된 일기"))
        val stale = base.copy(source = base.source.copy(relational = RelationalDiaryCache(failure.copy(status = RelationalStatus.STALE), null)))
        assertTrue(read(stale).scenes.isEmpty())
        assertFalse(read(stale).preparing)
        assertNull(diaryTitle(walk.sessionId, stale.source))
    }

    @Test fun `only matching historical temperature enters the existing header`() {
        val card = response.bundle!!.cards.first()
        val facts = JSONObject().put("provider", "kma-vilage-fcst:ncst").put("unit", "celsius")
            .put("temperature_c", 20.0).put("observed_at", card.anchor.eventAt.toString()).put("grid", org.json.JSONArray(listOf(60, 127)))
        fun header(): RelationalDiaryCard {
            val weather = JSONObject().put("observations", org.json.JSONArray().put(JSONObject().put("source_id", "w1").put("facts", facts)))
            return card.copy(header = card.header.copy(weather = kotlinx.serialization.json.Json.parseToJsonElement(weather.toString()) as kotlinx.serialization.json.JsonObject))
        }
        assertEquals(20.0, relationalTemperature(header())!!.celsius, 0.0)
        facts.put("observed_at", card.anchor.eventAt.plusSeconds(3600).toString())
        assertNull(relationalTemperature(header()))
    }
}
