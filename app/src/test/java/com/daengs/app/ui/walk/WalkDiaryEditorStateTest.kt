package com.daengs.app.ui.walk

import androidx.compose.runtime.saveable.SaverScope
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WalkDiaryEditorStateTest {
    private val summary = WalkSummary("s", listOf("dog"), 100, 10000, null, 100.0, 9900, emptyList(), null)
    private val point = WalkRoutePoint(GeoPoint(37.5, 127.0), 4000, 3f, 3900, 20.0, 1.5, 0, 2)

    @Test fun `selected revisit supplies the exact saved time location accuracy and pet`() {
        val state = WalkDiaryEditorState("s")
        state.beginAdding(summary, true)
        state.choosePoint(point)
        val revisit = point.copy(capturedAtMillis = 8000, accuracyMeters = 5f, pointIndex = 4)
        state.choosePoint(revisit)
        state.chooseType(WalkMomentType.NOTE, "dog")
        val entry = requireNotNull(state.entry)
        assertTrue(state.adding); assertTrue(state.editorOpen)
        assertEquals("s", entry.sessionId); assertEquals("dog", entry.petId)
        assertEquals(revisit.point, entry.point)
        assertEquals(8000L, entry.recordedAtMillis)
        assertEquals(8000L, entry.locationCapturedAtMillis)
        assertEquals(5f, entry.accuracyMeters)
    }

    @Test fun `cancel keeps location picking while successful save exits adding`() {
        val state = WalkDiaryEditorState("s")
        state.beginAdding(summary, true); state.choosePoint(point); state.chooseType(WalkMomentType.NOTE, null)
        state.dismissEntry()
        assertTrue(state.adding); assertFalse(state.editorOpen); assertNull(state.chosenPoint)
        state.choosePoint(point); state.chooseType(WalkMomentType.NOTE, null)
        state.entrySaved()
        assertFalse(state.adding); assertFalse(state.editorOpen); assertNull(state.chosenPoint)
    }

    @Test fun `route-less addition opens a note at session start without inventing a location`() {
        val state = WalkDiaryEditorState("s")
        state.beginAdding(summary, false)
        assertTrue(state.editorOpen); assertFalse(state.adding)
        assertEquals(WalkMomentType.NOTE, state.entry!!.type)
        assertEquals(100L, state.entry!!.recordedAtMillis)
        assertNull(state.entry!!.point); assertNull(state.entry!!.locationCapturedAtMillis)
    }

    @Test fun `leaving adding or switching to explorer removes the selected point`() {
        val state = WalkDiaryEditorState("s")
        state.beginAdding(summary, true); state.choosePoint(point)
        state.beginAdding(summary, true)
        assertFalse(state.adding); assertNull(state.chosenPoint)
        state.beginAdding(summary, true); state.choosePoint(point); state.cancelAdding()
        assertFalse(state.adding); assertNull(state.chosenPoint)
    }

    @Test fun `missing session clears every open target`() {
        val state = populated()
        state.clear()
        assertFalse(state.adding); assertFalse(state.editorOpen)
        assertNull(state.chosenPoint); assertNull(state.entry)
        assertNull(state.editingScene); assertNull(state.photo)
    }

    @Test fun `restoration retains only adding and never reopens stale targets`() {
        val saver = WalkDiaryEditorState.saver("s")
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(saver) { scope.save(populated()) }!!
        val restored = saver.restore(saved)!!
        assertTrue(restored.adding); assertFalse(restored.editorOpen)
        assertNull(restored.chosenPoint); assertNull(restored.entry)
        assertNull(restored.editingScene); assertNull(restored.photo)
    }

    private fun populated() = WalkDiaryEditorState("s").apply {
        beginAdding(summary, true); choosePoint(point); chooseType(WalkMomentType.NOTE, null)
        editScene(DiaryScene("s/start", "s", 100, "시작", "본문", null, ""))
        showPhoto(WalkPhoto("photo", "s", 4000, point.point, File("unused.jpg")))
    }
}
