package com.daengs.app.walk.detail

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daengs.app.BuildConfig
import com.daengs.app.DaengsApp
import com.daengs.app.ui.walk.WalkDetailState
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import com.daengs.app.walk.sync.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Explicit live opt-in. Auth stays inside the logged-in app; only a new test note is mutated. */
@RunWith(AndroidJUnit4::class)
class WalkDetailLiveDeviceTest {
    @Test fun savedNoteRetriesDeliveryReadsBackAndDeletesOnServer() = runBlocking {
        check(BuildConfig.APPLICATION_ID == "com.daengs.app.devtest")
        check(InstrumentationRegistry.getArguments().getString("allowLiveNote") == "true") {
            "Requires explicit authorization to create and delete one temporary server note"
        }
        val app = ApplicationProvider.getApplicationContext<DaengsApp>()
        val auth = requireNotNull(app.sessionProvider.freshSession()) { "Sign in to the development app first" }
        val account = app.sessionProvider.accountScope.value
        check(account.ownerId == auth.appUserId)
        val remote = WalkApi.list(auth.accessToken).getOrThrow().firstOrNull()
            ?: error("A completed walk is required; this test never creates a walk")
        val id = UUID.randomUUID().toString()
        val note = WalkEntry(id, remote.clientSessionId, WalkMomentType.NOTE,
            remote.startedAtMillis, note = "DAENGS detail integration temporary note $id")
        val cleanup = File(app.filesDir, "detail-live-review-cleanup.json")
        check(!cleanup.exists()) { "Resolve the previous live review cleanup ticket before another run" }
        cleanup.writeText(JSONObject().put("entry", id).put("walk", remote.id)
            .put("session", remote.clientSessionId).put("owner", auth.appUserId).toString())
        var db = Room.inMemoryDatabaseBuilder(app, WalkDatabase::class.java).build()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        var failDelivery = true
        val delivered = CompletableDeferred<Unit>()
        suspend fun seed() = db.walkDao().insertSession(WalkSessionRow(remote.clientSessionId,
            remote.startedAtMillis, auth.appUserId, remote.endedAtMillis, serverWalkId = remote.id))
        fun data(): StoredWalkDetailData {
            val dao = db.walkDao()
            val owner = { app.sessionProvider.accountScope.value.ownerId.orEmpty() }
            val sync = WalkEntrySync(dao, WalkEntryV2Sync(dao, owner), preferLegacy = true, owner = owner)
            return StoredWalkDetailData(remote.clientSessionId, account, { app.sessionProvider.accountScope.value },
                WalkHistory(RoomWalkFixLog(dao, owner = owner)), dao, WalkEntryStore(dao, owner),
                WalkPhotoStore(dao, File(app.cacheDir, "detail-live-review"), owner = owner), {}, {
                    if (failDelivery) error("fixture scheduler unavailable")
                    sync.sync(auth.accessToken, remote.clientSessionId, remote.id)
                    delivered.complete(Unit)
                }, app.sessionProvider::freshSession, { _, _ -> }, { _, _, _ -> })
        }
        suspend fun remoteNote(): JSONObject? {
            val result = try { WalkApi.call(auth.accessToken, "/${remote.id}/entries", "GET", parse = ::JSONObject).getOrThrow() }
            catch (e: WalkHttpException) {
                if (e.statusCode != 426) throw e
                WalkApi.call(auth.accessToken, "/${remote.id}/entries", "GET", v2 = true, parse = ::JSONObject).getOrThrow()
            }
            val rows = result.getJSONArray("entries")
            return (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.getString("id") == id }
        }
        try {
            seed()
            val initial = data()
            val state = WalkDetailState(initial, initial, scope, {})
            val committed = CompletableDeferred<Unit>()
            state.saveEntry(note) { committed.complete(Unit) }
            withTimeout(15_000) { committed.await() }
            val saved = requireNotNull(db.walkDao().entry(id))
            assertTrue(saved.dirty); assertNotNull(state.error); assertNull(state.entryError)
            failDelivery = false
            state.retry()
            withTimeout(60_000) { delivered.await() }
            val acknowledged = requireNotNull(db.walkDao().entry(id))
            assertFalse(acknowledged.dirty)
            assertEquals(saved.mutationId, acknowledged.mutationId)
            assertEquals(note.note, remoteNote()!!.getJSONObject("content").getString("note"))

            // A fresh local store imports the HTTP result, with no cached copy of the test note.
            scope.coroutineContext.cancelChildren()
            db.close()
            db = Room.inMemoryDatabaseBuilder(app, WalkDatabase::class.java).build()
            seed()
            data().open()
            assertEquals(note.note, db.walkDao().entry(id)!!.entry()!!.note)
            assertFalse(db.walkDao().entry(id)!!.dirty)
            data().deleteEntry(id)
            assertNull(db.walkDao().entry(id)!!.payload)
            assertFalse(db.walkDao().entry(id)!!.dirty)
            assertTrue(remoteNote()?.isNull("content") != false)
        } finally {
            withContext(NonCancellable) { withTimeout(60_000) {
                failDelivery = false
                // Clean only this run's generated ID, including after an assertion failure.
                if (db.isOpen) {
                    data().open()
                    if (db.walkDao().entry(id)?.payload != null) data().deleteEntry(id)
                    check(remoteNote()?.isNull("content") != false) { "Temporary server note cleanup failed" }
                    check(cleanup.delete())
                    db.close()
                }
                scope.cancel()
            } }
        }
    }
}
