package com.daengs.app.place

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 장소 API 의 표준 클라이언트. 앞으로 생길 장소 화면들이 다 이걸 쓴다. */
class PlaceApi(
    private val baseUrl: () -> String,
    private val json: Json = Json,
) {
    private val endpoint: String get() = "${baseUrl().trimEnd('/')}/v2/places/search"

    suspend fun search(request: PlaceSearchRequest): PlaceSearchResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(request.toJson().toString())
                }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                if (status !in 200..299) {
                    throw PlaceApiException(status, body.take(500))
                }
                val response = json.parseToJsonElement(body).jsonObject
                val nameQuery = request.nameQuery.trim()
                if (nameQuery.isNotEmpty() && response["name_query"] != JsonPrimitive(nameQuery)) {
                    throw SerializationException("Server did not confirm the requested name filter")
                }
                response.toPlaceSearchResponse()
            } finally {
                connection.disconnect()
            }
        }
}

class PlaceApiException(
    val status: Int,
    val responseBody: String,
) : IllegalStateException("Place search failed ($status): $responseBody")
