package com.daengs.app.territory.bookmarks

import com.daengs.app.location.GeoPoint
import org.json.JSONObject
import java.time.Instant

enum class BookmarkLocationStatus { AVAILABLE, NOT_FOUND, UNAVAILABLE }
data class TerritoryBookmark(val siteId: String, val createdAtMillis: Long, val point: GeoPoint?,
    val locationStatus: BookmarkLocationStatus)
data class BookmarkList(val items: List<TerritoryBookmark>, val totalCount: Int, val limit: Int)
data class BookmarkMutation(val siteId: String, val isBookmarked: Boolean, val createdAtMillis: Long?,
    val totalCount: Int, val limit: Int)

internal fun requireBookmarkSite(value: String) {
    require(value.length <= 96 && Regex("^territory-site:hex-v1:140:-?[0-9]+:-?[0-9]+$").matches(value))
}
private fun JSONObject.count(key: String): Int = get(key).let {
    require(it is Int || it is Long)
    (it as Number).toLong().also { n -> require(n in 0..Int.MAX_VALUE) }.toInt()
}
internal fun parseBookmarkList(body: String): BookmarkList {
    val root = JSONObject(body)
    val total = root.count("total_count")
    val limit = root.count("limit").also { require(it > 0) }
    val array = root.getJSONArray("items")
    require(array.length() == total && total <= limit)
    val items = (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        val id = (row.get("site_id") as String).also(::requireBookmarkSite)
        val status = BookmarkLocationStatus.valueOf(row.getString("location_status"))
        val location = row.get("location")
        val point = if (status == BookmarkLocationStatus.AVAILABLE) {
            val obj = location as JSONObject
            val lat = (obj.get("lat") as Number).toDouble()
            val lng = (obj.get("lng") as Number).toDouble()
            require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0)
            GeoPoint(lat, lng)
        } else { require(location == JSONObject.NULL); null }
        TerritoryBookmark(id, Instant.parse(row.getString("created_at")).toEpochMilli(), point, status)
    }
    require(items.map { it.siteId }.distinct().size == total)
    return BookmarkList(items, total, limit)
}
internal fun parseBookmarkMutation(body: String, siteId: String, saved: Boolean): BookmarkMutation {
    val root = JSONObject(body)
    val id = (root.get("site_id") as String).also(::requireBookmarkSite)
    require(id == siteId && root.get("is_bookmarked") == saved)
    val created = root.get("created_at").let { if (it == JSONObject.NULL) null else Instant.parse(it as String).toEpochMilli() }
    require((created != null) == saved)
    val total = root.count("total_count")
    val limit = root.count("limit").also { require(it > 0) }
    require(total <= limit && (!saved || total > 0))
    return BookmarkMutation(id, saved, created, total, limit)
}
