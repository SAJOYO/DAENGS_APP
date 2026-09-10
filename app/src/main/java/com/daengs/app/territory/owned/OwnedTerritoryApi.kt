package com.daengs.app.territory.owned

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

fun interface OwnedTerritoryClient {
    suspend fun fetch(token: String, petId: String?, cursor: String?): OwnedTerritoryPage
}

class OwnedTerritoryHttpException(val status: Int, val code: String?) :
    IllegalStateException("Owned territory request failed ($status)")

/** Read-only, bounded requests. No viewport, GPS, claim, camera or walking session dependencies. */
class OwnedTerritoryApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) : OwnedTerritoryClient {
    override suspend fun fetch(token: String, petId: String?, cursor: String?): OwnedTerritoryPage =
        withContext(Dispatchers.IO) {
            ensureActive()
            require(token.isNotBlank())
            petId?.let(::requireOwnedPetId)
            cursor?.let { require(it.length in 1..1024) }
            val base = baseUrl().trimEnd('/').also { check(it.isNotBlank()) }
            val query = listOfNotNull("limit=50", petId?.let { "pet_id=$it" },
                cursor?.let { "cursor=" + URLEncoder.encode(it, "UTF-8") }).joinToString("&")
            val connection = URL("$base/app/territory/my-sites?$query").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val buffer = CharArray(4096)
                    val text = StringBuilder()
                    while (true) {
                        ensureActive()
                        val count = reader.read(buffer)
                        if (count < 0) break
                        require(text.length + count <= 256_000) { "Owned territory response too large" }
                        text.append(buffer, 0, count)
                    }
                    text.toString()
                }.orEmpty()
                ensureActive()
                if (status !in 200..299) throw OwnedTerritoryHttpException(status,
                    runCatching { JSONObject(body).optJSONObject("detail")?.optString("code") }.getOrNull())
                parseOwnedTerritories(body, petId)
            } finally { connection.disconnect() }
        }
}
