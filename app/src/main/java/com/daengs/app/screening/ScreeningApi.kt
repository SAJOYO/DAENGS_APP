package com.daengs.app.screening

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 피부 스크리닝 서버. 계약은 저쪽 `gayeoniee/deeplearning_test` 의
 * `serve.py`(HTTP)와 `src/agent.py`(응답)다.
 *
 * [AuthApi][com.daengs.app.auth.AuthApi] 와 같은 이유로 **HTTP 라이브러리를 안 쓴다.**
 * 부를 엔드포인트가 하나다. 다만 이쪽은 multipart 라 본문을 직접 써야 해서 배관이
 * 조금 더 길다 — 그래도 Retrofit + OkHttp 두 덩어리보다는 짧다.
 *
 * **서버는 아직 없다.** 저쪽도 데모 서버라 인증·HTTPS·크기 제한이 없고 공개 주소에
 * 띄우면 안 된다고 적혀 있다. 주소가 비어 있으면 [configured] 가 false 이고,
 * 화면이 그걸 보고 버튼을 막는다 — 카카오 로그인과 같은 방식이다.
 */
object ScreeningApi {

    /** 주소가 없으면 아무것도 못 부른다. */
    val configured: Boolean
        get() = BuildConfig.SCREEN_BASE_URL.isNotBlank()

    /**
     * 사진 한 장을 보내고 판정을 받는다.
     *
     * @param jpeg [Photo.prepare] 가 줄여 놓은 바이트. 서버 한도가 12MB 다.
     * @param box 앱의 **가이드 프레임**. 정규화 `[x, y, w, h]`. 지금은 안 보낸다 —
     *   프레임 UI 가 아직 없다. 저쪽 주석대로 없으면 화면 중앙으로 물러서고,
     *   1단계는 큰 차이가 없지만 **2단계 분포는 학습 크롭과 어긋난다.**
     *   프레임을 붙이면 여기로 넘기면 된다.
     */
    suspend fun screen(jpeg: ByteArray, box: FloatArray? = null): Result<ScreeningReport> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(configured) {
                    "진단 서버 주소가 없습니다. local.properties 의 daengs.screenUrl 을 채우세요."
                }
                val conn = open("/v1/screen")
                conn.use {
                    it.writeMultipart(jpeg, box)
                    ScreeningReport.parse(it.readJson())
                }
            }.recoverCatching { cause ->
                // **서버가 준 문장은 그대로 통과시킨다.** 저쪽이 사용자에게 보여 줄
                // 말로 써 놨다("사진이 너무 큽니다" 같은). 그건 [error] 로 던지므로
                // IllegalStateException 이다.
                //
                // 나머지는 연결이 안 된 것이고, 그때 예외 메시지는
                // "Failed to connect to /127.0.0.1:8000" 같은 영어 한 줄이라
                // 말풍선에 그대로 띄우면 안 된다.
                if (cause is IllegalStateException) throw cause
                throw IllegalStateException("진단 서버에 닿지 못했어요. 주소와 네트워크를 확인해 주세요.", cause)
            }
        }

    // -- 아래는 배관 -------------------------------------------------------

    private fun open(path: String): HttpURLConnection {
        val conn = URL(BuildConfig.SCREEN_BASE_URL.trimEnd('/') + path)
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        // 모델이 CPU 로 돌면 사진 한 장에 1~3초 걸린다고 저쪽이 적어 뒀다.
        // 첫 요청은 가중치를 올리느라 더 걸릴 수 있어 넉넉히 준다.
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    /**
     * `multipart/form-data` 본문을 직접 쓴다.
     *
     * 경계 문자열은 **본문에 없어야 한다.** JPEG 안에 우연히 들어갈 확률을 없애려고
     * base64 에 안 쓰이는 문자(`-`)만으로 길게 잡았다.
     */
    private fun HttpURLConnection.writeMultipart(jpeg: ByteArray, box: FloatArray?) {
        doOutput = true
        // 스트리밍 모드. 안 켜면 안드로이드가 본문 전체를 메모리에 다시 쌓는다.
        setFixedLengthStreamingMode(multipartLength(jpeg, box))
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
        DataOutputStream(outputStream.buffered()).use { out ->
            if (box != null) {
                out.writeLatin1(fieldHeader("box"))
                out.writeLatin1(box.joinToString(",", "[", "]"))
                out.writeLatin1(CRLF)
            }
            out.writeLatin1(photoHeader())
            out.write(jpeg)
            out.writeLatin1(CRLF)
            out.writeLatin1("--$BOUNDARY--$CRLF")
        }
    }

    /**
     * 본문 길이를 미리 잰다. [setFixedLengthStreamingMode] 에 넘길 값이다.
     *
     * 머리말은 전부 ASCII 라 문자 수 = 바이트 수다.
     */
    private fun multipartLength(jpeg: ByteArray, box: FloatArray?): Long {
        var n = 0L
        if (box != null) {
            n += fieldHeader("box").length
            n += box.joinToString(",", "[", "]").length
            n += CRLF.length
        }
        n += photoHeader().length
        n += jpeg.size
        n += CRLF.length
        n += "--$BOUNDARY--$CRLF".length
        return n
    }

    private fun fieldHeader(name: String) =
        "--$BOUNDARY$CRLF" +
            "Content-Disposition: form-data; name=\"$name\"$CRLF$CRLF"

    private fun photoHeader() =
        "--$BOUNDARY$CRLF" +
            "Content-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"$CRLF" +
            "Content-Type: image/jpeg$CRLF$CRLF"

    private fun DataOutputStream.writeLatin1(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))

    /**
     * 200 대가 아니면 서버가 준 문장을 그대로 예외에 담는다. FastAPI 의 `detail` 이고,
     * 저쪽이 사용자에게 보여 줄 말로 써 놨다("사진이 너무 큽니다" 같은).
     */
    private fun HttpURLConnection.readJson(): JSONObject {
        if (responseCode !in 200..299) {
            val detail = runCatching {
                JSONObject(errorStream?.bufferedReader()?.readText().orEmpty()).optString("detail")
            }.getOrNull()
            error(if (detail.isNullOrBlank()) "진단 서버 오류 ($responseCode)" else detail)
        }
        return JSONObject(inputStream.bufferedReader().use { it.readText() })
    }

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    private const val CRLF = "\r\n"
    private const val BOUNDARY = "----daengs--------------------------"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000
}
