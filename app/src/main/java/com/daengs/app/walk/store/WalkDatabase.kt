package com.daengs.app.walk.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** 산책 원본 위치만 소유하는 로컬 DB. 네트워크 동기화 여부는 이 저장소의 책임이 아니다. */
@Database(
    entities = [WalkSessionRow::class, WalkSessionDogRow::class, WalkFixRow::class],
    version = 4,
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

        fun open(context: Context): WalkDatabase =
            Room.databaseBuilder(context.applicationContext, WalkDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
