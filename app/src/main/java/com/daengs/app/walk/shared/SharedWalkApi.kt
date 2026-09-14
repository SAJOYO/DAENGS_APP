package com.daengs.app.walk.shared

import com.daengs.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 서버 기본값과 같다(`services/walk_group.DEFAULT_LIMIT`). */
const val SHARED_WALK_PAGE_LIMIT = 20

/** 함께 보기 요청의 결과. **실패를 빈 목록으로 바꾸지 않는다** — "없다" 와 "못 읽었다" 는 다르다. */
sealed interface SharedWalkResult<out T> {
    data class Ready<T>(val value: T) : SharedWalkResult<T>

    /** 서버에 이 경로가 없다(공동 조회 전 서버). 화면은 쓸 수 없다고만 말한다. */
    data object Unsupported : SharedWalkResult<Nothing>

    /** 볼 수 없는 강아지이거나 그 강아지의 산책이 아니다(구성원이 아님·나감·내보내짐). */
    data class NotFound(val message: String) : SharedWalkResult<Nothing>

    data class Failed(val message: String) : SharedWalkResult<Nothing>
}

/** 테스트가 가짜를 넣는 자리. */
interface SharedWalkReader {
    suspend fun list(
        accessToken: String,
        petId: String,
        cursor: String?,
        limit: Int = SHARED_WALK_PAGE_LIMIT,
    ): SharedWalkResult<SharedWalkPage>

    suspend fun detail(accessToken: String, petId: String, walkId: String): SharedWalkResult<SharedWalkDetail>
}

/**
 * 산책 기록 **공동 조회** API — 계약은 SAJOYO/DAENGS_dev#539 `routers/pet_walks.py`.
 *
 * **읽기만 한다.** 산책을 올리고 고치고 지우는 길은 계속 `WalkApi`(`/app/walks`, 올린 사람 것)다.
 * 기존 `/app/walks` 목록도 그대로 내 산책만 준다 — 이 API 를 못 쓰는 옛 서버에서도 개인 산책은
 * 그대로 돈다.
 *
 * `baseUrl` 을 생성자로 받는 것은 [com.daengs.app.pet.PetMemberApi] 와 같은 이유다 — 테스트가
 * 내장 HTTP 서버를 향하게 하기 위해서다. ⚠️ 토큰을 로그·예외 메시지에 싣지 않는다.
 */
class SharedWalkApi(private val baseUrl: () -> String = { BuildConfig.API_BASE_URL }) : SharedWalkReader {

    override suspend fun list(
        accessToken: String,
        petId: String,
        cursor: String?,
        limit: Int,
    ): SharedWalkResult<SharedWalkPage> {
        val path = buildString {
            append("/app/pets/").append(encode(petId)).append("/walks?limit=").append(limit)
            if (cursor != null) append("&cursor=").append(encode(cursor))
        }
        return get(accessToken, path) { SharedWalkPage.parse(JSONObject(it)) }
    }

    override suspend fun detail(accessToken: String, petId: String, walkId: String): SharedWalkResult<SharedWalkDetail> =
        get(accessToken, "/app/pets/${encode(petId)}/walks/${encode(walkId)}") { SharedWalkDetail.parse(JSONObject(it)) }

    private suspend fun <T> get(accessToken: String, path: String, parse: (String) -> T): SharedWalkResult<T> =
        withContext(Dispatchers.IO) {
            if (baseUrl().isBlank()) {
                return@withContext SharedWalkResult.Failed("서버 주소가 없습니다. local.properties 의 daengs.apiBaseUrl 을 채우세요.")
            }
            val conn = try {
                (URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
            } catch (e: Exception) {
                return@withContext unreachable()
            }
            try {
                val code = conn.responseCode
                when {
                    code in 200..299 -> {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        try {
                            SharedWalkResult.Ready(parse(body))
                        } catch (e: JSONException) {
                            SharedWalkResult.Failed("산책 기록을 읽지 못했어요.")
                        }
                    }
                    // FastAPI 라우팅 404 는 `"Not Found"` 한 줄이고, 우리 404 는 서버가 쓴 한국어
                    // 문장이다(`PetInviteBundleApi.looksLikeMissingRoute` 와 같은 판정).
                    code == 404 -> {
                        val detail = conn.detail()
                        if (detail == null || detail.equals("Not Found", ignoreCase = true)) {
                            SharedWalkResult.Unsupported
                        } else {
                            SharedWalkResult.NotFound(detail)
                        }
                    }
                    else -> SharedWalkResult.Failed(conn.detail() ?: "서버 오류 ($code)")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                unreachable()
            } finally {
                conn.disconnect()
            }
        }

    private fun unreachable() = SharedWalkResult.Failed("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요.")

    private fun HttpURLConnection.detail(): String? = runCatching {
        when (val raw = JSONObject(errorStream?.bufferedReader()?.readText().orEmpty()).opt("detail")) {
            is String -> raw.takeIf(String::isNotBlank)
            is JSONObject -> raw.optString("message").takeIf(String::isNotBlank)
            else -> null
        }
    }.getOrNull()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
