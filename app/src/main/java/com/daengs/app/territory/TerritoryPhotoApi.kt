package com.daengs.app.territory

import com.daengs.app.location.LocationSample
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal const val MAX_TERRITORY_PHOTO_BYTES = 12L * 1024 * 1024
internal val TERMINAL_PHOTO_STATUSES = setOf("VERIFIED", "REJECTED", "FAILED")

internal fun photoCaptureBody(captureId: String, sessionId: String, siteId: String, petId: String,
                              fix: LocationSample, capturedAt: Long): String =
    JSONObject(markBody(sessionId, siteId, petId, fix)).apply {
        remove("claiming_pet_id"); remove("observed_at")
        put("client_capture_id", canonicalUuid(captureId))
        put("captured_at", Instant.ofEpochMilli(capturedAt).toString())
        put("content_type", "image/jpeg")
    }.toString()

internal data class TerritoryUploadTicket(val photoId: String, val status: String,
    val url: String?, val headers: Map<String, String>)

internal fun parsePhotoTicket(body: String, capture: String): TerritoryUploadTicket {
    val value = JSONObject(body)
    val expected = JSONObject(capture)
    listOf("client_capture_id", "client_session_id", "site_id").forEach {
        require(value.getString(it) == expected.getString(it))
    }
    require(Instant.parse(value.getString("captured_at")) == Instant.parse(expected.getString("captured_at")))
    val status = value.getString("status")
    require(status in TERMINAL_PHOTO_STATUSES || status in setOf("PENDING_UPLOAD", "VISION_PENDING"))
    val url = if (value.isNull("upload_url")) null else value.getString("upload_url")
    val headers = value.optJSONObject("upload_headers")?.let { row -> row.keys().asSequence().associateWith { row.getString(it) } }.orEmpty()
    if (status == "PENDING_UPLOAD") require(!url.isNullOrBlank())
    return TerritoryUploadTicket(canonicalUuid(value.getString("attempt_id")), status, url, headers)
}

fun interface TerritoryPhotoUploader {
    suspend fun upload(url: String, headers: Map<String, String>, file: File)
}

class TerritoryUploadException(val status: Int) : Exception("인증 사진 전송 오류 ($status)")

/** Storage tickets carry their own authorization. The app's Bearer token never enters this client. */
class HttpTerritoryPhotoUploader : TerritoryPhotoUploader {
    override suspend fun upload(url: String, headers: Map<String, String>, file: File) = withContext(Dispatchers.IO) {
        val target = URL(url)
        require(target.protocol in setOf("https", "http") && target.userInfo == null)
        require(file.isFile && file.length() in 1..MAX_TERRITORY_PHOTO_BYTES)
        val connection = target.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            headers.forEach { (key, value) ->
                require(key.lowercase() !in setOf("authorization", "cookie", "host", "content-length"))
                require(!key.contains('\r') && !key.contains('\n') && !value.contains('\r') && !value.contains('\n'))
                connection.setRequestProperty(key, value)
            }
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(file.length())
            file.inputStream().use { input -> connection.outputStream.use { output -> input.copyTo(output) } }
            val status = connection.responseCode
            // Create-only retry after a lost PUT response: confirm checks the existing object.
            if (status !in 200..299 && status !in setOf(409, 412)) throw TerritoryUploadException(status)
        } finally { connection.disconnect() }
    }
}
