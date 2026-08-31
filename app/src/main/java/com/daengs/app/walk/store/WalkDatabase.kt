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
    entities = [WalkSessionRow::class, WalkFixRow::class],
    version = 2,
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

        fun open(context: Context): WalkDatabase =
            Room.databaseBuilder(context.applicationContext, WalkDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
