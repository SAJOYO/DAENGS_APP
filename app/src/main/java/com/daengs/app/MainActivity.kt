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
import com.daengs.app.ui.home.BottomTab
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.daengs.app.miniroom.rememberRoomStore
import com.daengs.app.dogcard.CardHolder
import com.daengs.app.ui.dogcard.CardDrawScreen
import com.daengs.app.ui.dogcard.DrawDog
import com.daengs.app.ui.dogcard.birthCode
import com.daengs.app.dogcard.seedCards
import com.daengs.app.pet.Pet
import com.daengs.app.miniroom.rememberOutsideView
import com.daengs.app.pet.rememberPetHolder
import com.daengs.app.ui.pet.PetFormScreen
import com.daengs.app.ui.chat.ChatScreen
import com.daengs.app.ui.dex.CardDexScreen
import com.daengs.app.ui.dogcard.CutoutLabScreen
import com.daengs.app.ui.home.HomeScreen
import com.daengs.app.ui.landing.LandingScreen
import com.daengs.app.ui.places.PlacesScreen
import com.daengs.app.ui.walk.WalkDetailScreen
import com.daengs.app.ui.walk.WalkHistoryScreen
import com.daengs.app.ui.walk.WalkScreen
import com.daengs.app.walk.WalkDayTotals
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.launch

/** 화면들. 아직 [Screen] 하나로 충분하다 — 아래 주석 참고. */
private enum class Screen {
    Landing,
    /** 강아지 등록. **로그인했는데 강아지가 없으면** 여기로 온다. */
    Onboarding,
    Home, Chat, Dex,
    /** 내 주변 장소. 하단 탭에서 들어온다. */
    Places,
    /** 산책. **미니룸의 문으로 들어온다** — 탭이 아니다. */
    Walk,
    /** 지난 산책 목록. 홈의 산책 요약 카드에서 들어온다. */
    WalkHistory,
    /** 산책 하나. 목록에서 고른 것이라 어느 세션인지는 [MainActivity] 가 들고 있다. */
    WalkDetail,
    /** 카드 실험실. **디버그 빌드의 개발자 패널에서만** 열린다. 사용자 흐름에 없다. */
    CutoutLab,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 시스템 스플래시. **setContent 보다 먼저** 불러야 한다.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val walkRuntime = (application as DaengsApp).walkRuntime
        val cardStore = (application as DaengsApp).cardStore
        val walkController = walkRuntime.controller
        setContent {
            DaengsTheme {
                // 화면이 넷이 됐지만 **네비게이션 라이브러리는 아직 안 넣는다.**
                // 흐름이 갈래 없이 일직선(랜딩 → 홈 ⇄ 도감)이고, 딥링크도 백스택
                // 복원도 필요 없다. 산책 게임이 붙어 옆길이 생기면 그때가 맞다.
                val store = rememberTokenStore()
                // 탈퇴할 때 방까지 지워야 해서 여기서도 잡는다 (홈이 쓰는 것과 같은 저장소).
                val roomStore = rememberRoomStore()
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
                val pets = rememberPetHolder()

                // 뽑아 놓은 카드. **여기서 들고 있는다** — 도감·홈·뽑기 셋이 보고,
                // 화면이 바뀌어도 안 죽어야 한다 (`outside`, `homeTab` 과 같은 이유).
                val cards = remember { CardHolder(cardStore) }

                // 창밖 날씨. **여기서 들고 있는다** — 화면이 바뀌어도 안 죽는다.
                // 홈 안에서 부르면 도감·산책을 갔다 올 때마다 폴백(맑은 낮)부터 다시
                // 시작해서 카드가 눈앞에서 바뀐다 (`OutsideSource` 주석). `homeTab`
                // 을 여기로 올린 것과 같은 이유다.
                val outside by rememberOutsideView()

                // **부르기 전에 토큰을 새로 받는다.**
                //
                // access 는 5분이다. 앱을 켜 두고 몇 분 뒤에 강아지를 등록하면
                // 서버가 "인증이 만료되었습니다"로 막는다 — 실제로 그렇게 걸렸다.
                // 재발급이 되면 세션도 같이 갈아 끼워야 다음 호출이 또 만료를 안 만난다.
                val freshToken: suspend () -> String? = {
                    val restored = restoreSession(store)
                    if (restored != null) session = restored
                    (restored ?: session)?.accessToken
                }
                // 고치는 중인 강아지. null 이면 새로 등록하는 것이다.
                var editing by remember { mutableStateOf<Pet?>(null) }
                /** 목록에서 고른 산책. 상세 화면은 id 만 받아 스스로 읽어 온다. */
                var openedWalkId by remember { mutableStateOf<String?>(null) }

                /** 홈 카드의 오늘치. null 은 **아직 못 읽은 것**이라 카드가 `-` 로 둔다. */
                var todayWalks by remember { mutableStateOf<WalkDayTotals?>(null) }
                // 방 이름표. **null 은 아직 안 정했거나 못 받아온 것**이고, 그때
                // 화면이 대표 강아지 이름으로 짓는다.
                // 홈의 하단 탭. **여기서 들고 있는다** — 화면이 바뀌어도 안 지워진다.
                var homeTab by rememberSaveable { mutableStateOf(BottomTab.Home) }

                // 마이 화면이 열려 있나. **탭이 아니라 상단바의 프로필 버튼으로 연다.**
                //
                // 탭과 같은 이유로 여기서 든다 — **강아지를 추가하러 나갔다 오면
                // 마이로 돌아와야** 하는데, 홈 안에서 들면 화면이 바뀔 때 같이 죽어서
                // 등록을 마치고 나면 방으로 떨어진다.
                var myOpen by rememberSaveable { mutableStateOf(false) }
                var roomName by remember { mutableStateOf<String?>(null) }
                var renameBusy by remember { mutableStateOf(false) }
                var renameError by remember { mutableStateOf<String?>(null) }

                /**
                 * 홈이 다시 보일 때마다 오늘치를 다시 읽는다.
                 *
                 * 산책을 끝내거나 목록에서 돌아오면 숫자가 바뀌어 있어야 한다 —
                 * `screen` 을 키로 두면 그 두 경우가 다 잡힌다.
                 */
                LaunchedEffect(screen) {
                    if (screen == Screen.Home) todayWalks = walkRuntime.history.todayTotals()
                }

                var withdrawBusy by remember { mutableStateOf(false) }
                var withdrawError by remember { mutableStateOf<String?>(null) }
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
                // 세션이 생기거나 바뀌면 강아지를 받아 온다. **강아지가 없으면
                // 온보딩으로 보낸다** — 출시 앱은 로그인이 필수이고, 로그인했는데
                // 강아지가 없는 상태로 홈에 두면 방에 세울 아이가 없다.
                LaunchedEffect(session) {
                    if (session == null) {
                        pets.forget()
                        // 남의 방 이름표가 남으면 안 된다. 로그아웃하면 지어진 이름으로.
                        roomName = null
                        return@LaunchedEffect
                    }
                    val token = freshToken() ?: return@LaunchedEffect
                    pets.refresh(token)
                    // 이름표. 못 받아도 조용하다 — 지어진 이름이 걸린다.
                    AuthApi.me(token).onSuccess { roomName = it.roomName }
                    if (pets.isEmpty == true && screen == Screen.Home) screen = Screen.Onboarding
                    // 로그인 직후. **새 폰이면 여기서 지난 산책이 되돌아온다.**
                    walkRuntime.sync.syncOnce(token)

                    // 둘러보기로 뽑아 둔 카드에 도장을 찍고 목록을 받는다.
                    // 남의 카드는 안 건드린다 (`CardDao.claimOrphans`).
                    session?.appUserId?.let { cards.claimOrphans(it) }
                    // 출시본에서는 아무 일도 안 일어난다 — 디버그 소스셋의 시드다.
                    pets.primary?.let { seedCards(context, cardStore, it.id, it.name, it.birthDate) }
                    cards.load(session?.appUserId)
                }

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

                    Screen.Onboarding -> PetFormScreen(
                        initial = editing,
                        busy = pets.busy,
                        error = pets.error,
                        // 첫 등록에는 취소가 없다 — 강아지 없이 갈 곳이 없다.
                        // 나중에 마이에서 들어온 것(추가·고치기)만 되돌아간다.
                        onCancel = if (pets.isEmpty == true && editing == null) {
                            null
                        } else {
                            { pets.clearError(); editing = null; screen = Screen.Home }
                        },
                        onSubmit = { draft ->
                            scope.launch {
                                val token = freshToken() ?: return@launch
                                val target = editing
                                val ok = if (target == null) {
                                    pets.add(token, draft)
                                } else {
                                    pets.edit(token, target.id, draft)
                                }
                                if (ok) {
                                    editing = null
                                    screen = Screen.Home
                                }
                            }
                        },
                    )

                    Screen.Home -> HomeScreen(
                        onOpenDex = { screen = Screen.Dex },
                        onOpenChat = { screen = Screen.Chat },
                        onOpenPlaces = { screen = Screen.Places },
                        onOpenWalk = { screen = Screen.Walk },
                        onOpenWalkHistory = { screen = Screen.WalkHistory },
                        todayWalks = todayWalks,
                        signedIn = session != null,
                        // 둘러보기로 들어온 사람이 다시 로그인할 길. 랜딩으로
                        // 되돌리면 기존 카카오 경로를 그대로 쓴다.
                        onSignIn = { screen = Screen.Landing },
                        tab = homeTab,
                        onSelectTab = { homeTab = it },
                        myOpen = myOpen,
                        onOpenMy = { myOpen = true },
                        onCloseMy = { myOpen = false },
                        outside = outside,
                        pets = pets.pets.orEmpty(),
                        canAddMore = pets.canAddMore,
                        onAddPet = { editing = null; screen = Screen.Onboarding },
                        onEditPet = { editing = it; screen = Screen.Onboarding },
                        onPickPrimary = { pet ->
                            scope.launch {
                                val token = freshToken() ?: return@launch
                                pets.choosePrimary(token, pet.id)
                            }
                        },
                        roomName = roomName,
                        renameBusy = renameBusy,
                        renameError = renameError,
                        onDismissRename = { renameError = null },
                        onRenameRoom = if (session == null) {
                            null
                        } else {
                            { name ->
                                renameBusy = true
                                renameError = null
                                scope.launch {
                                    val token = freshToken()
                                    if (token == null) {
                                        renameBusy = false
                                        renameError = "다시 로그인해 주세요."
                                        return@launch
                                    }
                                    AuthApi.setRoomName(token, name)
                                        .onSuccess { roomName = it.roomName }
                                        .onFailure { renameError = it.message }
                                    renameBusy = false
                                }
                            }
                        },
                        deletePetBusy = pets.busy,
                        deletePetError = pets.error,
                        onDismissDeletePet = { pets.clearError() },
                        onDeletePet = { pet ->
                            scope.launch {
                                val token = freshToken() ?: return@launch
                                // **서버가 먼저다.** 실패했는데 기기에서만 지우면 그
                                // 아이의 산책이 다음 동기화 때 되돌아온다.
                                if (pets.remove(token, pet.id)) {
                                    walkRuntime.history.forgetDog(pet.id)
                                    todayWalks = walkRuntime.history.todayTotals()
                                }
                            }
                        },
                        withdrawBusy = withdrawBusy,
                        withdrawError = withdrawError,
                        onDismissWithdraw = { withdrawError = null },
                        onWithdraw = {
                            val old = session ?: return@HomeScreen
                            withdrawBusy = true
                            withdrawError = null
                            scope.launch {
                                val token = freshToken() ?: old.accessToken
                                val result = AuthApi.withdraw(token)
                                withdrawBusy = false
                                result
                                    .onSuccess {
                                        // 방은 서버에 사본이 없어 이 기기에만 있다.
                                        // 안 지우면 다음에 로그인한 사람이 남의 방을
                                        // 물려받는다.
                                        store.clear()
                                        roomStore.clear()
                                        pets.forget()
                                        // **산책 좌표도 지운다.** 서버는 탈퇴에서
                                        // 산책까지 지우는데 폰의 Room 에는 원본이
                                        // 남아 있었다 — 경로는 집과 생활권을 그대로
                                        // 드러내는 값이라, 그걸 두고 "계정을 지우면
                                        // 데이터도 지운다" 고 할 수 없다.
                                        walkRuntime.history.forgetEverything()
                                        todayWalks = walkRuntime.history.todayTotals()
                                        // 뽑은 카드도 이 기기에만 있다. 서버에 사본이
                                        // 없으므로 여기서 안 지우면 다음에 로그인한
                                        // 사람이 남의 도감을 물려받는다.
                                        cards.forgetEverything()
                                        session = null
                                        screen = Screen.Landing
                                    }
                                    .onFailure { e ->
                                        // **아무것도 안 지운다.** 로그아웃과 정반대다 —
                                        // 로그아웃은 실패해도 기기에서 지우는 게 맞지만,
                                        // 탈퇴는 서버에 계정이 살아 있는데 앱만 잊으면
                                        // 사용자는 지워진 줄 알고 다시 시도할 길도 잃는다.
                                        withdrawError = e.message ?: "탈퇴하지 못했어요."
                                    }
                            }
                        },
                        onSignOut = {
                            val old = session
                            session = null
                            // 다음 사람이 남의 강아지를 보면 안 된다.
                            pets.forget()
                            store.clear()
                            screen = Screen.Landing
                            // 서버 쪽 세션도 지운다. 실패해도 기기에서는 이미 지웠다.
                            if (old != null && AuthApi.configured) {
                                scope.launch { AuthApi.logout(old.refreshToken) }
                            }
                        },
                        onOpenCutoutLab = if (BuildConfig.DEBUG) {
                            { screen = Screen.CutoutLab }
                        } else {
                            null
                        },
                    )

                    Screen.Chat -> ChatScreen(
                        onBack = { screen = Screen.Home },
                        avatar = pets.primary?.breedArt,
                        dogId = pets.primary?.id,
                        accessTokenProvider = freshToken,
                    )

                    Screen.Places -> PlacesScreen(
                        onBack = { screen = Screen.Home },
                        avatarBreed = pets.primary?.breedArt,
                    )

                    Screen.WalkHistory -> WalkHistoryScreen(
                        history = walkRuntime.history,
                        // 목록을 열 때 한 번 더. 걷고 나서 지하철에 들어갔던 기록이
                        // 여기서 올라가고, 다른 기기에서 한 산책이 여기서 내려온다.
                        onSync = { scope.launch { walkRuntime.sync.syncOnce(freshToken()) } },
                        onBack = { screen = Screen.Home },
                        pets = pets.pets.orEmpty(),
                        onOpen = { id ->
                            openedWalkId = id
                            screen = Screen.WalkDetail
                        },
                    )

                    Screen.WalkDetail -> openedWalkId?.let { id ->
                        WalkDetailScreen(
                            sessionId = id,
                            history = walkRuntime.history,
                            onBack = { screen = Screen.WalkHistory },
                            pets = pets.pets.orEmpty(),
                        )
                    }

                    Screen.Walk -> WalkScreen(
                        onBack = { screen = Screen.Home },
                        walkController = walkController,
                        avatarBreed = pets.primary?.breedArt,
                        pets = pets.pets.orEmpty(),
                        onFinished = {
                            scope.launch { walkRuntime.sync.syncOnce(freshToken()) }
                        },
                    )

                    Screen.Dex -> CardDexScreen(
                        onClose = { screen = Screen.Home },
                        drawn = cards.cards,
                        draw = { done ->
                            CardDrawScreen(
                                dogs = pets.pets.orEmpty().map { pet ->
                                    DrawDog(
                                        id = pet.id,
                                        name = pet.name,
                                        codeText = pet.birthDate
                                            ?.let { birthCode(it.monthValue, it.dayOfMonth) }
                                            ?: birthCode(8, 24),
                                        isPrimary = pet.isPrimary,
                                    )
                                },
                                drawsLeft = cards.drawsLeft(),
                                onCancel = done,
                                onDrawn = { dog, template, face, core ->
                                    cards.draw(
                                        template = template,
                                        face = face,
                                        core = core,
                                        dogId = dog?.id,
                                        dogName = dog?.name ?: "우리 아이",
                                        codeText = dog?.codeText ?: birthCode(8, 24),
                                        appUserId = session?.appUserId,
                                    )
                                },
                                onOpenDex = done,
                            )
                        },
                    )

                    Screen.CutoutLab -> CutoutLabScreen(onBack = { screen = Screen.Home })
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
