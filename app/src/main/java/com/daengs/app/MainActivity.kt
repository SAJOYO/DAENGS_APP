package com.daengs.app

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
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
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.miniroom.rememberRoomStore
import com.daengs.app.ui.dogcard.rememberComposedCard
import com.daengs.app.dogcard.CardHolder
import androidx.compose.runtime.mutableIntStateOf
import com.daengs.app.farewell.FarewellScreen
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.dogcard.CardDrawScreen
import com.daengs.app.ui.dogcard.DrawDog
import com.daengs.app.ui.dogcard.birthCode
import com.daengs.app.dogcard.makeDevCard
import com.daengs.app.dogcard.seedCards
import com.daengs.app.pet.Pet
import com.daengs.app.ui.startup.LoadingScreen
import com.daengs.app.ui.startup.StartupTarget
import com.daengs.app.ui.startup.startupTarget
import com.daengs.app.miniroom.rememberOutsideView
import com.daengs.app.pet.rememberPetHolder
import com.daengs.app.ui.pet.PetFormScreen
import com.daengs.app.ui.chat.ChatScreen
import com.daengs.app.ui.dex.CardDexScreen
import com.daengs.app.ui.dogcard.CutoutLabScreen
import com.daengs.app.ui.home.HomeScreen
import com.daengs.app.ui.landing.LandingScreen
import com.daengs.app.ui.places.PlacesRoute
import com.daengs.app.ui.walk.WalkDetailScreen
import com.daengs.app.ui.walk.WalkHistoryScreen
import com.daengs.app.ui.walk.WalkOrientation
import com.daengs.app.ui.walk.WalkRoute
import com.daengs.app.walk.WalkDayTotals
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.launch

/** 화면들. 아직 [Screen] 하나로 충분하다 — 아래 주석 참고. */
private enum class Screen {
    Landing,
    /**
     * 강아지 목록을 기다리는 동안.
     *
     * **저장된 토큰으로 켤 때만 지난다.** 목록 없이 홈을 띄우면 방이 데모로 채워져서
     * 남의 강아지 넉 마리가 스친다 (`ui/startup/StartupGate.kt`).
     */
    Loading,
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
        val app = application as DaengsApp
        val walkRuntime = app.walkRuntime
        val cardStore = app.cardStore
        val walkController = walkRuntime.controller
        setContent {
            DaengsTheme {
                // 화면이 넷이 됐지만 **네비게이션 라이브러리는 아직 안 넣는다.**
                // 흐름이 갈래 없이 일직선(랜딩 → 홈 ⇄ 도감)이고, 딥링크도 백스택
                // 복원도 필요 없다. 산책 게임이 붙어 옆길이 생기면 그때가 맞다.
                val store = remember { app.tokenStore }
                // 탈퇴할 때 방까지 지워야 해서 여기서도 잡는다 (홈이 쓰는 것과 같은 저장소).
                val roomStore = rememberRoomStore()
                // 방 액자에 건 카드. **방과 도감이 만나는 자리가 여기 하나다** —
                // 고르는 곳은 도감이고 걸리는 곳은 방이라, 둘 다 아는 쪽이 들어야 한다.
                var frameCardId by remember { mutableStateOf(roomStore.loadFrameCardId()) }
                // **방 둘러보기.** 처음 방을 열 때 한 번 뜨고, 마이의 "다시 보기" 로
                // 다시 켠다. 본 적 있음은 기기에 남는다 — 계정이 아니라 이 폰의 일이다.
                var tourOpen by remember { mutableStateOf(!roomStore.tourSeen()) }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // **저장된 토큰을 동기로 읽는다.** 비동기로 읽으면 랜딩이 한 프레임
                // 번쩍였다가 홈으로 넘어간다.
                val saved = remember { store.load() }
                var screen by rememberSaveable {
                    mutableStateOf(if (saved == null) Screen.Landing else Screen.Loading)
                }
                // 방향은 기록 세션이 아니라 화면 설정이다. 사용자가 산책에서 고른 방향은
                // 회전 재생성 뒤에도 남고, 다른 화면은 기존 세로 구성을 지킨다.
                var walkOrientation by rememberSaveable {
                    mutableStateOf(WalkOrientation.PORTRAIT)
                }
                LaunchedEffect(screen, walkOrientation) {
                    requestedOrientation = when {
                        screen != Screen.Walk -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        walkOrientation == WalkOrientation.PORTRAIT ->
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
                var session by remember { mutableStateOf(saved) }
                var busy by remember { mutableStateOf(false) }
                val pets = rememberPetHolder()

                // 뽑아 놓은 카드. **여기서 들고 있는다** — 도감·홈·뽑기 셋이 보고,
                // 화면이 바뀌어도 안 죽어야 한다 (`outside`, `homeTab` 과 같은 이유).
                val cards = remember { CardHolder(cardStore) }

                // 개발자 패널이 고른 대표 견종. **여기서 들고 있는다** — 홈이 들고
                // 있었더니 홈 밖으로 못 나가서, 챗봇 얼굴을 보려면 로그인해서 강아지를
                // 등록하는 수밖에 없었다. 릴리스에서는 패널이 빈 껍데기라 늘 null 이다.
                // 저장하지 않는다 (패널 스위치와 같은 규칙).
                var devBreed by remember { mutableStateOf<DogBreed?>(null) }
                // **고른 값이 이긴다.** 홈이 하던 그대로다 — 로그인해서 대표가 있는
                // 상태에서도 다른 견종을 세워 보려고 고르는 것이라, 대표가 이기면
                // 고르기가 아무 일도 안 하는 것처럼 보인다.
                val artBreed = devBreed ?: pets.primary?.breedArt

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
                    val restored = app.sessionProvider.freshSession()
                    if (restored != null) session = restored
                    restored?.accessToken
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
                // 날씨 카드가 펴져 있나. **화면이 바뀌어도 기억한다** — 접어 두고
                // 도감에 갔다 왔는데 다시 펴져 있으면 접은 뜻이 없다.
                var weatherOpen by rememberSaveable { mutableStateOf(true) }
                // 도감을 열자마자 뽑기를 띄울지. 턴테이블의 "뽑으러 가기" 가 켠다.
                var dexOpensDraw by remember { mutableStateOf(false) }
                // 배웅. **화면을 안 늘린다** — 마이 위에 덮인다 (`myOpen` 과 같은 결).
                //
                // 배웅한 날은 **서버가 갖고 있다**(`pets.farewell_on`). 기기에 적어 두던
                // 것을 옮긴 것이라, 기기를 바꿔도 그 기록이 남는다.
                var farewell by remember { mutableStateOf<Pet?>(null) }
                // 지우기는 두 군데서 부른다 — 목록의 삭제와 배웅한 아이의 자리.
                // **서버가 먼저다.** 실패했는데 기기에서만 지우면 그 아이의 산책이
                // 다음 동기화 때 되돌아온다.
                val removePet: (Pet) -> Unit = { pet ->
                    scope.launch {
                        val token = freshToken() ?: return@launch
                        if (pets.remove(token, pet.id)) {
                            walkRuntime.history.forgetDog(pet.id)
                            todayWalks = walkRuntime.history.todayTotals()
                        }
                    }
                }
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
                // **못 살린 이유를 구분해야 한다.** freshSession은 두 경우에 null 을
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
                    // 로그인 직후. 끝났지만 전달되지 않은 산책을 durable 작업으로 넘기고,
                    // 새 폰이면 서버의 지난 산책도 되찾는다.
                    walkRuntime.delivery.enqueuePending()
                    walkRuntime.sync.syncOnce(token)

                    // 둘러보기로 뽑아 둔 카드에 도장을 찍고 목록을 받는다.
                    // 남의 카드는 안 건드린다 (`CardDao.claimOrphans`).
                    session?.appUserId?.let { cards.claimOrphans(it) }
                    // 출시본에서는 아무 일도 안 일어난다 — 디버그 소스셋의 시드다.
                    pets.primary?.let { seedCards(context, cardStore, it.id, it.name, it.birthDate) }
                    cards.load(session?.appUserId)
                }

                // **로딩을 떠나는 곳은 여기 하나다.** 갈림길 판정은 순수 함수로 빼서
                // 테스트가 잠근다 — 서버가 죽었을 때 갇히는 것이 제일 무서운 회귀다.
                LaunchedEffect(screen, pets.pets, pets.error) {
                    if (screen != Screen.Loading) return@LaunchedEffect
                    screen = when (startupTarget(pets.pets, pets.error)) {
                        StartupTarget.Wait -> return@LaunchedEffect
                        StartupTarget.Home -> Screen.Home
                        StartupTarget.Onboarding -> Screen.Onboarding
                    }
                }

                LaunchedEffect(Unit) {
                    if (saved != null) {
                        val restored = app.sessionProvider.freshSession()
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
                    Screen.Loading -> LoadingScreen()

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
                                        app.sessionProvider.save(it)
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

                    // 배웅은 **마이 위에 덮인다.** 화면을 늘리지 않는 것은 확대 뷰나
                    // 뽑기와 같은 결이고, 마이에서 들어와 마이로 돌아와야 하기 때문이다.
                    Screen.Home -> if (farewell != null) {
                        val pet = farewell!!
                        FarewellScreen(
                            dogName = pet.name,
                            // **목록의 값을 본다.** 서버에 보내고 목록을 다시 받으므로
                            // 여기 값이 곧 서버의 값이다.
                            sentOn = pets.pets?.firstOrNull { it.id == pet.id }?.farewellOn
                                ?: pet.farewellOn,
                            onSendOff = { day ->
                                scope.launch {
                                    val token = freshToken() ?: return@launch
                                    pets.sendOff(token, pet, day)
                                }
                            },
                            onUndo = {
                                scope.launch {
                                    val token = freshToken() ?: return@launch
                                    pets.sendOff(token, pet, null)
                                }
                            },
                            onClose = { farewell = null },
                            face = {
                                val art = pet.breedArt
                                if (art != null) {
                                    DogAvatar(art, Modifier.size(120.dp))
                                } else {
                                    PawAvatar(size = 120.dp)
                                }
                            },
                            // **읽기만 한다.** 함께 있을 때 적어 둔 것을 보여 주는 것이지
                            // 고치는 자리가 아니다. 모르는 항목은 줄에서 빠진다.
                            profile = buildList {
                                add("견종" to (pet.breedArt?.label ?: "믹스"))
                                pet.sex?.let {
                                    add("성별" to if (it == Pet.Sex.MALE) "남아" else "여아")
                                }
                                pet.weightKg?.let { add("몸무게" to "${it}kg") }
                                pet.birthDate?.let { d ->
                                    val label =
                                        if (pet.birthDateKind == Pet.BirthDateKind.FAMILY_DAY) {
                                            "가족이 된 날"
                                        } else {
                                            "생일"
                                        }
                                    add(label to "%d년 %d월 %d일".format(d.year, d.monthValue, d.dayOfMonth))
                                }
                            },
                            onDelete = {
                                removePet(pet)
                                farewell = null
                            },
                        )
                    } else HomeScreen(
                        tourOpen = tourOpen,
                        onReplayTour = { tourOpen = true },
                        onTourClose = {
                            tourOpen = false
                            roomStore.markTourSeen()
                        },
                        // 액자 그림. 고른 카드가 지워졌으면 못 찾고, 그때는 발자국이다.
                        framePicture = rememberComposedCard(
                            cards.cards.firstOrNull { it.id == frameCardId },
                        ),
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
                        weatherOpen = weatherOpen,
                        drawnCards = cards.cards,
                        onOpenDraw = {
                            dexOpensDraw = true
                            screen = Screen.Dex
                        },
                        onToggleWeather = { weatherOpen = !weatherOpen },
                        onOpenMy = { myOpen = true },
                        onCloseMy = { myOpen = false },
                        outside = outside,
                        pets = pets.pets.orEmpty(),
                        canAddMore = pets.canAddMore,
                        onAddPet = { editing = null; screen = Screen.Onboarding },
                        onEditPet = { editing = it; screen = Screen.Onboarding },
                        onFarewell = { farewell = it },
                        farewellOf = { it.farewellOn },
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
                        onDeletePet = removePet,
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
                                        app.sessionProvider.clear()
                                        roomStore.clear()
                                        frameCardId = null
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
                            app.sessionProvider.clear()
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
                        // 야채를 지정해 카드를 만든다. **뽑기와 같은 값을 넣는다** —
                        // 아래 `onDrawn` 과 이름·번호가 갈리면 지정해서 만든 카드만
                        // 다른 글자를 달고 나와서, 확인하려던 것이 안 맞는다.
                        onMakeCard = if (BuildConfig.DEBUG) {
                            { template ->
                                val dog = pets.primary
                                scope.launch {
                                    makeDevCard(
                                        context = context,
                                        cards = cards,
                                        template = template,
                                        dogId = dog?.id,
                                        dogName = dog?.name ?: "우리 아이",
                                        codeText = dog?.birthDate
                                            ?.let { birthCode(it.monthValue, it.dayOfMonth) }
                                            ?: birthCode(8, 24),
                                        appUserId = session?.appUserId,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        // 이번 판에 뽑아 둔 카드가 한 장이라도 있나. **파일을 뒤지지
                        // 않는다** — 얼굴 없는 카드는 위에서 막으므로, 목록에 있으면
                        // 얼굴도 있다.
                        canMakeCard = cards.cards.isNotEmpty(),
                        devBreed = devBreed,
                        onPickDevBreed = { devBreed = it },
                    )

                    Screen.Chat -> ChatScreen(
                        onBack = { screen = Screen.Home },
                        avatar = artBreed,
                        dogId = pets.primary?.id,
                        accessTokenProvider = freshToken,
                    )

                    Screen.Places -> PlacesRoute(
                        onBack = { screen = Screen.Home },
                        primaryPet = pets.primary,
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

                    Screen.Walk -> WalkRoute(
                        onBack = { screen = Screen.Home },
                        onRequestOrientation = { walkOrientation = it },
                        walkController = walkController,
                        history = walkRuntime.history,
                        avatarBreed = artBreed,
                        pets = pets.pets.orEmpty(),
                        outside = outside,
                    )

                    Screen.Dex -> CardDexScreen(
                        onClose = { screen = Screen.Home },
                        startInDraw = dexOpensDraw.also { dexOpensDraw = false },
                        drawn = cards.cards,
                        framedCardId = frameCardId,
                        onFrame = { card ->
                            frameCardId = card?.id
                            roomStore.saveFrameCardId(card?.id)
                        },
                        onDelete = { card ->
                            scope.launch {
                                // **액자를 먼저 비운다.** 걸려 있던 카드를 지우고
                                // 액자만 두면 그림이 사라진 자리가 남는다 (탈퇴할 때와
                                // 같은 정리다).
                                if (frameCardId == card.id) {
                                    frameCardId = null
                                    roomStore.saveFrameCardId(null)
                                }
                                cards.remove(card.id)
                            }
                        },
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
                                        // 이제 뽑기는 사용자가 원 안에 맞춘 얼굴만
                                        // 넘긴다. 옛 카드와 갈리는 기준이다.
                                        userFramed = true,
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
