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

    @Before
    fun clean() {
        context.getDatabasePath(NAME).also { it.parentFile?.mkdirs() }.delete()
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

        db.close()
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

        db.close()
    }

    /** 옮긴 뒤에도 아이를 더 붙일 수 있다 — 새 표가 제대로 선 것을 확인한다. */
    @Test
    fun `옮긴 뒤에도 아이를 더 붙일 수 있다`() = runBlocking {
        legacyV3 {
            execSQL("INSERT INTO walk_session VALUES ('s1','dog-1',1000,2000,NULL,NULL,NULL,NULL)")
        }

        val db = openLatest()
        val log = RoomWalkFixLog(db.walkDao())
        db.walkDao().insertSessionDog(WalkSessionDogRow(sessionId = "s1", dogId = "dog-2"))

        assertEquals(listOf("dog-1", "dog-2"), log.session("s1")?.dogIds)

        db.close()
    }

    /** 버전 3 짜리 DB 파일을 손으로 만든다. */
    private fun legacyV3(fill: SQLiteDatabase.() -> Unit) {
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME), null)
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
        legacy.close()
    }

    /** 버전 5의 실제 표 모양. 행동 표가 없는 상태에서 6으로 올린다. */
    private fun legacyV5(fill: SQLiteDatabase.() -> Unit) {
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME), null)
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
        legacy.close()
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
            )
            .build()

    private companion object {
        const val NAME = "migration_walk.db"

        /** `app/schemas/...WalkDatabase/3.json` 의 `identityHash` 다. */
        const val V3_IDENTITY_HASH = "371f16795ec71629c21eb73e3e740ce9"
        const val V5_IDENTITY_HASH = "63387eabb3336c05e5d738962113b772"
    }
}
