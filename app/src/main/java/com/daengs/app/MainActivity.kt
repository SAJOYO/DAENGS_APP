package com.daengs.app

import android.os.Bundle
import android.os.SystemClock
import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.daengs.app.ui.home.BottomTab
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.daengs.app.auth.AuthApi
import com.daengs.app.auth.CancelledByUser
import com.daengs.app.auth.NicknameTakenException
import com.daengs.app.auth.Session
import com.daengs.app.auth.logIdTokenShape
import com.daengs.app.auth.loginWithKakao
import com.daengs.app.chat.ChatHistoryCoordinator
import com.daengs.app.chat.ChatSummaryCoordinator
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.miniroom.rememberRoomStore
import com.daengs.app.ui.dogcard.rememberComposedCard
import com.daengs.app.dogcard.CardHolder
import com.daengs.app.dogcard.CardSyncRunner
import com.daengs.app.dogcard.MissLog
import androidx.compose.runtime.mutableIntStateOf
import com.daengs.app.farewell.FarewellScreen
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.PetAvatar
import com.daengs.app.ui.pet.PetPhotoPicker
import com.daengs.app.ui.dogcard.CardDrawScreen
import com.daengs.app.ui.dogcard.DrawDog
import com.daengs.app.ui.dogcard.birthCode
import com.daengs.app.dogcard.makeDevCard
import com.daengs.app.pet.Pet
import com.daengs.app.ui.startup.LoadingScreen
import com.daengs.app.ui.startup.StartupTarget
import com.daengs.app.ui.startup.startupTarget
import com.daengs.app.ui.startup.loadingHoldMs
import com.daengs.app.miniroom.rememberOutsideView
import com.daengs.app.pet.devPets
import com.daengs.app.pet.photoTargetId
import com.daengs.app.pet.rememberPetHolder
import com.daengs.app.pet.rememberPetPhotoHolder
import com.daengs.app.screening.rememberScreeningHolder
import com.daengs.app.ui.screening.ScreeningHistoryScreen
import com.daengs.app.ui.pet.PetFormScreen
import com.daengs.app.ui.chat.ChatScreen
import com.daengs.app.ui.dex.CardDexScreen
import com.daengs.app.ui.dogcard.CutoutLabScreen
import com.daengs.app.ui.home.HomeScreen
import com.daengs.app.ui.home.PetNeed
import com.daengs.app.ui.home.PetNeededDialog
import com.daengs.app.ui.home.needsPet
import com.daengs.app.ui.landing.LandingScreen
import com.daengs.app.ui.nickname.NicknameScreen
import com.daengs.app.ui.places.PlacesRoute
import com.daengs.app.care.CareLogCoordinator
import com.daengs.app.ui.storage.ChatSummaryRoute
import com.daengs.app.ui.walk.WalkDetailScreen
import com.daengs.app.ui.walk.WalkHistoryScreen
import com.daengs.app.ui.walk.WalkOrientation
import com.daengs.app.ui.walk.WalkRoute
import com.daengs.app.walk.WalkDayTotals
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.delay
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
    /**
     * 이름 확인. **카카오 로그인이 막 끝났을 때만** 지난다.
     *
     * 저장된 토큰으로 켤 때는 안 지난다 — 그래서 "본 적 있음" 을 기기에 안 남긴다
     * (`ui/nickname/NicknameScreen.kt`).
     */
    Nickname,
    /** 강아지 등록. 예전에는 로그인 직후 여기로 끌고 왔지만 지금은 아니다. */
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
    /** 피부 변화 기록. 대화의 AI 기능 선택에서 들어온다. */
    ScreeningHistory,
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
                // 방에서 뺀 아이들. **뺀 쪽을 적는다** — 새로 등록한 아이는 저절로
                // 방에 서야 하므로 기본이 "다 들어감" 이다 (`RoomStore.loadHiddenPetIds`).
                var hiddenRoomPetIds by remember { mutableStateOf(roomStore.loadHiddenPetIds()) }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                // Chat 과 Storage 를 오가도 서버에서 고른 대화와 요약 결과를 잃지 않는다.
                // 토큰은 넣어 두지 않고 매 동작마다 아래 freshToken 경계를 지난다.
                val chatHistory = remember(scope) { ChatHistoryCoordinator(scope) }
                val chatSummaries = remember(scope) { ChatSummaryCoordinator(scope) }
                // 저장소 탭의 오늘의 케어 기록 (#201). 요약 보관함과 같은 생애 — 서버 사본이고 기기에 안 남긴다.
                val careLog = remember(scope) { CareLogCoordinator(scope) }
                val chatHistoryState by chatHistory.state.collectAsState()

                // **저장된 토큰을 동기로 읽는다.** 비동기로 읽으면 랜딩이 한 프레임
                // 번쩍였다가 홈으로 넘어간다.
                val saved = remember { store.load() }
                var screen by rememberSaveable {
                    mutableStateOf(if (saved == null) Screen.Landing else Screen.Loading)
                }
                val walkHistoryState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                // 로딩이 뜬 시각. **로딩은 처음 한 번만 지나는 길**이라 여기서 한 번
                // 잡으면 된다 (`screen` 의 초기값이 곧 이 화면이다).
                val loadingSince = remember { SystemClock.elapsedRealtime() }
                // 방향은 기록 세션이 아니라 화면 설정이다. 사용자가 산책에서 고른 방향은
                // 회전 재생성 뒤에도 남고, 다른 화면은 기존 세로 구성을 지킨다.
                var walkOrientation by rememberSaveable {
                    mutableStateOf(WalkOrientation.PORTRAIT)
                }
                // 이머시브 무대가 떠 있나. **무대만 가로를 허용한다** — 배경이 좌우로
                // 펼쳐지는 장면이라 가로가 이득인 유일한 자리다. 나머지는 세로 전용으로
                // 그려져 있어 눕히면 무너진다 (실기기에서 확인).
                var immersiveOpen by remember { mutableStateOf(false) }
                // 무대를 눕혔나. **무대를 닫으면 원래대로 돌아온다** — 세워 둔 채로
                // 나가면 다음에 들어올 때 이유 없이 누워 있다.
                var immersiveLandscape by remember { mutableStateOf(false) }
                // **`immersiveLandscape` 도 키다.** 값만 바뀌고 이 자리가 안 돌면 버튼을 눌러도
                // 방향이 그대로다 — 실제로 그렇게 안 돌아갔다.
                LaunchedEffect(screen, walkOrientation, immersiveOpen, immersiveLandscape) {
                    requestedOrientation = when {
                        screen == Screen.Dex && immersiveOpen ->
                            if (immersiveLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        screen != Screen.Walk -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        walkOrientation == WalkOrientation.PORTRAIT ->
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
                var session by remember { mutableStateOf(saved) }
                var busy by remember { mutableStateOf(false) }
                val pets = rememberPetHolder()
                // 프로필 사진. **원본은 서버이고 기기에 있는 것은 캐시다**
                // (`pet/PetPhotos.kt`). 그래서 폰을 바꿔도 사진이 따라온다.
                val petPhotos = rememberPetPhotoHolder()
                LaunchedEffect(pets.pets) {
                    // 캐시를 그린다. 서버와 맞추는 것은 로그인 직후 아래에서 한다 —
                    // 여기서 하면 목록이 바뀔 때마다 서버를 두드리게 되고,
                    // `freshToken` 이 아직 선언되기 전이라 잡히지도 않는다.
                    petPhotos.load(pets.pets.orEmpty().map { it.id })
                }
                // 사진을 바꾸는 중인 아이. 홈 위에 덮인다 (배웅 화면과 같은 방식).
                var photoFor by remember { mutableStateOf<Pet?>(null) }
                // 개발자 패널로 올려 본 사진. **저장하지 않는다** — 강아지 기록 없이
                // 얼굴만 보는 자리라 걸어 둘 id 가 없다 (견종 갈아끼우기와 같은 결).
                var devPhoto by remember { mutableStateOf<ImageBitmap?>(null) }
                var devPhotoPicking by remember { mutableStateOf(false) }
                // 개발자 패널이 넣어 본 가짜 강아지. **저장도 전송도 안 한다** —
                // 강아지에 딸린 화면들이 로그인해야만 보여서, 계정을 못 쓰는 기기에서
                // 그것들을 보는 유일한 길이다 (`pet/DevPets.kt`).
                var devPetCount by remember { mutableIntStateOf(0) }
                // **null 을 살려 둔 짝이 하나 더 있다.** 아래 `shownPets` 는 화면에 넘기기
                // 편하게 빈 목록으로 눌러 놓은 것이라, 거기서는 "아직 못 받아 왔다" 와
                // "한 마리도 없다" 가 갈리지 않는다. 빈 방을 띄울지는 그 둘을 갈라야
                // 정할 수 있어서(`needsPet`), 눌러 놓기 전의 값을 같이 둔다.
                val devHerd = devPets(devPetCount)
                val shownPetsOrNull: List<Pet>? =
                    if (devHerd.isNotEmpty()) devHerd else pets.pets
                val shownPets = shownPetsOrNull.orEmpty()

                // 개발자 패널이 고른 대표 견종. **여기서 들고 있는다** — 홈이 들고
                // 있었더니 홈 밖으로 못 나가서, 챗봇 얼굴을 보려면 로그인해서 강아지를
                // 등록하는 수밖에 없었다. 릴리스에서는 패널이 빈 껍데기라 늘 null 이다.
                // 저장하지 않는다 (패널 스위치와 같은 규칙).
                var devBreed by remember { mutableStateOf<DogBreed?>(null) }
                // **고른 값이 이긴다.** 홈이 하던 그대로다 — 로그인해서 대표가 있는
                // 상태에서도 다른 견종을 세워 보려고 고르는 것이라, 대표가 이기면
                // 고르기가 아무 일도 안 하는 것처럼 보인다.
                // 대표 아이의 얼굴. 상단바·챗봇·산책 지도의 내 위치 표시가 이걸 쓴다.
                // **개발자 패널이 넣어 본 아이도 대표가 된다** — 안 그러면 그 상태에서
                // 산책 지도의 얼굴만 파란 점으로 남는다.
                val artBreed = devBreed
                    ?: shownPets.firstOrNull { it.isPrimary }?.breedArt
                    ?: pets.primary?.breedArt

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
                // 피부 변화 기록. **서버가 진짜라 기기에 안 둔다** (`ScreeningHolder`).
                val screenings = rememberScreeningHolder(freshToken)

                // 뽑아 놓은 카드. **여기서 들고 있는다** — 도감·홈·뽑기 셋이 보고,
                // 화면이 바뀌어도 안 죽어야 한다 (`outside`, `homeTab` 과 같은 이유).
                //
                // **`freshToken` 뒤에 둔다.** 지우기가 서버에도 알려야 하는데, 코틀린은
                // 앞서 선언된 지역 변수만 잡는다 — 위에 두면 컴파일이 안 된다.
                val cards = remember { CardHolder(cardStore, freshToken, MissLog(context)) }

                // 카드를 서버와 맞추는 자리. **claimOrphans 뒤에 돈다** — 순서가
                // 뒤집히면 방금 로그인한 사람의 둘러보기 카드가 안 올라간다.
                val cardSync = remember { CardSyncRunner(cardStore, app.cardFiles, freshToken) }
                LaunchedEffect(session?.appUserId, pets.primary?.id) {
                    val petId = pets.primary?.id.takeIf { session != null }
                    chatHistory.selectPet(petId)
                    chatSummaries.selectPet(petId)
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
                // 강아지가 있어야 하는 기능을 눌렀을 때 뜨는 문. null 이면 안 뜬다.
                // **한 벌만 둔다** — 자리마다 만들면 문구가 갈린다 (`PetGate.kt`).
                var petNeed by remember { mutableStateOf<PetNeed?>(null) }
                /** 서버가 지어 준 사람 이름. null 이면 아직 못 받았거나 옛 서버다. */
                var nickname by remember { mutableStateOf<String?>(null) }
                var nicknameBusy by remember { mutableStateOf(false) }
                var nicknameError by remember { mutableStateOf<String?>(null) }
                /** 「마이」의 "고치기" 로 들어왔나. 인사말을 건너뛸지 정한다. */
                var editingNickname by remember { mutableStateOf(false) }
                // 개발자 패널의 "빈 방으로 보기". **디버그에서 이 상태를 볼 유일한 길이다** —
                // 여기서는 카카오 로그인이 안 돼서 `로그인함 + 강아지 0마리` 에 닿을 수가
                // 없고, 둘러보기는 로그인 전이라 데모가 선다. 릴리스에서는 패널이 빈
                // 껍데기라 늘 false 다. 저장하지 않는다 (패널 스위치와 같은 규칙).
                var devEmptyRoom by remember { mutableStateOf(false) }

                /**
                 * 로그인했는데 아직 강아지가 없나. 빈 방과 문이 **같은 값을 본다** —
                 * 갈라지면 "방은 비었는데 산책은 그냥 되는" 화면이 생긴다.
                 */
                val waitsForPet = devEmptyRoom || needsPet(session != null, shownPetsOrNull)

                /**
                 * 강아지가 필요한 자리로 가기 전. 없으면 [go] 대신 문을 띄운다.
                 *
                 * **부르는 자리를 한 군데로 모은 것이 요점이다.** 화면을 옮기는 줄이
                 * 여기저기 있어서, 자리마다 조건을 적으면 새 화면이 생길 때 빠뜨린다.
                 */
                fun askPetThen(need: PetNeed, go: () -> Unit) {
                    if (waitsForPet) petNeed = need else go()
                }
                // 지우기는 두 군데서 부른다 — 목록의 삭제와 배웅한 아이의 자리.
                // **서버가 먼저다.** 실패했는데 기기에서만 지우면 그 아이의 산책이
                // 다음 동기화 때 되돌아온다.
                val removePet: (Pet) -> Unit = { pet ->
                    scope.launch {
                        val token = freshToken() ?: return@launch
                        if (pets.remove(token, pet.id)) {
                            // 사진도 같이 지운다. 남으면 다음에 같은 id 를 받은 아이에게
                            // 남의 얼굴이 붙는다.
                            petPhotos.clear(pet.id, freshToken())
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
                // **로그인 여부와 무관하게 한 번 읽는다.**
                //
                // 예전에는 읽는 자리가 아래 `LaunchedEffect(session)` 의 토큰 뒤에만
                // 있어서, 둘러보기로 뽑은 카드가 앱을 껐다 켜면 도감에서 사라졌다 —
                // DB 에는 그대로 있는데 목록에 안 올라왔다.
                //
                // **남의 카드가 새지 않는다.** `CardDao.forUser` 가
                // `appUserId IS NULL OR appUserId = :id` 라 주인 없는 카드와 내 카드만
                // 준다 (`CardRows.appUserId` 주석의 설계다). 로그인 뒤에는 아래에서
                // 도장을 찍고 다시 읽으므로 이 줄이 그 흐름을 앞지르지 않는다.
                LaunchedEffect(Unit) { cards.load(session?.appUserId) }

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
                    AuthApi.me(token).onSuccess { roomName = it.roomName; nickname = it.nickname }
                    // **확인 화면에 잡아 두지 않는다.** 옛 서버라 칸이 없거나 `me` 가
                    // 실패하면 보여 줄 이름이 없다. 그때는 그냥 방으로 보낸다 —
                    // 이름은 다음 로그인에 서버가 채운다.
                    if (screen == Screen.Nickname && nickname == null) screen = Screen.Home
                    // **강아지가 없어도 여기서 끌고 가지 않는다.** 예전에는 로그인하자마자
                    // 등록 화면으로 보냈는데, 방을 보기도 전에 정보를 채우게 만드는 자리라
                    // 거기서 이탈했다. 빈 방으로 들여보내고([EmptyRoomInvite]), 강아지가
                    // 있어야 하는 기능을 누를 때 청한다([PetNeed]).
                    // 로그인 직후. 끝났지만 전달되지 않은 산책을 durable 작업으로 넘기고,
                    // 새 폰이면 서버의 지난 산책도 되찾는다.
                    walkRuntime.delivery.enqueuePending()
                    walkRuntime.sync.syncOnce(token)

                    // 프로필 사진도 여기서 맞춘다. **새 폰이면 여기서 받아 오고**,
                    // 서버가 생기기 전부터 이 폰에 있던 사진은 여기서 올라간다.
                    // **실패해도 조용하다** — 저장소가 아직 안 켜졌으면 저쪽이 503 인데,
                    // 그걸 띄우면 사진을 안 쓰는 사람에게 로그인마다 팝업이 뜬다.
                    petPhotos.sync(pets.pets.orEmpty(), token)

                    // 둘러보기로 뽑아 둔 카드에 도장을 찍고 목록을 받는다.
                    // 남의 카드는 안 건드린다 (`CardDao.claimOrphans`).
                    session?.appUserId?.let { cards.claimOrphans(it) }
                    // 서버와 맞춘다. **새 폰이면 여기서 카드가 되돌아오고**, 이 폰에만
                    // 있던 카드는 여기서 올라간다. **실패해도 조용하다** — 도감은
                    // 기기 것만으로도 온전히 돈다.
                    cardSync.syncOnce(session?.appUserId)
                    cards.load(session?.appUserId)
                }

                // **로딩을 떠나는 곳은 여기 하나다.** 갈림길 판정은 순수 함수로 빼서
                // 테스트가 잠근다 — 서버가 죽었을 때 갇히는 것이 제일 무서운 회귀다.
                //
                // **적어도 [MIN_LOADING_MS] 는 보여 주고 나간다.** 목록이 빨리 오는 날에는
                // 이 화면이 두어 프레임만 스쳐서, 화면이 바뀐 것이 아니라 끊긴 것으로
                // 읽혔다. 기다리는 길이는 **로딩이 뜬 시각에서** 잰다 — 이 블록은 목록이
                // 바뀔 때마다 다시 도는데, 그때마다 새로 700 을 세면 목록이 여러 번
                // 갱신되는 날에 몇 초씩 잡혀 있는다.
                LaunchedEffect(screen, pets.pets, pets.error) {
                    if (screen != Screen.Loading) return@LaunchedEffect
                    val next = when (startupTarget(pets.pets, pets.error)) {
                        StartupTarget.Wait -> return@LaunchedEffect
                        StartupTarget.Home -> Screen.Home
                    }
                    delay(loadingHoldMs(loadingSince, SystemClock.elapsedRealtime()))
                    screen = next
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
                                        // 이름을 한 번 보여 준다. 아직 못 받았으므로
                                        // 화면이 잠깐 비는데, 위 `me` 갈래가 곧 채우거나
                                        // 홈으로 넘긴다.
                                        editingNickname = false
                                        screen = Screen.Nickname
                                    }
                                    .onFailure { e ->
                                        // 사용자가 취소한 것은 오류가 아니다.
                                        error = if (e is CancelledByUser) null else e.message
                                    }
                            }
                        },
                        onSkip = { screen = Screen.Home },
                    )

                    Screen.Nickname -> nickname?.let { issued ->
                        NicknameScreen(
                            issued = issued,
                            onStart = { editingNickname = false; screen = Screen.Home },
                            onSave = { picked ->
                                scope.launch {
                                    nicknameBusy = true
                                    nicknameError = null
                                    val token = freshToken()
                                    if (token == null) {
                                        nicknameBusy = false
                                        nicknameError = "다시 로그인해 주세요."
                                        return@launch
                                    }
                                    AuthApi.setNickname(token, picked)
                                        .onSuccess {
                                            nickname = it.nickname
                                            editingNickname = false
                                            screen = Screen.Home
                                        }
                                        .onFailure { e ->
                                            // **미리 물어봤을 때는 비어 있었을 수 있다.**
                                            // 그래서 "쓰고 있어요" 가 아니라 "방금
                                            // 가져갔어요" 로 말한다.
                                            nicknameError = if (e is NicknameTakenException) {
                                                "방금 다른 분이 가져갔어요. 다른 이름을 골라 주세요."
                                            } else {
                                                e.message
                                            }
                                        }
                                    nicknameBusy = false
                                }
                            },
                            busy = nicknameBusy,
                            error = nicknameError,
                            startEditing = editingNickname,
                            ask = { value ->
                                val token = freshToken()
                                if (token == null) {
                                    Result.failure(IllegalStateException("토큰 없음"))
                                } else {
                                    AuthApi.nicknameAvailable(token, value)
                                }
                            },
                        )
                    }

                    Screen.Onboarding -> PetFormScreen(
                        initial = editing,
                        busy = pets.busy,
                        error = pets.error,
                        // **첫 등록에도 취소가 있다.** 예전에는 강아지가 없으면 이 손잡이를
                        // 없앴다 — "강아지 없이 갈 곳이 없다" 는 이유였는데, 이제 빈 방이
                        // 갈 곳이다. 빠져나갈 수 없는 화면이 첫 진입 이탈의 큰 몫이었다.
                        onCancel = { pets.clearError(); editing = null; screen = Screen.Home },
                        // 고치기로 들어왔으면 이미 올려 둔 사진을 보여 준다.
                        photo = editing?.let { petPhotos[it.id] },
                        onClearPhoto = editing?.let { pet ->
                            { scope.launch { petPhotos.clear(pet.id, freshToken()) } }
                        },
                        onSubmit = { draft, photo ->
                            scope.launch {
                                val token = freshToken() ?: return@launch
                                val target = editing
                                // 새로 등록하면 id 를 서버가 만든다. 목록을 다시 받은
                                // 뒤에 **늘어난 하나**를 찾아야 사진을 걸 자리를 안다.
                                val before = pets.pets.orEmpty().map { it.id }.toSet()
                                val ok = if (target == null) {
                                    pets.add(token, draft)
                                } else {
                                    pets.edit(token, target.id, draft)
                                }
                                if (ok) {
                                    if (photo != null) {
                                        val id = photoTargetId(
                                            editingId = target?.id,
                                            before = before,
                                            after = pets.pets.orEmpty().map { it.id },
                                        )
                                        if (id != null) petPhotos.set(id, photo, freshToken())
                                    }
                                    editing = null
                                    screen = Screen.Home
                                }
                            }
                        },
                    )

                    // 배웅과 사진 고르기는 **마이 위에 덮인다.** 화면을 늘리지 않는 것은
                    // 확대 뷰나 뽑기와 같은 결이고, 마이에서 들어와 마이로 돌아와야 하기
                    // 때문이다.
                    Screen.Home -> if (devPhotoPicking) {
                        PetPhotoPicker { made ->
                            devPhotoPicking = false
                            if (made != null) devPhoto = made.asImageBitmap()
                        }
                    } else if (photoFor != null) {
                        val pet = photoFor!!
                        PetPhotoPicker { made ->
                            photoFor = null
                            if (made != null) {
                                scope.launch { petPhotos.set(pet.id, made, freshToken()) }
                            }
                        }
                    } else if (farewell != null) {
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
                            face = { PetAvatar(petPhotos[pet.id], pet.breedArt, 120.dp) },
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
                        onOpenChat = { askPetThen(PetNeed.Chat) { screen = Screen.Chat } },
                        storageContent = { storageModifier ->
                            ChatSummaryRoute(
                                petId = pets.primary?.id.takeIf { session != null },
                                historyState = chatHistoryState,
                                coordinator = chatSummaries,
                                careCoordinator = careLog,
                                accessTokenProvider = freshToken,
                                onOpenSource = { sessionId ->
                                    scope.launch {
                                        val token = freshToken() ?: return@launch
                                        if (chatHistory.openSession(token, sessionId)) screen = Screen.Chat
                                    }
                                },
                                onOpenCitation = { citation ->
                                    citation.url?.let { url ->
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                },
                                modifier = storageModifier,
                            )
                        },
                        onOpenPlaces = { screen = Screen.Places },
                        onOpenWalk = { askPetThen(PetNeed.Walk) { screen = Screen.Walk } },
                        onOpenWalkHistory = { screen = Screen.WalkHistory },
                        todayWalks = todayWalks,
                        gameContent = {
                            com.daengs.app.ui.home.HomeGameRoute(
                                repository = app.activityRepository,
                                ownerId = session?.appUserId,
                                petId = pets.primary?.id,
                                petName = pets.primary?.name,
                                onOpenGame = { askPetThen(PetNeed.Walk) { screen = Screen.Walk } },
                            )
                        },
                        signedIn = session != null,
                        nickname = nickname,
                        // 「마이」의 "고치기". 이름 확인 화면과 **같은 칸**을 띄운다 —
                        // 두 벌이 되면 문구와 기다리는 시간이 갈린다.
                        onEditNickname = nickname?.let {
                            { editingNickname = true; screen = Screen.Nickname }
                        },
                        waitsForPet = waitsForPet,
                        onToggleEmptyRoom = { devEmptyRoom = !devEmptyRoom },
                        // 둘러보기로 들어온 사람이 다시 로그인할 길. 랜딩으로
                        // 되돌리면 기존 카카오 경로를 그대로 쓴다.
                        onSignIn = { screen = Screen.Landing },
                        tab = homeTab,
                        onSelectTab = { homeTab = it },
                        myOpen = myOpen,
                        weatherOpen = weatherOpen,
                        drawnCards = cards.cards,
                        // **도감 보기는 안 막는다** (`onOpenDex`). 뽑기만 막는다 —
                        // 이미 뽑아 둔 카드를 못 보게 하면 그게 더 이상하다.
                        onOpenDraw = {
                            askPetThen(PetNeed.Card) {
                                dexOpensDraw = true
                                screen = Screen.Dex
                            }
                        },
                        onToggleWeather = { weatherOpen = !weatherOpen },
                        onOpenMy = { myOpen = true },
                        onCloseMy = { myOpen = false },
                        outside = outside,
                        pets = shownPets,
                        photoOf = { petPhotos[it] },
                        onEditPhoto = { pets.primary?.let { pet -> photoFor = pet } },
                        hiddenRoomPetIds = hiddenRoomPetIds,
                        onToggleRoomPet = { pet ->
                            hiddenRoomPetIds = if (pet.id in hiddenRoomPetIds) {
                                hiddenRoomPetIds - pet.id
                            } else {
                                hiddenRoomPetIds + pet.id
                            }
                            roomStore.saveHiddenPetIds(hiddenRoomPetIds)
                        },
                        devPhoto = devPhoto,
                        onPickDevPhoto = { devPhotoPicking = true },
                        onClearDevPhoto = { devPhoto = null },
                        devPetCount = devPetCount,
                        onPickDevPets = { devPetCount = it },
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
                                        // 탈퇴 요청 시작 때의 계정을 고정한다. 토큰을 지운 뒤 owner()는 빈 값이다.
                                        walkController.stop()
                                        app.sessionProvider.clear()
                                        walkRuntime.history.forgetOwner(old.appUserId)
                                        // 방은 서버에 사본이 없어 이 기기에서도 지운다.
                                        roomStore.clear()
                                        frameCardId = null
                                        pets.forget()
                                        todayWalks = walkRuntime.history.todayTotals()
                                        // 뽑은 카드도 이 기기에만 있다. 서버에 사본이
                                        // 없으므로 여기서 안 지우면 다음에 로그인한
                                        // 사람이 남의 도감을 물려받는다.
                                        cards.forgetEverything()
                                        // 우리 아이의 진짜 사진이다. 안 지우면 다음에
                                        // 이 폰으로 로그인한 사람이 물려받는다.
                                        petPhotos.forgetEverything()
                                        // 다음 사람이 남의 피부 사진을 보면 안 된다.
                                        screenings.forget()
                                        // 방 구성도 이 기기의 것이다. `roomStore.clear()`
                                        // 가 파일을 비우므로 화면이 든 값도 같이 비운다.
                                        hiddenRoomPetIds = emptySet()
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
                            walkController.stop()
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
                        // 말풍선 얼굴은 안 바뀐다. 보행 촬영 화면에서만 쓴다.
                        avatarPhoto = pets.primary?.let { petPhotos[it.id] },
                        dogId = pets.primary?.id.takeIf { session != null },
                        accessTokenProvider = freshToken,
                        historyCoordinator = chatHistory,
                        // 로그인해야 기록이 있다. 안 됐으면 길 자체를 안 보여 준다.
                        onOpenScreeningHistory = { screen = Screen.ScreeningHistory }
                            .takeIf { session != null },
                    )

                    Screen.ScreeningHistory -> ScreeningHistoryScreen(
                        holder = screenings,
                        onBack = { screen = Screen.Chat },
                        // 대표 아이의 것만 본다. 없으면 전부 — 아이를 아직 등록 안
                        // 했어도 진단은 할 수 있어서 그 기록이 남아 있다.
                        petId = pets.primary?.id,
                    )

                    Screen.Places -> PlacesRoute(
                        onBack = { screen = Screen.Home },
                        primaryPet = pets.primary,
                        // **빌드에 따라 다른 화면을 띄우지 않는다.** 이 값이
                        // `BuildConfig.DEBUG` 이던 동안, 고쳐 온 내 주변(업종 대·소분류,
                        // 조건 검색, 결과 패널)이 디버그 APK 에만 나오고 스토어판은
                        // v1.0.2 의 옛 화면 그대로였다. "고쳤다" 고 한 것이 스토어에서는
                        // 안 보인다.
                        //
                        // 두 화면은 같은 PlacesViewModel·state 를 쓰므로 이 한 줄이 곧
                        // 릴리스 UI 다. 옛 PlacesScreen 은 이제 아무도 안 부르지만, 새
                        // 화면이 릴리스로 한 판 나가는 것을 보기 전에는 지우지 않는다.
                        useConnectedSearch = true,
                        // 지도의 내 위치도 올린 사진을 따른다. 산책 지도와 같은
                        // 목록(`pets.primary`)을 본다 — `shownPets` 를 보면 개발자
                        // 패널의 가짜 아이를 켰을 때 사진만 사라진다.
                        avatarPhoto = pets.primary?.let { petPhotos[it.id] }?.asAndroidBitmap(),
                        profileOwnerId = session?.appUserId,
                        profilePets = pets.pets,
                        profilesBusy = pets.busy,
                        profilesError = pets.error,
                        onRefreshProfiles = { scope.launch { freshToken()?.let { pets.refresh(it) } } },
                    )

                    Screen.WalkHistory -> walkHistoryState.SaveableStateProvider("walk-history") { WalkHistoryScreen(
                        history = walkRuntime.history,
                        // 목록을 열 때 한 번 더. 걷고 나서 지하철에 들어갔던 기록이
                        // 여기서 올라가고, 다른 기기에서 한 산책이 여기서 내려온다.
                        onSync = { scope.launch { walkRuntime.sync.syncOnce(freshToken()) } },
                        onBack = { screen = Screen.Home },
                        pets = pets.pets.orEmpty(),
                        photoOf = { petPhotos[it] },
                        onOpen = { id ->
                            openedWalkId = id
                            screen = Screen.WalkDetail
                        },
                    )

                    }

                    Screen.WalkDetail -> openedWalkId?.let { id ->
                        com.daengs.app.ui.walk.WalkDiaryMapScreen(
                            sessionId = id,
                            history = walkRuntime.history,
                            onBack = { screen = Screen.WalkHistory },
                            pets = pets.pets.orEmpty(),
                        )
                    }

                    Screen.Walk -> WalkRoute(
                        onBack = { screen = Screen.Home },
                        // 산책 전 `일기` 는 홈의 `지난 산책` 과 같은 화면으로 간다.
                        onOpenDiaryList = { screen = Screen.WalkHistory },
                        onRequestOrientation = { walkOrientation = it },
                        walkController = walkController,
                        history = walkRuntime.history,
                        avatarBreed = artBreed,
                        // 지도의 내 위치도 올린 사진을 따른다.
                        //
                        // **고르는 줄과 같은 목록을 본다.** 아래 `pets` 가 서버 목록인데
                        // 여기만 `shownPets`(개발자 패널의 가짜 아이가 이기는 목록)를
                        // 보고 있었다. 그래서 디버그에서 가짜 강아지를 켜면, 고르는 줄엔
                        // 진짜 아이들이 그대로인데 **지도 위 사진만 사라졌다** — 가짜 아이의
                        // id 는 `petPhotos` 에 없기 때문이다. 챗봇 화면은 원래
                        // `pets.primary` 를 쓰고 있어서 거기에 맞춘다.
                        avatarPhoto = pets.primary?.let { petPhotos[it.id] }?.asAndroidBitmap(),
                        pets = pets.pets.orEmpty(),
                        photoOf = { petPhotos[it] },
                        outside = outside,
                    )

                    Screen.Dex -> CardDexScreen(
                        onClose = { screen = Screen.Home },
                        onImmersiveChange = {
                            immersiveOpen = it
                            if (!it) immersiveLandscape = false
                        },
                        immersiveLandscape = immersiveLandscape,
                        onToggleImmersiveOrientation = { immersiveLandscape = !immersiveLandscape },
                        // **도감 보기는 열어 두고 뽑기만 막는다.** 이미 뽑아 둔 카드를
                        // 못 보게 하면 그게 더 이상하다.
                        onDrawBlocked = if (waitsForPet) {
                            { petNeed = PetNeed.Card }
                        } else {
                            null
                        },
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
                                // 꽝도 하루 한 번을 쓴다. 안 적으면 그 판이 없던 일이 된다.
                                onMiss = { cards.recordMiss() },
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

                // **화면 밖에 둔다.** 문은 홈에서만 뜨는 것이 아니라(챗봇 카드 · 방문 ·
                // 뽑기) 어느 화면에서 눌렀든 그 위에 떠야 한다. `when` 안에 넣으면
                // 자리마다 한 벌씩 생긴다.
                petNeed?.let { need ->
                    PetNeededDialog(
                        need = need,
                        onAdd = {
                            petNeed = null
                            editing = null
                            screen = Screen.Onboarding
                        },
                        onDismiss = { petNeed = null },
                    )
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
