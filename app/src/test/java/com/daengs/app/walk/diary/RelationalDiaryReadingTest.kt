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

    @Test fun `behavior card binds only the current matching original and keeps failed action identity`() {
        val parsed = RelationalDiaryResponse.parse(relationalBehaviorFixture().toString())
        val card = parsed.bundle!!.cards[1]
        val ref = card.originals.single().ref
        val entry = WalkEntry(ref.id, parsed.sessionId, WalkMomentType.SNIFFING, card.anchor.eventAt.toEpochMilli(),
            baseVersion = WalkEntryVersion(ref.version.toInt(), "m"))
        val source = input(parsed).copy(entries = listOf(entry, entry.copy(id = "other-pin", type = WalkMomentType.BARKING)))
        val scene = read(source).scenes[1]
        assertEquals(entry.id, scene.entryId)
        assertEquals(StoryboardEntryReference(entry.id, ref.version.toLong(), null), scene.source!!.entryReference)
        assertEquals(DiarySceneKind.SNIFFING, diarySceneKind(scene, source.entries, entry.sessionId))
        assertEquals(card.body, scene.body)
        for (entries in listOf(emptyList(), listOf(entry, entry), listOf(entry.copy(id = "other-pin")),
            listOf(entry.copy(sessionId = "other-walk")), listOf(entry.copy(type = WalkMomentType.BARKING)),
            listOf(entry.copy(petId = "other-dog")), listOf(entry.copy(syncPending = true)),
            listOf(entry.copy(baseVersion = WalkEntryVersion(999, "m"))))) {
            assertNull(read(source.copy(entries = entries)).scenes[1].entryId)
        }
        val failed = card.copy(action = card.action.copy(status = RelationalPartStatus.FAILED,
            semanticStatus = RelationalSemanticStatus.NOT_PUBLISHED, text = ""), body = card.space.text)
        val failedValue = parsed.copy(bundle = parsed.bundle.copy(cards = listOf(failed)))
        val failedScene = read(input(failedValue).copy(entries = listOf(entry))).scenes.single()
        assertEquals(entry.id, failedScene.entryId)
        assertEquals(DiarySceneKind.SNIFFING, diarySceneKind(failedScene, listOf(entry), entry.sessionId))
    }

    @Test fun `administrative header uses existing address label and survives saved response parsing`() {
        val json = JSONObject(raw)
        val first = json.getJSONObject("bundle").getJSONArray("cards").getJSONObject(0)
        first.getJSONObject("header").put("administrative_address", JSONObject()
            .put("sido", "서울특별시").put("sigungu", "서초구").put("dong", "반포4동")
            .put("address_type", "administrative_dong"))
        val parsed = RelationalDiaryResponse.parse(json.toString())
        val restored = RelationalDiaryResponse.parse(parsed.rawJson)
        val scene = read(input(restored)).scenes.first()
        assertEquals("서울 서초구 반포4동", scene.content!!.address)
        assertEquals("서초구", scene.content.administrativeAddress!!.sigungu)
        assertEquals(response.bundle!!.cards.first().body, scene.body)
        assertFalse(scene.body.contains("서초구"))
        // The pre-address saved format remains readable with its original dong label.
        assertEquals("반포4동", read().scenes.first().content!!.address)
        assertNull(read().scenes.first().content!!.administrativeAddress)
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

    @Test fun `stale or failed generation keeps live originals without old prose or deleted records`() {
        val note = WalkEntry("note", walk.sessionId, WalkMomentType.NOTE, walk.startedAtMillis, note = "  수정한 메모\n원문  ")
        val action = WalkEntry("action", walk.sessionId, WalkMomentType.SNIFFING, walk.startedAtMillis + 1)
        val image = DiaryPhotoInput("p", walk.startedAtMillis + 2, GeoPoint(37.5, 127.0), walk.startedAtMillis)
        val base = input().copy(entries = listOf(note, action), photos = listOf(image), photoIds = setOf("p"))
        val missing = base.copy(source = base.source.copy(relational = null, relationalStatus = "stale"))
        for (status in listOf("stale", "failed", "pending", "running")) {
            val diary = read(missing.copy(source = missing.source.copy(relationalStatus = status)))
            assertEquals(3, diary.scenes.size)
            assertFalse(diary.preparing)
            assertTrue(diary.scenes.all { it.body.isEmpty() })
            assertEquals(listOf(note.note), diary.scenes.first().originalNotes)
            assertEquals("action", diary.scenes[1].entryId)
            assertEquals("last_known", diary.scenes[2].content!!.locationMethod)
        }
        val first = read(missing).scenes.first().source!!
        val draft = StoryboardDraft().edit(first, body = "내가 남긴 문장", acknowledge = true).toJson()
        assertEquals("내가 남긴 문장", read(missing, draft = draft).scenes.first().body)
        // A later valid generation does not silently swallow the user's independent original-card edit.
        assertTrue(read(base, draft = draft).scenes.any { it.source?.id == first.id && it.body == "내가 남긴 문장" })
        assertFalse(read(missing.copy(entries = listOf(action)), draft = draft).scenes.any { it.entryId == "note" })
        assertEquals(1, read(missing.copy(entries = listOf(action), photos = emptyList(), photoIds = emptySet()), draft = draft).scenes.size)
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
