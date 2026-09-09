package com.daengs.app.territory

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Public occupancy omits other users' session/attempt identities. Never invent those values. */
data class SharedTerritoryOccupancy(
    val ownerPetId: String,
    val ownerPetName: String,
    val isMine: Boolean,
    val certification: ClaimCertification,
    val occupiedAtMillis: Long,
    val protectedUntilMillis: Long? = null,
)

data class SharedTerritorySite(val siteId: String, val version: Long, val occupancy: SharedTerritoryOccupancy?,
    val serverNowMillis: Long? = null, val policyVersion: String? = null,
    val receivedAtNanos: Long = System.nanoTime())

fun interface TerritoryOccupancyClient {
    suspend fun fetch(accessToken: String, siteIds: List<String>): List<SharedTerritorySite>
}

class TerritoryOccupancyApiException(val status: Int) : Exception("점유 조회 응답 오류 ($status)")

/** GET /occupancies stays independent from the durable action API. */
class TerritoryOccupancyApi(private val baseUrl: () -> String) : TerritoryOccupancyClient {
    override suspend fun fetch(accessToken: String, siteIds: List<String>): List<SharedTerritorySite> =
        withContext(Dispatchers.IO) {
            require(accessToken.isNotBlank())
            val ids = siteIds.distinct()
            require(ids.size in 1..100 && ids.all { SITE_ID.matches(it) })
            val query = ids.joinToString("&") { "site_ids=" + URLEncoder.encode(it, "UTF-8") }
            val connection = URL(baseUrl().trimEnd('/') + "/app/territory/occupancies?$query")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                if (status !in 200..299) throw TerritoryOccupancyApiException(status)
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(4096)
                    val result = StringBuilder()
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        require(result.length + count <= 256_000) { "점유 응답이 너무 큽니다" }
                        result.append(buffer, 0, count)
                    }
                    result.toString()
                }
                parseSharedTerritories(body, ids)
            } finally { connection.disconnect() }
        }
}

internal fun parseSharedTerritories(body: String, requested: List<String>): List<SharedTerritorySite> {
    val array = JSONArray(body)
    val sites = (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        val version = row.get("version")
        require(version is Int || version is Long)
        require((version as Number).toLong() >= 0)
        val occupancy = if (row.get("occupancy") == JSONObject.NULL) null else {
            val value = row.getJSONObject("occupancy")
            val mine = value.get("is_mine")
            require(mine is Boolean)
            val petId = value.getString("owner_pet_id")
            require(UUID.fromString(petId).toString() == petId)
            SharedTerritoryOccupancy(petId, value.getString("owner_pet_name"), mine,
                ClaimCertification.valueOf(value.getString("certification")),
                Instant.parse(value.getString("occupied_at")).toEpochMilli(),
                if (value.has("protected_until") && !value.isNull("protected_until")) Instant.parse(value.getString("protected_until")).toEpochMilli() else null)
        }
        SharedTerritorySite(row.getString("site_id"), version.toLong(), occupancy,
            if (row.has("server_now") && !row.isNull("server_now")) Instant.parse(row.getString("server_now")).toEpochMilli() else null,
            if (row.has("policy_version") && !row.isNull("policy_version")) row.getString("policy_version") else null)
    }
    require(sites.map { it.siteId }.toSet() == requested.toSet() && sites.size == requested.size) {
        "점유 응답의 장소 목록이 요청과 다릅니다"
    }
    return sites
}

private val SITE_ID = Regex("^territory-site:hex-v1:140:-?\\d+:-?\\d+$")
