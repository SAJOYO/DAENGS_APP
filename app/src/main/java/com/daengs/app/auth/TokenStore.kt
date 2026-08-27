package com.daengs.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 로그인 토큰을 기기에 저장한다.
 *
 * **[com.daengs.app.miniroom.RoomStore] 와 같은 모양이되 암호화한다.** 저쪽 백엔드가
 * 그렇게 하라고 못박아 뒀다 — 앱에는 httpOnly 쿠키가 없어서 토큰을 본문으로 받아
 * 직접 보관하는 구조이고, 그걸 평문으로 두면 쿠키를 안 쓴 의미가 없어진다.
 *
 * **읽기가 동기인 게 중요하다.** 첫 프레임에 로그인 여부를 알아야 랜딩 화면이
 * 번쩍였다가 홈으로 넘어가지 않는다. (`RoomStore` 가 DataStore 를 안 쓴 이유와 같다)
 *
 * 이 파일은 클라우드 백업에서 빼 뒀다 (`res/xml/backup_rules.xml`). `allowBackup` 이
 * 켜져 있어서 그냥 두면 남의 기기로 토큰이 딸려 간다.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val app = context.applicationContext
        val key = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            app,
            PREFS,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): Session? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return Session(
            appUserId = prefs.getString(KEY_USER, "").orEmpty(),
            accessToken = access,
            refreshToken = refresh,
            accessExpiresAtMs = prefs.getLong(KEY_ACCESS_EXP, 0L),
            refreshExpiresAtMs = prefs.getLong(KEY_REFRESH_EXP, 0L),
        )
    }

    /**
     * **응답을 받을 때마다 부른다.** refresh 토큰은 쓸 때마다 바뀌므로(회전), 옛 것을
     * 남겨 두고 다시 쓰면 서버가 재사용으로 보고 **그 회원의 세션을 전부 끊는다.**
     */
    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_USER, session.appUserId)
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_ACCESS_EXP, session.accessExpiresAtMs)
            .putLong(KEY_REFRESH_EXP, session.refreshExpiresAtMs)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        /** 백업 규칙이 이 이름을 가리킨다. 바꾸면 거기도 같이 고쳐야 한다. */
        const val PREFS = "daengs_session"

        private const val KEY_USER = "app_user_id"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ACCESS_EXP = "access_expires_at"
        private const val KEY_REFRESH_EXP = "refresh_expires_at"
    }
}

@Composable
fun rememberTokenStore(): TokenStore {
    val context = LocalContext.current
    return remember(context) { TokenStore(context) }
}
