package com.daengs.app.ui.walk

import com.daengs.app.location.GeoPoint
import com.daengs.app.map.layers.moments.usesActionArtwork
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.diary.*
import org.junit.Assert.*
import org.junit.Test

class DiarySingleSceneMarkerTest {
    private fun diary(): DiaryWalk {
        val summary = explorerPanelPreviewRead().diary!!.summary
        val session = summary.sessionId
        val point = GeoPoint(37.5, 127.0)
        val excretion = WalkEntry("excretion", session, WalkMomentType.EXCRETION, 7_500, point, 7_500)
        val barking = WalkEntry("barking", session, WalkMomentType.BARKING, 15_000, point, 15_000)
        fun scene(id: String, at: Long, entry: WalkEntry? = null) =
            DiaryScene("$session/$id", session, at, "장면", "", point, "", entryId = entry?.id)
        return DiaryWalk(summary, listOf(
            scene("general-1", 0), scene("original:entry:excretion", 7_500, excretion),
            scene("generated-barking", 15_000, barking), scene("general-4", 22_500),
        ), "", sourceEntries = listOf(excretion, barking))
    }

    @Test fun `general excretion barking general shows one marker each with ordinals 1 2 3 4`() {
        val diary = diary()
        val presentation = diary.scenePresentation()
        val markers = diarySceneMarkers(presentation, diary.scenes[2].id)
        assertEquals(listOf(1, 2, 3, 4), presentation.items.map { it.ordinal })
        assertEquals(diary.scenes.map { it.id }, markers.map { it.id })
        assertEquals(listOf(1, 2, 3, 4), markers.map { it.diaryPin!!.ordinal })
        assertEquals(listOf(false, true, true, false), markers.map { it.usesActionArtwork })
        assertEquals(setOf(WalkMomentType.EXCRETION), markers[1].behaviors)
        assertEquals(setOf(WalkMomentType.BARKING), markers[2].behaviors)
        assertEquals("1", markers[0].label)
        assertEquals("4", markers[3].label)
        assertEquals(diary.scenes[2].id, markers.single { it.selected }.id)
        assertTrue(markers.none { it.id.startsWith("diary-action:") })
    }

    @Test fun `unlocated scene keeps its number and does not renumber later markers`() {
        val diary = diary()
        val unlocated = diary.copy(scenes = diary.scenes.mapIndexed { index, scene ->
            if (index == 1) scene.copy(point = null) else scene
        })
        assertEquals(listOf(1, 2, 3, 4), unlocated.scenePresentation().items.map { it.ordinal })
        assertEquals(listOf(1, 3, 4), diarySceneMarkers(unlocated.scenePresentation(), null).map { it.diaryPin!!.ordinal })
    }

    @Test fun `same coordinates and timestamps never merge different scene identities`() {
        val diary = diary()
        val sameTime = diary.copy(scenes = diary.scenes.map { it.copy(atMillis = 0) })
        val markers = diarySceneMarkers(sameTime.scenePresentation(), null)
        assertEquals(1, markers.map { it.point }.distinct().size)
        assertEquals(4, markers.map { it.id }.distinct().size)
    }

    @Test fun `source reference alone binds an action without parsing the scene id or prose`() {
        val diary = diary()
        val action = diary.sourceEntries[1]
        val scene = diary.scenes[2].copy(entryId = null, title = "바꾼 제목",
            source = StoryboardScene("server-card", 15_000, "", "", "", "source",
                entryReference = StoryboardEntryReference(action.id, null, null)))
        val reading = diary.copy(scenes = listOf(scene))
        val item = reading.scenePresentation().items.single()
        assertEquals(action, item.action)
        assertEquals(scene.id, diarySceneMarkers(reading.scenePresentation(), null).single().id)
        assertTrue(diarySceneMarkers(reading.scenePresentation(), null).single().usesActionArtwork)
    }

    @Test fun `stale or inconsistent source references are not repaired by proximity`() {
        val diary = diary()
        val scene = diary.scenes[1]
        val source = StoryboardScene("server-card", scene.atMillis, "", "", "", "source",
            entryReference = StoryboardEntryReference(scene.entryId!!, 99, null))
        for (invalid in listOf(
            scene.copy(source = source),
            scene.copy(source = source.copy(entryReference = StoryboardEntryReference("other", null, null))),
            scene.copy(needsReview = true),
        )) {
            val reading = diary.copy(scenes = listOf(invalid))
            assertNull(reading.scenePresentation().items.single().action)
            assertFalse(diarySceneMarkers(reading.scenePresentation(), null).single().usesActionArtwork)
        }
    }

    @Test fun `foreign duplicate and ambiguous action bindings never choose an arbitrary record`() {
        val diary = diary()
        val scene = diary.scenes[1]
        val action = diary.sourceEntries[0]
        val foreign = diary.copy(scenes = listOf(scene), sourceEntries = listOf(action.copy(sessionId = "other")))
        val duplicate = diary.copy(scenes = listOf(scene), sourceEntries = listOf(action, action.copy(note = "another")))
        val ambiguous = diary.copy(scenes = listOf(scene, scene.copy(id = "another-scene")))
        assertNull(foreign.scenePresentation().items.single().action)
        assertNull(duplicate.scenePresentation().items.single().action)
        assertTrue(ambiguous.scenePresentation().items.all { it.action == null })
        assertEquals(2, diarySceneMarkers(ambiguous.scenePresentation(), null).size)
    }

    @Test fun `removing a visible scene never resurrects its original action as a map marker`() {
        val diary = diary()
        val hidden = diary.copy(scenes = diary.scenes.filterNot { it.entryId == "excretion" })
        val markers = diarySceneMarkers(hidden.scenePresentation(), null)
        assertEquals(hidden.scenes.map { it.id }, markers.map { it.id })
        assertTrue(markers.none { WalkMomentType.EXCRETION in it.behaviors })
        assertEquals(2, hidden.sourceEntries.size)
    }

    @Test fun `notes keep ordinary numbered artwork and boundaries consume no ordinal`() {
        val diary = diary()
        val note = diary.sourceEntries[0].copy(type = WalkMomentType.NOTE)
        val session = diary.summary.sessionId
        val start = diary.scenes[0].copy(id = "$session/start", title = "사용자가 바꾼 시작 제목")
        val end = start.copy(id = "$session/end")
        val withBoundaries = diary.copy(scenes = listOf(start, diary.scenes[1], diary.scenes[3], end),
            sourceEntries = listOf(note))
        val presentation = withBoundaries.scenePresentation()
        assertEquals(listOf(null, 1, 2, null), presentation.items.map { it.ordinal })
        val markers = diarySceneMarkers(presentation, null)
        assertEquals(listOf(1, 2), markers.map { it.diaryPin!!.ordinal })
        assertTrue(markers.none { it.usesActionArtwork })
    }

    @Test fun `behavior artwork keeps group inspection dimming and selection metadata`() {
        val diary = diary()
        val ids = diary.scenes.take(3).map { it.id }
        val markers = diarySceneMarkers(diary.scenePresentation(), diary.scenes[1].id, ids)
        assertTrue(markers[1].selected)
        assertTrue(markers[1].usesActionArtwork)
        assertTrue(markers.take(3).all { it.diaryPin!!.inspected && !it.diaryPin.dimmed })
        assertTrue(markers[3].diaryPin!!.dimmed)
        val replay = diaryReplayMarkers(markers, setOf(diary.scenes[2].id))
        assertEquals(markers.map { it.id to it.point }, replay.map { it.id to it.point })
        assertEquals(markers.map { it.behaviors }, replay.map { it.behaviors })
        assertTrue(replay.none { it.diaryPin!!.inspected || it.diaryPin.dimmed })
    }

    @Test fun `a read shares one presentation but a new emission cannot borrow its old action`() {
        val base = explorerPanelPreviewRead()
        val read = base.copy(diary = diary())
        assertSame(read.scenePresentation, read.scenePresentation)
        val newer = read.copy(diary = read.diary!!.copy(sourceEntries = emptyList()))
        assertNotSame(read.scenePresentation, newer.scenePresentation)
        assertEquals(2, read.scenePresentation.items.count { it.action != null })
        assertTrue(newer.scenePresentation.items.all { it.action == null })
    }
}
