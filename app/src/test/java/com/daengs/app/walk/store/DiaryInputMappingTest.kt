package com.daengs.app.walk.store

import android.app.Application
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.LocalDiaryBoard
import com.daengs.app.walk.diary.diaryTitle
import com.daengs.app.walk.support.titledDiaryFixture
import com.daengs.app.walk.sync.storyboardEntryStamp
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DiaryInputMappingTest {
    @Test fun `tombstones stay in source stamps but never become visible entries`() {
        val entry = WalkEntry("note", "s", WalkMomentType.NOTE, 1000, note = "원본 메모")
        val live = WalkEntryRow("note", "s", entry.toJson().toString(), 3, "saved", false)
        val deleted = live.copy(id = "deleted", payload = null)
        val rows = listOf(live, deleted)
        val stamp = storyboardEntryStamp(rows)
        val analysis = WalkSceneAnalysisRow("s", 1, stamp, "revision", "ready",
            titledDiaryFixture().toString(), null, stamp)
        val input = diaryBoardInput(rows, analysis, null, emptyList(), null)
        assertEquals(listOf("note"), input.entries.map { it.id })
        assertEquals(3, input.entries.single().baseVersion!!.revision)
        assertTrue(input.source.analysis.canReview)
        assertFalse(diaryBoardInput(listOf(live), analysis, null, emptyList(), null).source.analysis.canReview)
        for (changed in listOf(live.copy(dirty = true), live.copy(pinDirty = true),
            live.copy(pendingRequest = "pending"), live.copy(syncError = "conflict"))) {
            val stale = diaryBoardInput(listOf(changed, deleted), analysis, null, emptyList(), null)
            assertNull(stale.source.analysis.bundle)
            assertEquals("기록 동기화 후 장면을 다시 분석해요.", stale.source.analysis.notice)
            assertEquals(changed.syncError, stale.entries.single().syncError)
            assertEquals(changed.dirty || changed.pinDirty || changed.pendingRequest != null,
                stale.entries.single().syncPending)
        }
    }

    @Test fun `published title ignores malformed entry bodies bases and unused legacy hash inputs`() {
        val entry = WalkEntryRow("bad", "s", "not json", 1, "saved", false)
        val image = WalkPhotoRow("photo", "s", "owner", 1000, 999, Double.NaN, 127.0, 3f)
        val analysis = WalkSceneAnalysisRow("s", 1, "diary:old", "revision", "ready", "bad bundle", null, "diary:old")
        val publication = WalkDiaryPublicationRow("s", 10000, 30000, "bad base", titledDiaryFixture().toString())
        val source = diaryBoardSource(listOf(entry), analysis, null, listOf(image), publication)
        assertEquals("함께 남긴 산책 기록", diaryTitle("s", source))
        assertNull(diaryTitle("other", source))
        assertNull(diaryTitle("s", diaryBoardSource(listOf(entry), analysis, null, listOf(image),
            publication.copy(publishedBundle = null))))
    }

    @Test fun `photo projection retains capture time location and scene identity without a file`() {
        val row = WalkPhotoRow("photo", "s", "owner", 4000, 3500, 37.0, 127.0, 3f)
        val walk = WalkSummary("s", emptyList(), 0, 10000, null, 100.0, 10000, emptyList(), null)
        val raw = LocalDiaryBoard.build(walk, emptyList(), emptyList(), listOf(row.toDiaryPhotoInput()))
        val scenes = JSONObject(raw).getJSONArray("scenes")
        val photo = scenes.getJSONObject(1)
        assertEquals(3, scenes.length())
        assertEquals("photo:photo", photo.getString("id"))
        assertEquals("photo", photo.getString("photo"))
        assertEquals(4000L, photo.getLong("at"))
        assertEquals("산책 사진", photo.getString("title"))
        assertEquals("사진을 남겼다.", photo.getString("body"))
        val parsed = LocalDiaryBoard.parse(raw).scenes[1]
        assertEquals(GeoPoint(37.0, 127.0), parsed.diary!!.point)
        assertEquals(4000L, parsed.atMillis)
    }
}
