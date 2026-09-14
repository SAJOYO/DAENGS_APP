package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkExplorationStoreTest {
    @Test fun `checkpoint reopens and stays owner scoped`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "checkpoint.db"
        fun open() = Room.databaseBuilder(context, WalkDatabase::class.java, name).build()
        var db = open()
        try {
            db.walkDao().insertSession(WalkSessionRow("s", 0, "owner", 1000))
            db.walkDao().saveExploration("s", "owner", "small-checkpoint")
            db.close(); db = open()
            val dao = db.walkDao()
            assertEquals("small-checkpoint", dao.exploration("s", "owner")!!.payload)
            dao.saveExploration("s", "other", "foreign")
            assertNull(dao.exploration("s", "other"))
            assertEquals("small-checkpoint", dao.exploration("s", "owner")!!.payload)
            dao.deleteSession("s")
            dao.saveExploration("s", "owner", "late")
            assertNull(dao.exploration("s", "owner"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
