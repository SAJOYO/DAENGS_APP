package com.daengs.app.walk.diary

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 여러 산책의 봉인된 공간 결과를 읽는다. 원본 업로드를 맡는 `WalkApi`와 수명이 다르다. */
class SpatialDiaryApi(
    private val baseUrl: () -> String = { BuildConfig.API_BASE_URL },
) {
    val configured: Boolean get() = baseUrl().isNotBlank()

    suspend fun query(
        accessToken: String,
        query: SpatialDiaryQuery,
    ): Result<SpatialDiaryView> = withContext(Dispatchers.IO) {
        runCatching {
            val address = baseUrl().trimEnd('/')
            check(address.isNotBlank()) {
                "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요."
            }
            require(accessToken.isNotBlank()) { "공간 일기를 보려면 로그인이 필요해요." }
            val connection = (
                URL("$address/app/walks/spatial-diary/views/query").openConnection()
                    as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            connection.use {
                it.outputStream.use { output ->
                    output.write(query.toJson().toString().toByteArray(Charsets.UTF_8))
                }
                if (it.responseCode !in 200..299) it.fail()
                val response = it.inputStream.bufferedReader().use { reader -> reader.readText() }
                SpatialDiaryView.parse(JSONObject(response))
            }
        }.recoverCatching { cause ->
            when (cause) {
                is SpatialDiaryHttpException,
                is IllegalArgumentException,
                -> throw cause
                else -> throw IllegalStateException(
                    "공간 일기를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.",
                    cause,
                )
            }
        }
    }

    private fun HttpURLConnection.fail(): Nothing {
        val status = responseCode
        val root = runCatching {
            JSONObject(errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
        }.getOrNull()
        val detail = root?.opt("detail")
        val detailObject = detail as? JSONObject
        val code = detailObject?.optString("code")?.takeIf(String::isNotBlank)
        val message = when (detail) {
            is JSONObject -> detail.optString("message").takeIf(String::isNotBlank)
            is String -> detail.takeIf(String::isNotBlank)
            else -> null
        } ?: "서버 오류 ($status)"
        throw SpatialDiaryHttpException(status, code, message)
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

class SpatialDiaryHttpException(
    val statusCode: Int,
    val code: String?,
    message: String,
) : IllegalStateException(message)
