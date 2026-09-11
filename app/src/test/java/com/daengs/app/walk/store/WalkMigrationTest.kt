package com.daengs.app.walk.store

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.RecordedWalkAction
import com.daengs.app.walk.WalkMomentType
import com.daengs.app.walk.WalkSyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 버전 3 에서 최신으로 올라가도 **걸은 기록과 동기화 단계가 그대로 남는지.**
 *
 * 이 DB 에는 사용자가 걸은 원본 좌표가 들어 있고, 서버에 사본이 없는 산책도 있다
 * (아직 안 올라간 것). 마이그레이션이 잘못되면 앱 업데이트 한 번에 지난 산책이
 * 통째로 사라지는데, 그건 되돌릴 방법이 없다.
 *
 * `MigrationTestHelper` 대신 손으로 옛 DB 를 만든다 — 스키마 json 을 test 소스셋의
 * 에셋으로 끌어오는 설정을 더하지 않으려고 그런다. 대신 **진짜 SQLite 파일**을 쓰고
 * 진짜 Room 으로 연다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkMigrationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before @After
    fun clean() {
        context.deleteDatabase(NAME)
        context.getDatabasePath(NAME).parentFile?.mkdirs()
    }

    @Test
    fun `7의 경로와 메모를 9에서도 보존한다`() = verifyPhotoUpgrade(7)

    @Test
    fun `8의 스토리보드와 경로와 메모를 보존하며 사진 표만 추가한다`() = verifyPhotoUpgrade(8)

    @Test
    fun `9의 사진과 검토본을 보존하고 분석 표를 추가한다`() = verifyPhotoUpgrade(9)

    @Test
    fun `10의 정상 분석과 검토본을 보존하고 원본 stamp를 이관한다`() = verifyPhotoUpgrade(10)

    @Test
    fun `11의 경로 행동 사진 검토본을 보존하고 핀 대기 상태를 빈 값으로 추가한다`() = verifyPhotoUpgrade(11)

    @Test
    fun `12의 기존 사진은 전송 대상으로 남고 사진 없는 복원 세션은 게시자가 되지 않는다`() = verifyPhotoUpgrade(12)

    @Test
    fun `13의 보드와 사용자 수정은 보존하며 기존 세션을 다시 준비하지 않는다`() = verifyPhotoUpgrade(13)

    @Test
    fun `14의 원본과 공개 상태를 보존하고 수신 필드는 결손으로 남긴다`() = verifyPhotoUpgrade(14)

    private fun verifyPhotoUpgrade(version: Int) = runBlocking {
        val schema = org.json.JSONObject(java.io.File("schemas/com.daengs.app.walk.store.WalkDatabase/$version.json").readText())
            .getJSONObject("database").getJSONArray("entities")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME), null).use { old ->
            for (index in 0 until schema.length()) {
                val entity = schema.getJSONObject(index)
                val name = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", name))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", name))
                }
            }
            old.execSQL("INSERT INTO walk_session (id, ownerId, startedAtMillis, endedAtMillis, syncState) VALUES ('s1','owner',1000,2000,'derived')")
            old.execSQL("INSERT INTO walk_fix VALUES ('s1',0,0,1100,37.5,127.0,5.0,0)")
            old.execSQL("INSERT INTO walk_session_dog VALUES ('s1','dog')")
            old.execSQL("INSERT INTO walk_entry (id,sessionId,payload,revision,mutationId,dirty,syncError) VALUES ('e','s1','kept',3,'mutation',1,NULL)")
            if (version >= 8) old.execSQL("INSERT INTO walk_storyboard VALUES ('s1','reviewed-story')")
            if (version >= 9) old.execSQL("INSERT INTO walk_photo VALUES ('p','s1','owner',1500,1400,37.5,127.0,5.0)")
            if (version == 10) {
                old.execSQL("INSERT INTO walk_scene_analysis VALUES ('s1',5,'original-stamp','input','ready','saved-bundle',NULL)")
                old.execSQL("INSERT INTO walk_session (id, ownerId, startedAtMillis, endedAtMillis, syncState) VALUES ('s2','owner',1000,2000,'derived')")
                old.execSQL("INSERT INTO walk_scene_analysis VALUES ('s2',2,'failed-stamp','input','failed',NULL,'failure')")
            }
            if (version == 11) old.execSQL("INSERT INTO walk_scene_analysis VALUES ('s1',5,'original-stamp','input','ready','saved-bundle',NULL,'original-stamp')")
            if (version == 12) old.execSQL("INSERT INTO walk_session (id,ownerId,startedAtMillis,endedAtMillis) VALUES ('restored','owner',1000,2000)")
            if (version >= 13) {
                old.execSQL("INSERT INTO walk_photo_sync VALUES ('s1','owner','publisher',1,0,NULL)")
                old.execSQL("INSERT INTO walk_scene_analysis VALUES ('s1',5,'original-stamp','input','ready','saved-bundle',NULL,'original-stamp')")
            }
            if (version >= 14) {
                old.execSQL("INSERT INTO walk_diary_publication VALUES ('s1',2000,12000,'local-base','published-board',2500)")
            }
            old.version = version
        }
        val db = openLatest()
        try {
            val dao = db.walkDao()
            assertEquals(if (version >= 14) WalkDiaryPublicationRow("s1", 2000, 12000,
                "local-base", "published-board", 2500) else null, dao.diaryPublication("s1"))
            assertEquals("owner", dao.session("s1")!!.ownerId)
            assertEquals("derived", dao.session("s1")!!.syncState)
            assertEquals(1, dao.fixes("s1").size)
            assertEquals(null, dao.fixes("s1").single().ingressSeq)
            assertEquals(null, dao.fixes("s1").single().speedMps)
            assertEquals(emptyList<RecordingEpochRow>(), dao.recordingEpochs("s1"))
            assertEquals("dog", dao.sessionDogs("s1").single().dogId)
            assertEquals("kept", dao.entry("e")!!.payload)
            assertEquals(3, dao.entry("e")!!.revision)
            assertEquals(true, dao.entry("e")!!.dirty)
            assertEquals(null, dao.entry("e")!!.pinPayload)
            assertEquals(null, dao.entry("e")!!.pendingRequest)
            assertEquals(false, dao.entry("e")!!.isV2)
            assertEquals(0, dao.entry("e")!!.pinRevision)
            assertEquals(if (version >= 9) listOf("p") else emptyList<String>(), dao.photoIds())
            assertEquals(if (version >= 9) 1L else null, dao.photoSync("s1")?.revision)
            assertEquals(if (version >= 9) 0L else null, dao.photoSync("s1")?.acknowledgedRevision)
            assertEquals(null, dao.photoSync("restored"))
            assertEquals(if (version >= 8) "reviewed-story" else null, dao.storyboard("s1")?.payload)
            if (version == 11 || version >= 13) {
                assertEquals("saved-bundle", dao.sceneAnalysis("s1")!!.bundle)
                assertEquals("original-stamp", dao.sceneAnalysis("s1")!!.bundleEntryStamp)
            }
            if (version == 10) {
                assertEquals("saved-bundle", dao.sceneAnalysis("s1")!!.bundle)
                assertEquals("original-stamp", dao.sceneAnalysis("s1")!!.bundleEntryStamp)
                assertEquals(null, dao.sceneAnalysis("s2")!!.bundleEntryStamp)
                assertEquals("failed", dao.sceneAnalysis("s2")!!.status)
                dao.deleteSession("s1")
                assertEquals(null, dao.sceneAnalysis("s1"))
                assertEquals(null, dao.storyboard("s1"))
                assertEquals(emptyList<String>(), dao.photoIds())
            }
        } finally { db.close() }
    }

    @Test
    fun `6의 옛 행동은 비우지만 산책 경로는 7에 남는다`() = runBlocking {
        legacyV5 {
            execSQL("INSERT INTO walk_session (id, startedAtMillis, endedAtMillis, syncState) VALUES ('s1',1000,2000,'local_only')")
            execSQL("INSERT INTO walk_fix VALUES ('s1',0,0,1100,37.5,127.0,5.0,0)")
        }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME), null).use { old ->
            old.execSQL("CREATE TABLE walk_action (id TEXT NOT NULL, sessionId TEXT NOT NULL, typeCode TEXT NOT NULL, " +
                "recordedAtMillis INTEGER NOT NULL, locationCapturedAtMillis INTEGER NOT NULL, lat REAL NOT NULL, " +
                "lng REAL NOT NULL, accuracyM REAL, PRIMARY KEY(id), FOREIGN KEY(sessionId) REFERENCES walk_session(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_walk_action_sessionId ON walk_action(sessionId)")
            old.execSQL("INSERT INTO walk_action VALUES ('old','s1','explore',1500,1400,37.5,127.0,5.0)")
            old.version = 6
        }
        val db = openLatest()
        try {
            val log = RoomWalkFixLog(db.walkDao())
            assertEquals(1, log.fixes("s1").size)
            assertEquals(0, log.actions("s1").size)
            assertEquals(0, db.walkDao().actions("s1").size)
        } finally { db.close() }
    }

    @Test
    fun `3에서 6으로 올라가도 세션과 좌표와 강아지가 남는다`() = runBlocking {
        legacyV3 {
            execSQL(
                "INSERT INTO walk_session VALUES " +
                    "('s1','dog-1',1000,2000,61,1,18.5,NULL)," +
                    "('s2',NULL,3000,4000,NULL,NULL,NULL,9000)",
            )
            execSQL("INSERT INTO walk_fix VALUES ('s1',0,0,1100,37.5,127.0,5.0,0)")
            execSQL("INSERT INTO walk_fix VALUES ('s1',1,0,1200,37.501,127.0,5.0,0)")
        }

        val db = openLatest()
        try {
            val log = RoomWalkFixLog(db.walkDao())

            val kept = log.finishedSessions()
            assertEquals(listOf("s2", "s1"), kept.map { it.id })

            // 있던 강아지는 조인으로 옮겨진다. 없던 것은 빈 목록이다.
            assertEquals(listOf("dog-1"), log.session("s1")?.dogIds)
            assertEquals(emptyList<String>(), log.session("s2")?.dogIds)

            // 좌표는 손대지 않는다. 표를 다시 만드는 동안 쓸려 나가면 안 된다.
            assertEquals(listOf(0, 1), log.fixes("s1").map { it.clientSeq })

            // 날씨와 "올라간 시각"도 그대로다. 예전 synced는 계산 완료가 아니라 원본
            // 업로드만 뜻했으므로 raw_uploaded로 옮겨져 다음 sync에서 finalize된다.
            assertEquals(61, log.session("s1")?.weather?.weatherCode)
            assertEquals(9000L, log.session("s2")?.syncedAtMillis)
            assertEquals(WalkSyncState.LOCAL_ONLY, log.session("s1")?.syncState)
            assertEquals(WalkSyncState.RAW_UPLOADED, log.session("s2")?.syncState)
            assertEquals(null, log.session("s2")?.serverWalkId)
        } finally { db.close() }
    }

    @Test
    fun `5에서 6으로 올라가면 기존 산책은 남고 행동 원본을 저장할 수 있다`() = runBlocking {
        legacyV5 {
            execSQL(
                "INSERT INTO walk_session VALUES " +
                    "('s1',1000,2000,61,1,18.5,'raw_uploaded','server-1',9000)",
            )
            execSQL("INSERT INTO walk_session_dog VALUES ('s1','dog-1')")
            execSQL("INSERT INTO walk_fix VALUES ('s1',0,0,1100,37.5,127.0,5.0,0)")
        }

        val db = openLatest()
        try {
            val log = RoomWalkFixLog(db.walkDao())

            assertEquals(listOf("dog-1"), log.session("s1")?.dogIds)
            assertEquals(WalkSyncState.RAW_UPLOADED, log.session("s1")?.syncState)
            assertEquals("server-1", log.session("s1")?.serverWalkId)
            assertEquals(1, log.fixes("s1").size)
            assertEquals(emptyList<RecordedWalkAction>(), log.actions("s1"))

            log.appendAction(
                RecordedWalkAction(
                    id = "a1",
                    sessionId = "s1",
                    type = WalkMomentType.SNIFFING,
                    recordedAtMillis = 1_500L,
                    locationCapturedAtMillis = 1_400L,
                    point = GeoPoint(37.5, 127.0),
                    accuracyMeters = 5f,
                ),
            )
            assertEquals(listOf("a1"), log.actions("s1").map { it.id })
        } finally { db.close() }
    }

    /** 옮긴 뒤에도 아이를 더 붙일 수 있다 — 새 표가 제대로 선 것을 확인한다. */
    @Test
    fun `옮긴 뒤에도 아이를 더 붙일 수 있다`() = runBlocking {
        legacyV3 {
            execSQL("INSERT INTO walk_session VALUES ('s1','dog-1',1000,2000,NULL,NULL,NULL,NULL)")
        }

        val db = openLatest()
        try {
            val log = RoomWalkFixLog(db.walkDao())
            db.walkDao().insertSessionDog(WalkSessionDogRow(sessionId = "s1", dogId = "dog-2"))

            assertEquals(listOf("dog-1", "dog-2"), log.session("s1")?.dogIds)
        } finally { db.close() }
    }

    /** 버전 3 짜리 DB 파일을 손으로 만든다. */
    private fun legacyV3(fill: SQLiteDatabase.() -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME), null).use { legacy ->
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `walk_session` (`id` TEXT NOT NULL, `dogId` TEXT, " +
                    "`startedAtMillis` INTEGER NOT NULL, `endedAtMillis` INTEGER, " +
                    "`weatherCode` INTEGER, `isDay` INTEGER, `temperatureC` REAL, " +
                    "`syncedAtMillis` INTEGER, PRIMARY KEY(`id`))",
            )
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `walk_fix` (`sessionId` TEXT NOT NULL, " +
                    "`clientSeq` INTEGER NOT NULL, `chainIndex` INTEGER NOT NULL, " +
                    "`atMillis` INTEGER NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, " +
                    "`accuracyM` REAL, `isMock` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`sessionId`, `clientSeq`), FOREIGN KEY(`sessionId`) " +
                    "REFERENCES `walk_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_walk_fix_sessionId` ON `walk_fix` (`sessionId`)",
            )
            // Room 은 이 표의 해시로 "내가 아는 스키마인가"를 판단한다. 없으면 열자마자
            // 무결성 확인 실패로 터진다.
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            legacy.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(V3_IDENTITY_HASH),
            )
            legacy.fill()
            legacy.version = 3
        }
    }

    /** 버전 5의 실제 표 모양. 행동 표가 없는 상태에서 6으로 올린다. */
    private fun legacyV5(fill: SQLiteDatabase.() -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME), null).use { legacy ->
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `walk_session` (`id` TEXT NOT NULL, " +
                    "`startedAtMillis` INTEGER NOT NULL, `endedAtMillis` INTEGER, " +
                    "`weatherCode` INTEGER, `isDay` INTEGER, `temperatureC` REAL, " +
                    "`syncState` TEXT NOT NULL DEFAULT 'local_only', `serverWalkId` TEXT, " +
                    "`syncedAtMillis` INTEGER, PRIMARY KEY(`id`))",
            )
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `walk_session_dog` (`sessionId` TEXT NOT NULL, " +
                    "`dogId` TEXT NOT NULL, PRIMARY KEY(`sessionId`, `dogId`), " +
                    "FOREIGN KEY(`sessionId`) REFERENCES `walk_session`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_walk_session_dog_sessionId` " +
                    "ON `walk_session_dog` (`sessionId`)",
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_walk_session_dog_dogId` " +
                    "ON `walk_session_dog` (`dogId`)",
            )
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `walk_fix` (`sessionId` TEXT NOT NULL, " +
                    "`clientSeq` INTEGER NOT NULL, `chainIndex` INTEGER NOT NULL, " +
                    "`atMillis` INTEGER NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, " +
                    "`accuracyM` REAL, `isMock` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`sessionId`, `clientSeq`), FOREIGN KEY(`sessionId`) " +
                    "REFERENCES `walk_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_walk_fix_sessionId` ON `walk_fix` (`sessionId`)",
            )
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            legacy.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(V5_IDENTITY_HASH),
            )
            legacy.fill()
            legacy.version = 5
        }
    }

    private fun openLatest(): WalkDatabase =
        Room.databaseBuilder(context, WalkDatabase::class.java, NAME)
            .addMigrations(
                WalkDatabase.MIGRATION_1_2,
                WalkDatabase.MIGRATION_2_3,
                WalkDatabase.MIGRATION_3_4,
                WalkDatabase.MIGRATION_4_5,
                WalkDatabase.MIGRATION_5_6,
                WalkDatabase.MIGRATION_6_7,
                WalkDatabase.MIGRATION_7_8,
                WalkDatabase.MIGRATION_8_9,
                WalkDatabase.MIGRATION_9_10,
                WalkDatabase.MIGRATION_10_11,
                WalkDatabase.MIGRATION_11_12,
                WalkDatabase.MIGRATION_12_13,
                WalkDatabase.MIGRATION_13_14,
                WalkDatabase.MIGRATION_14_15,
            )
            .build()

    private companion object {
        const val NAME = "migration_walk.db"

        /** `app/schemas/...WalkDatabase/3.json` 의 `identityHash` 다. */
        const val V3_IDENTITY_HASH = "371f16795ec71629c21eb73e3e740ce9"
        const val V5_IDENTITY_HASH = "63387eabb3336c05e5d738962113b772"
    }
}
