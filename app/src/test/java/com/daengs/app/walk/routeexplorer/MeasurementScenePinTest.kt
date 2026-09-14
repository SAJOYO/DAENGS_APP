package com.daengs.app.walk.routeexplorer

import android.app.Application
import com.daengs.app.walk.*
import com.daengs.app.walk.pin.ActionPin
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MeasurementScenePinTest {
    @Test fun `estimated action binds only within the section owning all of its source evidence`() {
        val detail = measuredSceneDetail(secondVisit = true)
        val scene = measuredScene(detail, 7).copy(source = null, entryId = "action", atMillis = 15_000)
        fun entry(seqs: List<Int>): WalkEntry {
            val payload = JSONObject().put("state", "resolved").put("method", "estimated")
                .put("target_at", Instant.ofEpochMilli(scene.atMillis)).put("point", JSONObject()
                    .put("lat", scene.point!!.latitude).put("lng", scene.point.longitude))
                .put("source_refs", JSONArray().apply { seqs.forEach { seq ->
                    val f = detail.observations.single { it.clientSeq == seq }
                    put(JSONObject().put("client_seq", seq).put("chain_index", f.chainIndex)
                        .put("at", Instant.ofEpochMilli(f.atMillis)))
                } })
            return WalkEntry("action", "measured", WalkMomentType.NOTE, scene.atMillis, scene.point, scene.atMillis,
                pin = ActionPin(payload.toString()))
        }
        val review = CompletedRouteReview(detail)
        val focus = review.recordSceneFocus(scene, entry(listOf(7, 8)))
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals("section-1", focus.binding!!.sectionId)
        assertEquals(7, focus.binding.sourceStart!!.clientSeq)
        assertEquals(8, focus.binding.sourceEnd!!.clientSeq)
        assertNull(focus.binding.locationSource)
        assertTrue(review.recordSceneFocus(scene, entry(listOf(2, 8))).paths.isEmpty())
        assertTrue(review.recordSceneFocus(scene, entry(emptyList())).paths.isEmpty())
    }

    @Test fun `action receipt selects its actual visit and preserves an older captured location`() {
        val detail = measuredSceneDetail(secondVisit = true)
        val scene = measuredScene(detail, 7).copy(source = null, entryId = "action")
        fun entry(at: Long = scene.atMillis, seq: Int = 7, method: String = "observed"): WalkEntry {
            val f = detail.observations.single { it.clientSeq == seq }
            val payload = JSONObject().put("state", "resolved").put("method", method)
                .put("target_at", Instant.ofEpochMilli(at)).put("point", JSONObject()
                    .put("lat", scene.point!!.latitude).put("lng", scene.point.longitude))
                .put("source_refs", JSONArray().put(JSONObject().put("client_seq", seq)
                    .put("chain_index", f.chainIndex).put("at", Instant.ofEpochMilli(f.atMillis))))
            return WalkEntry("action", "measured", WalkMomentType.NOTE, at, scene.point, f.atMillis,
                note = "수정한 행동 설명", pin = ActionPin(payload.toString()))
        }
        val review = CompletedRouteReview(detail)
        val focus = review.recordSceneFocus(scene, entry())
        assertEquals(SceneRouteRelation.CONNECTED, focus.relation)
        assertEquals("section-1", focus.binding!!.sectionId)
        assertEquals(7, focus.binding.locationSource!!.clientSeq)
        val later = scene.copy(atMillis = scene.atMillis + 1_000)
        val old = review.recordSceneFocus(later, entry(later.atMillis))
        assertEquals(SceneRouteRelation.EARLIER_LOCATION, old.relation)
        assertEquals(scene.atMillis, old.binding!!.locationAtMillis)
        assertEquals(later.atMillis, old.binding.eventAtMillis)
        assertTrue(old.paths.isEmpty())
        assertTrue(review.recordSceneFocus(later, entry()).paths.isEmpty())
        val malformed = entry().let { it.copy(pin = ActionPin(JSONObject(it.pin!!.payload)
            .put("source_refs", JSONArray().put(JSONObject().put("client_seq", 99))).toString())) }
        assertTrue(review.recordSceneFocus(scene, malformed).paths.isEmpty())
        assertEquals("수정한 행동 설명", entry().note)
    }
}
