package com.daengs.app.walk.pin

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import com.daengs.app.walk.sync.storyboardEntryStamp
import com.daengs.app.walk.sync.WalkEntryV2Sync
import org.json.JSONArray
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ActionPinReviewTest {
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var pins: ActionPinStore
    private var time = 10_000L
    private var activeSession: String? = null
    @Before fun open(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        dao = db.walkDao()
        dao.insertSession(WalkSessionRow("s", 0, "owner", null))
        pins = ActionPinStore(dao, { "owner" }, { time }, { activeSession })
    }
    @After fun close() = db.close()
    private suspend fun create() {
        pins.create("e", ActionPinRequest(UUID.randomUUID(), "owner", "s", 0, time), WalkMomentType.SNIFFING, null)
    }
    private fun remote(row: WalkEntryRow, revision: Int = row.revision) = JSONObject()
        .put("id", row.id).put("revision", revision).put("pin_revision", row.pinRevision)
        .put("mutation_id", row.mutationId).put("deleted", false)
        .put("content", JSONObject(row.payload!!)).put("pin", row.pinPayload?.let(::JSONObject) ?: JSONObject.NULL)

    @Test fun legacyNoteReadPreservesLegacyStamp(): Unit = runBlocking {
        val entry = WalkEntry(id = "note", sessionId = "s", type = WalkMomentType.NOTE, recordedAtMillis = time, note = "old note")
        val row = WalkEntryRow(entry.id, "s", entry.toJson().toString(), 1, UUID.randomUUID().toString(), false)
        dao.insertEntry(row)
        val before = storyboardEntryStamp(dao.entries("s"))
        dao.acceptPinRemote("s", remote(row).toString(), "owner")
        assertEquals("Reading unchanged legacy note must preserve cached analysis", before, storyboardEntryStamp(dao.entries("s")))
    }

    @Test fun activeRecoveryMustAllowRemainingObservationWindow(): Unit = runBlocking {
        activeSession = "s"
        create()
        time = 11_000
        pins.recover() // MainActivity repeats this when its composition is recreated.
        assertEquals("provisional", dao.entry("e")!!.entry()!!.pin!!.state)
        dao.insertFix(WalkFixRow("s", 0, 0, 12_000, 37.5, 127.0, 5f, false))
        dao.insertFix(WalkFixRow("s", 1, 0, 14_000, 37.5, 127.0, 5f, false))
        dao.insertFix(WalkFixRow("s", 2, 0, 16_000, 37.5, 127.0, 5f, false))
        time = 18_000
        pins.finish("e")
        assertEquals("Active walk supplied valid fixes before deadline", "resolved", dao.entry("e")!!.entry()!!.pin!!.state)
    }

    @Test fun orphanRecoveryStillClosesBeforeDeadline(): Unit = runBlocking {
        create()
        activeSession = "other-walk"
        time = 11_000
        pins.recover()
        val pin = JSONObject(dao.entry("e")!!.pinPayload!!)
        assertEquals("unlocated", pin.getString("state"))
        assertEquals("recovered", pin.getString("reason"))
    }

    @Test fun activeRecoveryClosesExpiredPinWithoutExtendingDeadline(): Unit = runBlocking {
        create()
        activeSession = "s"
        time = 19_000
        pins.recover()
        val pin = JSONObject(dao.entry("e")!!.pinPayload!!)
        assertEquals("unlocated", pin.getString("state"))
        assertEquals("deadline", pin.getString("reason"))
        assertEquals("1970-01-01T00:00:18Z", pin.getString("resolve_by"))
    }

    @Test fun noteReadPreservesKnownV2AndRequiresServerEvidenceToUpgradeLegacy(): Unit = runBlocking {
        val entry = WalkEntry(id = "note", sessionId = "s", type = WalkMomentType.NOTE, recordedAtMillis = time, note = "note")
        val row = WalkEntryRow(entry.id, "s", entry.toJson().toString(), 1, UUID.randomUUID().toString(), false)
        val response = remote(row).toString()
        dao.acceptPinRemote("s", response, "owner")
        assertFalse(dao.entry("note")!!.isV2) // A downloaded null pin alone proves nothing.
        dao.rebaseLegacyEntry("note", response, "owner", requiresV2 = true)
        assertTrue(dao.entry("note")!!.isV2) // A 426 prevents an endless v1 retry loop.
        dao.acceptPinRemote("s", response, "owner")
        assertTrue(dao.entry("note")!!.isV2)
    }

    @Test fun pinConflictMustNotSilentlyRebaseConcurrentContentEdit(): Unit = runBlocking {
        create()
        val initial = dao.entry("e")!!
        dao.updatePinRow(initial.copy(revision = 1, pinRevision = 1, dirty = false, pinDirty = false))
        val server = dao.entry("e")!!
        time = 18_000
        pins.finish("e")
        val sent = dao.preparePinRequest("e", "owner")!! // pin finalization in flight
        WalkEntryStore(dao).save(dao.entry("e")!!.entry()!!.copy(type = WalkMomentType.BARKING))
        val changedElsewhere = remote(server, 2)
        changedElsewhere.getJSONObject("content").put("behavior_code", "excretion")
        dao.conflictPinRequest("e", sent, changedElsewhere.toString(), "owner")
        assertNotNull("Remote content changed while local correction was based on revision 1", dao.entry("e")!!.syncError)
        val sync = WalkEntryV2Sync(dao, { "owner" }) { _, path, method, _, _ ->
            assertEquals("Conflicting correction must wait for user review", "GET", method)
            if (path == "/entry-capabilities") JSONObject().put("read_versions", JSONArray(listOf("walk-entry-v2")))
            else JSONObject().put("entries", JSONArray().put(changedElsewhere))
        }
        sync.sync("token", "s", "walk")
        assertEquals(WalkMomentType.BARKING, dao.entry("e")!!.entry()!!.type)
        assertTrue(dao.entry("e")!!.dirty)
        assertNotNull(dao.entry("e")!!.syncError)
    }

    @Test fun pinOnlyConflictDoesNotBlockContentWhenServerOnlyNormalizesJson(): Unit = runBlocking {
        create()
        dao.updatePinRow(dao.entry("e")!!.copy(revision = 1, pinRevision = 1, dirty = false, pinDirty = false))
        val server = dao.entry("e")!!
        time = 18_000
        pins.finish("e")
        val sent = dao.preparePinRequest("e", "owner")!!
        WalkEntryStore(dao).save(dao.entry("e")!!.entry()!!.copy(type = WalkMomentType.BARKING))
        val normalized = remote(server, 2)
        normalized.getJSONObject("content").put("recorded_at", "1970-01-01T00:00:10+00:00")
        dao.conflictPinRequest("e", sent, normalized.toString(), "owner")
        assertNull(dao.entry("e")!!.syncError)
        val next = JSONObject(dao.preparePinRequest("e", "owner")!!)
        assertEquals("content", next.getString("kind"))
        assertEquals(2, next.getJSONObject("body").getInt("expected_revision"))
    }
}
