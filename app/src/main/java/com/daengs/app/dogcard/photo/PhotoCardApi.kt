package com.daengs.app.dogcard.photo

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 포토 카드 서버 경계. **홀더는 이 인터페이스만 본다** — 테스트가 가짜로 갈아 끼운다.
 */
interface PhotoCardRemote {
    /**
     * 만들기를 **시작한다.** 202 와 `generating` 이 온다. 30~60초 뒤 [get] 이 `ready` 를 준다.
     * `titleName` 이 있으면 서버가 제목을 `<카드명> <titleName>` 으로 짓는다 (docs §9.1).
     */
    suspend fun create(
        token: String, month: Int, dogName: String, dogId: String?, jpeg: ByteArray, titleName: String? = null,
    ): Result<PhotoCard>
    suspend fun list(token: String): Result<PhotoCardList>
    suspend fun get(token: String, id: String): Result<PhotoCardDetail>
    /** 없는 카드(404)도 성공으로 본다 — 이미 지워졌다는 뜻이라 기기에서도 지우면 된다. */
    suspend fun delete(token: String, id: String): Result<Unit>
    suspend fun download(url: String): Result<ByteArray>
}

/** `/app/ai-cards`. 배관은 `CardApi` 와 같다 — 저쪽 문장을 그대로 통과시킨다. */
object HttpPhotoCardRemote : PhotoCardRemote {

    val configured: Boolean get() = BuildConfig.API_BASE_URL.isNotBlank()

    override suspend fun create(
        token: String, month: Int, dogName: String, dogId: String?, jpeg: ByteArray, titleName: String?,
    ): Result<PhotoCard> = call(token, "?${photoCardQuery(month, dogName, dogId, titleName)}", "POST", jpeg) {
        parsePhotoCard(org.json.JSONObject(it))
    }

    override suspend fun list(token: String): Result<PhotoCardList> =
        call(token, "", "GET") { parsePhotoCardList(it) }

    override suspend fun get(token: String, id: String): Result<PhotoCardDetail> =
        call(token, "/$id", "GET") { parsePhotoCardDetail(it) }

    override suspend fun delete(token: String, id: String): Result<Unit> =
        call(token, "/$id", "DELETE", notFoundOk = true) { }

    override suspend fun download(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = BODY_TIMEOUT_MS
            }
            try {
                if (conn.responseCode !in 200..299) error("그림을 받지 못했어요 (${conn.responseCode})")
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }.recoverCatching { rethrow(it) }
    }

    private suspend fun <T> call(
        token: String,
        path: String,
        method: String,
        jpeg: ByteArray? = null,
        notFoundOk: Boolean = false,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val conn = (URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/app/ai-cards$path")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = if (jpeg != null) BODY_TIMEOUT_MS else TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                if (jpeg != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "image/jpeg")
                    conn.setFixedLengthStreamingMode(jpeg.size)
                    conn.outputStream.use { it.write(jpeg) }
                }
                val code = conn.responseCode
                if (notFoundOk && code == 404) return@runCatching parse("")
                if (code !in 200..299) {
                    error(photoCardErrorMessage(code, conn.errorStream?.bufferedReader()?.use { it.readText() }))
                }
                parse(if (code == 204) "" else conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.recoverCatching { rethrow(it) }
    }

    private fun rethrow(cause: Throwable): Nothing {
        if (cause is IllegalStateException) throw cause
        throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
    }

    private const val TIMEOUT_MS = 10_000
    private const val BODY_TIMEOUT_MS = 30_000
}
