package com.daengs.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.daengs.app.auth.AuthApi
import com.daengs.app.auth.CancelledByUser
import com.daengs.app.auth.Session
import com.daengs.app.auth.logIdTokenShape
import com.daengs.app.auth.loginWithKakao
import com.daengs.app.auth.rememberTokenStore
import com.daengs.app.auth.restoreSession
import com.daengs.app.ui.chat.ChatScreen
import com.daengs.app.ui.dex.CardDexScreen
import com.daengs.app.ui.home.HomeScreen
import com.daengs.app.ui.landing.LandingScreen
import com.daengs.app.ui.places.PlacesScreen
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.launch

/** 화면 다섯. 아직 [Screen] 하나로 충분하다 — 아래 주석 참고. */
private enum class Screen { Landing, Home, Chat, Dex, Places }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 시스템 스플래시. **setContent 보다 먼저** 불러야 한다.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DaengsTheme {
                // 화면이 넷이 됐지만 **네비게이션 라이브러리는 아직 안 넣는다.**
                // 흐름이 갈래 없이 일직선(랜딩 → 홈 ⇄ 도감)이고, 딥링크도 백스택
                // 복원도 필요 없다. 산책 게임이 붙어 옆길이 생기면 그때가 맞다.
                val store = rememberTokenStore()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // **저장된 토큰을 동기로 읽는다.** 비동기로 읽으면 랜딩이 한 프레임
                // 번쩍였다가 홈으로 넘어간다.
                val saved = remember { store.load() }
                var screen by remember {
                    mutableStateOf(if (saved == null) Screen.Landing else Screen.Home)
                }
                var session by remember { mutableStateOf(saved) }
                var busy by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }

                // access 가 만료됐으면 조용히 재발급한다.
                //
                // **못 살린 이유를 구분해야 한다.** [restoreSession] 은 두 경우에 null 을
                // 주는데 대응이 정반대다.
                //
                //  - 토큰이 죽었다 (refresh 7일이 지났다) → 저장소를 비웠다. 랜딩으로 보낸다
                //  - 재발급 요청이 실패했다 (서버가 잠깐 안 된다) → 저장소는 그대로다.
                //    **쫓아내면 안 된다.** 다음에 켤 때 다시 시도한다
                //
                // 구분은 저장소를 다시 읽어서 한다. 비었으면 앞의 경우다.
                LaunchedEffect(Unit) {
                    if (saved != null) {
                        val restored = restoreSession(store)
                        when {
                            restored != null -> session = restored
                            store.load() == null -> {
                                session = null
                                screen = Screen.Landing
                            }
                        }
                    }
                }

                when (screen) {
                    Screen.Landing -> LandingScreen(
                        canLogin = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank() && AuthApi.configured,
                        busy = busy,
                        error = error,
                        onKakaoLogin = {
                            busy = true
                            error = null
                            scope.launch {
                                val result = signIn(context)
                                busy = false
                                result
                                    .onSuccess {
                                        store.save(it)
                                        session = it
                                        screen = Screen.Home
                                    }
                                    .onFailure { e ->
                                        // 사용자가 취소한 것은 오류가 아니다.
                                        error = if (e is CancelledByUser) null else e.message
                                    }
                            }
                        },
                        onSkip = { screen = Screen.Home },
                    )

                    Screen.Home -> HomeScreen(
                        onOpenDex = { screen = Screen.Dex },
                        onOpenChat = { screen = Screen.Chat },
                        onOpenPlaces = { screen = Screen.Places },
                        signedIn = session != null,
                        onSignOut = {
                            val old = session
                            session = null
                            store.clear()
                            screen = Screen.Landing
                            // 서버 쪽 세션도 지운다. 실패해도 기기에서는 이미 지웠다.
                            if (old != null && AuthApi.configured) {
                                scope.launch { AuthApi.logout(old.refreshToken) }
                            }
                        },
                    )

                    Screen.Chat -> ChatScreen(onBack = { screen = Screen.Home })

                    Screen.Places -> PlacesScreen(onBack = { screen = Screen.Home })

                    Screen.Dex -> CardDexScreen(onClose = { screen = Screen.Home })
                }
            }
        }
    }
}

/** 카카오에서 `id_token` 을 받아 우리 서버 세션으로 바꾼다. */
private suspend fun signIn(context: android.content.Context): Result<Session> =
    loginWithKakao(context).mapCatching { kakao ->
        logIdTokenShape(kakao.idToken, kakao.nonce)
        AuthApi.loginWithKakao(kakao.idToken, kakao.nonce).getOrThrow()
    }
