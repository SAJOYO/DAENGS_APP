package com.daengs.app.walk.diary

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StoryboardEvidenceTest {
    private fun fixture(name: String) = javaClass.getResource("/storyboard/$name.json")!!.readText()

    @Test fun `pet reassignment preserves prose and requires acknowledgement before snapshot`() {
        val old = GeoStoryboardBundle.parse(fixture("v2-before"))
        val updated = GeoStoryboardBundle.parse(fixture("v2-after"))
        val scene = old.scenes.first { it.entryReference != null }
        val draft = StoryboardDraft().edit(scene, title = "A와 함께", body = "보호자가 쓴 이야기")
        val oldSnapshot = storyboardSnapshot(old.sessionId, applyStoryboardEdits(old.scenes, draft))
        val saved = draft.copy(reviewed = oldSnapshot)
        val current = applyStoryboardEdits(updated.scenes, saved)
        val changed = current.first { it.id == scene.id }
        assertEquals("pet-b", changed.entryReference!!.petId)
        assertEquals(2L, changed.entryReference.revision)
        assertEquals("A와 함께", changed.title)
        assertEquals("보호자가 쓴 이야기", changed.body)
        assertTrue(changed.needsReview)
        assertEquals(oldSnapshot, saved.reviewed)
        assertNotEquals(oldSnapshot, storyboardSnapshot(updated.sessionId, current))
        val acknowledged = saved.edit(changed, acknowledge = true)
        assertFalse(applyStoryboardEdits(updated.scenes, acknowledged).first { it.id == scene.id }.needsReview)
        val snapshot = JSONObject(storyboardSnapshot(updated.sessionId, applyStoryboardEdits(updated.scenes, acknowledged)))
        val records = snapshot.getJSONArray("scenes")
        val frozen = (0 until records.length()).map { records.getJSONObject(it) }.first { it.getString("id") == scene.id }
        assertEquals("pet-b", frozen.getJSONObject("source_payload").getJSONObject("entry").getString("pet_id"))
        assertEquals(2, frozen.getJSONObject("source_payload").getJSONObject("entry").getInt("revision"))
    }

    @Test fun `shortfall and deferred lookups are explicit without losing action records`() {
        val short = GeoStoryboardBundle.parse(fixture("v2-short"))
        assertFalse(short.selection!!.minimumMet)
        assertTrue(short.selection.description().contains("유효 관측 구간이 부족"))
        val budget = GeoStoryboardBundle.parse(fixture("v2-budget"))
        assertEquals(8, budget.selection!!.selectedCount)
        assertEquals(1, budget.selection.deferredActionCount)
        assertFalse(budget.selection.coverageMet)
        assertEquals(9, budget.scenes.count { it.entryReference != null })
        assertTrue(budget.selection.description().contains("행동 기록은 남아"))
        assertNull(GeoStoryboardBundle.parse(fixture("pinless")).selection) // Cached v1 is still readable.
    }

    @Test fun `v2 rejects malformed attribution and selection even when producer revision repeats`() {
        val edits: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONObject("selection").put("minimum_met", false) },
            { it.getJSONObject("selection").put("selected_count", 2.5) },
            { it.getJSONObject("selection").put("longest_unread_m", -1) },
            { obj ->
                val scenes = obj.getJSONArray("scenes")
                (0 until scenes.length()).map { scenes.getJSONObject(it) }.first { !it.isNull("entry") }
                    .getJSONObject("entry").put("revision", 0)
            },
        )
        for (edit in edits) {
            val data = JSONObject(fixture("v2-before"))
            edit(data)
            assertTrue(runCatching { GeoStoryboardBundle.parse(data.toString()) }.isFailure)
        }
    }
}
