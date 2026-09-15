package com.daengs.app.walk.diary

import android.app.Application
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.store.*
import com.daengs.app.walk.support.titledDiaryFixture
import com.daengs.app.walk.sync.diaryInputStamp
import com.daengs.app.walk.sync.storyboardEntryStamp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Robolectric supplies org.json; these projections need no Room database or photo files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkDiaryAssemblyTest {
    private data class StoredInput(
        val entries: List<WalkEntryRow> = emptyList(),
        val analysis: WalkSceneAnalysisRow? = null,
        val photoSync: WalkPhotoSyncRow? = null,
        val photoRows: List<WalkPhotoRow> = emptyList(),
        val publication: WalkDiaryPublicationRow? = null,
    ) {
        fun source() = diaryBoardSource(entries, analysis, photoSync, photoRows, publication)
        fun full() = diaryBoardInput(entries, analysis, photoSync, photoRows, publication)
    }
    private val walk = WalkSummary("s", emptyList(), 0, 10000, null, 100.0, 10000, emptyList(), null)
    private val note = WalkEntry("note", "s", WalkMomentType.NOTE, 1000, note = "원본 메모")
    private val empty = StoredInput()
    private fun row(entry: WalkEntry) = WalkEntryRow(entry.id, entry.sessionId, entry.toJson().toString(), 1, entry.id, false)
    private fun analysis(stamp: String = storyboardEntryStamp(emptyList()), raw: String = titledDiaryFixture().toString()) =
        WalkSceneAnalysisRow("s", 1, stamp, "revision", "ready", raw, null, stamp)
    private fun assemble(input: StoredInput, photos: List<WalkPhoto> = emptyList(), draft: String? = null) =
        assembleDiary(walk, input.full(), photos, draft, emptyList())

    @Test fun `preparing publication suppresses cached analysis local entries photos and drafts`() {
        val image = WalkPhoto("photo", "s", 2000, GeoPoint(37.0, 127.0), File("unused.jpg"))
        val rows = listOf(row(note))
        val input = empty.copy(entries = rows, analysis = analysis(storyboardEntryStamp(rows)),
            publication = WalkDiaryPublicationRow("s", 10000, 30000, baseBundle = "not yet prepared"))
        val diary = assemble(input, listOf(image), draft = "not parsed during preparation")
        assertTrue(diary.preparing)
        assertFalse(diary.published)
        assertTrue(diary.scenes.isEmpty())
        assertTrue(diary.sourceEntries.isEmpty())
        assertEquals("", diary.notice)
        assertNull(diary.title)
        assertNull(diaryTitle("s", input.source()))
    }

    @Test fun `pending publication keeps original actions addressable without exposing draft scenes`() {
        val action = WalkEntry("sniff", "s", WalkMomentType.SNIFFING, 1000)
        val diary = assemble(empty.copy(entries = listOf(row(note), row(action)),
            publication = WalkDiaryPublicationRow("s", 10000, 30000, baseBundle = "not yet prepared")))
        val reading = requireNotNull(DiaryActionTarget("s", "sniff").resolve(diary))
        assertEquals(action.copy(baseVersion = com.daengs.app.walk.WalkEntryVersion(1, "sniff")), reading.entry)
        assertNull(reading.scene)
        assertTrue(diary.scenes.isEmpty())
    }

    @Test fun `publication keeps saved prose and explicit edits while reconciling live entries and photos`() {
        val changed = note.copy(id = "changed", recordedAtMillis = 2000)
        val removed = note.copy(id = "removed", recordedAtMillis = 3000)
        val imageRow = WalkPhotoRow("photo", "s", "owner", 4000, 4000, 37.0, 127.0, 3f)
        val base = LocalDiaryBoard.build(walk, emptyList(), listOf(note, changed, removed), listOf(imageRow.toDiaryPhotoInput()))
        val published = base.replace("\"body\":\"원본 메모\"", "\"body\":\"저장된 일기 문장\"")
        val board = GeoStoryboardBundle.parse(published)
        val draft = StoryboardDraft().edit(board.scenes.first(), body = "내가 고친 시작")
            .edit(board.scenes.last(), hidden = true)
        val added = note.copy(id = "added", recordedAtMillis = 5000, note = "새 메모")
        val rows = listOf(row(note).copy(revision = 2, mutationId = "acknowledged"),
            row(changed.copy(note = "고친 메모")).copy(dirty = true),
            row(removed).copy(payload = null, dirty = true), row(added).copy(dirty = true))
        val publication = WalkDiaryPublicationRow("s", 10000, 30000, base, published, 11000)
        val input = empty.copy(entries = rows, analysis = analysis(raw = "ignored legacy analysis"),
            photoRows = listOf(imageRow), publication = publication)
        val image = WalkPhoto("photo", "s", 4000, GeoPoint(37.0, 127.0), File("unused.jpg"))
        val diary = assemble(input, listOf(image), draft.toJson())
        assertTrue(diary.published)
        assertFalse(diary.preparing)
        assertEquals("", diary.notice)
        assertEquals(listOf("s/start", "s/entry:note", "s/entry:changed", "s/photo:photo", "s/entry:added"),
            diary.scenes.map { it.id })
        assertEquals("내가 고친 시작", diary.scenes.first().body)
        assertEquals("저장된 일기 문장", diary.scenes.single { it.entryId == "note" }.body)
        assertEquals("고친 메모", diary.scenes.single { it.entryId == "changed" }.body)
        assertEquals("새 메모", diary.scenes.single { it.entryId == "added" }.body)
        assertSame(image, diary.scenes.single { it.photo != null }.photo)
        assertEquals(listOf("note", "changed", "added"), diary.sourceEntries.map { it.id })
        assertEquals("고친 메모", diary.sourceEntries.single { it.id == "changed" }.note)
        assertTrue(diary.sourceEntries.single { it.id == "changed" }.syncPending)
        val deletedPhoto = assemble(input.copy(photoRows = emptyList()), draft = draft.toJson())
        assertEquals(diary.scenes.filter { it.photo == null }, deletedPhoto.scenes)
    }

    @Test fun `title projection does not parse entries or reconcile publication base`() {
        val rows = listOf(row(note).copy(payload = "not an entry"))
        val input = empty.copy(entries = rows, analysis = analysis(storyboardEntryStamp(rows)))
        assertEquals("함께 남긴 산책 기록", diaryTitle("s", input.source()))
        val published = input.copy(publication = WalkDiaryPublicationRow("s", 10000, 30000,
            baseBundle = "not needed for a title", publishedBundle = titledDiaryFixture().toString()))
        assertEquals("함께 남긴 산책 기록", diaryTitle("s", published.source()))
        assertNull(diaryTitle("other", published.source()))
    }

    @Test fun `entry and photo mutations invalidate legacy title and scenes together`() {
        val rows = listOf(row(note))
        val sync = WalkPhotoSyncRow("s", "owner", "publisher", 1, 1)
        val input = empty.copy(entries = rows, photoSync = sync,
            analysis = analysis(diaryInputStamp(rows, sync, emptyList())))
        assertEquals("함께 남긴 산책 기록", assemble(input).title)
        for (stale in listOf(input.copy(photoSync = sync.copy(revision = 2)),
            input.copy(entries = listOf(row(note).copy(dirty = true))))) {
            val diary = assemble(stale)
            assertNull(diaryTitle("s", stale.source()))
            assertNull(diary.title)
            assertFalse(diary.published)
            assertEquals(listOf("s/start", "s/entry:note", "s/end"), diary.scenes.map { it.id })
            assertEquals("원본 메모", diary.scenes.single { it.entryId == "note" }.body)
            assertEquals("원본 메모", diary.sourceEntries.single().note)
        }
    }

    @Test fun `failed refresh retains the prior matching bundle and notice`() {
        val input = empty.copy(analysis = analysis())
        val ready = assemble(input)
        val failed = input.copy(analysis = input.analysis!!.copy(status = "failed", error = "offline"))
        val diary = assemble(failed)
        assertEquals(ready.scenes, diary.scenes)
        assertEquals("함께 남긴 산책 기록", diary.title)
        assertEquals(diary.title, diaryTitle("s", failed.source()))
        assertEquals("최신 분석을 확인하지 못했어요. 이전에 저장한 장면을 보여줘요.", diary.notice)
    }

    @Test fun `wrong session and malformed legacy bundle fall back to local scenes`() {
        for (raw in listOf(titledDiaryFixture().put("session_id", "other").toString(), "malformed")) {
            val input = empty.copy(analysis = analysis(raw = raw))
            assertNull(diaryTitle("s", input.source()))
            val diary = assemble(input)
            assertNull(diary.title)
            assertEquals(listOf("s/start", "s/end"), diary.scenes.map { it.id })
            assertFalse(diary.preparing)
        }
    }

    @Test fun `malformed published bundle still fails instead of silently replacing saved prose`() {
        val base = LocalDiaryBoard.build(walk, emptyList(), emptyList(), emptyList())
        val input = empty.copy(analysis = analysis(),
            publication = WalkDiaryPublicationRow("s", 10000, 30000, base, "malformed", 11000))
        assertTrue(runCatching { diaryTitle("s", input.source()) }.isFailure)
        assertTrue(runCatching { assemble(input) }.isFailure)
    }
}
