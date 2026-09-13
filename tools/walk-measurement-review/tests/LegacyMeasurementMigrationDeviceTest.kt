package com.daengs.app.ui.walk

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daengs.app.walk.store.WalkDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Optional private source is supplied outside Git. Only its dedicated copy is migrated. */
@RunWith(AndroidJUnit4::class)
class LegacyMeasurementMigrationDeviceTest {
    @Test fun copiedLegacyDatabasePreservesEveryExistingRow() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.daengs.app.locationreview")
        val source = File(context.filesDir, "legacy-review-input.db")
        check(source.isFile) { "Provide a consistent private schema-18 snapshot first" }
        fun digest(file: File): Map<String, String> = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'walk_%' ORDER BY name", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            tables.associateWith { table ->
                check(table.matches(Regex("walk_[a-z_]+")))
                val hash = MessageDigest.getInstance("SHA-256")
                db.rawQuery("SELECT * FROM $table ORDER BY rowid", null).use { c ->
                    while (c.moveToNext()) for (i in 0 until c.columnCount) {
                        val value = if (c.isNull(i)) "null" else "${c.getType(i)}:${c.getString(i)}"
                        val bytes = value.toByteArray()
                        hash.update("${bytes.size}:".toByteArray()); hash.update(bytes)
                    }
                }
                hash.digest().joinToString("") { "%02x".format(it) }
            }
        }
        val before = digest(source)
        SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(18, it.version) }
        val name = "legacy-measurement-review.db"
        context.deleteDatabase(name)
        val room = Room.databaseBuilder(context, WalkDatabase::class.java, name).createFromFile(source)
            .addMigrations(WalkDatabase.MIGRATION_18_19, WalkDatabase.MIGRATION_19_20).build()
        try { assertNull(room.walkDao().session("nonexistent-review-session")) } finally { room.close() }
        val target = context.getDatabasePath(name)
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(20, it.version) }
        val after = digest(target)
        before.forEach { (table, hash) -> assertEquals("Existing table changed: $table", hash, after[table]) }
        assertTrue(after.keys.containsAll(listOf("walk_measurement", "walk_measurement_chunk", "walk_exploration")))
        assertEquals(before, digest(source))
    }
}
