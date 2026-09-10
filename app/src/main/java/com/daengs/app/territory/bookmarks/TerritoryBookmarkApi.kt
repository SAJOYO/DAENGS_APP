package com.daengs.app.territory.bookmarks

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface TerritoryBookmarkClient {
    suspend fun list(token: String): BookmarkList
    suspend fun set(token: String, siteId: String, saved: Boolean): BookmarkMutation
}
class BookmarkHttpException(val status: Int, val code: String?, val limit: Int? = null) :
    IllegalStateException("Bookmark request failed ($status)")

class TerritoryBookmarkApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) : TerritoryBookmarkClient {
    override suspend fun list(token: String) = parseBookmarkList(request(token, "GET", ""))
    override suspend fun set(token: String, siteId: String, saved: Boolean): BookmarkMutation {
        requireBookmarkSite(siteId)
        return parseBookmarkMutation(request(token, if (saved) "PUT" else "DELETE", "/$siteId"), siteId, saved)
    }

    private suspend fun request(token: String, method: String, suffix: String): String = withContext(Dispatchers.IO) {
        ensureActive()
        require(token.isNotBlank())
        val base = baseUrl().trimEnd('/').also { check(it.isNotBlank()) }
        val connection = URL("$base/app/territory/bookmarks$suffix").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
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
                    require(text.length + count <= 256_000) { "Bookmark response too large" }
                    text.append(buffer, 0, count)
                }
                text.toString()
            }.orEmpty()
            ensureActive()
            if (status != 200) {
                val detail = runCatching { JSONObject(body).optJSONObject("detail") }.getOrNull()
                throw BookmarkHttpException(status, detail?.optString("code"),
                    detail?.optInt("limit", -1)?.takeIf { it > 0 })
            }
            body
        } finally { connection.disconnect() }
    }
}
