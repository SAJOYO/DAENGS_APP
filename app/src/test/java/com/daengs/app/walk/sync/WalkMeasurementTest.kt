package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.auth.AccountScope
import com.daengs.app.walk.*
import com.daengs.app.walk.store.*
import com.daengs.app.walk.trajectory.RecordContextKind
import com.daengs.app.walk.routeexplorer.CompletedRouteReview
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
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
class WalkMeasurementTest {
    private val owner = "22222222-2222-2222-2222-222222222222"
    private val remote = "33333333-3333-3333-3333-333333333333"
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun cases(name: String) = WalkMotionStore.objects(JSONObject(javaClass.getResource("/walk/$name.json")!!.readText()).getJSONArray("cases"))
    private fun source(name: String = "walking") = cases("gps-motion-precision-v1").single { it.getString("name") == name }
    private fun wire(name: String = "walking") = cases("walk-measurement-v1").single { it.getString("name") == name }
    private fun pages(w: JSONObject) = w.getJSONArray("pages").let { a -> (0 until a.length()).map(a::getString) }
    private fun plan(c: JSONObject): WalkPrecisionContract.Plan {
        val m = c.getJSONObject("manifest"); val e = m.getJSONArray("epochs")
        val d = RemoteWalkDetail.parse(JSONObject().put("id", remote).put("client_session_id", m.getString("client_session_id"))
            .put("started_at", Instant.ofEpochMilli(e.getJSONObject(0).getLong("started_at_millis")).toString())
            .put("ended_at", Instant.ofEpochMilli(e.getJSONObject(e.length()-1).getLong("ended_at_millis")).toString())
            .put("points", c.getJSONArray("raw_points")))
        val base = WalkMotionContract.read(d.walk.toSession(1).copy(ownerId = owner, syncState = WalkSyncState.DERIVED),
            d.fixes, m, WalkMotionStore.objects(c.getJSONArray("points")))
        return WalkPrecisionContract.read(base, c.getJSONObject("precision_manifest"), WalkMotionStore.objects(c.getJSONArray("precision_points")))
    }
    private fun local(p: WalkPrecisionContract.Plan) = readCompletedRoute(p.base.input.session, p.base.input.fixes, p.base.input.epochs)
    private suspend fun seed(db: WalkDatabase, p: WalkPrecisionContract.Plan) {
        val b = p.base; val s = b.input.session; val dao = db.walkDao()
        RoomWalkFixLog(dao, owner = { owner }).restoreSession(s)
        dao.installCoordinateOrigin(s.id, owner, "captured")
        b.input.fixes.forEach { dao.insertObservation(it.motionRow(s.id, true)) }
        b.input.epochs.forEach { dao.saveRecordingEpoch(RecordingEpochRow.from(it)) }
        dao.insertMotionBackup(WalkMotionBackupRow(s.id, b.manifest.toString(), b.manifestHash, b.evidenceHash, 1))
        val receipt = JSONObject().put("evidence_fingerprint", b.evidenceHash).put("precision_fingerprint", p.evidenceHash)
        dao.insertMotionPrecision(p.row().copy(completedAtMillis = 1, verifiedAtMillis = 1, verificationJson = receipt.toString()))
    }
    private suspend fun response(w: JSONObject, path: String): String = when {
        path == "/trajectory-capabilities" -> JSONObject().put("persisted_measurements_supported", true)
            .put("measurement_versions", JSONArray().put(WalkMeasurementContract.VERSION)).toString()
        path.contains("/chunks/") -> pages(w)[path.substringAfter("/chunks/").substringBefore('?').toInt()]
        else -> w.getString("summary")
    }
    private fun withDb(test: suspend (WalkDatabase) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkDatabase::class.java).allowMainThreadQueries().build()
        try { test(db) } finally { db.close() }
    }

    @Test fun `all 32 server results feed the real detail route with local motion equivalence`() {
        for (w in cases("walk-measurement-v1")) {
            val p = plan(source(w.getString("name"))); val local = local(p)
            val adopted = WalkMeasurementContract.adopt(w.getString("summary"), pages(w), p, owner, local)
            assertNotNull(w.getString("name"), adopted.measurement)
            assertEquals(local.summary.distanceMeters, adopted.summary.distanceMeters, 1e-7)
            assertEquals(local.summary.activeDurationMillis, adopted.summary.activeDurationMillis)
            assertEquals(local.observations, adopted.observations)
            val contexts = CompletedRouteReview(adopted).context.contexts
            assertEquals(listOf(RecordContextKind.START, RecordContextKind.END), contexts.map { it.kind })
            assertEquals(adopted.route.start, contexts.first().walkingEndpoint)
        }
    }

    @Test fun `truncated altered reordered and foreign results never replace a detail`() {
        val p = plan(source()); val w = wire(); val text = w.getString("summary"); val page = pages(w)
        fun rejects(s: String = text, chunks: List<String> = page, account: String = owner) =
            assertTrue(runCatching { WalkMeasurementContract.adopt(s, chunks, p, account, local(p)) }.isFailure)
        rejects(chunks = emptyList()); rejects(chunks = page.map { it + " " }); rejects(account = "other")
        rejects(s = JSONObject(text).put("precision_fingerprint", "sha256:" + "0".repeat(64)).toString())
        val altered = JSONObject(page.first()); val points = altered.getJSONArray("points")
        points.getJSONObject(1).put("walking_distance_m", 99.0)
        val raw = altered.toString(); val s = JSONObject(text); val c = s.getJSONArray("required_route_chunks").getJSONObject(0)
        c.put("sha256", WalkMeasurementContract.hash(raw)).put("byte_size", raw.toByteArray().size)
        rejects(s.toString(), listOf(raw) + page.drop(1))

        // Rehashing an invented section break must not change the local engine's route topology.
        val split = JSONObject(page.first())
        val original = WalkMotionStore.objects(split.getJSONArray("points"))
        val walking = original.filter { it.getString("kind") == "walking_section" }
        val added = JSONObject(walking[1].toString()).put("section_index", 1).put("point_index", 0)
            .put("section_id", "invented").put("walking_distance_m", 0.0)
        val tail = walking.drop(2).mapIndexed { i, point -> JSONObject(point.toString())
            .put("section_index", 1).put("point_index", i + 1).put("section_id", "invented") }
        split.put("points", JSONArray(walking.take(2) + added + tail + original.filter { it.getString("kind") == "observed_run" }))
        val splitRaw = split.toString(); val splitSummary = JSONObject(text)
        splitSummary.put("walking_section_count", 2).put("route_point_count", original.size + 1)
        splitSummary.getJSONArray("required_route_chunks").getJSONObject(0)
            .put("sha256", WalkMeasurementContract.hash(splitRaw)).put("byte_size", splitRaw.toByteArray().size)
            .put("point_count", original.size + 1)
        rejects(splitSummary.toString(), listOf(splitRaw))
    }

    @Test fun `atomic cache reopens offline through the ordinary stored detail source`() = withDb { db ->
        val p = plan(source()); seed(db, p); val id = p.base.input.session.id; val w = wire()
        val sync = WalkMeasurementSync(db, { AccountScope(owner, 1) }, request = { _, path, _ -> response(w, path) })
        sync.refresh("t", id)
        val reopened = WalkMeasurementSync(db, { AccountScope(owner, 2) }, request = { _, _, _ -> throw IOException("offline") })
        val history = WalkHistory(RoomWalkFixLog(db.walkDao(), owner = { owner }))
        val scope = AccountScope(owner, 2)
        val dao = db.walkDao()
        val data = com.daengs.app.walk.detail.StoredWalkDetailData(id, scope, { scope }, history, dao,
            WalkEntryStore(dao) { owner }, WalkPhotoStore(dao, java.io.File(context.cacheDir, "measurement-photos")) { owner },
            {}, {}, { null }, { _, _ -> }, { _, _, _ -> }, measurements = reopened)
        val detail = requireNotNull(data.load())
        assertEquals(JSONObject(w.getString("summary")).getJSONObject("measurement").getString("measurement_id"), detail.measurement?.id)
        reopened.refresh("t", id) // A verified cached generation needs no network.
        val foreign = WalkMeasurementSync(db, { AccountScope("other", 3) })
        assertNull(foreign.cached(local(p)).measurement)
    }

    @Test fun `long routes span pages and survive a database close and reopen`() = runBlocking {
        val name = "measurement-reopen-${java.util.UUID.randomUUID()}"
        fun open() = Room.databaseBuilder(context, WalkDatabase::class.java, name).allowMainThreadQueries().build()
        var db = open()
        try {
            val p = plan(cases("walk-measurement-long-input-v1").single())
            val w = cases("walk-measurement-long-v1").single()
            seed(db, p)
            WalkMeasurementSync(db, { AccountScope(owner, 1) }, request = { _, path, _ -> response(w, path) }).refresh("t", p.base.input.session.id)
            assertTrue(db.walkDao().measurementChunks(p.base.input.session.id).size > 1)
            db.close(); db = open()
            val reopened = WalkMeasurementSync(db, { AccountScope(owner, 2) }, request = { _, _, _ -> throw IOException("offline") })
            val result = reopened.cached(local(p))
            assertNotNull(result.measurement)
            assertEquals(p.base.input.fixes.size, result.observations.size)
            assertEquals(local(p).summary.distanceMeters, result.route.end!!.cumulativeDistanceMeters, 1e-7)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `network loss and account generation changes cannot publish partial pages`() = withDb { db ->
        val p = plan(source()); seed(db, p); val id = p.base.input.session.id; val w = wire()
        val interrupted = WalkMeasurementSync(db, { AccountScope(owner, 1) }, request = { _, path, _ ->
            if (path.contains("/chunks/")) throw IOException("lost page") else response(w, path) })
        assertTrue(runCatching { interrupted.refresh("t", id) }.isFailure)
        assertNull(db.walkDao().measurement(id)); assertNull(interrupted.cached(local(p)).measurement)
        var scope = AccountScope(owner, 1)
        val stale = WalkMeasurementSync(db, { scope }, request = { _, path, _ ->
            response(w, path).also { if (path.contains("/chunks/")) scope = AccountScope(owner, 3) } })
        assertTrue(runCatching { stale.refresh("t", id) }.exceptionOrNull() is CancellationException)
        assertNull(db.walkDao().measurement(id)); assertTrue(db.walkDao().measurementChunks(id).isEmpty())
    }

    @Test fun `owner deletion cascades measurement and pages`() = withDb { db ->
        val p = plan(source()); seed(db, p); val id = p.base.input.session.id
        val sync = WalkMeasurementSync(db, { AccountScope(owner, 1) }, request = { _, path, _ -> response(wire(), path) })
        sync.refresh("t", id)
        RoomWalkFixLog(db.walkDao(), owner = { owner }).forgetOwner(owner)
        assertNull(db.walkDao().measurement(id)); assertTrue(db.walkDao().measurementChunks(id).isEmpty())
    }
}
