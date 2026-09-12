package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkPrecisionSyncTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun cases() = WalkMotionStore.objects(JSONObject(javaClass.getResource("/walk/gps-motion-precision-v1.json")!!.readText()).getJSONArray("cases"))
    private fun detail(c: JSONObject): RemoteWalkDetail {
        val m = c.getJSONObject("manifest"); val e = m.getJSONArray("epochs")
        return RemoteWalkDetail.parse(JSONObject().put("id", REMOTE).put("client_session_id", m.getString("client_session_id"))
            .put("started_at", Instant.ofEpochMilli(e.getJSONObject(0).getLong("started_at_millis")).toString())
            .put("ended_at", Instant.ofEpochMilli(e.getJSONObject(e.length()-1).getLong("ended_at_millis")).toString())
            .put("points", c.getJSONArray("raw_points")))
    }
    private fun plan(c: JSONObject): WalkPrecisionContract.Plan {
        val d = detail(c)
        val base = WalkMotionContract.read(d.walk.toSession(1).copy(ownerId = "owner", syncState = WalkSyncState.DERIVED),
            d.fixes, c.getJSONObject("manifest"), WalkMotionStore.objects(c.getJSONArray("points")))
        return WalkPrecisionContract.read(base, c.getJSONObject("precision_manifest"), WalkMotionStore.objects(c.getJSONArray("precision_points")))
    }
    private suspend fun seed(db: WalkDatabase, p: WalkPrecisionContract.Plan, captured: Boolean = true) {
        val b = p.base; val s = b.input.session; val dao = db.walkDao()
        RoomWalkFixLog(dao, owner = { "owner" }).restoreSession(s)
        if (captured) dao.installCoordinateOrigin(s.id, "owner", "captured")
        b.input.fixes.forEach { dao.insertObservation(it.motionRow(s.id, captured)) }
        b.input.epochs.forEach { dao.saveRecordingEpoch(RecordingEpochRow.from(it)) }
        dao.insertMotionBackup(WalkMotionBackupRow(s.id, b.manifest.toString(), b.manifestHash, b.evidenceHash, 1))
    }
    private fun withDb(test: suspend (WalkDatabase) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkDatabase::class.java).build()
        try { test(db) } finally { db.close() }
    }
    private fun walking() = cases().first { it.getString("name") == "walking" }

    @Test fun `32 Python precision replays match the actual Kotlin engine and hashes`() {
        for (c in cases()) {
            val p = plan(c)
            assertEquals(c.getString("precision_fingerprint"), p.evidenceHash)
            assertEquals(c.getString("precision_manifest_fingerprint"), p.manifestHash)
            assertEquals(p.evidenceHash, WalkPrecisionContract.create(p.base).evidenceHash)
            WalkPrecisionContract.verify(p, Server(c).calculation())
        }
    }

    @Test fun `upload completion alone remains pending and next sync verifies`() = withDb { db ->
        val c = walking(); val p = plan(c); seed(db, p)
        val server = Server(c).apply { calculationOffline = true }
        val precision = WalkMotionPrecisionSync(db, { "owner" }, request = server::call)
        val sync = WalkMotionSync(db, { "owner" }, precision = precision, request = server::call)
        val id = p.base.input.session.id
        assertTrue(runCatching { sync.sync("t", id, REMOTE) }.isFailure)
        assertNotNull(db.walkDao().motionPrecision(id)!!.completedAtMillis)
        assertNull(db.walkDao().motionPrecision(id)!!.verifiedAtMillis)
        assertEquals(listOf(id), db.walkDao().pendingMotionSessions().map { it.id })
        server.calculationOffline = false
        sync.sync("t", id, REMOTE)
        assertNotNull(db.walkDao().motionPrecision(id)!!.verifiedAtMillis)
        assertTrue(db.walkDao().pendingMotionSessions().isEmpty())
        assertEquals(p.chunks.size, server.uploads)
        assertFalse(db.walkDao().motionPrecision(id)!!.verificationJson!!.contains("segments"))
    }

    @Test fun `mismatched distance time path reasons and provenance never verify`() = withDb { db ->
        val c = walking(); val p = plan(c); seed(db, p)
        for (mutate in listOf<(JSONObject) -> Unit>(
            { it.put("distance_m", 999.0) }, { it.put("recording_duration_nanos", 1) },
            { it.put("segments", JSONArray()) }, { it.put("reason_counts", JSONObject()) },
            { it.put("precision_fingerprint", "wrong") }, { it.put("coordinate_basis", "stored-raw-v1-six-decimals") },
            { it.put("point_count", 4.0) }, { it.put("device_result_verified", true) },
        )) {
            val server = Server(c).apply { changeCalculation = mutate }
            assertTrue(runCatching { WalkMotionPrecisionSync(db, { "owner" }, request = server::call)
                .sync("t", p.base.input.session.id, REMOTE, "owner") }.isFailure)
            assertNull(db.walkDao().motionPrecision(p.base.input.session.id)!!.verifiedAtMillis)
        }
    }

    @Test fun `unknown old coordinates are never uploaded as originals`() = withDb { db ->
        val p = plan(walking()); seed(db, p, captured = false)
        WalkMotionPrecisionSync(db, { "owner" }, request = { _,_,_,_-> error("must not call") })
            .sync("t", p.base.input.session.id, REMOTE, "owner")
        assertNull(db.walkDao().motionPrecision(p.base.input.session.id))
    }

    @Test fun `lost completion survives process restart without resending chunks`() = runBlocking {
        val name = "precision-retry.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, WalkDatabase::class.java, name).build()
        var db = open()
        val c = walking(); val p = plan(c); val id = p.base.input.session.id
        val server = Server(c).apply { loseComplete = true }
        try {
            seed(db, p)
            assertTrue(runCatching { WalkMotionPrecisionSync(db, { "owner" }, request = server::call).sync("t", id, REMOTE, "owner") }.isFailure)
            assertNull(db.walkDao().motionPrecision(id)!!.completedAtMillis)
            db.close(); db = open()
            assertEquals(p.base.input.fixes.map { it.lat.toRawBits() }, db.walkDao().fixes(id).map { it.toModel().lat.toRawBits() })
            assertEquals(listOf(id), db.walkDao().pendingMotionSessions().map { it.id })
            WalkMotionPrecisionSync(db, { "owner" }, request = server::call).sync("t", id, REMOTE, "owner")
            assertNotNull(db.walkDao().motionPrecision(id)!!.verifiedAtMillis)
            assertEquals(p.chunks.size, server.uploads)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `unsupported server leaves captured evidence pending without disturbing base receipt`() = withDb { db ->
        val p = plan(walking()); seed(db,p); val id = p.base.input.session.id
        val old = db.walkDao().motionBackup(id)
        val sync = WalkMotionPrecisionSync(db, { "owner" }, request = { _,path,_,_ ->
            check(path == "/motion-capabilities")
            JSONObject().put("version", WalkMotionContract.VERSION).put("backup_supported", true)
        })
        assertTrue(runCatching { sync.sync("t", id, REMOTE, "owner") }.isFailure)
        assertEquals(old, db.walkDao().motionBackup(id))
        assertNull(db.walkDao().motionPrecision(id)!!.verifiedAtMillis)
        assertEquals(listOf(id), db.walkDao().pendingMotionSessions().map { it.id })
    }

    @Test fun `account change or deletion during calculation cannot acknowledge`() = withDb { db ->
        val c = walking(); val p = plan(c); seed(db, p); val server = Server(c)
        var account = "owner"
        val sync = WalkMotionPrecisionSync(db, { account }, request = { t,path,m,b ->
            server.call(t,path,m,b).also { if (path.endsWith("motion-calculation")) account = "other" }
        })
        assertTrue(runCatching { sync.sync("t", p.base.input.session.id, REMOTE, "owner") }.isFailure)
        assertNull(db.walkDao().motionPrecision(p.base.input.session.id)!!.verifiedAtMillis)
        account = "owner"
        val deleted = WalkMotionPrecisionSync(db, { account }, request = { t,path,m,b ->
            server.call(t,path,m,b).also { if (path.endsWith("motion-calculation")) db.walkDao().deleteSession(p.base.input.session.id) }
        })
        assertTrue(runCatching { deleted.sync("t", p.base.input.session.id, REMOTE, "owner") }.isFailure)
        assertNull(db.walkDao().motionPrecision(p.base.input.session.id))
    }

    @Test fun `restore installs bits policy and verification atomically and upgrades coarse restore`() = withDb { db ->
        val c = walking(); val p = plan(c); val server = Server(c).apply { sealed = true }
        val id = p.base.input.session.id
        val sync = WalkMotionSync(db, { "owner" }, precision = WalkMotionPrecisionSync(db, { "owner" }, request = server::call), request = server::call)
        server.changeCalculation = { it.put("distance_m", 999.0) }
        assertTrue(runCatching { sync.restore("t", detail(c), "owner") }.isFailure)
        assertNull(db.walkDao().session(id))
        // An earlier base-only restore must be eligible for a later precision upgrade.
        WalkMotionSync(db, { "owner" }, request = server::call).restore("t", detail(c), "owner")
        assertNull(db.walkDao().session(id)!!.coordinateOrigin)
        assertTrue(sync.needsPrecisionRestore("t", id, REMOTE, "owner"))
        server.changeCalculation = {}
        sync.restore("t", detail(c), "owner", expectedLocal = true)
        assertEquals("verified", db.walkDao().session(id)!!.coordinateOrigin)
        assertEquals(p.base.input.fixes.map { it.lat.toRawBits() }, db.walkDao().fixes(id).map { it.latBits })
        assertNotNull(db.walkDao().motionPrecision(id)!!.verifiedAtMillis)
        assertFalse(sync.needsPrecisionRestore("t", id, REMOTE, "owner"))
    }

    @Test fun `signed zeros and high precision survive SQLite round trip`() = withDb { db ->
        val s = RecordedSession("bits", ownerId = "owner", startedAtMillis = 1)
        val log = RoomWalkFixLog(db.walkDao(), owner = { "owner" })
        log.openSession(s)
        log.append("bits", RecordedFix(0, 0, 2, -0.0, 127.12345678901234, -0.0f, false))
        val f = log.fixes("bits").single()
        assertEquals(Long.MIN_VALUE, f.lat.toRawBits())
        assertEquals(Int.MIN_VALUE, f.accuracyM!!.toRawBits())
        assertEquals(127.12345678901234.toRawBits(), f.lng.toRawBits())
        assertEquals("captured", db.walkDao().session("bits")!!.coordinateOrigin)
    }

    private inner class Server(val case: JSONObject) {
        val p = plan(case)
        var sealed = false; var uploads = 0; var calculationOffline = false; var loseComplete = false
        var changeCalculation: (JSONObject) -> Unit = {}
        val received = mutableSetOf<Int>()
        fun calculation() = JSONObject(case.getJSONObject("expected").toString()).apply {
            val b = p.base; val policy = b.manifest.getJSONObject("policy")
            put("version", WalkPrecisionContract.CALCULATION); put("walk_id", REMOTE); put("client_session_id", b.input.session.id)
            put("policy_version", policy.getString("version")); put("measurement_version", policy.getString("measurement_version"))
            put("config_hash", policy.getString("config_hash")); put("manifest_fingerprint", b.manifestHash)
            put("evidence_fingerprint", b.evidenceHash); put("precision_fingerprint", p.evidenceHash)
            put("coordinate_basis", "device-fix-bits-v1"); put("device_result_verified", false)
            changeCalculation(this)
        }
        suspend fun call(token: String, path: String, method: String, body: JSONObject?): JSONObject {
            assertEquals("t", token)
            if (path == "/motion-capabilities") return JSONObject().put("version", WalkMotionContract.VERSION)
                .put("backup_supported", true).put("calculation_verified", false).put("chunk_size", 256).put("max_points",100_000).put("max_epochs",1024)
                .put("precision_versions", JSONArray(listOf(WalkPrecisionContract.VERSION))).put("calculation_versions", JSONArray(listOf(WalkPrecisionContract.CALCULATION)))
            if (path.endsWith("motion-calculation")) { if (calculationOffline) throw IOException("offline"); return calculation() }
            val base = path.contains("motion-backup")
            if (path.contains("/chunks/")) {
                val i = path.substringAfterLast('/').toInt()
                if (method == "PUT") { uploads++; received += i; assertEquals(p.chunkHashes[i], WalkPrecisionContract.chunkDigest(WalkMotionStore.objects(body!!.getJSONArray("points")))) }
                return (if (base) p.base.chunk(i) else p.chunk(i)).put("chunk_index",i)
                    .put("chunk_fingerprint", if (base) p.base.chunkHashes[i] else p.chunkHashes[i])
            }
            if (path.endsWith("/complete")) {
                check(received == p.chunks.indices.toSet()); sealed = true
                if (loseComplete) { loseComplete = false; throw IOException("lost response") }
            }
            return JSONObject().put("version", if (base) WalkMotionContract.VERSION else WalkPrecisionContract.VERSION)
                .put("manifest", if (base) p.base.manifest else p.manifest).put("manifest_fingerprint", if (base) p.base.manifestHash else p.manifestHash)
                .put("calculation_verified",false).put("state", if (base || sealed) "complete" else "collecting")
                .put("received_chunks", JSONArray(if (base || sealed) p.chunks.indices.toList() else received.sorted()))
                .put("evidence_fingerprint", if (base) p.base.evidenceHash else if (sealed) p.evidenceHash else JSONObject.NULL)
        }
    }
    companion object { const val REMOTE = "22222222-2222-2222-2222-222222222222" }
}
