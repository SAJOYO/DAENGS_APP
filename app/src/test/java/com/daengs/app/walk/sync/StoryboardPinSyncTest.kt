package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.diary.GeoStoryboardBundle
import com.daengs.app.walk.store.storedStoryboardAnalysisView
import com.daengs.app.walk.store.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StoryboardPinSyncTest {
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private var owner = "owner"
    private val at = "1970-01-01T00:00:05Z"
    @Before fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        dao = db.walkDao()
        dao.insertSession(WalkSessionRow("s", 0, owner, 10000))
        val entry = WalkEntry(id = "e", sessionId = "s", type = WalkMomentType.SNIFFING, recordedAtMillis = 5000)
        dao.insertEntry(WalkEntryRow("e", "s", entry.toJson().toString(), 1, "mutation", false,
            pinPayload = JSONObject().put("state", "resolved").toString(), pinRevision = 1, isV2 = true))
    }
    @After fun close() = db.close()
    private fun caps() = JSONObject().put("storyboard_formats", JSONArray(listOf(GeoStoryboardBundle.FORMAT_V5)))
    private fun bundle(): JSONObject = JSONObject("""{
        "format":"walk-storyboard-candidates-v5","session_id":"s","source_revision":"r","synthetic":false,
        "title":null,"title_fact_ids":[],"selection":{"minimum_target":1,"selected_count":0,"minimum_met":false,
        "shortfall_reason":"insufficient_distinct_valid_route","coverage_met":true,"longest_unread_m":0,"max_unread_m":300,"max_anchors":8,"deferred_action_count":0},
        "scenes":[{"id":"entry:e","revision":"r","started_at":"$at","ended_at":"$at","route":null,
        "reasons":["action"],"title":"킁킁","facts":[{"id":"f","kind":"action","text":"킁킁 기록","source_ids":["local"]}],
        "sources":[{"id":"local","provider":"walk-observation","status":"known","captured_at":null,"url":null}],
        "entry":{"entry_id":"e","revision":1,"pet_id":null},"observation":null,
        "pin":{"revision":1,"resolution_id":"pin-id","state":"resolved","method":"estimated","target_at":"$at",
        "point":{"lat":37.5,"lng":127.0},"uncertainty_m":null,"uncertainty_basis":"unknown"}}]}
    """)
    private fun response(b: JSONObject = bundle()) = JSONObject().put("session_id", "s").put("generation", 1)
        .put("input_revision", "r").put("entry_revisions", JSONObject().put("e", 1)).put("status", "ready").put("bundle", b)

    @Test fun opaqueTokenAndV5PinBecomeReviewableWithoutRawObservation(): Unit = runBlocking {
        WalkStoryboardSync(dao, { owner }, { caps() }) { token, _, body ->
            assertEquals("opaque.encrypted.jwe.token.value", token)
            assertEquals(GeoStoryboardBundle.FORMAT_V5, body.getString("bundle_format"))
            response()
        }.sync("opaque.encrypted.jwe.token.value", "s", "walk")
        val view = storedStoryboardAnalysisView(dao.sceneAnalysis("s"), dao.entries("s"))
        assertTrue(view.canReview)
        assertTrue(view.bundle!!.scenes.single().evidence.contains("추정 위치"))
        assertNull(view.bundle.scenes.single().observation)
        assertTrue(view.bundle.scenes.single().sourcePayload!!.contains("pin-id"))
    }

    @Test fun unsupportedServerKeepsLocalEntryWithoutPosting(): Unit = runBlocking {
        var posts = 0
        val sync = WalkStoryboardSync(dao, { owner }, { JSONObject() }) { _, _, _ -> posts++; response() }
        assertTrue(runCatching { sync.sync("opaque", "s", "walk") }.isFailure)
        assertEquals(0, posts)
        assertNotNull(dao.entry("e")!!.payload)
    }

    @Test fun pinRequestNeverDowngradesWhenV5IsRejected(): Unit = runBlocking {
        var posts = 0
        val sync = WalkStoryboardSync(dao, { owner }, { caps() }) { _, _, _ ->
            posts++
            throw WalkHttpException(422, """[{"type":"literal_error","loc":["body","bundle_format"]}]""")
        }
        assertTrue(runCatching { sync.sync("opaque", "s", "walk") }.isFailure)
        assertEquals(1, posts)
    }

    @Test fun pendingPinBlocksAnalysisBeforeCapabilityOrPost(): Unit = runBlocking {
        dao.updatePinRow(dao.entry("e")!!.copy(pinPayload = """{"state":"provisional"}"""))
        var calls = 0
        val sync = WalkStoryboardSync(dao, { owner }, { calls++; caps() }) { _, _, _ -> calls++; response() }
        assertTrue(runCatching { sync.sync("opaque", "s", "walk") }.isFailure)
        assertEquals(0, calls)
    }

    @Test fun accountSwitchDuringNegotiationDoesNotPost(): Unit = runBlocking {
        var posts = 0
        WalkStoryboardSync(dao, { owner }, { owner = "other"; caps() }) { _, _, _ -> posts++; response() }
            .sync("opaque", "s", "walk")
        assertEquals(0, posts)
        assertNull(dao.sceneAnalysis("s"))
    }

    @Test fun unlocatedPinIsReadableAndMalformedProvenanceIsRejected() {
        val b = bundle()
        val pin = b.getJSONArray("scenes").getJSONObject(0).getJSONObject("pin")
        pin.put("state", "unlocated").put("method", "none").put("point", JSONObject.NULL)
        assertTrue(GeoStoryboardBundle.parse(b.toString()).scenes.single().evidence.contains("위치 없이 남긴 행동"))
        pin.put("point", "invalid")
        assertTrue(runCatching { GeoStoryboardBundle.parse(b.toString()) }.isFailure)
        pin.put("point", JSONObject.NULL)
        pin.put("target_at", "1970-01-01T00:00:06Z")
        assertTrue(runCatching { GeoStoryboardBundle.parse(b.toString()) }.isFailure)
        pin.put("target_at", at).put("state", "resolved").put("method", "estimated")
        assertTrue(runCatching { GeoStoryboardBundle.parse(b.toString()) }.isFailure)
    }
}
