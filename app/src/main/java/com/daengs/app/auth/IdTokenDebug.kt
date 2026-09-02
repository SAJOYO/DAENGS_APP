package com.daengs.app.auth

import android.util.Base64
import android.util.Log
import com.daengs.app.BuildConfig
import org.json.JSONObject

/**
 * 서버가 `id_token` 을 거부했을 때 **무엇이 틀렸는지** 좁히는 도구.
 *
 * 저쪽은 이유를 응답에 안 담는다 — 담으면 "검증을 통과하는 조건"을 알려 주는 셈이라
 * 일부러 그렇게 했다. 그래서 앱에서 토큰을 뜯어 본다.
 *
 * **값은 안 찍는다.** `aud` 는 팀의 REST API 키이고 `sub` 는 회원번호다. 맞는지
 * 여부와 모양만 남긴다. 디버그 빌드에서만 돈다.
 */
internal fun logIdTokenShape(idToken: String, sentNonce: String?) {
    if (!BuildConfig.DEBUG) return
    runCatching {
        val parts = idToken.split(".")
        if (parts.size != 3) {
            Log.w(TAG, "id_token 이 JWT 모양이 아니다 (마디 ${parts.size}개)")
            return
        }
        val body = JSONObject(String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING)))
        val now = System.currentTimeMillis() / 1000
        val exp = body.optLong("exp", 0)
        val aud = body.optString("aud", "")
        val nonce = body.optString("nonce", "")

        Log.i(TAG, "id_token 점검")
        Log.i(TAG, "  iss   = ${body.optString("iss")}  (기대: https://kauth.kakao.com)")
        // 전체를 안 찍는다 — 팀의 REST API 키다. 콘솔 값과 대조할 만큼만 가린다.
        val masked = if (aud.length >= 8) "${aud.take(4)}…${aud.takeLast(4)}" else "?"
        Log.i(TAG, "  aud   = ${aud.length}자, 16진수=${aud.matches(Regex("[0-9a-f]+"))}, 지문=$masked")
        Log.i(TAG, "  exp   = ${exp - now}초 남음")
        Log.i(TAG, "  nonce = 토큰에 ${if (nonce.isEmpty()) "없음" else "있음"}, 우리가 보낸 것과 ${if (nonce == sentNonce) "같음" else "다름"}")
        Log.i(TAG, "  sub   = ${if (body.has("sub")) "있음" else "없음"}")
    }.onFailure { Log.w(TAG, "id_token 을 못 읽었다: $it") }
}

private const val TAG = "DaengsAuth"
