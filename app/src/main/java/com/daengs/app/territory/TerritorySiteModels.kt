package com.daengs.app.territory

import com.daengs.app.location.GeoPoint
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 원천 시설의 종류를 드러내지 않는 앱용 중립 점령지. */
data class TerritorySite(
    val id: String,
    val point: GeoPoint,
    val distanceMeters: Double,
)

data class TerritorySitePage(
    val count: Int,
    val truncated: Boolean,
    val sites: List<TerritorySite>,
) {
    init {
        require(count == sites.size) { "territory site count does not match sites" }
    }
}

data class NearbyTerritorySitesRequest(
    val origin: GeoPoint,
    val radiusMeters: Int = TERRITORY_QUERY_RADIUS_METERS,
    val limit: Int = TERRITORY_QUERY_LIMIT,
) {
    init {
        require(origin.latitude in 32.0..40.0) { "latitude is outside the service area" }
        require(origin.longitude in 123.0..133.0) { "longitude is outside the service area" }
        require(radiusMeters in 50..3_000) { "radiusMeters must be in 50..3000" }
        require(limit in 1..500) { "limit must be in 1..500" }
    }
}

fun JsonObject.toTerritorySitePage(): TerritorySitePage {
    val sites = getValue("sites").jsonArray.map { item ->
        item.jsonObject.let { site ->
            TerritorySite(
                id = site.getValue("site_id").jsonPrimitive.content,
                point = GeoPoint(
                    latitude = site.getValue("lat").jsonPrimitive.double,
                    longitude = site.getValue("lng").jsonPrimitive.double,
                ),
                distanceMeters = site.getValue("distance_m").jsonPrimitive.double,
            )
        }
    }
    return TerritorySitePage(
        count = getValue("count").jsonPrimitive.int,
        truncated = getValue("truncated").jsonPrimitive.boolean,
        sites = sites,
    )
}

const val TERRITORY_QUERY_RADIUS_METERS = 3_000
const val TERRITORY_QUERY_LIMIT = 500
