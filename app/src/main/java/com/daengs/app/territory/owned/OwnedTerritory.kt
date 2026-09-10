package com.daengs.app.territory.owned

import com.daengs.app.location.GeoPoint
import com.daengs.app.territory.ClaimCertification
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class OwnedTerritory(
    val siteId: String, val version: Long,
    val petId: String, val petName: String, val petBreed: String?,
    val certification: ClaimCertification, val occupiedAtMillis: Long,
    val expiresAtMillis: Long?, val point: GeoPoint?,
)

data class OwnedTerritoryPage(
    val seasonId: String?, val serverNowMillis: Long, val petId: String?,
    val totalCount: Int, val items: List<OwnedTerritory>, val nextCursor: String?,
)

internal fun requireOwnedPetId(value: String) {
    require(UUID.fromString(value).toString() == value)
}

/** Nullable fields must actually exist; malformed responses never become an empty collection. */
internal fun parseOwnedTerritories(body: String, petId: String?): OwnedTerritoryPage {
    val root = JSONObject(body)
    val status = root.getString("status")
    require(status in setOf("READY", "NO_ACTIVE_SEASON"))
    val season = root.nullableString("season_id")
    require((status == "READY") == !season.isNullOrBlank())
    if (status == "NO_ACTIVE_SEASON") require(season == null)
    val filter = root.nullableString("pet_id")
    require(filter == petId) { "Owned territory filter mismatch" }
    val now = root.whole("server_now_ms").also { require(it >= 0) }
    val total = root.whole("total_count").also { require(it in 0..Int.MAX_VALUE) }.toInt()
    val next = root.nullableString("next_cursor")?.also { require(it.length in 1..1024) }
    val array = root.getJSONArray("items")
    require(array.length() <= 100 && total >= array.length())
    val items = (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        val id = row.getString("site_id").also {
            require(it.length <= 96 && Regex("^territory-site:hex-v1:140:-?\\d+:-?\\d+$").matches(it))
        }
        val ownerPet = row.getString("pet_id").also(::requireOwnedPetId)
        require(petId == null || petId == ownerPet)
        val location = row.get("location")
        val point = when (row.getString("location_status")) {
            "NOT_FOUND" -> { require(location == JSONObject.NULL); null }
            "AVAILABLE" -> (location as JSONObject).let {
                val lat = it.getDouble("lat"); val lng = it.getDouble("lng")
                require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0)
                GeoPoint(lat, lng)
            }
            else -> error("Unknown territory location status")
        }
        OwnedTerritory(id, row.whole("version").also { require(it >= 0) }, ownerPet,
            row.getString("pet_name"), row.nullableString("pet_breed"),
            ClaimCertification.valueOf(row.getString("certification")),
            Instant.parse(row.getString("occupied_at")).toEpochMilli(),
            row.nullableString("expires_at")?.let { Instant.parse(it).toEpochMilli() }, point)
    }
    require(items.map { it.siteId }.distinct().size == items.size)
    require(next == null || items.isNotEmpty())
    if (status == "NO_ACTIVE_SEASON") require(total == 0 && items.isEmpty() && next == null)
    return OwnedTerritoryPage(season, now, filter, total, items, next)
}

private fun JSONObject.nullableString(key: String): String? = get(key).let {
    if (it == JSONObject.NULL) null else (it as String)
}
private fun JSONObject.whole(key: String): Long = get(key).let {
    require(it is Int || it is Long); (it as Number).toLong()
}
