package com.daengs.app.auth

import android.content.Context
import androidx.compose.runtime.Immutable
import com.kakao.sdk.auth.model.OAuthToken
import android.util.Log
import com.kakao.sdk.common.model.AuthError
import com.kakao.sdk.common.model.AuthErrorCause
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.security.SecureRandom

/**
 * 로그인 세션 하나. 서버 응답을 그대로 담는다.
 *
 * access 는 5분, refresh 는 7일이다. **refresh 는 회전해도 연장되지 않는다** — 이
 * 시각이 지나면 카카오 로그인부터 다시 해야 한다.
 */
@Immutable
data class Session(
    val appUserId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtMs: Long,
    val refreshExpiresAtMs: Long,
) {
    /** 만료 직전을 미리 죽은 것으로 친다. 부르는 도중에 넘어가면 401 을 맞는다. */
    fun accessAlive(nowMs: Long): Boolean = nowMs < accessExpiresAtMs - SKEW_MS

    fun refreshAlive(nowMs: Long): Boolean = nowMs < refreshExpiresAtMs - SKEW_MS

    private companion object {
        const val SKEW_MS = 30_000L
    }
}

/**
 * 앱을 켤 때 어디로 갈지 정한다.
 *
 * - 토큰이 없으면 랜딩
 * - access 가 살아 있으면 그대로 홈
 * - access 만 죽었으면 **재발급 후** 홈. 새 refresh 로 반드시 덮어쓴다
 * - 둘 다 죽었으면 랜딩 (7일이 지난 것)
 *
 * 재발급이 네트워크 문제로 실패하면 **토큰을 안 지운다.** 서버가 잠깐 안 될 때마다
 * 로그아웃시키면 안 된다 — 다음에 켤 때 다시 시도한다.
 */
suspend fun restoreSession(store: TokenStore, nowMs: Long = System.currentTimeMillis()): Session? {
    val saved = store.load() ?: return null
    if (saved.accessAlive(nowMs)) return saved
    if (!saved.refreshAlive(nowMs)) {
        store.clear()
        return null
    }
    if (!AuthApi.configured) return null
    return AuthApi.refresh(saved.refreshToken)
        .onSuccess(store::save)
        .getOrNull()
}

/**
 * 카카오 로그인 → `id_token`.
 *
 * 톡이 깔려 있으면 앱 대 앱으로 간다. 사용자가 거기서 취소하면 **웹 로그인으로
 * 넘어가지 않는다** — 취소한 사람에게 다른 창을 또 띄우는 꼴이라 저쪽 가이드도
 * 그렇게 하지 말라고 한다. 톡이 없거나 로그인 자체가 안 되는 경우에만 웹으로 간다.
 *
 * **`openid` scope 를 우리가 넘기지 않는다.** SDK 2.24 의 로그인 함수에는 `scopes`
 * 인자가 아예 없다 — 콘솔에서 OpenID Connect 를 켜 두면 카카오가 알아서 붙여서
 * `idToken` 을 준다. 그래서 `id_token` 이 안 오면 **콘솔에서 OIDC 가 꺼진 것**이고,
 * 앱에서 고칠 수 있는 게 없다. 아래에서 그렇게 알려 준다.
 *
 * @return `id_token` 과 함께 보낸 nonce. 서버가 같은 값으로 대조한다.
 */
suspend fun loginWithKakao(context: Context): Result<KakaoLogin> {
    val nonce = newNonce()
    val client = UserApiClient.instance

    val token = if (client.isKakaoTalkLoginAvailable(context)) {
        val first = awaitLogin { cb -> client.loginWithKakaoTalk(context, nonce = nonce, callback = cb) }
        first.exceptionOrNull()?.let { e ->
            if (e.isUserCancel()) return Result.failure(CancelledByUser)
            // 취소가 아닌 실패만 웹으로 넘긴다. 무엇 때문이었는지는 남겨 둔다 —
            // 여기서 조용히 웹으로 새면 원인을 다시는 못 본다.
            Log.i(TAG, "카카오톡 로그인 실패, 웹으로 넘어간다: ${e.javaClass.simpleName} $e")
        }
        if (first.isSuccess) first else {
            awaitLogin { cb -> client.loginWithKakaoAccount(context, nonce = nonce, callback = cb) }
        }
    } else {
        awaitLogin { cb -> client.loginWithKakaoAccount(context, nonce = nonce, callback = cb) }
    }

    token.exceptionOrNull()?.let { if (it.isUserCancel()) return Result.failure(CancelledByUser) }

    return token.mapCatching {
        val id = it.idToken
            ?: error("카카오가 id_token 을 주지 않았습니다. 콘솔에서 OpenID Connect 가 켜져 있는지 확인해 주세요.")
        KakaoLogin(idToken = id, nonce = nonce)
    }
}

@Immutable
data class KakaoLogin(val idToken: String, val nonce: String)

/** 사용자가 직접 취소한 것. 오류 문구를 띄우지 않는다. */
object CancelledByUser : Exception("사용자가 로그인을 취소했습니다.")

/**
 * 사용자가 그만둔 것인가. **모양이 둘이다.**
 *
 * - [ClientErrorCause.Cancelled] — 카카오톡 화면을 뒤로가기로 벗어난 경우
 * - [AuthErrorCause.AccessDenied] — **동의 화면에서 `취소` 를 누른 경우.** 이쪽은
 *   카카오 서버가 "거부"로 응답하는 것이라 클라이언트 오류가 아니다
 *
 * 처음에 앞의 것만 봤더니, 동의 화면에서 취소했는데 **웹 로그인 창이 또 떴다.**
 * 그만두겠다고 누른 사람에게 다른 창을 들이미는 꼴이었다.
 */
private fun Throwable.isUserCancel(): Boolean =
    (this as? ClientError)?.reason == ClientErrorCause.Cancelled ||
        (this as? AuthError)?.reason == AuthErrorCause.AccessDenied

private const val TAG = "DaengsAuth"

private suspend fun awaitLogin(
    start: ((OAuthToken?, Throwable?) -> Unit) -> Unit,
): Result<OAuthToken> = suspendCoroutine { cont ->
    start { token, error ->
        cont.resume(
            when {
                token != null -> Result.success(token)
                error != null -> Result.failure(error)
                else -> Result.failure(IllegalStateException("카카오 로그인에 실패했습니다."))
            },
        )
    }
}

/** 매 로그인마다 새로 만든다. 같은 값을 재사용하면 nonce 의 뜻이 없어진다. */
private fun newNonce(): String {
    val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
    return bytes.joinToString("") { "%02x".format(it) }
}
