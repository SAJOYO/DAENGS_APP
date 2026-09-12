package com.daengs.app.walk.sync

import com.daengs.app.walk.store.WalkDao
import com.daengs.app.walk.store.WalkPhotoUploadSnapshot
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** The image never leaves the private photo store. This sends only a frozen metadata manifest. */
class WalkPhotoSync(
    private val dao: WalkDao,
    private val owner: () -> String,
    private val request: suspend (String, String, String, JSONObject?) -> JSONObject = { token, path, method, body ->
        WalkApi.call(token, path, method, body, parse = ::JSONObject).getOrThrow()
    },
) {
    private val mutex = Mutex()

    suspend fun sync(token: String, sessionId: String, walkId: String) = mutex.withLock {
        val capturedOwner = owner().takeIf { it.isNotBlank() } ?: return@withLock
        val initial = dao.photoUploadSnapshot(sessionId, capturedOwner, walkId) ?: return@withLock
        if (initial.state.revision == initial.state.acknowledgedRevision && initial.state.pendingPayload == null) return@withLock
        val capabilities = try { request(token, "/photo-metadata/capabilities", "GET", null) }
        catch (e: WalkHttpException) { if (e.statusCode == 404) return@withLock else throw e }
        val versions = capabilities.getJSONArray("write_versions")
        if ((0 until versions.length()).none { versions.getString(it) == FORMAT }) return@withLock
        val maxRecords = capabilities.getInt("max_records")
        check(maxRecords > 0) { "사진 전송 한도를 확인할 수 없어요." }
        repeat(3) {
            if (owner() != capturedOwner) return@withLock
            val snapshot = dao.photoUploadSnapshot(sessionId, capturedOwner, walkId) ?: return@withLock
            val state = snapshot.state
            if (state.revision == state.acknowledgedRevision && state.pendingPayload == null) return@withLock
            val sent = state.pendingPayload ?: run {
                check(snapshot.photos.size <= maxRecords) { "한 산책의 사진은 ${maxRecords}개까지 전송할 수 있어요." }
                photoManifest(snapshot).toString()
            }.also {
                if (dao.freezePhotoUpload(sessionId, capturedOwner, state.revision, it) != 1)
                    throw IOException("사진이 편집돼 다음 전송에서 다시 준비합니다.")
            }
            if (owner() != capturedOwner) return@withLock
            val body = JSONObject(sent)
            val result = try {
                request(token, "/$walkId/photo-metadata", "PUT", body)
            } catch (e: WalkHttpException) {
                // Only validation rejection proves no write. Lost ACKs and 409 conflicts
                // retain the exact request, even if local photos changed in the meantime.
                if (e.statusCode == 422 && owner() == capturedOwner &&
                    dao.rejectPhotoUpload(sessionId, capturedOwner, body.getLong("expected_revision"), sent) == 1) {
                    val current = dao.photoUploadSnapshot(sessionId, capturedOwner, walkId)
                    if (current != null && current.state.revision > body.getLong("revision")) return@repeat
                }
                throw e
            }
            check(result.getString("format") == FORMAT && result.getString("status") == "complete" &&
                result.getString("client_session_id") == sessionId &&
                result.getString("publisher_id") == body.getString("publisher_id") &&
                result.getLong("revision") == body.getLong("revision")) { "사진 동기화 응답이 요청과 달라요." }
            if (owner() != capturedOwner) return@withLock
            dao.acknowledgePhotoUpload(sessionId, capturedOwner, body.getLong("revision"), sent)
        }
        val remaining = dao.photoUploadSnapshot(sessionId, capturedOwner, walkId)?.state
        if (remaining != null && remaining.revision > remaining.acknowledgedRevision)
            throw IOException("편집된 사진 목록을 다음 전송에서 이어갑니다.")
    }
}

private const val FORMAT = "walk-photo-metadata-v1"

internal fun photoManifest(snapshot: WalkPhotoUploadSnapshot): JSONObject = JSONObject().apply {
    val state = snapshot.state
    put("format", FORMAT)
    put("publisher_id", state.publisherId)
    put("revision", state.revision)
    put("expected_revision", state.acknowledgedRevision)
    put("photos", JSONArray().apply {
        snapshot.photos.sortedBy { it.id }.forEach { photo ->
            put(JSONObject().apply {
                put("id", photo.id)
                put("captured_at", Instant.ofEpochMilli(photo.capturedAtMillis).toString())
                put("location_captured_at", Instant.ofEpochMilli(photo.locationCapturedAtMillis).toString())
                put("point", JSONObject().put("lat", photo.lat).put("lng", photo.lng))
                put("accuracy_m", photo.accuracyM.toDouble())
            })
        }
    })
}
