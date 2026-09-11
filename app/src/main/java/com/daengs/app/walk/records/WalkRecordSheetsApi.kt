package com.daengs.app.walk.records

import com.daengs.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Reads sealed per-walk support; never derives a replacement from the phone's GPS. */
class WalkRecordSheetsApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) {
    suspend fun query(token: String, walkIds: Map<String, String>): Result<Map<String, WalkRecordSheetResult>> {
        val expected = walkIds.toMap()
        return withContext(Dispatchers.IO) {
            try {
                require(token.isNotBlank() && expected.size in 1..400)
                val address = baseUrl().trimEnd('/')
                check(address.isNotBlank()) { "서버 연결을 확인해 주세요." }
                val connection = URL("$address/app/walks/spatial-diary/sheets/query").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 30_000
                    connection.instanceFollowRedirects = false
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    val body = JSONObject().put("client_session_ids", JSONArray(expected.keys.toList()))
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    check(connection.responseCode in 200..299) { "지도 흔적을 불러오지 못했어요. (${connection.responseCode})" }
                    // Bound the body before constructing JSON; the server also caps raw cells.
                    val bytes = ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(bytes.size() + count <= MAX_RESPONSE_BYTES) { "지도 흔적이 너무 커요. 산책을 조금 줄여 주세요." }
                            bytes.write(buffer, 0, count)
                        }
                    }
                    val context = currentCoroutineContext()
                    val result = parseWalkRecordSheets(JSONObject(bytes.toString(Charsets.UTF_8.name())), expected) {
                        context.ensureActive()
                    }
                    currentCoroutineContext().ensureActive()
                    Result.success(result)
                } finally { connection.disconnect() }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Result.failure(failure)
            }
        }
    }

    private companion object { const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024 }
}
