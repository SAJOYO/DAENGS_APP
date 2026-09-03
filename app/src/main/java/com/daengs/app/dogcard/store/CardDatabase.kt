package com.daengs.app.dogcard.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 뽑아 놓은 카드만 소유하는 로컬 DB.
 *
 * **`WalkDatabase` 에 표를 더하지 않고 파일을 따로 뒀다.** 두 가지 이유다.
 *
 * 하나, **지우는 규칙이 정반대다.** 산책 기록은 탈퇴할 때 통째로 지우는 것이 개인정보
 * 처리이고, 카드는 나중에 파는 재화라 함부로 지우면 안 된다. 같은 DB 에 두면 언젠가
 * 한쪽 정리가 다른 쪽을 쓸어 간다.
 *
 * 둘, **마이그레이션 이력이 얽힌다.** `WalkDatabase` 는 이미 version 4 이고 그 중
 * 하나는 표를 통째로 다시 만든다. 카드 스키마를 고칠 때마다 사용자가 걸어서 쌓은
 * 좌표를 걸고 실험하게 된다.
 *
 * 조인할 일이 없어서 파일이 둘인 대가는 핸들 하나뿐이다.
 */
@Database(entities = [DrawnCardRow::class], version = 2, exportSchema = true)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        const val NAME = "daengs_card.db"

        /**
         * **`fallbackToDestructiveMigration()` 을 쓰지 않는다.** 여기 든 카드는 서버에
         * 사본이 없다 — 그걸 켜면 앱 업데이트 한 번에 모아 둔 도감이 통째로 사라진다.
         * 스키마를 고칠 때는 `Migration` 을 쓴다 (`WalkDatabase` 가 하는 대로).
         */
        /**
         * 사용자가 원형 틀에 직접 맞춘 카드인지 한 칸.
         *
         * **기본값이 `0` 이라 기존 행은 그대로 두면 된다.** 예전 카드는 정말로 앱이
         * 알아서 끼운 것이고, 그 카드들은 예전 규칙으로 계속 그려야 한다 — 이미
         * 뽑아 둔 카드가 업데이트로 달라지면 안 된다.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE drawn_card ADD COLUMN userFramed INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun open(context: Context): CardDatabase =
            Room.databaseBuilder(context.applicationContext, CardDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
