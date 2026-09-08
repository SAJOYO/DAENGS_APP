package com.daengs.app.walk.store

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.location.GeoPoint
import com.daengs.app.location.LocationSample
import com.daengs.app.walk.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkPhotoStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: WalkDatabase
    private lateinit var dao: WalkDao
    private lateinit var store: WalkPhotoStore
    private lateinit var directory: File
    private var owner = "a"
    private val capture = WalkPhotoCapture("s", "a", 2000,
        LocationSample(GeoPoint(37.5, 127.0), 1900, 1_000_000_000, 5f))

    @Before fun open(): Unit = runBlocking {
        context.deleteDatabase("photo-test.db")
        directory = temporary.newFolder("photos")
        reopen()
        dao.insertSession(WalkSessionRow("s", startedAtMillis = 1000, endedAtMillis = 3000, ownerId = owner))
    }
    private fun reopen() {
        db = Room.databaseBuilder(context, WalkDatabase::class.java, "photo-test.db").build()
        dao = db.walkDao()
        store = WalkPhotoStore(dao, directory) { owner }
    }
    @After fun close() { db.close(); context.deleteDatabase("photo-test.db") }
    private fun shot(): File = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
    private fun log() = RoomWalkFixLog(dao, prunePhotos = store::prune) { owner }

    @Test fun `재실행 뒤 사진 파일과 촬영 위치가 복원되고 일기 전송 큐는 비어 있다`() = runBlocking {
        val source = shot()
        val photo = store.save(capture, source)
        assertFalse(source.exists())
        db.close(); reopen()
        val restored = store.observe("s").first().single()
        assertEquals(photo, restored)
        assertArrayEquals(byteArrayOf(1, 2, 3), restored.file.readBytes())
        assertEquals(1900L, dao.photo(photo.id)!!.locationCapturedAtMillis)
        assertTrue(dao.entries("s").isEmpty())
        assertTrue(dao.dirtyEntrySessions().isEmpty())
        assertTrue(log().actions("s").isEmpty())
    }

    @Test fun `사진만 있는 짧은 산책은 보관하고 운동 횟수는 늘리지 않는다`() = runBlocking {
        store.save(capture, shot())
        val history = WalkHistory(log())
        assertTrue(history.keepIfWalk("s"))
        assertEquals(1, history.finished().size)
        assertEquals(0, history.finished().totalsFor(0, 10000).count)
    }

    @Test fun `사진 삭제는 다른 사진과 세션을 보존한다`() = runBlocking {
        val first = store.save(capture, shot())
        val second = store.save(capture, shot())
        store.delete(first.id)
        assertFalse(first.file.exists())
        assertTrue(second.file.exists())
        assertEquals(listOf(second.id), store.observe("s").first().map { it.id })
        assertNotNull(dao.session("s"))
    }

    @Test fun `다른 계정은 사진을 읽거나 지우거나 이전 촬영을 저장하지 못한다`() = runBlocking {
        val photo = store.save(capture, shot())
        owner = "b"
        assertTrue(store.observe("s").first().isEmpty())
        assertTrue(runCatching { store.delete(photo.id) }.isFailure)
        val source = shot()
        assertTrue(runCatching { store.save(capture, source) }.isFailure)
        assertFalse(source.exists())
        assertEquals(1, directory.listFiles()!!.size)
        assertTrue(photo.file.exists())
    }

    @Test fun `없어진 산책 또는 빈 촬영 파일은 고아 사진을 남기지 않는다`() = runBlocking {
        assertTrue(runCatching { store.save(capture, temporary.newFile()) }.isFailure)
        dao.deleteSession("s")
        val source = shot()
        assertTrue(runCatching { store.save(capture, source) }.isFailure)
        assertFalse(source.exists())
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun `세션 삭제와 시작시 정리가 고아 파일만 지운다`() = runBlocking {
        val photo = store.save(capture, shot())
        val orphan = File(directory, "interrupted.jpg").apply { writeText("partial") }
        store.prune()
        assertFalse(orphan.exists()); assertTrue(photo.file.exists())
        log().deleteSession("s")
        assertFalse(photo.file.exists())
        assertTrue(dao.photoIds().isEmpty())
    }

    @Test fun `다견 기록은 남기고 탈퇴 계정의 사진만 지운다`() = runBlocking {
        dao.insertSessionDog(WalkSessionDogRow("s", "one"))
        dao.insertSessionDog(WalkSessionDogRow("s", "two"))
        val shared = store.save(capture, shot())
        log().forgetDog("one")
        assertTrue(shared.file.exists())
        owner = "b"
        dao.insertSession(WalkSessionRow("other", startedAtMillis = 1000, endedAtMillis = null, ownerId = owner))
        val other = store.save(capture.copy(sessionId = "other", ownerId = owner), shot())
        owner = "a"
        log().forgetEverything()
        assertFalse(shared.file.exists()); assertTrue(other.file.exists())
    }
}
