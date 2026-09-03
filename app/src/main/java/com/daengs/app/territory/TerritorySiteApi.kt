package com.daengs.app.territory

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class TerritorySiteApi(
    private val baseUrl: () -> String,
    private val json: Json = Json,
) {
    suspend fun nearby(request: NearbyTerritorySitesRequest): TerritorySitePage =
        withContext(Dispatchers.IO) {
            val connection = (
                URL(territorySitesNearbyUrl(baseUrl(), request)).openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
            }

            try {
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                if (status !in 200..299) {
                    throw TerritorySiteApiException(status, body.take(500))
                }
                json.parseToJsonElement(body).jsonObject.toTerritorySitePage()
            } finally {
                connection.disconnect()
            }
        }
}

internal fun territorySitesNearbyUrl(
    baseUrl: String,
    request: NearbyTerritorySitesRequest,
): String = buildString {
    append(baseUrl.trimEnd('/'))
    append("/territory/sites/nearby")
    append("?lat=").append(request.origin.latitude)
    append("&lng=").append(request.origin.longitude)
    append("&radius_m=").append(request.radiusMeters)
    append("&limit=").append(request.limit)
}

class TerritorySiteApiException(
    val status: Int,
    val responseBody: String,
) : IllegalStateException("Territory site search failed ($status): $responseBody")
