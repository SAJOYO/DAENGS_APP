package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.diary.relational.*
import com.daengs.app.walk.sync.diaryInputStamp
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelationalDiaryStorageTest {
    private val raw get() = relationalFixture().toString()
    private val response get() = RelationalDiaryResponse.parse(raw)
    private val id get() = response.sessionId

    private suspend fun seed(dao: WalkDao) {
        dao.insertSession(WalkSessionRow(id, 0, endedAtMillis = 10000, ownerId = "owner", serverWalkId = "remote"))
        response.entryRevisions.forEach { (entryId, revision) ->
            dao.insertEntry(WalkEntryRow(entryId, id, "{}", revision.toInt(), "mutation", false))
        }
    }

    private fun checkDb(block: suspend (WalkDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try { seed(db.walkDao()); block(db.walkDao()) } finally { db.close() }
    }

    @Test fun `Room reopen preserves response`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        // Keep the native SQLite path short on Windows/Robolectric (including its journal suffix).
        val name = "relational.db"
        var db = Room.databaseBuilder(context, WalkDatabase::class.java, name).build()
        try {
            seed(db.walkDao())
            val stamp = db.walkDao().relationalDiaryInputStamp(id)
            assertTrue(db.walkDao().acceptRelationalDiary(raw, id, "remote", "owner", stamp))
            assertNull(db.walkDao().diaryPublication(id))
            db.close()
            db = Room.databaseBuilder(context, WalkDatabase::class.java, name).build()
            val saved = db.walkDao().readRelationalDiary(id, "owner")!!
            assertEquals(raw, saved.latest.rawJson)
            assertEquals(raw, saved.published!!.rawJson)
            assertEquals("", saved.published.bundle!!.cards.last().body)
            assertNull(db.walkDao().readRelationalDiary(id, "other-owner"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `edits ownership and mismatched revisions cannot store or resurrect old originals`() = checkDb { dao ->
        val stamp = dao.relationalDiaryInputStamp(id)
        assertFalse(dao.acceptRelationalDiary(raw, id, "remote", "other-owner", stamp))
        assertFalse(dao.acceptRelationalDiary(raw, id, "other-walk", "owner", stamp))
        val wrong = relationalFixture().put("entry_revisions", JSONObject())
        assertFalse(dao.acceptRelationalDiary(wrong.toString(), id, "remote", "owner", stamp))
        assertTrue(dao.acceptRelationalDiary(raw, id, "remote", "owner", stamp))
        dao.deletePinAwareEntry(response.entryRevisions.keys.first(), "deleted")
        assertNull(dao.readRelationalDiary(id, "owner"))
        assertFalse(dao.acceptRelationalDiary(raw, id, "remote", "owner", stamp))
    }

    @Test fun `pending and failed refresh keep publication but stale removes it`() = checkDb { dao ->
        val stamp = dao.relationalDiaryInputStamp(id)
        assertTrue(dao.acceptRelationalDiary(raw, id, "remote", "owner", stamp))
        val poll = relationalFixture().put("generation", 2).put("status", "running").put("bundle", JSONObject.NULL)
            .put("execution_limits", JSONObject().put("generation_timeout_s", 180))
        assertTrue(dao.acceptRelationalDiary(poll.toString(), id, "remote", "owner", stamp))
        assertFalse(dao.acceptRelationalDiary(raw, id, "remote", "owner", stamp))
        val running = dao.readRelationalDiary(id, "owner")!!
        assertEquals(RelationalStatus.RUNNING, running.latest.status)
        assertEquals("180", running.latest.executionLimits["generation_timeout_s"].toString())
        assertEquals(raw, running.published!!.rawJson)
        poll.put("status", "failed").put("error_code", "provider_failed")
        assertTrue(dao.acceptRelationalDiary(poll.toString(), id, "remote", "owner", stamp))
        assertNotNull(dao.readRelationalDiary(id, "owner")!!.published)
        poll.put("status", "stale")
        assertTrue(dao.acceptRelationalDiary(poll.toString(), id, "remote", "owner", stamp))
        assertNull(dao.readRelationalDiary(id, "owner")!!.published)
    }

    @Test fun `stale input cannot be restored by a delayed ready from the same generation`() = checkDb { dao ->
        val stamp = dao.relationalDiaryInputStamp(id)
        val stale = relationalFixture().put("status", "stale").put("bundle", JSONObject.NULL).put("input_revision", "changed")
        assertTrue(dao.acceptRelationalDiary(stale.toString(), id, "remote", "owner", stamp))
        assertFalse(dao.acceptRelationalDiary(raw, id, "remote", "owner", stamp))
        assertNull(dao.readRelationalDiary(id, "owner")!!.published)
    }

    @Test fun `late legacy sync and same-generation poll cannot overwrite relational ready`() = checkDb { dao ->
        val stamp = dao.relationalDiaryInputStamp(id)
        assertTrue(dao.acceptRelationalDiary(raw, id, "remote", "owner", stamp))
        val poll = relationalFixture().put("status", "running").put("bundle", JSONObject.NULL)
        assertFalse(dao.acceptRelationalDiary(poll.toString(), id, "remote", "owner", stamp))
        val old = WalkSceneAnalysisRow(id, 100, diaryInputStamp(dao.entries(id), null, emptyList()), "old", "running", null, null)
        assertFalse(dao.acceptSceneAnalysis(old, "owner"))
        assertEquals(raw, dao.readRelationalDiary(id, "owner")!!.published!!.rawJson)
    }

    @Test fun `remote photo manifest survives a read on a device without a local publisher`() = checkDb { dao ->
        val remote = relationalFixture().put("photos_status", "complete")
            .put("photo_manifest", JSONObject().put("publisher_id", "other-device").put("revision", 3))
        assertNull(dao.photoSync(id))
        assertTrue(dao.acceptRelationalDiary(remote.toString(), id, "remote", "owner", dao.relationalDiaryInputStamp(id)))
        assertEquals("other-device", dao.readRelationalDiary(id, "owner")!!.published!!.photoManifest!!.publisherId)
        assertNull(dao.photoSync(id)) // Reading does not create a local uploader or overwrite the remote manifest.
        dao.insertPhotoSync(WalkPhotoSyncRow(id, "owner", "local", 1, 0))
        assertNull(dao.readRelationalDiary(id, "owner"))
        assertFalse(dao.acceptRelationalDiary(remote.toString(), id, "remote", "owner", dao.relationalDiaryInputStamp(id)))
    }

    @Test fun `photo acknowledgement is required and malformed responses leave cache intact`() = checkDb { dao ->
        val stamp = dao.relationalDiaryInputStamp(id)
        assertTrue(dao.acceptRelationalDiary(raw, id, "remote", "owner", stamp))
        assertTrue(runCatching { dao.acceptRelationalDiary("{}", id, "remote", "owner", stamp) }.isFailure)
        assertEquals(raw, dao.readRelationalDiary(id, "owner")!!.published!!.rawJson)
        dao.insertPhotoSync(WalkPhotoSyncRow(id, "owner", "publisher", 1, 1))
        val nextStamp = dao.relationalDiaryInputStamp(id)
        assertFalse(dao.acceptRelationalDiary(raw, id, "remote", "owner", nextStamp))
        val withPhoto = relationalFixture().put("photos_status", "complete")
            .put("photo_manifest", JSONObject().put("publisher_id", "publisher").put("revision", 1))
        assertTrue(dao.acceptRelationalDiary(withPhoto.toString(), id, "remote", "owner", nextStamp))
        assertNotNull(dao.readRelationalDiary(id, "owner"))
    }
}
