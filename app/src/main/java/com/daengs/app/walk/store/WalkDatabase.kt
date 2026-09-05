package com.daengs.app.walk.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** 산책 원본 위치·사용자 행동과 서버 계산까지의 동기화 단계를 소유하는 로컬 DB. */
@Database(
    entities = [WalkSessionRow::class, WalkSessionDogRow::class, WalkFixRow::class, WalkActionRow::class, WalkEntryRow::class, WalkPhotoRow::class],
    version = 8,
    exportSchema = true,
)
abstract class WalkDatabase : RoomDatabase() {
    abstract fun walkDao(): WalkDao

    companion object {
        const val NAME = "daengs_walk.db"

        /**
         * 세션에 날씨 세 칸을 더한다.
         *
         * **부수지 않고 더한다.** 이 DB 에는 사용자가 걸은 기록이 들어 있고 서버에
         * 사본이 없다 — `fallbackToDestructiveMigration()` 을 쓰면 앱 업데이트 한 번에
         * 지난 산책이 통째로 사라진다.
         *
         * 셋 다 nullable 이라 기존 행은 그대로 두면 된다. 예전 산책의 날씨는 정말로
         * 모르는 것이고, 모르는 것은 null 로 남는다.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE walk_session ADD COLUMN weatherCode INTEGER")
                connection.execSQL("ALTER TABLE walk_session ADD COLUMN isDay INTEGER")
                connection.execSQL("ALTER TABLE walk_session ADD COLUMN temperatureC REAL")
            }
        }

        /**
         * 서버에 올라간 시각을 더한다.
         *
         * nullable 이라 기존 행은 그대로 두면 된다 — **예전 산책은 정말로 아직 안
         * 올라간 것**이고, 다음 동기화 때 올라간다.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE walk_session ADD COLUMN syncedAtMillis INTEGER")
            }
        }

        /**
         * 강아지 한 칸을 표로 옮긴다.
         *
         * **컬럼을 지우려면 표를 다시 만들어야 한다.** minSdk 26 의 SQLite 에는
         * `ALTER TABLE ... DROP COLUMN` 이 없다 (3.35 부터다).
         *
         * 순서가 중요하다 — **값을 먼저 옮기고** 컬럼을 지운다. 이미 걸어서 쌓인
         * 기록이 있고, 서버에 사본이 없는 산책도 있다.
         *
         * `walk_fix` 는 손대지 않는다. 이름으로 참조하므로 새 표가 같은 이름을 달면
         * 그대로 이어진다. Room 이 마이그레이션 동안 외래키를 꺼 두기 때문에
         * `DROP TABLE` 이 좌표를 쓸어 가지도 않는다.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `walk_session_dog` (" +
                        "`sessionId` TEXT NOT NULL, `dogId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`sessionId`, `dogId`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `walk_session`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_walk_session_dog_sessionId` " +
                        "ON `walk_session_dog` (`sessionId`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_walk_session_dog_dogId` " +
                        "ON `walk_session_dog` (`dogId`)",
                )

                // 있던 값을 먼저 옮긴다.
                connection.execSQL(
                    "INSERT OR IGNORE INTO `walk_session_dog` (`sessionId`, `dogId`) " +
                        "SELECT `id`, `dogId` FROM `walk_session` WHERE `dogId` IS NOT NULL",
                )

                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `walk_session_new` (" +
                        "`id` TEXT NOT NULL, `startedAtMillis` INTEGER NOT NULL, " +
                        "`endedAtMillis` INTEGER, `weatherCode` INTEGER, `isDay` INTEGER, " +
                        "`temperatureC` REAL, `syncedAtMillis` INTEGER, PRIMARY KEY(`id`))",
                )
                connection.execSQL(
                    "INSERT INTO `walk_session_new` SELECT `id`, `startedAtMillis`, " +
                        "`endedAtMillis`, `weatherCode`, `isDay`, `temperatureC`, " +
                        "`syncedAtMillis` FROM `walk_session`",
                )
                connection.execSQL("DROP TABLE `walk_session`")
                connection.execSQL("ALTER TABLE `walk_session_new` RENAME TO `walk_session`")
            }
        }

        /**
         * "좌표가 올라감"과 "계산이 끝남" 사이를 복구 가능한 상태로 만든다.
         *
         * 예전 [syncedAtMillis]는 chunk 업로드 직후 찍혔다. 그 기록을 `derived`로
         * 간주하면 실제로는 분석이 없는 산책을 영원히 finalize하지 않으므로
         * `raw_uploaded`로 옮긴다. 예전 행에는 서버 id가 없지만 create API가
         * `client_session_id`에 멱등이라 다음 동기화에서 다시 얻을 수 있다.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE walk_session ADD COLUMN syncState TEXT NOT NULL " +
                        "DEFAULT 'local_only'",
                )
                connection.execSQL("ALTER TABLE walk_session ADD COLUMN serverWalkId TEXT")
                connection.execSQL(
                    "UPDATE walk_session SET syncState = 'raw_uploaded' " +
                        "WHERE syncedAtMillis IS NOT NULL",
                )
            }
        }

        /**
         * 사용자가 산책 중 직접 누른 행동 원본을 세션 아래에 더한다.
         *
         * 기존 산책은 행동이 없던 것이 아니라 앱이 저장하지 않았던 것이므로 빈 표에서
         * 시작한다. 추측으로 과거 행동을 만들지 않는다. 세션을 지우면 외래키가 같이 지운다.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `walk_action` (" +
                        "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `typeCode` TEXT NOT NULL, " +
                        "`recordedAtMillis` INTEGER NOT NULL, " +
                        "`locationCapturedAtMillis` INTEGER NOT NULL, `lat` REAL NOT NULL, " +
                        "`lng` REAL NOT NULL, `accuracyM` REAL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `walk_session`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_walk_action_sessionId` " +
                        "ON `walk_action` (`sessionId`)",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                // 사용자 결정: 옛 행동은 이관하지 않고 경로는 보존한다.
                connection.execSQL("DELETE FROM walk_action")
                connection.execSQL("ALTER TABLE walk_session ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                connection.execSQL("CREATE TABLE IF NOT EXISTS walk_entry (id TEXT NOT NULL, " +
                    "sessionId TEXT NOT NULL, payload TEXT, revision INTEGER NOT NULL, " +
                    "mutationId TEXT NOT NULL, dirty INTEGER NOT NULL, syncError TEXT, PRIMARY KEY(id), " +
                    "FOREIGN KEY(sessionId) REFERENCES walk_session(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_walk_entry_sessionId ON walk_entry(sessionId)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS walk_photo (id TEXT NOT NULL, " +
                    "sessionId TEXT NOT NULL, ownerId TEXT NOT NULL, capturedAtMillis INTEGER NOT NULL, " +
                    "locationCapturedAtMillis INTEGER NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, " +
                    "accuracyM REAL NOT NULL, PRIMARY KEY(id), " +
                    "FOREIGN KEY(sessionId) REFERENCES walk_session(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_walk_photo_sessionId ON walk_photo(sessionId)")
            }
        }

        fun open(context: Context): WalkDatabase =
            Room.databaseBuilder(context.applicationContext, WalkDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                .build()
    }
}
