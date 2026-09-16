package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Test

class DiaryActionTargetTest {
    private val summary = WalkSummary("s", emptyList(), 0, 2000, null, 0.0, 2000, emptyList(), null)
    private val a = WalkEntry("a", "s", WalkMomentType.SNIFFING, 1000, point = GeoPoint(37.5, 127.0))
    private val b = a.copy(id = "b")
    private fun scene(entry: WalkEntry) = DiaryScene("scene-${entry.id}", "s", 1000,
        "같은 제목", "저장된 본문", a.point, "", entryId = entry.id)
    private val diary = DiaryWalk(summary, listOf(scene(b), scene(a)), "", sourceEntries = listOf(a, b))

    @Test fun `same type time and point still open distinct original scenes`() {
        assertEquals("scene-a", DiaryActionTarget("s", "a").resolve(diary)?.scene?.id)
        assertEquals("scene-b", DiaryActionTarget("s", "b").resolve(diary)?.scene?.id)
    }

    @Test fun `no explicit reference never borrows a nearby scene`() {
        val result = DiaryActionTarget("s", "a").resolve(diary.copy(scenes = listOf(scene(b), scene(a).copy(entryId = null))))!!
        assertEquals(a, result.entry)
        assertNull(result.scene)
    }

    @Test fun `stale conflicting hidden and ambiguous references preserve original action`() {
        val ref = StoryboardEntryReference("a", null, null)
        val source = StoryboardScene("source", 1000, "", "", "", "f", entryReference = ref)
        val invalid = listOf(
            listOf(scene(a).copy(needsReview = true)),
            listOf(scene(a).copy(sessionId = "other")),
            listOf(scene(a).copy(source = source.copy(hidden = true))),
            listOf(scene(a).copy(source = source.copy(entryReference = ref.copy(entryId = "b")))),
            listOf(scene(a).copy(source = source.copy(entryReference = ref.copy(revision = 3)))),
            listOf(scene(a), scene(a).copy(id = "second")),
            listOf(scene(a), scene(b).copy(id = scene(a).id)),
        )
        invalid.forEach { scenes ->
            val result = DiaryActionTarget("s", "a").resolve(diary.copy(scenes = scenes))!!
            assertEquals(a, result.entry)
            assertNull(result.scene)
        }
        assertEquals("scene-a", DiaryActionTarget("s", "a").resolve(diary.copy(
            scenes = listOf(scene(a).copy(entryId = null, source = source))))?.scene?.id)
    }

    @Test fun `missing duplicate or wrong session entry is unavailable`() {
        val target = DiaryActionTarget("s", "a")
        assertNull(DiaryActionTarget("other", "a").resolve(diary))
        assertNull(target.resolve(diary.copy(sourceEntries = listOf(b))))
        assertNull(target.resolve(diary.copy(sourceEntries = listOf(a, a))))
        assertNull(target.resolve(diary.copy(sourceEntries = listOf(a.copy(sessionId = "other")))))
    }
}
