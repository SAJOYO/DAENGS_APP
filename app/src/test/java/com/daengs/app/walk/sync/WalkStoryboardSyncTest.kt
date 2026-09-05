package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.store.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkStoryboardSyncTest {
    private val owner = "owner"
    private val token = "header."+java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"sub\":\"owner\"}".toByteArray())+".signature"
    private fun response(generation: Long = 1): JSONObject {
        val bundle = JSONObject(javaClass.getResource("/storyboard/pinless.json")!!.readText())
            .put("session_id", "s").put("synthetic", false)
        return JSONObject().put("session_id", "s").put("generation", generation).put("input_revision", "revision")
            .put("status", "ready").put("entry_revisions", JSONObject()).put("bundle", bundle)
    }

    @Test fun `real response persists separately from edits and survives reading again`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            dao.saveStoryboard(WalkStoryboardRow("s", "existing user draft"))
            val sync = WalkStoryboardSync(dao, { owner }) { _, path, body ->
                assertEquals("/remote/storyboard", path)
                assertEquals(0, body.getJSONObject("expected_entries").length())
                response()
            }
            sync.sync(token, "s", "remote")
            assertEquals("ready", dao.sceneAnalysis("s")!!.status)
            assertEquals("existing user draft", dao.storyboard("s")!!.payload)
            assertFalse(JSONObject(dao.sceneAnalysis("s")!!.bundle!!).getBoolean("synthetic"))
            dao.deleteSession("s")
            assertNull(dao.sceneAnalysis("s"))
        } finally { db.close() }
    }

    @Test fun `source mutation during request rejects late result and preserves edits`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            val sync = WalkStoryboardSync(dao, { owner }) { _, _, _ ->
                dao.insertEntry(WalkEntryRow("new", "s", "{}", 0, "mutation", true))
                response()
            }
            assertTrue(runCatching { sync.sync(token, "s", "remote") }.isFailure)
            assertNotEquals("ready", dao.sceneAnalysis("s")!!.status)
            assertNull(dao.sceneAnalysis("s")!!.bundle)
        } finally { db.close() }
    }

    @Test fun `older generation and other owner cannot replace current source`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            val row = WalkSceneAnalysisRow("s", 5, storyboardEntryStamp(emptyList()), "new", "ready", "{}", null)
            assertTrue(dao.acceptSceneAnalysis(row, owner))
            assertFalse(dao.acceptSceneAnalysis(row.copy(generation = 4), owner))
            assertFalse(dao.acceptSceneAnalysis(row.copy(generation = 6), "other"))
            var calls = 0
            WalkStoryboardSync(dao, { "other" }) { _, _, _ -> calls++; response() }.sync(token, "s", "remote")
            assertEquals(0, calls)
            assertEquals(5L, dao.sceneAnalysis("s")!!.generation)
        } finally { db.close() }
    }

    @Test fun `failed analysis can retry but malformed or synthetic response cannot become ready`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalkDatabase::class.java).build()
        try {
            val dao = db.walkDao()
            dao.insertSession(WalkSessionRow("s", 0, endedAtMillis = 10000, ownerId = owner))
            var synthetic = true
            val sync = WalkStoryboardSync(dao, { owner }) { _, _, _ -> response().also {
                it.getJSONObject("bundle").put("synthetic", synthetic)
            } }
            assertTrue(runCatching { sync.sync(token, "s", "remote") }.isFailure)
            assertEquals("failed", dao.sceneAnalysis("s")!!.status)
            synthetic = false
            sync.sync(token, "s", "remote")
            assertEquals("ready", dao.sceneAnalysis("s")!!.status)
        } finally { db.close() }
    }
}
