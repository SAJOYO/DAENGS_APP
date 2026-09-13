package com.daengs.app.ui.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.daengs.app.BuildConfig
import com.daengs.app.auth.AccountScope
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import com.daengs.app.walk.detail.StoredWalkDetailData
import com.daengs.app.walk.diary.LocalDiaryBoard
import com.daengs.app.walk.store.*
import com.daengs.app.walk.sync.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/** Real stored detail and map, isolated synthetic inputs. Reopening never seeds or contacts a server. */
class MeasurementReviewActivity : ComponentActivity() {
    private lateinit var db: WalkDatabase
    internal lateinit var dao: WalkDao
    internal lateinit var data: StoredWalkDetailData
    internal var ready by mutableStateOf(false)
    internal var failure: Throwable? = null
    internal var detail: WalkSessionDetail? = null
    private val account = AccountScope(OWNER, 1)
    private val marker get() = File(filesDir, "measurement-review-session")
    internal lateinit var session: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.APPLICATION_ID == "com.daengs.app.locationreview")
        val reset = savedInstanceState == null && intent.getBooleanExtra("reset", false)
        if (reset) deleteDatabase(DB)
        db = Room.databaseBuilder(this, WalkDatabase::class.java, DB).build()
        dao = db.walkDao()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (reset) seed()
                    session = marker.readText()
                    val offline = WalkMeasurementSync(db, { account }, request = { _, _, _ -> error("Offline reopen attempted HTTP") })
                    data = StoredWalkDetailData(session, account, { account }, WalkHistory(RoomWalkFixLog(dao) { OWNER }),
                        dao, WalkEntryStore(dao) { OWNER }, WalkPhotoStore(dao, File(cacheDir, "measurement-photos")) { OWNER },
                        {}, {}, { null }, { _, _ -> }, { _, _, _ -> }, measurements = offline)
                    detail = requireNotNull(data.load())
                    check(detail!!.measurement != null) { "Verified measurement must survive Room reopening" }
                }
                ready = true
            } catch (e: Exception) { failure = e }
        }
        setContent { DaengsTheme {
            if (ready) WalkDiaryMapForAccount(session, data, data, {}, Modifier, emptyList(),
                WalkSessionOrigin.RECORDS, account, {}, { null })
        } }
    }

    private fun fixture(name: String): JSONObject = assets.open("walk/$name.json").bufferedReader().use {
        WalkMotionStore.objects(JSONObject(it.readText()).getJSONArray("cases")).single { it.getString("name") == "high-speed-reentry" }
    }

    private suspend fun seed() {
        val source = fixture("gps-motion-precision-v1")
        val wire = fixture("walk-measurement-v1")
        val manifest = source.getJSONObject("manifest")
        val epochs = manifest.getJSONArray("epochs")
        val remote = RemoteWalkDetail.parse(JSONObject().put("id", REMOTE)
            .put("client_session_id", manifest.getString("client_session_id"))
            .put("started_at", Instant.ofEpochMilli(epochs.getJSONObject(0).getLong("started_at_millis")).toString())
            .put("ended_at", Instant.ofEpochMilli(epochs.getJSONObject(epochs.length() - 1).getLong("ended_at_millis")).toString())
            .put("points", source.getJSONArray("raw_points")))
        val coarse = WalkMotionContract.read(remote.walk.toSession(1).copy(ownerId = OWNER, syncState = WalkSyncState.DERIVED),
            remote.fixes, manifest, WalkMotionStore.objects(source.getJSONArray("points")))
        val precision = WalkPrecisionContract.read(coarse, source.getJSONObject("precision_manifest"),
            WalkMotionStore.objects(source.getJSONArray("precision_points")))
        val base = precision.base
        val id = base.input.session.id
        RoomWalkFixLog(dao) { OWNER }.restoreSession(base.input.session)
        dao.installCoordinateOrigin(id, OWNER, "captured")
        base.input.fixes.forEach { dao.insertObservation(it.motionRow(id, true)) }
        base.input.epochs.forEach { dao.saveRecordingEpoch(RecordingEpochRow.from(it)) }
        dao.insertMotionBackup(WalkMotionBackupRow(id, base.manifest.toString(), base.manifestHash, base.evidenceHash, 1))
        val receipt = JSONObject().put("evidence_fingerprint", base.evidenceHash).put("precision_fingerprint", precision.evidenceHash)
        dao.insertMotionPrecision(precision.row().copy(completedAtMillis = 1, verifiedAtMillis = 1, verificationJson = receipt.toString()))
        val persisted = requireNotNull(dao.motionInput(id, OWNER))
        val rebuilt = WalkPrecisionContract.create(WalkMotionContract.create(persisted))
        check(rebuilt.base.evidenceHash == base.evidenceHash && rebuilt.evidenceHash == precision.evidenceHash) {
            "Room source differs: session=${persisted.session == base.input.session}, " +
                "fixes=${persisted.fixes == base.input.fixes}, epochs=${persisted.epochs == base.input.epochs}, " +
                "base=${rebuilt.base.evidenceHash == base.evidenceHash}, precision=${rebuilt.evidenceHash == precision.evidenceHash}"
        }
        MeasurementFixtureHttp(wire).use { http ->
            val api = WalkHttpApi(http.origin)
            WalkMeasurementSync(db, { account }, request = { token, path, method ->
                api.call(token, path, method, parse = { it }).getOrThrow()
            }).refresh("synthetic-review-token", id)
            check(dao.measurement(id) != null) { "No verified cache after ${http.requests.size} HTTP requests" }
            check(http.requests.any { it.startsWith("POST /app/walks/$REMOTE/measurements?") })
            check(http.requests.count { "/chunks/" in it } == wire.getJSONArray("pages").length())
        }
        val local = readCompletedRoute(base.input.session, base.input.fixes, base.input.epochs)
        // A boundary card may have no source time binding. Use an actual recorded fix for range navigation.
        val fix = base.input.fixes.sortedBy { it.clientSeq }[1]
        val entry = WalkEntry("$id-range-note", id, WalkMomentType.NOTE, fix.atMillis,
            com.daengs.app.location.GeoPoint(fix.lat, fix.lng), fix.atMillis, fix.accuracyM, note = "구간 안에서 남긴 검증 메모예요.")
        dao.insertEntry(WalkEntryRow(entry.id, id, entry.toJson().toString(), 1, "range-review", false))
        val board = JSONObject(LocalDiaryBoard.build(local.summary, local.observations, listOf(entry), emptyList()))
        WalkMotionStore.objects(board.getJSONArray("scenes")).single { it.optString("entry") == entry.id }.put("title", RANGE_TITLE)
        board.getJSONArray("scenes").getJSONObject(0).put("title", TITLE)
            .put("body", (1..100).joinToString("\n\n") { "검증 문단 $it. 함께 걷다가 쉬었던 순간을 다시 읽어요." })
        dao.insertDiaryPublication(WalkDiaryPublicationRow(id, 0, 0, board.toString(), board.toString(), 1))
        marker.writeText(id)
    }

    override fun onDestroy() {
        lifecycleScope.cancel()
        super.onDestroy()
        db.close()
    }

    companion object {
        val PROCESS = UUID.randomUUID().toString()
        const val OWNER = "22222222-2222-2222-2222-222222222222"
        const val REMOTE = "33333333-3333-3333-3333-333333333333"
        const val DB = "measurement-device.db"
        const val TITLE = "측정 동선 실기기 검증"
        const val RANGE_TITLE = "구간에서 읽을 메모"
    }
}
