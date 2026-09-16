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
 * 지금은 목록 조회와 구성원 삭제뿐이다 — 대표 승계는 다음 단계에서 여기에 이어 붙인다.
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

    /**
     * 구성원 한 명을 뺀다 (`DELETE /app/pets/{petId}/members/{targetAppUserId}`).
     *
     * **내보내기와 나가기가 같은 요청이다.** 저쪽이 「대표 또는 본인」으로 권한을
     * 잡아 둬서(`docs/co-care-contract.md` §엔드포인트 표), 앱이 대상 id 를 남으로
     * 주면 내보내기이고 나로 주면 나가기다 — 경로를 둘로 나누면 서버에 없는 계약을
     * 앱이 지어내는 꼴이 된다.
     *
     * [petId] 는 **카드가 들고 있는 표시 행 id**(`display_pet_id`) 다. 목록 조회와 같은
     * 값이라야 방금 본 명단에서 뺀 사람이 그 명단에서 빠진다.
     *
     * 성공은 **204 다** — 본문이 없어서 읽지 않는다. 403·404·409 는 저쪽이 사용자에게
     * 보여 줄 문장을 `detail` 에 담아 주므로 [listMembers] 와 같이 그대로 통과시킨다.
     */
    suspend fun remove(accessToken: String, petId: String, targetAppUserId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(configured) { "서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요." }
                val conn = (URL("${baseUrl().trimEnd('/')}/app/pets/$petId/members/$targetAppUserId")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                conn.use {
                    if (it.responseCode !in 200..299) it.fail()
                }
            }.onFailure { cause ->
                if (cause is kotlinx.coroutines.CancellationException) throw cause
            }.recoverCatching { cause ->
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
