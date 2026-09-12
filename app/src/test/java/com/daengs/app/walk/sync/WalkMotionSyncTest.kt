package com.daengs.app.walk.sync

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.*
import com.daengs.app.walk.motion.*
import com.daengs.app.walk.store.*
import java.io.IOException
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkMotionSyncTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun fixture() = JSONObject(javaClass.getResource("/walk/gps-motion-backup-v1.json")!!.readText())
    private fun golden(): WalkMotionContract.Plan {
        val f = fixture()
        val raw = RemoteWalkDetail.parse(JSONObject().put("id", REMOTE).put("client_session_id", ID)
            .put("started_at", "2026-09-11T00:00:00Z").put("ended_at", "2026-09-11T00:00:05Z")
            .put("points", f.getJSONArray("raw_points"))).fixes
        return WalkMotionContract.read(RecordedSession(ID, ownerId = "owner", startedAtMillis = START,
            endedAtMillis = START+5000, syncState = WalkSyncState.DERIVED, serverWalkId = REMOTE),
            raw, f.getJSONObject("manifest"), WalkMotionStore.objects(f.getJSONArray("points")))
    }
    private fun remote(plan: WalkMotionContract.Plan) = RemoteWalkDetail(RemoteWalk(REMOTE, ID, emptyList(),
        START, START+5000, null), plan.input.fixes.map { RecordedFix(it.clientSeq, it.chainIndex, it.atMillis,
            it.lat, it.lng, it.accuracyM, it.isMock, recordingEligible = it.recordingEligible) })
    private suspend fun seed(db: WalkDatabase, plan: WalkMotionContract.Plan) = db.withTransaction {
        val log = RoomWalkFixLog(db.walkDao(), owner = { "owner" })
        log.restoreSession(plan.input.session)
        plan.input.fixes.forEach { db.walkDao().insertObservation(it.motionRow(ID)) }
        plan.input.epochs.forEach { log.saveRecordingEpoch(it) }
    }
    private fun withDb(test: suspend (WalkDatabase) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkDatabase::class.java).build()
        try { test(db) } finally { db.close() }
    }

    @Test fun `server golden digests and large integers special floats and null agree`() {
        val f = fixture(); val plan = golden()
        assertEquals(f.getString("manifest_fingerprint"), plan.manifestHash)
        assertEquals(f.getString("chunk_fingerprint"), plan.chunkHashes.single())
        assertEquals(f.getString("evidence_fingerprint"), plan.evidenceHash)
        assertEquals(9007199254740993L, plan.input.epochs.single().startedElapsedNanos)
        assertEquals(listOf(Int.MIN_VALUE, 0x7fc00001, null), plan.input.fixes.map { it.speedMps?.toRawBits() })
        assertEquals(plan.evidenceHash, WalkMotionContract.create(plan.input).evidenceHash)
    }

    @Test fun `malformed or future metadata is rejected before local promotion`() {
        val plan = golden()
        for (mutate in listOf<(JSONObject,List<JSONObject>) -> Unit>(
            { m, _ -> m.getJSONObject("policy").put("measurement_version", "future") },
            { m, _ -> m.getJSONObject("policy").put("config_json", "{}") },
            { m, _ -> m.put("raw_input_fingerprint", "sha256:"+"0".repeat(64)) },
            { m, _ -> m.getJSONArray("epochs").getJSONObject(0).put("drained", false) },
            { _, p -> p[0].put("elapsed_realtime_nanos", 9007200254740993.0) },
            { _, p -> p[0].put("recording_eligible", false) },
            { _, p -> p[0].remove("speed_mps_bits") },
            { _, p -> p[0].put("source_epoch", "other") },
        )) {
            val m = JSONObject(plan.manifest.toString()); val p = plan.points.map { JSONObject(it.toString()) }
            mutate(m,p)
            assertTrue(runCatching { WalkMotionContract.read(plan.input.session, plan.input.fixes,m,p) }.isFailure)
        }
    }

    @Test fun `partial upload and lost complete response resume from frozen file database`() = runBlocking {
        val original = golden()
        val fixes = (0..256).map { i -> original.input.fixes[i%3].copy(clientSeq=i, ingressSeq=i.toLong()) }
        val plan = WalkMotionContract.create(original.input.copy(fixes=fixes,
            epochs=listOf(original.input.epochs.single().copy(persistedCount=257, targetIngressSeq=256))))
        val server = Server(plan).apply { failIndex = 1 }
        val name = "motion-resume-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context,WalkDatabase::class.java,name).build()
        var db = open()
        try {
            seed(db,plan)
            assertTrue(runCatching { WalkMotionSync(db,{"owner"},request=server::call).sync("t",ID,REMOTE) }.isFailure)
            assertNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
            assertEquals(setOf(0), server.received)
            db.close(); db = open()
            val log = RoomWalkFixLog(db.walkDao(),owner={"owner"})
            assertEquals(listOf(ID),log.sessionsPendingAnalysis().map { it.id })
            server.failIndex = null; server.loseComplete = true
            assertTrue(runCatching { WalkMotionSync(db,{"owner"},request=server::call).sync("t",ID,REMOTE) }.isFailure)
            assertNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
            assertTrue(server.sealed)
            WalkMotionSync(db,{"owner"},request=server::call).sync("t",ID,REMOTE)
            assertNotNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
            assertTrue(log.sessionsPendingAnalysis().isEmpty())
            assertEquals(1,server.uploads.count { it == 0 })
            assertEquals(1,server.uploads.count { it == 1 })
            assertEquals(listOf(Int.MIN_VALUE,0x7fc00001,null), log.fixes(ID).take(3).map { it.speedMps?.toRawBits() })
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `invalid completion never acknowledges and retry recovers`() = withDb { db ->
        val plan = golden(); seed(db,plan)
        val server = Server(plan).apply { corruptReceipt = true }
        val sync = WalkMotionSync(db,{"owner"},request=server::call)
        assertTrue(runCatching { sync.sync("t",ID,REMOTE) }.isFailure)
        assertNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
        assertEquals("CONTRACT_REJECTED",db.walkDao().motionBackup(ID)!!.lastError)
        server.corruptReceipt = false
        sync.sync("t",ID,REMOTE)
        assertNotNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
    }

    @Test fun `unavailable backup preserves v1 queue and still delivers photos while motion retries`() = withDb { db ->
        val plan=golden(); seed(db,plan)
        val server=Server(plan).apply { supported=false }
        val log=RoomWalkFixLog(db.walkDao(),owner={"owner"})
        db.walkDao().insertEntry(WalkEntryRow("action",ID,"{\"id\":\"action\"}",1,"m",true,null))
        var photos=0; var diary=0
        val sync=WalkSync(log,api=NoRawApi(),motion=WalkMotionSync(db,{"owner"},request=server::call),
            photoSync={_,_,_->photos++},storyboardSync={_,_,_->diary++},warn={_,_->})
        val failure=runCatching { sync.syncPendingSession("t",ID) }.exceptionOrNull()
        assertTrue(failure!!.isRetryableWalkDeliveryFailure())
        assertEquals(1,photos); assertEquals(1,diary)
        assertEquals("derived",db.walkDao().session(ID)!!.syncState)
        assertFalse(db.walkDao().entry("action")!!.isV2)
        assertTrue(db.walkDao().entry("action")!!.dirty)
        assertNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
        assertEquals(0,server.uploads.size)
    }

    @Test fun `legacy and speed only policies do not request backup`() = withDb { db ->
        for (policy in listOf(null, MotionPolicies.encode(MotionPolicies.freeze(ID)))) {
            db.walkDao().deleteSession(ID)
            RoomWalkFixLog(db.walkDao(),owner={"owner"}).restoreSession(golden().input.session.copy(motionPolicyJson=policy))
            WalkMotionSync(db,{"owner"},request={_,_,_,_->error("legacy must not call")}).sync("t",ID,REMOTE)
            assertNull(db.walkDao().motionBackup(ID))
            assertTrue(RoomWalkFixLog(db.walkDao(),owner={"owner"}).sessionsPendingAnalysis().isEmpty())
        }
    }

    @Test fun `restore verifies before write and preserves float bits through reopening`() = runBlocking {
        val plan=golden(); val server=Server(plan).apply { finish() }
        val name="motion-restore-test.db"; context.deleteDatabase(name)
        fun open()=Room.databaseBuilder(context,WalkDatabase::class.java,name).build()
        var db=open()
        try {
            server.corruptPage=true
            assertTrue(runCatching { WalkMotionSync(db,{"owner"},request=server::call).restore("t",remote(plan),"owner") }.isFailure)
            assertNull(db.walkDao().session(ID)); assertTrue(db.walkDao().fixes(ID).isEmpty())
            server.corruptPage=false
            WalkMotionSync(db,{"owner"},request=server::call).restore("t",remote(plan),"owner")
            db.close(); db=open()
            val input=db.walkDao().motionInput(ID,"owner")!!
            assertEquals(plan.evidenceHash,WalkMotionContract.create(input).evidenceHash)
            assertNotNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
            assertEquals(plan.input.epochs,input.epochs)
            assertEquals(listOf(Int.MIN_VALUE,0x7fc00001,null),input.fixes.map { it.speedMps?.toRawBits() })
            val source=summarize(plan.input.session,plan.input.fixes,epochs=plan.input.epochs)
            val restored=summarize(input.session,input.fixes,epochs=input.epochs)
            assertEquals(source.distanceMeters,restored.distanceMeters,0.0)
            assertEquals(source.activeDurationMillis,restored.activeDurationMillis)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `collecting restore stays absent while missing backup remains legacy`() = withDb { db ->
        val plan=golden(); val server=Server(plan)
        val sync=WalkMotionSync(db,{"owner"},request=server::call)
        assertTrue(runCatching { sync.restore("t",remote(plan),"owner") }.exceptionOrNull() is IOException)
        assertNull(db.walkDao().session(ID))
        server.missing=true
        sync.restore("t",remote(plan),"owner")
        assertNull(db.walkDao().session(ID)!!.motionPolicyJson)
        assertEquals(3,db.walkDao().fixes(ID).size)
        assertTrue(db.walkDao().recordingEpochs(ID).isEmpty())
        // Subsequent complete backup upgrades a previously restored legacy row without replacing its entries.
        db.walkDao().insertEntry(WalkEntryRow("note",ID,"kept",1,"m",true,null))
        server.missing=false; server.finish()
        sync.restore("t",remote(plan),"owner")
        assertNotNull(db.walkDao().session(ID)!!.motionPolicyJson)
        assertEquals("kept",db.walkDao().entry("note")!!.payload)
    }

    @Test fun `account switch and deletion reject late completion without resurrection`() = withDb { db ->
        val plan=golden(); seed(db,plan); val server=Server(plan)
        var account="owner"
        val sync=WalkMotionSync(db,{account},request={t,p,m,b ->
            server.call(t,p,m,b).also { if(p.endsWith("/complete")) account="other" }
        })
        assertTrue(runCatching { sync.sync("t",ID,REMOTE) }.isFailure)
        assertNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
        account="owner"
        val deleted=WalkMotionSync(db,{account},request={t,p,m,b ->
            server.call(t,p,m,b).also { if(p.endsWith("/complete")) db.walkDao().deleteOwnerSessions("owner") }
        })
        assertTrue(runCatching { deleted.sync("t",ID,REMOTE) }.isFailure)
        assertNull(db.walkDao().session(ID)); assertNull(db.walkDao().motionBackup(ID))
    }

    @Test fun `epoch collision rolls back all restored rows and preserves foreign recording`() = withDb { db ->
        val plan=golden(); val server=Server(plan).apply { finish() }
        val log=RoomWalkFixLog(db.walkDao(),owner={"owner"})
        log.restoreSession(plan.input.session.copy(id="other"))
        val other=plan.input.epochs.single().copy(sessionId="other")
        log.saveRecordingEpoch(other)
        assertTrue(runCatching { WalkMotionSync(db,{"owner"},request=server::call).restore("t",remote(plan),"owner") }.isFailure)
        assertNull(db.walkDao().session(ID)); assertTrue(db.walkDao().fixes(ID).isEmpty())
        assertEquals(other,db.walkDao().recordingEpochById(other.id)!!.toModel())
    }

    @Test fun `withdrawal barrier and in flight legacy deletion prevent late restoration`() = withDb { db ->
        val plan=golden(); val server=Server(plan).apply { finish() }
        val log=RoomWalkFixLog(db.walkDao(),owner={"owner"})
        val withdrawn=WalkMotionSync(db,{"owner"},restorationGuard=log::restoringForOwner,request={t,p,m,b ->
            server.call(t,p,m,b).also { if(p.contains("/chunks/")) log.forgetOwner("owner") }
        })
        assertTrue(runCatching { withdrawn.restore("t",remote(plan),"owner") }.isFailure)
        assertNull(db.walkDao().session(ID))
        val fresh=RoomWalkFixLog(db.walkDao(),owner={"owner"})
        fresh.restoreSession(remote(plan).walk.toSession(10).copy(ownerId="owner"))
        remote(plan).fixes.forEach { fresh.append(ID,it) }
        val deleted=WalkMotionSync(db,{"owner"},restorationGuard=fresh::restoringForOwner,request={t,p,m,b ->
            server.call(t,p,m,b).also { if(p.contains("/chunks/")) fresh.deleteSession(ID) }
        })
        assertTrue(runCatching { deleted.restore("t",remote(plan),"owner") }.isFailure)
        assertNull(db.walkDao().session(ID))
    }

    @Test fun `future capability authentication failure and cancellation never become legacy restore`() = withDb { db ->
        val plan=golden()
        for (error in listOf<Exception>(WalkHttpException(401,"auth"), CancellationException("cancel"), IOException("network"))) {
            val failure=runCatching { WalkMotionSync(db,{"owner"},request={_,_,_,_->throw error}).restore("t",remote(plan),"owner") }.exceptionOrNull()
            assertSame(error,failure); assertNull(db.walkDao().session(ID))
        }
        val future=WalkMotionSync(db,{"owner"},request={_,_,_,_->JSONObject().put("version","future").put("backup_supported",true)})
        assertTrue(runCatching { future.restore("t",remote(plan),"owner") }.isFailure)
        assertNull(db.walkDao().session(ID))
    }

    @Test fun `syncOnce pulls measured backups through the production coordinator`() = withDb { db ->
        val plan=golden(); val server=Server(plan).apply { finish() }
        val log=RoomWalkFixLog(db.walkDao(),owner={"owner"})
        val rawApi=object : WalkApiClient by NoRawApi() {
            override suspend fun list(token:String)=Result.success(listOf(remote(plan).walk))
            override suspend fun detail(token:String,walkId:String)=Result.success(remote(plan))
        }
        WalkSync(log,rawApi,motion=WalkMotionSync(db,{"owner"},restorationGuard=log::restoringForOwner,request=server::call),
            warn={_,cause->throw AssertionError(cause)}).syncOnce("t")
        assertEquals(plan.evidenceHash,WalkMotionContract.create(db.walkDao().motionInput(ID,"owner")!!).evidenceHash)
    }

    @Test fun `long walks use bounded local rows and changed pending metadata cannot overwrite snapshot`() = withDb { db ->
        val original=golden()
        val points=(0 until 6000).map { i -> original.input.fixes[i%3].copy(clientSeq=i,ingressSeq=i.toLong()) }
        val plan=WalkMotionContract.create(original.input.copy(fixes=points,
            epochs=listOf(original.input.epochs.single().copy(persistedCount=6000,targetIngressSeq=5999))))
        seed(db,plan); val server=Server(plan).apply { supported=false }
        val sync=WalkMotionSync(db,{"owner"},request=server::call)
        assertTrue(runCatching { sync.sync("t",ID,REMOTE) }.isFailure)
        val saved=db.walkDao().motionBackup(ID)!!
        assertTrue(saved.manifestJson.length<8192)
        db.walkDao().updateMotionObservation(points[0].copy(provider="changed").motionRow(ID))
        server.supported=true
        assertTrue(runCatching { sync.sync("t",ID,REMOTE) }.isFailure)
        assertTrue(server.uploads.isEmpty())
        db.walkDao().updateMotionObservation(points[0].motionRow(ID))
        sync.sync("t",ID,REMOTE)
        assertEquals(24,server.uploads.size)
        assertNotNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
    }

    @Test fun `existing legacy walks only probe receipts instead of downloading raw again`() = withDb { db ->
        val plan=golden(); val server=Server(plan).apply { missing=true }
        val log=RoomWalkFixLog(db.walkDao(),owner={"owner"})
        log.restoreSession(plan.input.session.copy(motionPolicyJson=null))
        val api=object : WalkApiClient by NoRawApi() {
            override suspend fun list(token:String)=Result.success(listOf(remote(plan).walk))
        }
        WalkSync(log,api,motion=WalkMotionSync(db,{"owner"},request=server::call),warn={_,cause->throw AssertionError(cause)}).syncOnce("t")
        assertNull(db.walkDao().session(ID)!!.motionPolicyJson)
        assertTrue(server.uploads.isEmpty())
    }

    @Test fun `empty measured walk and concurrent delivery complete once`() = withDb { db ->
        val original=golden()
        val plan=WalkMotionContract.create(original.input.copy(fixes=emptyList(),epochs=listOf(
            original.input.epochs.single().copy(persistedCount=0,targetIngressSeq=-1))))
        seed(db,plan); val server=Server(plan); val sync=WalkMotionSync(db,{"owner"},request=server::call)
        coroutineScope { listOf(async { sync.sync("t",ID,REMOTE) },async { sync.sync("t",ID,REMOTE) }).awaitAll() }
        assertNotNull(db.walkDao().motionBackup(ID)!!.completedAtMillis)
        assertTrue(server.uploads.isEmpty()); assertEquals(1,server.completions)
    }

    private class Server(val plan: WalkMotionContract.Plan) {
        val received=mutableSetOf<Int>(); val uploads=mutableListOf<Int>()
        var supported=true; var sealed=false; var missing=false; var corruptPage=false
        var corruptReceipt=false; var loseComplete=false; var failIndex:Int?=null; var completions=0
        fun finish() { received.addAll(plan.chunks.indices); sealed=true }
        fun status()=JSONObject().put("version",WalkMotionContract.VERSION).put("calculation_verified",false)
            .put("manifest",plan.manifest).put("manifest_fingerprint",plan.manifestHash)
            .put("received_chunks",JSONArray(received.sorted())).put("state",if(sealed) "complete" else "collecting")
            .put("evidence_fingerprint",if(sealed) if(corruptReceipt) "sha256:"+"0".repeat(64) else plan.evidenceHash else JSONObject.NULL)
        suspend fun call(token:String,path:String,method:String,body:JSONObject?):JSONObject {
            assertEquals("t",token)
            if(path=="/motion-capabilities") return JSONObject().put("version",WalkMotionContract.VERSION)
                .put("backup_supported",supported).put("calculation_verified",false).put("chunk_size",256)
                .put("max_points",100_000).put("max_epochs",1024)
            check(path.startsWith("/$REMOTE/motion-backup"))
            if(path.endsWith("/complete")) {
                assertEquals("POST",method); assertEquals(plan.evidenceHash,body!!.getString("evidence_fingerprint"))
                check(received==plan.chunks.indices.toSet()); sealed=true; completions++
                if(loseComplete) { loseComplete=false; throw IOException("lost response") }
                return status()
            }
            if(path.contains("/chunks/")) {
                val index=path.substringAfterLast('/').toInt()
                if(method=="PUT") {
                    if(failIndex==index) throw IOException("offline")
                    assertEquals(plan.chunkHashes[index],WalkMotionContract.chunkDigest(WalkMotionStore.objects(body!!.getJSONArray("points"))))
                    uploads+=index; received+=index
                } else check(sealed)
                return plan.chunk(index).put("chunk_index",index).put("chunk_fingerprint",if(corruptPage) "bad" else plan.chunkHashes[index])
            }
            if(method=="GET" && missing) throw WalkHttpException(404,"missing")
            if(method=="PUT") assertEquals(plan.manifestHash,WalkMotionContract.manifestDigest(body!!))
            return status()
        }
    }

    private class NoRawApi : WalkApiClient {
        override val configured=true
        override suspend fun upload(token:String,session:RecordedSession,fixes:List<RecordedFix>):Result<String> = error("already derived")
        override suspend fun appendPoints(token:String,walkId:String,clientSessionId:String,fixes:List<RecordedFix>):Result<Unit> = error("already derived")
        override suspend fun finalize(token:String,walkId:String,manifest:WalkFinalizeManifest):Result<Unit> = error("already derived")
        override suspend fun list(token:String):Result<List<RemoteWalk>> = Result.success(emptyList())
        override suspend fun detail(token:String,walkId:String):Result<RemoteWalkDetail> = error("not used")
    }
    companion object {
        const val ID="11111111-1111-1111-1111-111111111111"
        const val REMOTE="22222222-2222-2222-2222-222222222222"
        const val START=1789084800000L
    }
}
