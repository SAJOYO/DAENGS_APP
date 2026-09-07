package com.daengs.app.territory

import java.net.HttpURLConnection
import java.net.URL
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.daengs.app.location.LocationSample

/** Bodies are serialized once, before delivery, and replayed byte for byte. */
fun interface TerritoryActionClient {
    suspend fun request(token: String, method: String, path: String, body: String?): String
}

class TerritoryActionException(val status: Int, val code: String?) : Exception("영역표시 응답 오류 ($status)")

class TerritoryActionApi(private val baseUrl: () -> String) : TerritoryActionClient {
    override suspend fun request(token: String, method: String, path: String, body: String?): String = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && (path.startsWith("/claim") || path.startsWith("/attempts")))
        val connection = URL(baseUrl().trimEnd('/') + "/app/territory" + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val chars = CharArray(64_001)
                var size = 0
                while (size < chars.size) {
                    val count = reader.read(chars, size, chars.size - size)
                    if (count < 0) break
                    size += count
                }
                require(size <= 64_000)
                String(chars, 0, size)
            }.orEmpty()
            if (status !in 200..299) {
                val code = runCatching { JSONObject(response).getJSONObject("detail").getString("code") }.getOrNull()
                throw TerritoryActionException(status, code?.takeIf { it.matches(Regex("[A-Za-z_]{1,64}")) })
            }
            response
        } finally { connection.disconnect() }
    }
}

internal fun canonicalUuid(value: String): String = value.also { require(UUID.fromString(it).toString() == it) }

internal fun sessionStartBody(startedAtMillis: Long, pets: List<String>): String {
    require(pets.size in 1..20 && pets.distinct().size == pets.size)
    return JSONObject().put("started_at", Instant.ofEpochMilli(startedAtMillis).toString())
        .put("pet_ids", JSONArray(pets.map(::canonicalUuid).sorted())).toString()
}

internal fun markBody(sessionId: String, siteId: String, petId: String, fix: LocationSample): String {
    require(siteId.matches(Regex("^territory-site:hex-v1:140:-?\\d+:-?\\d+$")))
    require(fix.point.latitude.isFinite() && fix.point.latitude in -90.0..90.0)
    require(fix.point.longitude.isFinite() && fix.point.longitude in -180.0..180.0)
    require(fix.accuracyMeters != null && fix.accuracyMeters.isFinite() && fix.accuracyMeters >= 0 && !fix.isMock)
    return JSONObject().put("client_session_id", canonicalUuid(sessionId)).put("site_id", siteId)
        .put("claiming_pet_id", canonicalUuid(petId))
        .put("observed_at", Instant.ofEpochMilli(fix.capturedAtMillis).toString())
        .put("lat", BigDecimal.valueOf(fix.point.latitude).setScale(7, RoundingMode.HALF_UP))
        .put("lng", BigDecimal.valueOf(fix.point.longitude).setScale(7, RoundingMode.HALF_UP))
        .put("accuracy_m", fix.accuracyMeters.toDouble()).put("is_mock", false).toString()
}

internal data class RemoteClaimSession(val phase: String, val version: Long)
internal fun parseClaimSession(body: String, sessionId: String, registration: String): RemoteClaimSession {
    val value = JSONObject(body)
    val original = JSONObject(registration)
    require(value.getString("client_session_id") == sessionId)
    require(Instant.parse(value.getString("started_at")) == Instant.parse(original.getString("started_at")))
    fun pets(row: JSONObject) = row.getJSONArray("pet_ids").let { a -> (0 until a.length()).map { canonicalUuid(a.getString(it)) }.sorted() }
    require(pets(value) == pets(original))
    val phase = value.getString("phase")
    require(phase in listOf("RECORDING", "PAUSED", "ENDED"))
    val version = value.get("version")
    require((version is Int || version is Long) && (version as Number).toLong() >= 0)
    return RemoteClaimSession(phase, (version as Number).toLong())
}

data class RemoteTerritoryClaim(val claimId: String, val disposition: ClaimDisposition,
    val photoStatus: ClaimPhotoStatus, val site: SharedTerritorySite,
    val currentPhotoId: String? = null, val resolutionCode: String? = null)

internal fun parseTerritoryClaim(body: String, request: String): RemoteTerritoryClaim {
    val value = JSONObject(body)
    require(value.has("current_photo_id") && value.has("resolution_code"))
    val original = JSONObject(request)
    require(value.getString("client_session_id") == original.getString("client_session_id"))
    require(value.getString("claiming_pet_id") == original.getString("claiming_pet_id"))
    val site = parseSharedTerritories(JSONArray().put(value.getJSONObject("site")).toString(),
        listOf(original.getString("site_id"))).single()
    return RemoteTerritoryClaim(canonicalUuid(value.getString("claim_id")),
        ClaimDisposition.valueOf(value.getString("disposition")), ClaimPhotoStatus.valueOf(value.getString("photo_status")), site,
        if (value.isNull("current_photo_id")) null else canonicalUuid(value.getString("current_photo_id")),
        if (value.isNull("resolution_code")) null else value.getString("resolution_code"))
}
