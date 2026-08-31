package com.daengs.app.walk.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** 산책 원본 위치만 소유하는 로컬 DB. 네트워크 동기화 여부는 이 저장소의 책임이 아니다. */
@Database(
    entities = [WalkSessionRow::class, WalkFixRow::class],
    version = 1,
    exportSchema = true,
)
abstract class WalkDatabase : RoomDatabase() {
    abstract fun walkDao(): WalkDao

    companion object {
        const val NAME = "daengs_walk.db"

        fun open(context: Context): WalkDatabase =
            Room.databaseBuilder(context.applicationContext, WalkDatabase::class.java, NAME)
                .build()
    }
}
