package com.daengs.app.pet

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 공동 돌봄 구성원 API. 계약은 저쪽 `routers/pet_member.py` · `schemas/pet_member.py` 다.
 *
 * [PetApi] 와 같은 서버·같은 토큰이라 같은 방식(`HttpURLConnection` + `org.json`)으로 짰다.
 * 지금은 목록 조회 하나뿐이다 — 초대·내보내기·승계는 다음 단계에서 여기에 이어 붙인다.
 *
 * `baseUrl` 을 생성자로 받는 것은 [WalkRecordSheetsApi][com.daengs.app.walk.records.WalkRecordSheetsApi]
 * 와 같은 이유다 — 테스트가 내장 HTTP 서버를 향하게 하기 위해서다.
 */
class PetMemberApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) {

    val configured: Boolean
        get() = baseUrl().isNotBlank()

    /** 구성원 목록. **구성원만 볼 수 있다** — 대표도 돌보미도 아니면 404 다. */
    suspend fun listMembers(accessToken: String, petId: String): Result<PetMemberList> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
                val conn = (URL("${baseUrl().trimEnd('/')}/app/pets/$petId/members")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                conn.use {
                    if (it.responseCode !in 200..299) it.fail()
                    PetMemberList.parse(JSONObject(it.inputStream.bufferedReader().use { r -> r.readText() }))
                }
            }.onFailure { cause ->
                if (cause is kotlinx.coroutines.CancellationException) throw cause
            }.recoverCatching { cause ->
                // 서버가 준 문장은 그대로 통과시킨다. 나머지는 연결이 안 된 것이고,
                // 그때 메시지는 영어 한 줄이라 화면에 그대로 띄우면 안 된다 (PetApi 와 같은 규칙).
                if (cause is IllegalStateException) throw cause
                throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
            }
        }

    // -- 아래는 배관. `PetApi.fail()` 과 같은 규칙이다 -----------------------

    /**
     * 저쪽이 사용자에게 보여 줄 문장으로 `detail` 을 써 놨다 ("강아지를 찾을 수 없습니다").
     * FastAPI 의 422 검증 오류는 `detail` 이 배열이라, 모르는 모양이면 상태 코드만 말한다.
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

    private inline fun <T> HttpURLConnection.use(body: (HttpURLConnection) -> T): T =
        try {
            body(this)
        } finally {
            disconnect()
        }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
