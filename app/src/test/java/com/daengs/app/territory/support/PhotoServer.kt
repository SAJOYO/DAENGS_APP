package com.daengs.app.territory.support

import com.daengs.app.territory.TerritoryActionClient
import com.daengs.app.territory.TerritoryActionException
import com.daengs.app.territory.TerritoryPhotoUploader
import com.daengs.app.territory.TerritoryUploadException
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals

internal class PhotoServer : TerritoryActionClient, TerritoryPhotoUploader {
    val claims = ClaimServer()
    data class Photo(val id: String, val body: String, var uploaded: Boolean = false, var status: String = "PENDING_UPLOAD")
    val photos = linkedMapOf<String, Photo>()
    val calls = mutableListOf<Triple<String, String, String?>>()
    var currentPhoto: Photo? = null
    var loseTicket = false
    var loseBinding = false
    var loseUpload = false
    var loseConfirm = false
    var expireUpload = false
    var decision: String? = null
    var siteChanged = false
    var rejectBinding: String? = null
    var uploadedBytes: ByteArray? = null
    var admissionFailure: String? = null
    var accessAction = "PHOTO_UPGRADE"
    var accessReason: String? = null
    var afterAdmission: (() -> Unit)? = null
    private fun ticket(photo: Photo) = JSONObject(photo.body)
        .put("attempt_id", photo.id).put("status", photo.status)
        .put("upload_url", if (photo.status == "PENDING_UPLOAD") "https://storage.invalid/${photo.id}?ticket=${calls.size}" else JSONObject.NULL)
        .put("upload_headers", JSONObject().put("Content-Type", "image/jpeg"))
        .put("expires_in_seconds", 300).toString()
    private fun claim(): String {
        val value = JSONObject(claims.committed[SITE]!!.second)
        currentPhoto?.let { photo ->
            if (photo.status == "VISION_PENDING" && decision != null) photo.status = decision!!
            value.put("current_photo_id", photo.id).put("photo_status", when (photo.status) {
                "VERIFIED" -> "VERIFIED"; "REJECTED" -> "REJECTED"; "FAILED" -> "RETRY_PENDING"; else -> "PENDING"
            })
            if (photo.status == "VERIFIED") {
                val site = value.getJSONObject("site")
                site.put("version", 2)
                if (siteChanged) {
                    value.put("resolution_code", "site_changed")
                    site.getJSONObject("occupancy").put("is_mine", false).put("owner_pet_id", DOG2)
                } else site.getJSONObject("occupancy").put("certification", "VERIFIED")
            }
        }
        return value.toString()
    }
    override suspend fun request(token: String, method: String, path: String, body: String?): String {
        calls += Triple(method, path, body)
        if (path.endsWith("/photo-access")) return JSONObject().put("allowed_action", accessAction)
            .put("reason", accessReason ?: JSONObject.NULL).toString()
        if (path.contains("/challenges/")) {
            admissionFailure?.let { throw TerritoryActionException(409, it) }
            afterAdmission?.invoke()
            return JSONObject().put("challenge_id", path.substringAfterLast('/')).toString()
        }
        if (path == "/attempts") {
            val captureId = JSONObject(body!!).getString("client_capture_id")
            val photo = photos.getOrPut(captureId) { Photo(UUID.randomUUID().toString(), body) }
            check(photo.body == body)
            if (loseTicket) { loseTicket = false; throw IOException("ticket response lost") }
            return ticket(photo)
        }
        if (method == "PUT" && path.startsWith("/claims/")) {
            val photo = photos.values.single { it.id == path.substringAfterLast('/') }
            if (currentPhoto != photo) {
                rejectBinding?.let { throw TerritoryActionException(409, it) }
                if (claims.phase != "RECORDING") throw TerritoryActionException(409, "NOT_RECORDING")
                currentPhoto = photo
            }
            if (loseBinding) { loseBinding = false; throw IOException("binding response lost") }
            return claim()
        }
        if (path.endsWith("/confirm")) {
            val photo = photos.values.single { path == "/attempts/${it.id}/confirm" }
            if (!photo.uploaded) throw TerritoryActionException(409, "photo_not_uploaded")
            if (photo.status == "PENDING_UPLOAD") photo.status = "VISION_PENDING"
            if (loseConfirm) { loseConfirm = false; throw TerritoryActionException(503, null) }
            return ticket(photo)
        }
        if (method == "GET" && path == "/claims/$CLAIM") return claim()
        return claims.request(token, method, path, body)
    }
    override suspend fun upload(url: String, headers: Map<String, String>, file: File) {
        calls += Triple("UPLOAD", url, null)
        val photo = photos.values.single { url.contains(it.id) }
        check(currentPhoto == photo) { "must bind before upload" }
        assertEquals(mapOf("Content-Type" to "image/jpeg"), headers)
        if (expireUpload) { expireUpload = false; throw TerritoryUploadException(403) }
        val bytes = file.readBytes()
        if (uploadedBytes != null && photo.uploaded) assertArrayEquals(uploadedBytes, bytes)
        photo.uploaded = true; uploadedBytes = bytes
        if (loseUpload) { loseUpload = false; throw IOException("upload response lost") }
    }
}
