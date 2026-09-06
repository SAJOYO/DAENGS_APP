package com.daengs.app.pet

import com.daengs.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 강아지 프로필 API. 계약은 저쪽 `routers/pet.py` · `schemas/pet.py` 다.
 *
 * **`AuthApi` 와 같은 서버, 같은 토큰이다.** 그래서 그쪽과 같은 방식으로 짰다 —
 * `HttpURLConnection` + `org.json`, 라이브러리 없이.
 *
 * 장소 쪽(`PlaceApi`)은 `kotlinx.serialization` 을 쓰는데 여기서 안 따라간 이유는
 * **`weight_kg` 가 숫자가 아니라 문자열로 오기 때문**이다 (아래 참고). 직렬화
 * 라이브러리로 받으려면 그 한 필드 때문에 커스텀 시리얼라이저를 붙여야 하고,
 * 그러면 손으로 읽는 것보다 코드가 는다.
 */
object PetApi {

    val configured: Boolean
        get() = BuildConfig.API_BASE_URL.isNotBlank()

    /** 내 강아지와 **서버가 정한 마릿수 상한**. 상한을 앱에 박아 두면 서버와 갈라진다. */
    suspend fun list(accessToken: String): Result<PetList> =
        call(accessToken, "", "GET") { PetList.parse(JSONObject(it)) }

    /** 등록. **첫 아이는 서버가 알아서 대표로 만든다** — 응답의 `is_primary` 로 온다. */
    suspend fun create(accessToken: String, draft: PetDraft): Result<Pet> =
        call(accessToken, "", "POST", draft.toJson()) { Pet.parse(JSONObject(it)) }

    /** 수정. **전체를 다시 보낸다** — 서버가 PUT 만 받는다 (부분 수정은 null 의 뜻이 갈린다). */
    suspend fun update(accessToken: String, petId: String, draft: PetDraft): Result<Pet> =
        call(accessToken, "/$petId", "PUT", draft.toJson()) { Pet.parse(JSONObject(it)) }

    /** 삭제. **대표를 지우면 서버가 남은 아이 중 먼저 등록한 아이로 승계한다.** */
    suspend fun delete(accessToken: String, petId: String): Result<Unit> =
        call(accessToken, "/$petId", "DELETE") { }

    /** 대표 바꾸기. 내 강아지가 아니면 404 다. */
    suspend fun setPrimary(accessToken: String, petId: String): Result<Unit> =
        call(accessToken, "/primary", "PUT", JSONObject().put("pet_id", petId)) { }

    // -- 아래는 배관 -------------------------------------------------------

    private suspend fun <T> call(
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
                // **응답 코드를 먼저 읽어야 요청이 실제로 나간다.** 204 는 본문이 없어서
                // 읽으려 들면 빈 문자열이므로, 파서에 넘기기 전에 코드로 가른다.
                if (it.responseCode !in 200..299) it.fail()
                parse(if (it.responseCode == 204) "" else it.inputStream.bufferedReader().use { r -> r.readText() })
            }
        }.onFailure { cause ->
            if (cause is kotlinx.coroutines.CancellationException) throw cause
        }.recoverCatching { cause ->
            // 서버가 준 문장은 그대로 통과시킨다. 나머지는 연결이 안 된 것이고,
            // 그때 메시지는 영어 한 줄이라 화면에 그대로 띄우면 안 된다.
            if (cause is IllegalStateException) throw cause
            throw IllegalStateException("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.", cause)
        }
    }

    /**
     * 저쪽이 사용자에게 보여 줄 문장으로 `detail` 을 써 놨다 ("강아지는 5마리까지…").
     *
     * **문장이 아닐 때가 있다.** 저쪽은 FastAPI 라 422 검증 오류의 `detail` 은
     * **배열**이다. 예전에는 `optString("detail")` 로 꺼냈는데, 그러면 그 배열이
     * 통째로 문자열이 되어 화면에 이렇게 찍혔다 —
     * `[{"type":"less_than_equal","loc":["body","weight_kg"], …}]`.
     * 비공개 테스트에서 몸무게에 큰 수를 넣었을 때 나왔다.
     *
     * 그래서 **모양을 보고 가른다.** 모르는 모양이면 상태 코드만 말한다. 날 것을
     * 보여 주느니 덜 알려 주는 편이 낫다 — `gait/GaitApi.kt` 의 `detailOf` 와
     * `walk/diary/SpatialDiaryApi.kt` 가 같은 방식이다.
     */
    private fun HttpURLConnection.fail(): Nothing {
        val body = runCatching {
            JSONObject(errorStream?.bufferedReader()?.readText().orEmpty())
        }.getOrNull()
        val detail = when (val raw = body?.opt("detail")) {
            is String -> raw.takeIf(String::isNotBlank)
            // 저쪽이 `{"message": …}` 로 감싸 보내는 계약도 있다 (보행·공간 일지).
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

    private const val TIMEOUT_MS = 10_000
}

/**
 * 목록 응답. 상한이 같이 온다 — 앱이 `+` 버튼을 언제 감출지 정하는 데 쓴다.
 *
 * **상한이 안 오면 null 이다.** 그 자리에 숫자를 지어 넣으면 서버와 갈라지고,
 * 상한 하나 때문에 파싱이 통째로 실패하면 **강아지 목록이 아예 안 뜬다.**
 * 모르면 `+` 를 감추지 않고, 넘치는 건 서버가 막는다 — 그쪽이 문장까지 준다.
 */
data class PetList(val pets: List<Pet>, val maxPets: Int?) {
    companion object {
        fun parse(json: JSONObject): PetList {
            val arr = json.getJSONArray("pets")
            return PetList(
                pets = (0 until arr.length()).map { Pet.parse(arr.getJSONObject(it)) },
                maxPets = if (json.isNull("max_pets")) null else json.optInt("max_pets"),
            )
        }
    }
}
