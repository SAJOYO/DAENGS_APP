package com.daengs.app.walk.diary

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GeoStoryboardBundleTest {
    @get:Rule val folder = TemporaryFolder()
    private fun fixture(name: String) = javaClass.getResource("/storyboard/$name.json")!!.readText()
    private fun bundle(name: String) = GeoStoryboardBundle.parse(fixture(name))

    @Test fun `geo fixtures expose environment movement and unknown gaps without adding actions`() {
        listOf("pinless", "clustered", "gap", "movement", "updated").forEach {
            assertTrue(bundle(it).synthetic)
        }
        assertTrue(bundle("pinless").scenes.any { it.evidence.contains("환경") || it.evidence.contains("commerce") })
        assertFalse(bundle("pinless").scenes.any { it.title == "짖기" || it.title == "킁킁" })
        assertTrue(bundle("gap").scenes.any { it.body.contains("이동 경로는 확인할 수 없어요") })
        assertTrue(bundle("movement").scenes.any { it.evidence.contains("세션 속도 변화") })
    }

    @Test fun `refresh preserves edits visibility and reviewed snapshot across restart`() {
        val old = bundle("clustered")
        val memo = old.scenes.single { it.title == "특별한 순간" }
        val bark = old.scenes.single { it.title == "짖기" }
        val sniff = old.scenes.single { it.title == "킁킁" }
        var draft = StoryboardDraft().edit(memo, body = "내가 쓴 문장")
            .edit(bark, body = "삭제해도 문장은 보존").edit(sniff, hidden = true)
        val snapshot = storyboardSnapshot(old.sessionId, applyStoryboardEdits(old.scenes, draft))
        draft = draft.copy(reviewed = snapshot)
        val dir = folder.newFolder()
        GeoStoryboardLabStore(dir).save(old, draft)
        val next = bundle("updated")
        val store = GeoStoryboardLabStore(dir)
        store.save(next, store.load(next.sessionId)!!.draft)
        val saved = GeoStoryboardLabStore(dir).load(next.sessionId)!!
        assertEquals(next.sourceRevision, saved.bundle.sourceRevision)
        val scenes = applyStoryboardEdits(saved.bundle.scenes, saved.draft)
        assertEquals("내가 쓴 문장", scenes.single { it.id == memo.id }.body)
        assertTrue(scenes.single { it.id == memo.id }.needsReview)
        assertTrue(scenes.single { it.id == sniff.id }.hidden)
        assertFalse(scenes.toString(), scenes.single { it.id == bark.id }.available)
        assertEquals(snapshot, saved.draft.reviewed)
        val current = storyboardSnapshot(next.sessionId, scenes)
        assertNotEquals(snapshot, current)
        assertFalse(current.contains(bark.id))
        assertTrue(current.contains("source_payload"))
        assertNull(store.load(bundle("pinless").sessionId))
        val acknowledged = saved.draft.edit(scenes.single { it.id == memo.id }, acknowledge = true)
        assertFalse(applyStoryboardEdits(next.scenes, acknowledged).single { it.id == memo.id }.needsReview)
    }

    @Test fun `malformed imports are rejected`() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("format", "v2") },
            { it.put("synthetic", "true") },
            { it.getJSONArray("scenes").getJSONObject(1).put("id", it.getJSONArray("scenes").getJSONObject(0).getString("id")) },
            { it.getJSONArray("scenes").getJSONObject(0).getJSONArray("facts").getJSONObject(0).put("source_ids", org.json.JSONArray(listOf("missing"))) },
            { it.getJSONArray("scenes").getJSONObject(1).getJSONObject("route").put("end_m", -1) },
            { it.getJSONArray("scenes").getJSONObject(0).put("started_at", "2030-01-01T00:00:00Z") },
        )
        mutations.forEach { mutate ->
            val obj = JSONObject(fixture("pinless")); mutate(obj)
            assertTrue(runCatching { GeoStoryboardBundle.parse(obj.toString()) }.isFailure)
        }
    }

    @Test fun `changed payload is detected even when producer reuses revision`() {
        val obj = JSONObject(fixture("pinless"))
        val before = GeoStoryboardBundle.parse(obj.toString())
        obj.getJSONArray("scenes").getJSONObject(0).getJSONArray("facts").getJSONObject(0).put("text", "정정")
        val after = GeoStoryboardBundle.parse(obj.toString())
        assertEquals(before.scenes.first().id, after.scenes.first().id)
        assertNotEquals(before.scenes.first().fingerprint, after.scenes.first().fingerprint)
    }

    @Test fun `JSON serialization does not invent a source revision change`() {
        val original = bundle("clustered")
        val reserialized = GeoStoryboardBundle.parse(JSONObject(original.rawJson).toString())
        assertEquals(original.scenes.map { it.fingerprint }, reserialized.scenes.map { it.fingerprint })
    }
}
