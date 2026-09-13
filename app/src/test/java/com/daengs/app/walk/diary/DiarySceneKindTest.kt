package com.daengs.app.walk.diary

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DiarySceneKindTest {
    private val scene = DiaryScene("s/entry:a", "s", 1000, "물 한 모금, 집으로", "킁킁, 달리다가 쉬었어요", null, "",
        entryId = "a")
    private val entry = WalkEntry("a", "s", WalkMomentType.NOTE, 1000, note = "물을 마셨어요")
    private fun kind(s: DiaryScene = scene, entries: List<WalkEntry> = listOf(entry)) = diarySceneKind(s, entries, "s")
    private fun source(ref: StoryboardEntryReference) = StoryboardScene("entry:a", 1000, "", "", "", "f", entryReference = ref)

    @Test fun `type comes from the source and ignores edits location and ordering`() {
        val expected = listOf(DiarySceneKind.SNIFFING, DiarySceneKind.EXCRETION, DiarySceneKind.BARKING, DiarySceneKind.NOTE)
        WalkMomentType.entries.zip(expected).forEach { (type, result) ->
            val inputs = listOf(entry.copy(type = type), entry.copy(id = "b", type = WalkMomentType.BARKING))
            assertEquals(result, kind(entries = inputs))
            assertEquals(result, kind(scene.copy(title = "전혀 다른 말", body = "", point = GeoPoint(37.5, 127.0)), inputs.reversed()))
        }
    }

    @Test fun `missing duplicate and other session sources cannot be guessed from prose`() {
        listOf(emptyList(), listOf(entry, entry), listOf(entry.copy(sessionId = "other"))).forEach {
            assertEquals(DiarySceneKind.GENERAL, kind(entries = it))
        }
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(sessionId = "other")))
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(entryId = "")))
        assertEquals(DiarySceneKind.NOTE, kind(entries = listOf(entry, entry.copy(sessionId = "other"))))
    }

    @Test fun `stale or conflicting reference stays general even when an entry exists`() {
        val ref = StoryboardEntryReference("a", 3, null, isNote = true)
        val s = scene.copy(source = source(ref))
        assertEquals(DiarySceneKind.GENERAL, kind(s))
        assertEquals(DiarySceneKind.NOTE, kind(s, listOf(entry.copy(baseVersion = WalkEntryVersion(3, "m")))))
        assertEquals(DiarySceneKind.GENERAL, kind(s, listOf(entry.copy(baseVersion = WalkEntryVersion(4, "m")))))
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(source = source(ref.copy(entryId = "b")))))
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(source = source(ref.copy(revision = null, petId = "other")))))
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(source = source(ref.copy(revision = null, isNote = false)))))
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(needsReview = true)))
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(source = source(ref).copy(available = false))))
    }

    @Test fun `record kind cannot override broken entry references or conflicting types`() {
        val note = DiarySceneContent("", "note", locationLabel = "")
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(content = note), emptyList()))
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(content = note.copy(recordKind = "behavior"))))
        assertEquals(DiarySceneKind.NOTE, kind(scene.copy(content = note)))
    }

    @Test fun `known observation kinds describe device motion and unsupported kinds remain general`() {
        val expected = mapOf("observed_dwell" to DiarySceneKind.DWELL, "observed_fast" to DiarySceneKind.FAST,
            "observed_slow" to DiarySceneKind.SLOW, "note" to DiarySceneKind.NOTE, "photo" to DiarySceneKind.PHOTO,
            "behavior" to DiarySceneKind.GENERAL, "drinking" to DiarySceneKind.GENERAL, "home" to DiarySceneKind.GENERAL)
        expected.forEach { (record, result) ->
            assertEquals(result, kind(scene.copy(entryId = null, content = DiarySceneContent("물 마시기", record, locationLabel = "급수대"))))
        }
        assertEquals(DiarySceneKind.GENERAL, kind(scene.copy(entryId = null)))
    }

    @Test fun `local and remote photos keep their category without needing a media file`() {
        val photo = WalkPhoto("p", "s", 1000, GeoPoint(37.5, 127.0), File("not-downloaded.jpg"))
        val s = scene.copy(entryId = null, photo = photo)
        assertEquals(DiarySceneKind.PHOTO, kind(s))
        assertEquals(DiarySceneKind.GENERAL, kind(s.copy(photo = photo.copy(sessionId = "other"))))
        assertEquals(DiarySceneKind.GENERAL, kind(s.copy(content = DiarySceneContent("", "photo", photoId = "q", locationLabel = ""))))
        assertEquals(DiarySceneKind.GENERAL, kind(s.copy(content = DiarySceneContent("", "note", locationLabel = ""))))
        assertEquals(DiarySceneKind.PHOTO, kind(s.copy(photo = null, content = DiarySceneContent("", "photo", photoId = "p", locationLabel = ""))))
    }

    @Test fun `same read projection handles duplicate scene ids and source-only updates`() {
        val summary = WalkSummary("s", emptyList(), 0, 2000, null, 0.0, 2000, emptyList(), null)
        val walk = DiaryWalk(summary, listOf(scene), "", sourceEntries = listOf(entry))
        assertEquals(DiarySceneKind.NOTE, walk.sceneKinds()[scene.id])
        assertEquals(DiarySceneKind.BARKING, walk.copy(sourceEntries = listOf(entry.copy(type = WalkMomentType.BARKING))).sceneKinds()[scene.id])
        assertEquals(DiarySceneKind.GENERAL, walk.copy(scenes = listOf(scene, scene)).sceneKinds()[scene.id])
        assertEquals(DiarySceneKind.GENERAL, walk.copy(sourceEntries = emptyList()).sceneKinds()[scene.id])
    }
}
