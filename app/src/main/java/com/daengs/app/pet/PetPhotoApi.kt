package com.daengs.app.pet

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 프로필 사진 API. 계약은 저쪽 `routers/pet.py` 의 `/app/pets/{id}/photo` 다.
 *
 * **[PetApi] 와 같은 서버, 같은 토큰이다.** 그래서 같은 방식으로 짰다 —
 * `HttpURLConnection` + `org.json`, 라이브러리 없이.
 *
 * ## 흐름이 왜 세 걸음인가
 *
 * 올리는 것은 `티켓 → PUT → confirm` 이다. 한 번에 안 보내는 이유는 **사진이 서버
 * 프로세스를 안 지나게 하려던 설계**라서다 — 저쪽이 GCS 로 갈 때를 대비해 계약을
 * 그렇게 뒀고(D-043), 지금은 서버 볼륨이라 실제로는 서버를 지나지만(D-052) **계약은
 * 그대로 남겼다.** 저쪽이 다시 GCS 로 되돌아가도 이 코드는 안 바뀐다.
 *
 * ⚠️ **confirm 을 부르기 전까지는 서버가 그 사진을 모른다.** PUT 만 하고 끊기면
 *    올리다 만 것으로 남고, 다음 티켓 발급 때 저쪽이 알아서 치운다.
 *
 * ⚠️ **티켓의 `upload_url` 을 그대로 쓴다.** 우리가 주소를 조립하지 않는다 —
 *    저쪽이 GCS 로 바뀌면 그 값이 Signed URL 이 되고, 그때 이 파일은 안 고친다.
 *    `upload_headers` 도 받은 그대로 붙인다.
 */
object PetPhotoApi {

    val configured: Boolean
        get() = BuildConfig.API_BASE_URL.isNotBlank()

    /** 사진 한 장을 올릴 자리. 다시 부르면 앞의 티켓은 버려진다. */
    suspend fun ticket(accessToken: String, petId: String): Result<PhotoTicket> =
        json(accessToken, "/$petId/photo", "POST", JSONObject().put("content_type", JPEG)) {
            PhotoTicket.parse(JSONObject(it))
        }

    /**
     * 티켓이 준 자리에 바이트를 올린다.
     *
     * **여기는 우리 API 가 아니다.** 주소도 헤더도 티켓이 준 것을 그대로 쓰고,
     * 토큰을 붙이지 않는다 — 저쪽 bridge 는 인증 헤더를 안 받고 **키가 자격**이다
     * (그래야 GCS 의 Signed URL 과 같은 모양이 된다).
     */
    suspend fun upload(ticket: PhotoTicket, bytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(ticket.uploadUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = UPLOAD_TIMEOUT_MS
                    doOutput = true
                    // 티켓이 준 헤더를 **그대로** 붙인다. Content-Type 이 안 맞으면 415 다.
                    ticket.uploadHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                    // 큰 파일을 통째로 메모리에 담지 않게 한다. 사진 한 장이라 크지 않지만,
                    // 스트리밍으로 두면 저쪽이 상한을 넘겼을 때 일찍 끊는다.
                    setFixedLengthStreamingMode(bytes.size)
                }
                conn.use {
                    it.outputStream.use { out -> out.write(bytes) }
                    if (it.responseCode !in 200..299) it.fail()
                }
            }.recoverCatching { rethrow(it) }
        }

    /** 올라온 사진을 확정한다. **여기서 처음으로 서버가 인정한다.** */
    suspend fun confirm(accessToken: String, petId: String): Result<Pet> =
        json(accessToken, "/$petId/photo/confirm", "POST") { Pet.parse(JSONObject(it)) }

    /**
     * 사진을 받아 온다. 없으면 409 라 실패로 온다.
     *
     * 두 걸음이다 — 주소를 받고, 그 주소에서 바이트를 받는다. 주소가 만료되는 계약이라
     * (GCS Signed URL) 받아 두고 오래 쓰지 않는다.
     */
    suspend fun download(accessToken: String, petId: String): Result<ByteArray> =
        json(accessToken, "/$petId/photo", "GET") { it }
            .mapCatching { body -> JSONObject(body).getString("download_url") }
            .mapCatching { url -> fetch(url) }

    /** 서버에서 지운다. 사진이 없어도 성공이다. */
    suspend fun delete(accessToken: String, petId: String): Result<Unit> =
        json(accessToken, "/$petId/photo", "DELETE") { }

    // -- 아래는 배관 -------------------------------------------------------

    private suspend fun fetch(url: String): ByteArray = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = UPLOAD_TIMEOUT_MS
        }
        conn.use {
            if (it.responseCode !in 200..299) it.fail()
            it.inputStream.use { input -> input.readBytes() }
        }
    }

    private suspend fun <T> json(
        accessToken: String,
        path: String,
        method: String,
        body: JSONObject? = null,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
            val conn = (URL("${BuildConfig.API_BASE_URL.trimEnd('/')}/app/pets$path")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            conn.use {
                if (body != null) {
                    it.doOutput = true
                    it.setRequestProperty("Content-Type", "application/json")
                    it.outputStream.use { out -> out.write(body.toString().toByteArray()) }
                }
                // 응답 코드를 먼저 읽어야 요청이 실제로 나간다. 204 는 본문이 없다.
                if (it.responseCode !in 200..299) it.fail()
                parse(if (it.responseCode == 204) "" else it.inputStream.bufferedReader().use { r -> r.readText() })
            }
        }.recoverCatching { rethrow(it) }
    }

    /**
     * 저쪽이 사용자에게 보여 줄 문장을 준다. **모양이 셋이라 갈라 읽는다** —
     * `PetApi.fail` 과 같은 이유이고, 여기는 **`{"code": …, "message": …}`** 가 더 온다
     * (사진 쪽 409 가 그 모양이다: `no_pending_photo` · `photo_not_uploaded` 등).
     */
    private fun HttpURLConnection.fail(): Nothing {
        val body = runCatching {
            JSONObject(errorStream?.bufferedReader()?.readText().orEmpty())
        }.getOrNull()
        val detail = when (val raw = body?.opt("detail")) {
            is String -> raw.takeIf(String::isNotBlank)
            is JSONObject -> raw.optString("message").takeIf(String::isNotBlank)
            else -> null
        }
        error(detail ?: "서버 오류 ($responseCode)")
    }

    private fun rethrow(cause: Throwable): Nothing {
        // 서버가 준 문장은 그대로 통과시킨다. 나머지는 연결이 안 된 것이고,
        // 그때 메시지는 영어 한 줄이라 화면에 그대로 띄우면 안 된다.
        if (cause is IllegalStateException) throw cause
        throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
    }

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    /** 앱이 굽는 것이 JPEG 이다 (`PetPhotos.write`). 저쪽은 webp 도 받는다. */
    const val JPEG = "image/jpeg"

    private const val TIMEOUT_MS = 10_000

    /** 사진 한 장이라 크지 않지만, 느린 망에서 10초는 짧다. */
    private const val UPLOAD_TIMEOUT_MS = 30_000
}

/**
 * 사진을 올릴 자리.
 *
 * **주소와 헤더를 우리가 만들지 않는다.** 서버가 준 것을 그대로 쓴다 — 저쪽이 GCS 로
 * 되돌아가면 이 값이 Signed URL 이 되고, 그때 앱은 안 고친다.
 */
data class PhotoTicket(
    val storageKey: String,
    val uploadUrl: String,
    val uploadHeaders: Map<String, String>,
    val expiresInSeconds: Int,
) {
    companion object {
        fun parse(json: JSONObject): PhotoTicket {
            val headers = json.optJSONObject("upload_headers")
            return PhotoTicket(
                storageKey = json.getString("storage_key"),
                uploadUrl = json.getString("upload_url"),
                uploadHeaders = headers?.keys()?.asSequence()
                    ?.associateWith { headers.getString(it) }
                    .orEmpty(),
                expiresInSeconds = json.optInt("expires_in_seconds"),
            )
        }
    }
}
