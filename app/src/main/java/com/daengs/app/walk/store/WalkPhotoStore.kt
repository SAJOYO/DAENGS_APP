package com.daengs.app.walk.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkPhotoCapture
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Entity(tableName = "walk_photo", foreignKeys = [ForeignKey(entity = WalkSessionRow::class,
    parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")])
data class WalkPhotoRow(@PrimaryKey val id: String, val sessionId: String, val ownerId: String,
    val capturedAtMillis: Long, val locationCapturedAtMillis: Long,
    val lat: Double, val lng: Double, val accuracyM: Float)

/** 파일은 앱 전용 영구 디렉터리, Room에는 셔터 위치만 보관한다. */
class WalkPhotoStore(private val dao: WalkDao, private val directory: File,
    private val owner: () -> String) {
    private val mutex = Mutex()
    private fun file(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(directory, "$id.jpg")
    }
    fun observe(sessionId: String) = dao.observePhotos(sessionId).map { rows ->
        if (dao.session(sessionId)?.ownerId != owner()) emptyList() else
            rows.filter { it.ownerId == owner() }.map {
                WalkPhoto(it.id, it.sessionId, it.capturedAtMillis, GeoPoint(it.lat, it.lng), file(it.id))
            }
    }

    /** 화면 회전으로 UI coroutine이 취소돼도 파일/DB 저장을 마친다. 실패 파일은 회수한다. */
    suspend fun save(capture: WalkPhotoCapture, source: File): WalkPhoto =
        withContext(Dispatchers.IO + NonCancellable) {
            mutex.withLock {
                val id = UUID.randomUUID().toString()
                val destination = file(id)
                try {
                    check(capture.ownerId == owner() &&
                        dao.session(capture.sessionId)?.ownerId == capture.ownerId) { "산책 계정이 변경되었어요." }
                    check(source.isFile && source.length() > 0) { "촬영한 사진이 없어요." }
                    check(directory.isDirectory || directory.mkdirs()) { "사진 저장 공간을 준비하지 못했어요." }
                    source.copyTo(destination)
                    // FK는 저장 중 삭제된 산책에 사진을 붙이지 못하게 한다.
                    check(capture.ownerId == owner()) { "산책 계정이 변경되었어요." }
                    dao.insertPhoto(WalkPhotoRow(id, capture.sessionId, capture.ownerId,
                        capture.capturedAtMillis, capture.sample.capturedAtMillis,
                        capture.sample.point.latitude, capture.sample.point.longitude,
                        requireNotNull(capture.sample.accuracyMeters)))
                    WalkPhoto(id, capture.sessionId, capture.capturedAtMillis, capture.sample.point, destination)
                } catch (e: Exception) {
                    destination.delete()
                    throw e
                } finally { source.delete() }
            }
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock {
            val row = dao.photo(id) ?: return@withLock
            check(row.ownerId == owner() && dao.session(row.sessionId)?.ownerId == owner())
            dao.deletePhoto(id)
            file(id).delete()
        }
    }

    /** 세션 cascade 삭제 뒤와 앱 시작 때 실행. 저장 중인 파일은 mutex로 보호한다. */
    suspend fun prune() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val kept = dao.photoIds().map { "$it.jpg" }.toSet()
            directory.listFiles()?.filter { it.isFile && it.name !in kept }?.forEach { it.delete() }
        }
    }
}
