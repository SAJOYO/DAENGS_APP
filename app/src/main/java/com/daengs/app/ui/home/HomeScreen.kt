package com.daengs.app.ui.home

import androidx.compose.foundation.clickable
import com.daengs.app.ui.PetAvatar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.BuildConfig
import com.daengs.app.miniroom.DogTapTarget
import com.daengs.app.miniroom.MiniRoomCanvas
import com.daengs.app.miniroom.MiniRoomState
import com.daengs.app.miniroom.RoomDefaults
import com.daengs.app.miniroom.rememberDogHerd
import com.daengs.app.miniroom.RoomGeometry
import com.daengs.app.miniroom.OutsideSnapshot
import com.daengs.app.miniroom.OutsideView
import com.daengs.app.miniroom.RoomTheme
import androidx.compose.ui.graphics.ImageBitmap
import com.daengs.app.miniroom.rememberRoomStore
import com.daengs.app.miniroom.art.ItemCatalog
import com.daengs.app.miniroom.art.footprintFacing
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.miniroom.art.rememberItemCatalog
import com.daengs.app.miniroom.rememberMiniRoomState
import com.daengs.app.dogcard.DrawnCard
import com.daengs.app.pet.Pet
import com.daengs.app.walk.WalkDayTotals
import kotlinx.coroutines.delay
import com.daengs.app.ui.dogcard.CardTemplate
import com.daengs.app.ui.my.MyScreen
import com.daengs.app.ui.storage.StorageComingSoon
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.ui.theme.DaengsTheme

/**
 * 챗봇 카드와 인벤토리 패널이 함께 쓰는 슬롯 높이.
 *
 * 둘의 높이가 다르면 방이 `weight(1f)` 로 남는 높이를 가져가기 때문에,
 * 인벤토리를 열고 닫을 때마다 방 크기가 그 차이만큼 튄다.
 * 높이 제약은 컴포넌트 안이 아니라 이 배치 지점에 둔다 — 그래야 두 카드를
 * 다른 화면에서 재사용할 때 자기 크기대로 쓸 수 있다.
 */
internal val CardSlotHeight = 146.dp

/**
 * 강아지 목록이 늦을 때 "불러오는 중" 을 띄우기까지 기다리는 시간.
 *
 * **바로 띄우지 않는다.** 빠른 망에서는 목록이 눈 깜짝할 사이에 오는데, 그때 표시가
 * 떴다 사라지면 그것 자체가 깜빡임이다 — 데모 강아지 네 마리가 사라지던 것을 고쳐
 * 놓고 같은 종류의 깜빡임을 새로 만드는 셈이 된다.
 */
private const val DOGS_LOADING_DELAY_MS = 600L

/**
 * 이름표가 앉는 자리 — **방 그림 기준 백분율**이다.
 *
 * 울타리가 방 앞쪽 오른편에 서 있으므로 그 위에 얹히도록 잡았다.
 * 바닥 앞 꼭짓점(FloorQuad.front, 43%)보다 오른쪽이라 앞모서리를 가리지 않는다.
 *
 * 화면 크기가 바뀌어도 방 그림 안에서의 자리는 그대로다.
 */
private object NamePlateSpec {
    /**
     * 이름표 아래 끝 (방 그림 높이 %).
     *
     * 가로는 **방 그림의 정중앙**이라 상수가 없다 — [Alignment.BottomCenter] 로
     * 맞추면 글자가 길어져도 가운데가 유지된다. 백분율로 잡으면 이름이 길어질 때
     * 한쪽으로 밀린다.
     *
     * 세로만 여기서 정한다. 100 이면 방 아래 끝에 딱 걸리고, 그보다 크면 방 밖으로
     * 내려간다. 102 는 울타리 아래에 살짝 걸치는 자리다.
     */
    const val BOTTOM = 102f
}

/**
 * 홈 화면.
 *
 * @param frameTimeMs null 이 아니면 애니메이션을 그 시각에 고정한다 (@Preview 용).
 * @param dateLabel TODAY 카드에 표시할 날짜. 기본은 기기의 오늘 날짜.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    frameTimeMs: Long? = null,
    /** 저장소 탭의 실제 내용. null 은 독립 Preview 와 미연결 호출이 쓰는 안내 화면이다. */
    storageContent: (@Composable (Modifier) -> Unit)? = null,
    /**
     * 지금 켜져 있는 하단 탭. **밖에서 들고 있는다.**
     *
     * 안에서 `remember` 로 들고 있으면 강아지를 추가하러 나갔다 오는 순간 사라진다 —
     * `when (screen)` 이 이 화면을 컴포지션에서 들어내기 때문이다. 그래서 마이 탭에서
     * 시작한 일이 끝나면 늘 홈으로 튕겼다.
     */
    tab: BottomTab = BottomTab.Home,
    onSelectTab: (BottomTab) -> Unit = {},
    dateLabel: String = HomeDemoData.todayLabel(),
    /** 방 벽의 액자를 눌렀을 때. 도감으로 들어간다. */
    onOpenDex: (() -> Unit)? = null,
    onOpenChat: (() -> Unit)? = null,
    /** 내 주변 탭을 눌렀을 때. 병원·카페·펫샵을 지도에서 찾는다. */
    onOpenPlaces: (() -> Unit)? = null,
    /** 방문을 열었을 때. 산책 화면으로 나간다 — **탭이 아니라 문이 산책의 입구다.** */
    onOpenWalk: (() -> Unit)? = null,
    /** 산책 요약 카드의 "지난 산책". 기록 목록으로 나간다. */
    onOpenWalkHistory: (() -> Unit)? = null,
    /** 오늘 걸은 것. null 이면 아직 못 읽은 것이라 카드가 `-` 로 둔다. */
    todayWalks: WalkDayTotals? = null,
    gameContent: (@Composable () -> Unit)? = null,
    /**
     * 창밖·문밖의 지금.
     *
     * **[MainActivity] 가 들고 내려보낸다.** 여기서 [rememberOutsideView] 를 부르면
     * 홈이 컴포지션에서 빠질 때 같이 죽어서, 도감이나 산책을 갔다 오면 폴백(맑은 낮,
     * 기온 없음)부터 다시 시작한다 — 카드가 눈앞에서 한 번 바뀐다. `homeTab` 을
     * 위로 올린 것과 같은 이유다.
     */
    outside: OutsideSnapshot = OutsideSnapshot.DEFAULT,
    /**
     * 마이 화면이 열려 있나. **탭이 아니라 상단바의 프로필 사진 버튼으로 연다.**
     *
     * 하단 저장소 탭과 갈라져 있다 — 예전에는 둘이 같은 자리였는데, 탭 이름이
     * "저장소" 가 되면서 하는 일과 어긋났다.
     *
     * 값은 [MainActivity] 가 들고 있다. 강아지를 추가하러 나갔다 오면 마이로
     * 돌아와야 하는데, 여기서 들면 화면이 바뀔 때 같이 죽는다.
     */
    myOpen: Boolean = false,
    /** 날씨 카드가 펴져 있나. 접으면 방을 비켜 준다 */
    weatherOpen: Boolean = true,
    onToggleWeather: (() -> Unit)? = null,
    /** 내가 뽑은 카드. 턴테이블의 곡 목록이 여기서 나온다 */
    drawnCards: List<DrawnCard> = emptyList(),
    /** 도감을 열고 곧장 뽑기를 띄운다. 턴테이블의 "뽑으러 가기" 가 쓴다 */
    onOpenDraw: (() -> Unit)? = null,
    onOpenMy: (() -> Unit)? = null,
    onCloseMy: (() -> Unit)? = null,
    /** 카카오로 로그인한 상태인가. 개발자 패널이 로그아웃을 띄울지 정한다. */
    signedIn: Boolean = false,
    /** 사람 이름. 「마이」 프로필 머리에 걸린다. null 이면 그 줄이 빠진다 */
    nickname: String? = null,
    /** OCR 학습 이용 동의 (#258). 마이 화면까지 그대로 흘려보낸다. */
    ocrConsent: Boolean = false,
    onOcrConsentChange: ((Boolean) -> Unit)? = null,
    /** 이름을 고치러 간다. null 이면 「마이」에 그 자리가 안 뜬다 */
    onEditNickname: (() -> Unit)? = null,
    /**
     * 로그인했는데 **아직 강아지가 없나.** 그때 방이 비고 「강아지 데려오기」가 뜬다.
     *
     * **여기서 계산하지 않고 받는다.** 이 화면의 [pets] 는 개발자 패널이 넣어 본 가짜
     * 아이까지 섞인 **null 이 아닌** 목록이라, "아직 못 받아 왔다" 와 "한 마리도 없다"
     * 가 여기서는 갈리지 않는다. 그 둘을 아는 것은 부르는 쪽이다
     * ([com.daengs.app.ui.home.needsPet]).
     */
    waitsForPet: Boolean = false,
    /** 개발자 패널의 "빈 방으로 보기". 릴리스에서는 패널이 빈 껍데기라 안 쓰인다 */
    onToggleEmptyRoom: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    /** 카드 실험실. 개발자 패널에서만 열린다. */
    onOpenCutoutLab: (() -> Unit)? = null,
    /**
     * 야채를 지정해 카드를 만든다. 개발자 패널에서만 불린다 —
     * 릴리스에서는 패널이 빈 껍데기라 이 손잡이가 쓰이지 않는다.
     */
    onMakeCard: ((CardTemplate) -> Unit)? = null,
    /**
     * 빌려 쓸 얼굴이 있나. 없으면 개발자 패널이 카드 만들기를 잠근다 —
     * 얼굴 없는 카드는 무대·창틀에 우리 것이 안 얹혀서 만들어 봐야 소용이 없다.
     */
    canMakeCard: Boolean = false,
    /**
     * 개발자 패널이 고른 대표 견종. **부르는 쪽이 든다** — 챗봇 화면도 같은 값을
     * 봐야 하는데, 홈이 들고 있으면 홈 밖으로 안 나간다. null 이면 진짜 대표를 따른다.
     */
    devBreed: DogBreed? = null,
    onPickDevBreed: ((DogBreed) -> Unit)? = null,
    /**
     * 개발자 패널로 올려 본 프로필 사진.
     *
     * **강아지 기록 없이 사진을 보는 유일한 길이다** — 진짜 사진은 등록한 아이에게
     * 딸리는데(`pet-photos/<id>.jpg`), 둘러보기에는 아이가 없다. 견종을 갈아끼우는
     * 줄이 있는 것과 같은 이유로 둔다. 저장하지 않는다.
     */
    devPhoto: ImageBitmap? = null,
    onPickDevPhoto: (() -> Unit)? = null,
    onClearDevPhoto: (() -> Unit)? = null,
    /** 개발자 패널이 넣어 본 가짜 강아지 마릿수. 0 이면 서버가 준 목록 그대로다 */
    devPetCount: Int = 0,
    onPickDevPets: ((Int) -> Unit)? = null,
    /** 둘러보기 상태에서 로그인하러 갈 때. 랜딩으로 되돌린다. */
    onSignIn: (() -> Unit)? = null,
    /**
     * 방 둘러보기(튜토리얼)를 띄울까.
     *
     * **부르는 쪽이 든다.** 봤는지 여부는 기기에 남는 값이라 저장을 아는 쪽이 정해야
     * 하고, "다시 보기" 도 홈 밖(마이)에서 켠다.
     */
    tourOpen: Boolean = false,
    /** 마이의 "다시 보기". null 이면 그 줄이 안 뜬다. */
    onReplayTour: (() -> Unit)? = null,
    /** 다 봤거나 건너뛰었을 때. 부르는 쪽이 본 적 있음으로 남긴다. */
    onTourClose: (() -> Unit)? = null,
    /** 내 강아지. null 이면 아직 못 받아 온 것이다. */
    pets: List<Pet>? = null,
    /**
     * 그 아이가 올린 프로필 사진. 없으면 견종 그림이다.
     *
     * **챗봇 얼굴에는 안 쓴다** — 거기는 학사모 쓴 "똑똑이" 자리다.
     */
    photoOf: (String) -> ImageBitmap? = { null },
    /** 대표 아이의 사진을 바꾸러 간다. null 이면 마이에서 그 자리가 안 뜬다 */
    onEditPhoto: (() -> Unit)? = null,
    /** 방에서 뺀 아이들. 기본은 비어 있고, 그러면 등록한 아이가 다 방에 선다 */
    hiddenRoomPetIds: Set<String> = emptySet(),
    /** 방에 두기/빼기를 눌렀다. null 이면 마이에서 그 줄이 안 뜬다 */
    onToggleRoomPet: ((Pet) -> Unit)? = null,
    canAddMore: Boolean = false,
    onAddPet: (() -> Unit)? = null,
    onEditPet: ((Pet) -> Unit)? = null,
    onPickPrimary: ((Pet) -> Unit)? = null,
    /**
     * 방 앞 이름표. **null 이면 아직 안 정한 것**이고, 그때 대표 강아지 이름으로
     * 짓는다 ([defaultRoomLabel]).
     */
    roomName: String? = null,
    /**
     * 방 액자에 걸린 그림. null 이면 발자국이 걸린다.
     *
     * 어느 카드를 걸었는지는 도감에서 고르고 [MainActivity] 가 들고 있다 — 방과 도감이
     * 서로를 모르는 채로 만나는 자리가 거기 하나다.
     */
    framePicture: ImageBitmap? = null,
    /** 이름표를 정한다. null 을 주면 되돌린다. 로그인 전이면 null 이라 안 눌린다. */
    onRenameRoom: ((String?) -> Unit)? = null,
    renameBusy: Boolean = false,
    renameError: String? = null,
    onDismissRename: (() -> Unit)? = null,
    /** 지우기. **그 아이와만 나간 산책 기록도 같이 지워진다.** */
    onDeletePet: ((Pet) -> Unit)? = null,
    /** 아이를 배웅하는 자리로. 마이가 그 길을 연다 */
    onFarewell: ((Pet) -> Unit)? = null,
    /** 그 아이를 함께 돌보는 사람을 보러 간다. 대표·돌보미 모두 들어간다. */
    onOpenMembers: ((Pet) -> Unit)? = null,
    /** 받은 초대 링크를 붙여넣어 공동 보호자가 되러 간다. */
    onAcceptInvite: (() -> Unit)? = null,
    /** 여러 아이를 한 링크로 부르는 자리로. 마이 탭이 받는다. */
    onInvitePeople: (() -> Unit)? = null,
    /** 이미 배웅한 아이의 날짜 */
    farewellOf: (Pet) -> java.time.LocalDate? = { null },
    deletePetBusy: Boolean = false,
    deletePetError: String? = null,
    onDismissDeletePet: (() -> Unit)? = null,
    /** 회원 탈퇴. 상태는 [MainActivity] 가 들고 있다 (랜딩의 busy·error 와 같은 결). */
    onWithdraw: (() -> Unit)? = null,
    withdrawBusy: Boolean = false,
    withdrawError: String? = null,
    onDismissWithdraw: (() -> Unit)? = null,
) {
    // 탭에서 뒤로 누르면 앱을 나가는 게 아니라 홈으로 온다 (PlacesScreen 과 같은 결).
    BackHandler(enabled = tab != BottomTab.Home) { onSelectTab(BottomTab.Home) }
    // 마이는 탭이 아니라 프로필 버튼으로 여는 화면이라 **따로 닫아 준다.**
    // ⚠️ 위의 탭 핸들러보다 **나중에** 등록한다 — 컴포즈는 나중에 등록된 것이
    // 이기므로, 마이가 열려 있으면 탭이 무엇이든 마이가 먼저 닫힌다.
    BackHandler(enabled = myOpen) { onCloseMy?.invoke() }
    var inventoryOpen by rememberSaveable { mutableStateOf(false) }

    // 프로필 얼굴의 견종.
    //
    // **대표 강아지를 따라간다.** 상단바와 챗봇 카드가 이걸 쓰고, 대표는 마이 탭에서
    // 고른다 — 그게 "대표 강아지"라는 말의 뜻이다.
    //
    // **대표의 견종이 우리 그림에 없으면(믹스) null 이고, 그러면 발자국이 뜬다.**
    // 예전에는 데모 강아지 한 마리로 떨어졌는데, 그게 바로 "아무 얼굴이나 골라 보여
    // 주는" 것이었다 — 믹스를 키우는 사람은 상단바에서 남의 개를 봤다. 마이·산책·장소는
    // 처음부터 발자국을 세우고 있었고, 홈만 빠져 있었다.
    //
    // 개발자 패널이 바꾼 값은 그 위에 잠깐 덮어쓴다 — 세션 한정이고 저장하지 않는다.
    //
    // **그 값을 여기서 들지 않고 [devBreed] 로 받는다.** 챗봇 화면·장소·산책도 같은
    // "대표 견종"을 보는데, 여기서 들고 있으면 홈 밖으로 못 나가서 로그인해야만
    // 챗봇 얼굴을 확인할 수 있었다.
    val profileBreed = devBreed ?: pets?.firstOrNull { it.isPrimary }?.breedArt
    // 상단바에 걸 사진. **개발자 패널로 견종을 바꿔 보는 중이면 안 쓴다** — 그때는
    // 그 견종 그림을 보려는 것이지 내 아이 사진을 보려는 것이 아니다.
    val profilePhoto = devPhoto ?: if (devBreed != null) null else {
        pets?.firstOrNull { it.isPrimary }?.let { photoOf(it.id) }
    }

    // 방에 서는 강아지 = 등록한 강아지. 목록이 바뀌면 자리를 지킨 채 갈아끼운다.
    // **한 번 걸러서 둘 다 그 결과를 본다.** 명부와 배웅 자리는 차례가 같아야 해서,
    // 거르는 곳이 둘이 되면 배웅한 아이의 하트가 남의 아이 곁에 뜬다.
    val inRoom = roomPets(pets, hiddenRoomPetIds)
    val herd = rememberDogHerd(roomRoster(inRoom, waitsForPet), departedInRoom(inRoom))
    val store = rememberRoomStore()
    // 테마는 id 만 저장한다 — 원시값이라 화면 회전에도 그대로 남는다
    var themeId by rememberSaveable { mutableStateOf(store.loadThemeId() ?: RoomTheme.DEFAULT.id) }
    val roomTheme = RoomTheme.byId(themeId)

    // 저장된 배치가 있으면 그걸로 시작한다. 없거나 못 읽으면 기본 배치.
    // rememberSaveable 이 화면 회전을, 이쪽이 앱 재시작을 담당한다.
    val roomState = rememberMiniRoomState(
        // 저장본에는 붙박이가 없을 수 있다 (#15 이전에 깔린 폰).
        // withFixtures 가 매번 얹으므로 여기 한 줄이면 마이그레이션이 끝난다.
        initial = remember { RoomDefaults.withFixtures(store.loadItems() ?: RoomDefaults.STARTER_MOVABLES) },
    )

    // 배치가 바뀔 때마다 저장. 드래그는 놓을 때 한 번만 커밋되고 회전도 탭 한 번이라
    // 쓰기가 잦지 않다. apply() 는 비동기라 UI 를 막지도 않는다.
    LaunchedEffect(roomState, store) {
        snapshotFlow { roomState.items.toList() }
            .collect { store.saveItems(it) }
    }
    // 테마마다 소품 그림이 다르므로 카탈로그가 테마를 알아야 한다.
    val catalog = rememberItemCatalog(roomTheme)

    // 창밖·문밖. 실제 시각·날씨를 따르되 **개발자 패널이 이기게** 둔다 —
    // 밤·눈을 보려고 밤에 눈이 오길 기다릴 수는 없다.
    //
    // 값은 [outside] 파라미터로 온다. 여기서 만들지 않는 이유는 그 주석에 있다.
    // @Preview 는 결정적이어야 한다 — 기본값이 기기 시각을 안 보게 한 벌로 고정한다.
    val live = if (LocalInspectionMode.current) PREVIEW_OUTSIDE else outside
    var outsideOverride by remember { mutableStateOf<OutsideView?>(null) }
    val outside = outsideOverride ?: live.view
    // 오버라이드 중에는 기온을 **모르는 것으로 친다.** 33도인데 "밤 눈" 을 강제하면
    // 눈+더위라는 없는 칸이 생긴다. null 은 어차피 반드시 지원해야 하는 경로다.
    val temperatureC = if (outsideOverride == null) live.temperatureC else null
    // 개발자 패널이 덮었으면 그건 **고른 값**이라 아는 것으로 친다.
    val known = outsideOverride != null || live.known
    val words = remember(outside, temperatureC, known) {
        homeWeatherWords(outside, temperatureC, known)
    }

    // 방 둘러보기가 밝힐 자리. 그리는 쪽이 등록하고 겹이 읽는다.
    val tourSpots = remember { TourSpots() }
    // 다시 열 때마다 처음부터. `tourOpen` 이 키라 껐다 켜면 1단계로 돌아온다.
    var tourStep by remember(tourOpen) { mutableIntStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // **반쯤 접혀 있으면 힌지 자리를 알아 둔다.** 창은 화면 전체로 남으므로
    // (안드로이드는 접혀도 창을 안 줄여 준다) 여기서 두 절반을 가른다.
    // 접히지 않았으면 null 이고 아래 판정들이 창 높이를 그대로 쓴다.
    val flexTop = rememberFlexTopHeight(maxHeight)
    // **가로면 하단바를 왼쪽 세로 레일로 바꾼다.** 가로에서는 세로 공간이 411dp 뿐이라
    // 하단바가 설 자리가 없어서, 눕히면 바가 통째로 사라지고 다른 탭으로 갈 방법이
    // 없었다 (실기기에서 확인). 레일은 세로를 안 먹는다.
    //
    // 창 높이가 아니라 **세워진 절반의 높이**로 정한다. 반접기에서 창 높이로
    // 정하면 세로가 넉넉한 줄 알고 상단바를 펴는데, 그 상단바는 방이 설 자리인
    // 세워진 절반을 깎아먹는다.
    val uprightHeight = flexTop ?: maxHeight
    val rail = usesNavRail(maxWidth, uprightHeight)
    val compactTop = hidesTopBar(uprightHeight)
    // **창 전체를 쓴다.** 누운 절반도 화면이다 — 거기에 카드와 바가 간다.
    Row(Modifier.fillMaxSize()) {
    if (rail) {
        DaengsNavRail(
            selected = tab,
            onSelect = { picked ->
                if (myOpen) onCloseMy?.invoke()
                when (picked) {
                    BottomTab.Dex -> onOpenDex?.invoke()
                    BottomTab.Nearby -> onOpenPlaces?.invoke()
                    else -> onSelectTab(picked)
                }
            },
            // 가운데 버튼은 하단바와 같은 뜻이다 — 챗봇이다 (아래 `onCenter` 참고).
            onCenter = { onOpenChat?.invoke() },
            tourSpots = tourSpots,
            header = if (!compactTop) null else {
                {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onOpenMy?.invoke() }
                        .padding(2.dp),
                ) { PetAvatar(profilePhoto, profileBreed, 34.dp) }
                }
            },
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CreamBg,
        // 인셋은 Scaffold 에 맡기지 않고 각 자식이 직접 처리한다.
        // 방 배경은 상태바 아래까지 흘려보내고 싶기 때문이다.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // **세로가 짧으면 상단바를 안 그린다.** 로고가 두 줄이라 가로에서는 이것만
            // 으로 화면의 3분의 1을 먹었다. 폴더블 세로 펼침은 세로가 넉넉하므로
            // 레일을 쓰더라도 상단바는 그대로 둔다.
            if (compactTop) return@Scaffold
            Box(Modifier.background(CreamBg).statusBarsPadding()) {
                DaengsTopBar(
                    // 알림 화면이 아직 없다. 없는 데로 보내는 것보다 안 눌리는 게 낫다.
                    onBell = {},
                    onProfile = { onOpenMy?.invoke() },
                    avatar = profileBreed,
                    photo = profilePhoto,
                )
            }
        },
        bottomBar = {
            if (rail) return@Scaffold
            DaengsBottomBar(
                tourSpots = tourSpots,
                selected = tab,
                // **밀어서 여는 탭은 선택 상태를 안 남긴다.** 남기면 도감에서
                // 돌아왔을 때 방이 떠 있는데 바는 도감이 켜져 있다. 마이가 실제
                // 화면이 되기 전에는 눈에 안 띄던 것이다.
                onSelect = { tab ->
                    // **마이가 열려 있으면 먼저 닫는다.** 마이는 탭이 아니라 홈 위에
                    // 덮이는 화면인데 바는 그대로 보인다. 안 닫으면 `selected` 가
                    // 여전히 홈이라 바의 홈을 눌러도 아무 일이 안 일어나서,
                    // 뒤로가기 말고는 나올 길이 없다.
                    if (myOpen) onCloseMy?.invoke()
                    when (tab) {
                        BottomTab.Dex -> onOpenDex?.invoke()
                        BottomTab.Nearby -> onOpenPlaces?.invoke()
                        else -> onSelectTab(tab)
                    }
                },
                onCenter = { onOpenChat?.invoke() },
            )
        },
    ) { inner ->
        if (myOpen) {
            MyScreen(
                // 마이는 홈 위에 덮이는 화면이라, 다시 보기를 누르면 마이를 닫고
                // 방 위에서 둘러보기가 열려야 한다.
                onReplayTour = onReplayTour?.let { go -> { onCloseMy?.invoke(); go() } },
                breed = profileBreed,
                // **홈이 이미 고른 얼굴을 그대로 준다.** 마이가 다시 계산하면 상단바와
                // 마이가 다른 얼굴을 보여 준다 — `breed = profileBreed` 와 짝이다.
                profilePhoto = profilePhoto,
                photoOf = photoOf,
                hiddenRoomPetIds = hiddenRoomPetIds,
                onToggleRoomPet = onToggleRoomPet,
                canToggleRoomPet = { canHideFromRoom(pets, hiddenRoomPetIds, it.id) },
                onEditPhoto = onEditPhoto,
                nickname = nickname,
                onEditNickname = onEditNickname,
                ocrConsent = ocrConsent,
                onOcrConsentChange = onOcrConsentChange,
                pets = pets,
                canAddMore = canAddMore,
                onAddPet = { onAddPet?.invoke() },
                onEditPet = { onEditPet?.invoke(it) },
                onPickPrimary = { onPickPrimary?.invoke(it) },
                onDeletePet = { onDeletePet?.invoke(it) },
                onFarewell = onFarewell,
                onOpenMembers = onOpenMembers,
                onAcceptInvite = onAcceptInvite,
                onInvitePeople = onInvitePeople,
                farewellOf = farewellOf,
                deleteBusy = deletePetBusy,
                deleteError = deletePetError,
                onDismissDelete = { onDismissDeletePet?.invoke() },
                signedIn = signedIn,
                onSignIn = { onSignIn?.invoke() },
                onSignOut = { onSignOut?.invoke() },
                onWithdraw = { onWithdraw?.invoke() },
                withdrawBusy = withdrawBusy,
                withdrawError = withdrawError,
                onDismissWithdraw = { onDismissWithdraw?.invoke() },
                modifier = Modifier.padding(inner),
            )
            return@Scaffold
        }

        if (tab == BottomTab.Storage) {
            val storageModifier = Modifier.padding(inner)
            if (storageContent == null) StorageComingSoon(storageModifier) else storageContent(storageModifier)
            return@Scaffold
        }

        // 스크롤 없음 — 전부 한 화면에 들어간다.
        // 카드 두 장은 필요한 만큼만 쓰고, 남는 세로는 방이 전부 가져간다.
        // 방은 RoomGeometry.of(width, height) 로 받은 상자에 맞춰 스스로 줄어든다.
        //
        // **넓으면 두 칸이다** (폴더블 펼침·태블릿). 세로로 쌓으면 방이 가운데 작게
        // 뜨고 좌우가 텅 비는데, 나란히 두면 방은 커지고 카드는 제 폭을 찾는다.
        // 가로모드 이야기가 아니다 — 폴드는 **세로로 펼쳐도** 이 폭이 나온다.
        // 콘텐츠 상자가 창 위에서 시작하는 자리. 반접기에서 방을 힌지 선에
        // **딱 맞추는 데** 쓴다 — 상단바는 내용에 따라 크기가 달라져서 상수로
        // 계산할 수 없고, 재는 수밖에 없다. 한 프레임 늦게 오므로 0 으로 시작한다.
        var contentTop by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier
                .padding(inner)
                // **상단바를 접으면 상태바 몫을 질 사람이 없어진다.**
                //
                // 인셋을 Scaffold 에 안 맡기고 자식이 각자 처리하는 구조라
                // (위 `contentWindowInsets = WindowInsets(0)`), 그 몫은 상단바의
                // `statusBarsPadding()` 이 지고 있었다. [hidesTopBar] 로 상단바가
                // 접히는 순간 그게 통째로 사라져서, 플립 커버에서 TODAY 카드가
                // 시계 위로 올라탔다.
                .then(if (compactTop) Modifier.statusBarsPadding() else Modifier)
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val y = with(density) { coords.positionInWindow().y.toDp() }
                    if (y != contentTop) contentTop = y
                },
        ) {
            val wide = maxWidth >= WIDE_BREAKPOINT
            // 반접기에서 힌지 위에 방이 들어갈 높이. null 이면 나눌 만하지
            // 않다는 뜻이라 아래의 한 칸 갈래로 간다.
            val flexRoom = flexTop?.let { flexRoomHeight(it, contentTop) }

        val room: @Composable (Modifier) -> Unit = { roomModifier ->
            RoomSection(
                tourSpots = tourSpots,
                framePicture = framePicture,
                weatherOpen = weatherOpen,
                onToggleWeather = onToggleWeather,
                drawnCards = drawnCards,
                onOpenDraw = onOpenDraw,
                onOpenChat = onOpenChat,
                state = roomState,
                catalog = catalog,
                dateLabel = dateLabel,
                frameTimeMs = frameTimeMs,
                inventoryOpen = inventoryOpen,
                onToggleInventory = { inventoryOpen = !inventoryOpen },
                theme = roomTheme,
                herd = herd,
                onOpenDex = onOpenDex,
                onOpenWalk = onOpenWalk,
                // 개발자 패널은 **지금 고른 값**이 있어야 하는 고르기다. 발자국을
                // 고를 수는 없으니 여기서만 데모 견종으로 채운다.
                profileBreed = profileBreed ?: HomeDemoData.DOG_BREED,
                onPickProfile = { onPickDevBreed?.invoke(it) },
                onPickDevPets = onPickDevPets,
                devPetCount = devPetCount,
                onPickDevPhoto = onPickDevPhoto,
                onClearDevPhoto = onClearDevPhoto,
                hasDevPhoto = devPhoto != null,
                onOpenCutoutLab = onOpenCutoutLab,
                onMakeCard = onMakeCard,
                canMakeCard = canMakeCard,
                roomName = roomName,
                defaultLabel = defaultRoomLabel(pets?.firstOrNull { it.isPrimary }?.name),
                onRenameRoom = onRenameRoom,
                renameBusy = renameBusy,
                renameError = renameError,
                onDismissRename = onDismissRename,
                outside = outside,
                onPickOutside = { outsideOverride = it },
                todayNote = words.today,
                dogsLoading = pets == null,
                waitsForPet = waitsForPet,
                onAddPet = onAddPet,
                onToggleEmptyRoom = onToggleEmptyRoom,
                tourOpen = tourOpen,
                showTodayCard = !wide,
                modifier = roomModifier,
            )
        }

        // 인벤토리를 방 위에 겹치면 바닥을 가려서 방금 놓은 물건이 안 보인다.
        // 편집 중에는 챗봇 카드 자리를 대신 쓴다 — 방은 그대로 다 보인다.
        val cards: @Composable ColumnScope.() -> Unit = {
            val slot = Modifier.padding(horizontal = 14.dp).height(CardSlotHeight)
            if (inventoryOpen) {
                InventoryPanel(
                    catalog = catalog,
                    available = { roomState.availableCount(it) },
                    onPick = { roomState.placeFromInventory(it, catalog) },
                    currentTheme = roomTheme,
                    onPickTheme = {
                        themeId = it.id
                        store.saveThemeId(it.id)
                    },
                    modifier = slot,
                )
            } else {
                ChatbotCard(
                    onOpenChat = { onOpenChat?.invoke() },
                    modifier = slot.tourSpot(tourSpots, TourStop.Chat),
                    avatar = profileBreed,
                )
            }
            Spacer(Modifier.height(10.dp))
            WalkSummaryCard(
                Modifier.padding(horizontal = 14.dp),
                todayWalks,
                words.daily,
                onOpenWalkHistory,
            )
            gameContent?.invoke()
            Spacer(Modifier.height(10.dp))
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                // **반씩 나눈다.** 방에 0.56 을 줘 봤더니 산책 요약 카드가 눌려서
                // "볕이 뜨거우니 / 해 지고 / 나가자댕!" 이 세 줄로 쪼개졌다. 방은
                // 정사각에 가까워 폭을 더 줘도 세로에 먼저 막히므로 덜 아쉽다.
                room(Modifier.weight(1f).fillMaxHeight())
                // **스크롤을 둔다.** 가로에서는 이 칸의 세로가 411dp 뿐이라 카드 셋
                // (TODAY·챗봇·산책 요약)이 안 들어가고 마지막 것이 잘렸다. 내용이
                // 넘치지 않는 폴드 세로에서는 스크롤이 생기지 않아 지금과 같다.
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 방 위에 얹혀 모서리를 덮던 카드를 여기로 올린다 (사용자 결정).
                    TodayCard(
                        dateLabel = dateLabel,
                        note = words.today,
                        icon = weatherIcon(outside),
                        accent = roomTheme.roomAccent,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        expanded = weatherOpen,
                        onToggle = onToggleWeather,
                    )
                    Spacer(Modifier.height(10.dp))
                    cards()
                }
            }
        } else if (flexRoom != null) {
            // **반접기는 위아래 두 칸이다.**
            //
            // 세워진 위쪽은 눈에서 떨어진 **보는 면**, 책상에 누운 아래쪽은
            // 손가락이 얹히는 **만지는 면**이다. 그래서 방은 위, 카드와 바는
            // 아래로 간다 — 카메라 앱이 뷰파인더를 위에 셔터를 아래에 두는
            // 것과 같은 이유다.
            //
            // 방이 힌지 선에 **딱 맞는다.** 접힌 자리를 가로지르면 방 그림이
            // 꺾여서 두 조각으로 보인다. 그래서 잰 값을 쓴다.
            //
            // 플립에서는 방이 476dp 를 받는다 — **일반 폰의 409dp 보다 크다.**
            // 좁아진 화면이 아니라 방이 제일 커지는 자리다.
            Column(Modifier.fillMaxSize()) {
                room(Modifier.fillMaxWidth().height(flexRoom))
                cards()
            }
        } else if (homeScrolls(maxHeight)) {
            // **세로가 짧으면 방에 자리를 떼어 주고 나머지를 흘린다.**
            //
            // 플립 커버(본문 337dp)에서 방이 통째로 사라졌다. 방이 `weight(1f)` 로
            // **남는** 높이를 가져가는데 카드가 먼저 327dp 를 먹어서 10dp 가
            // 남았기 때문이다. 남는 것을 주는 대신 [ROOM_MIN_HEIGHT] 를 먼저
            // 떼어 주고, 넘치는 카드는 스크롤로 닿게 한다.
            //
            // 플렉스 모드 위쪽 절반(412dp)도 같은 길로 온다 — 세로가 짧은 건
            // 마찬가지다.
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                room(Modifier.fillMaxWidth().height(ROOM_MIN_HEIGHT))
                cards()
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                room(Modifier.fillMaxWidth().weight(1f))
                cards()
            }
        }
        }
    }
    }

    // **겹은 Scaffold 위에 있다.** 하단바도 가리켜야 하는데 Scaffold 안에 있으면
    // 본문 영역에 갇혀서 바를 못 덮는다.
    if (tourOpen) {
        RoomTourOverlay(
            spots = tourSpots,
            stepIndex = tourStep,
            // **겹이 실제로 보여 준 번호로 센다.** 자리가 없어 건너뛴 단계가 있으면
            // 여기 든 값보다 앞서 있다. 그걸 무시하고 +1 하면 같은 단계를 또 그린다.
            onNext = { shown ->
                if (shown >= TOUR_STEPS.lastIndex) onTourClose?.invoke() else tourStep = shown + 1
            },
            onSkip = { onTourClose?.invoke() },
        )
    }
    }
}

@Composable
private fun RoomSection(
    /** 방 둘러보기가 밝힐 자리를 여기에 등록한다. null 이면 안 한다. */
    tourSpots: TourSpots?,
    state: MiniRoomState,
    catalog: ItemCatalog,
    dateLabel: String,
    frameTimeMs: Long?,
    inventoryOpen: Boolean,
    onToggleInventory: () -> Unit,
    /** 날씨 카드가 펴져 있나. 접으면 방 왼쪽 위를 비켜 준다 */
    weatherOpen: Boolean,
    onToggleWeather: (() -> Unit)?,
    /** 내가 뽑은 카드. 턴테이블의 곡 목록이 여기서 나온다 */
    drawnCards: List<DrawnCard>,
    /** 도감을 열고 곧장 뽑기를 띄운다 */
    onOpenDraw: (() -> Unit)?,
    theme: RoomTheme,
    herd: com.daengs.app.miniroom.DogHerd,
    onOpenDex: (() -> Unit)?,
    onOpenWalk: (() -> Unit)?,
    /** 강아지 퀵 메뉴의 "질문" 이 쓴다. 방 자체에는 챗봇으로 가는 다른 길이 없다. */
    onOpenChat: (() -> Unit)?,
    profileBreed: DogBreed,
    onPickProfile: (DogBreed) -> Unit,
    /** 개발자 패널에서 프로필 사진을 올려 본다. */
    onPickDevPhoto: (() -> Unit)? = null,
    onClearDevPhoto: (() -> Unit)? = null,
    hasDevPhoto: Boolean = false,
    onPickDevPets: ((Int) -> Unit)? = null,
    devPetCount: Int = 0,
    /** 카드 실험실. 개발자 패널에서만 열린다. */
    onOpenCutoutLab: (() -> Unit)?,
    /** 야채를 지정해 카드를 만든다. 개발자 패널에서만 불린다. */
    onMakeCard: ((CardTemplate) -> Unit)?,
    /** 빌려 쓸 얼굴이 있나. 개발자 패널이 카드 만들기를 잠글지 정한다. */
    canMakeCard: Boolean,
    /** 액자에 걸린 그림. null 이면 발자국. */
    framePicture: ImageBitmap? = null,
    /** 이름표에 걸 이름. 사용자가 정한 것이고, null 이면 [defaultLabel] 이 걸린다. */
    roomName: String?,
    /** 사용자가 안 정했을 때 걸리는 이름. 대표 강아지에서 지은 값이다. */
    defaultLabel: String,
    onRenameRoom: ((String?) -> Unit)?,
    renameBusy: Boolean,
    renameError: String?,
    onDismissRename: (() -> Unit)?,
    /** 창밖에 얹을 것. **개발자 패널이 이기는 것까지 정해서** 넘어온다. */
    outside: OutsideView,
    onPickOutside: (OutsideView) -> Unit,
    /** TODAY 카드 한 줄. 같은 날씨에서 지은 말이라 창밖과 안 어긋난다. */
    todayNote: String,
    /**
     * 강아지 목록을 아직 못 받았나.
     *
     * 그동안 방은 비어 있다 ([roomRoster] 가 아무도 안 세운다). 오래 걸리면 빈 방이
     * 고장처럼 보이므로 그때만 한 줄 띄운다 — **금방 끝나면 안 띄운다.** 뜨자마자
     * 사라지는 표시는 데모 강아지가 사라지던 것과 똑같이 깜빡임이다.
     */
    dogsLoading: Boolean,
    /**
     * 로그인했는데 **아직 강아지가 없나.** 그때 방이 진짜로 빈다.
     *
     * ⚠️ **[dogsLoading] 과 다른 상태다.** 저건 "곧 올 것"이고 이건 "아직 없는 것"이다.
     * 둘을 같은 문구로 덮으면 영영 안 오는 강아지를 기다리는 화면이 된다.
     */
    waitsForPet: Boolean,
    /** 강아지 등록으로. null 이면 빈 방에 아무 자리도 안 뜬다 */
    onAddPet: (() -> Unit)?,
    /** 개발자 패널의 "빈 방으로 보기". 릴리스에서는 패널이 빈 껍데기라 안 쓰인다 */
    onToggleEmptyRoom: (() -> Unit)?,
    /**
     * 방 둘러보기가 떠 있나. **떠 있으면 빈 방 초대를 가린다** —
     * 겹이 이 카드 위로 스포트라이트를 뚫어서 엉뚱한 것을 가리킨다
     * ([showsEmptyRoomInvite]).
     */
    tourOpen: Boolean,
    /**
     * 방 위에 TODAY 카드를 얹나.
     *
     * **넓은 화면에서는 끈다.** 두 칸 배치에서는 이 카드가 오른쪽 칸으로 올라간다 —
     * 방이 커지면서 카드가 방의 왼쪽 위 모서리를 덮기 때문이다.
     */
    showTodayCard: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // 개발자 도구는 **저장하지 않는다.** 실수로 켠 채 배포되면 안 된다.
    var developer by remember { mutableStateOf(false) }
    // 턴테이블 판. 방을 덮지 않고 아래에서 올라온다 — 이 방의 전축을 튼 것이라
    // 방과 턴테이블이 계속 보여야 그 맥락이 산다.
    var turntableOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // 저장을 눌렀는지. **성공했을 때만 창을 닫으려고** 둔다 — 실패했는데 닫히면
    // 사용자는 저장된 줄 알고 나간다.
    var renameSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(renameBusy) {
        if (!renameSubmitted || renameBusy) return@LaunchedEffect
        renameSubmitted = false
        if (renameError == null) renaming = false
    }
    var breedOverride by remember { mutableStateOf<DogBreed?>(null) }
    // @Preview 안에서는 무한 애니메이션이 돌지 않아 프레임 0 에 얼어붙는다.
    // 미리보기에서는 중간 프레임을 찍어 강아지 자세가 보이게 한다.
    val previewFrame = if (LocalInspectionMode.current) 400L else null

    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // 문이 화면 어디에 있나. **방이 터치 판정에 쓰는 것과 같은 값**이라
    // (`RoomTouchSpots`) 가리키는 곳과 눌리는 곳이 갈라지지 않는다.
    var doorSpot by remember { mutableStateOf<Rect?>(null) }
    // 방 상자가 창 안에서 어디에 있나. **`onSpots` 는 창 좌표로 온다**(`toWindow`) —
    // 둘러보기 겹이 화면 전체를 덮기 때문이다. 알약은 이 상자 안에 있으므로 그만큼 뺀다.
    var roomOrigin by remember { mutableStateOf(Offset.Zero) }
    // 알약을 누르면 이 값이 오르고, 방이 그때 문을 연다.
    var doorSignal by remember { mutableIntStateOf(0) }
    // 방금 누른 강아지. null 이면 퀵 메뉴가 안 떠 있다.
    //
    // **머리 자리까지 같이 들고 있는다** — 방이 히트 판정에 쓴 셈에서 나온 값이라
    // 여기서 다시 계산하면 가리키는 곳과 눌리는 곳이 갈라진다 ([doorSpot] 과 같은 규칙).
    var quickMenu by remember { mutableStateOf<DogTapTarget?>(null) }

    Box(
        modifier
            .onSizeChanged { boxSize = it }
            .onGloballyPositioned { roomOrigin = it.positionInWindow() },
    ) {
        MiniRoomCanvas(
            state = state,
            catalog = catalog,
            theme = theme,
            outside = outside,
            herd = herd,
            // 인벤토리가 열려 있는 동안이 편집 모드. 강아지는 확 숨는다.
            editing = inventoryOpen,
            modifier = Modifier.fillMaxSize(),
            // **자리를 여기서 다시 계산하지 않는다.** 방이 터치 판정에 쓰는 것과 같은
            // 셈으로 알려 준다 — 가리키는 곳과 눌리는 곳이 갈라지면 안 된다.
            // **자리는 언제나 받는다.** 둘러보기가 꺼져 있어도 문 알약이 이 값을 쓴다.
            onSpots = { spots ->
                doorSpot = spots.door
                if (tourSpots != null) {
                    tourSpots.put(TourStop.Door, spots.door)
                    tourSpots.put(TourStop.Frame, spots.frame)
                    spots.turntable?.let { tourSpots.put(TourStop.Turntable, it) }
                }
            },
            openDoorSignal = doorSignal,
            frameTimeMs = frameTimeMs ?: previewFrame,
            developer = developer,
            // 톡 누르면 방향 돌리기. 치우기는 "방 밖으로 끌어내기"로 분리했다 —
            // 탭 하나에 두 가지 뜻을 담으면 헷갈리고, 실수로 사라지면 곤란하다.
            // 편집 모드에서 탭 = 선택. 돌리기/치우기는 버튼으로 뺐다.
            onItemTap = { item -> state.select(item.instanceId) },
            onEmptyTap = { state.select(null) },
            // 문이 활짝 열린 순간 산책으로 나간다
            // (CONTEXT.md 4번: 미니룸(홈) -> [방문 클릭] -> 산책).
            // 편집 중에는 안 받는다 — 가구를 옮기다 화면이 넘어가면 하던 일을 잃는다.
            onDoorOpened = if (inventoryOpen) null else onOpenWalk,
            // 벽의 액자 -> 네오 채소 도감. 편집 중에는 안 받는다 — 가구를 옮기다가
            // 화면이 넘어가면 하던 일을 잃는다.
            onFrameTap = if (inventoryOpen) null else onOpenDex,
            // 뒷벽의 턴테이블 -> 내 카드의 음악. 액자와 같은 이유로 편집 중에는 안 받는다.
            onTurntableTap = if (inventoryOpen) null else { { turntableOpen = true } },
            // 강아지를 톡 누르면 머리 위에 퀵 메뉴. 편집 중에는 안 받는다 — 그때는
            // 강아지가 숨어 있고 손가락은 가구를 만지는 중이다. 둘러보기 중에도 안
            // 받는다 — 겹이 가리키는 곳과 다른 것이 떠 버린다.
            onDogTap = if (inventoryOpen || tourOpen) null else { { quickMenu = it } },
            framePicture = framePicture,
        )
        // 메뉴가 열린 동안 **그 아이는 서 있는다.** 안 멈추면 메뉴만 남고 아이가 걸어
        // 나가서, 누구 메뉴인지가 사라진다. 멈추는 장치는 끌 때 쓰는 것을 그대로 쓴다
        // (`DogHerd.update` 가 이 id 를 보고 그 아이만 건너뛴다).
        val menuDogId = quickMenu?.dogId
        DisposableEffect(menuDogId) {
            if (menuDogId != null) herd.draggingId = menuDogId
            onDispose { if (herd.draggingId == menuDogId) herd.draggingId = null }
        }
        // 목록이 늦을 때만 뜬다. 600ms 를 기다렸다 띄우므로 빠른 망에서는 안 보인다.
        var showDogsLoading by remember { mutableStateOf(false) }
        LaunchedEffect(dogsLoading) {
            showDogsLoading = false
            if (dogsLoading) {
                delay(DOGS_LOADING_DELAY_MS)
                showDogsLoading = true
            }
        }
        if (showDogsLoading) {
            Text(
                "강아지를 불러오는 중이에요",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // **빈 방은 이것과 함께여야 한다.** [roomPets] 주석대로 빈 방은 그 자체로는
        // "고장 난 것" 으로 읽힌다 — 여기가 그것을 "아직 아무도 안 왔다" 로 바꾼다.
        if (onAddPet != null &&
            showsEmptyRoomInvite(waitsForPet, showDogsLoading, inventoryOpen, tourOpen)
        ) {
            EmptyRoomInvite(onAddPet, Modifier.align(Alignment.Center))
        }

        // **문이 산책 나가는 곳이라고 말해 준다.**
        //
        // 문짝의 흰빛만으로는 "누를 수 있다" 까지만 읽히고 "누르면 산책" 까지는 못 간다.
        // 편집 중에는 안 띄운다 — 그때는 문이 산책으로 안 이어진다(`onDoorOpened` 가 null).
        // 빈 방 안내가 떠 있을 때도 안 띄운다. 그때 할 일은 산책이 아니라 등록이다.
        val doorBadgeVisible = onOpenWalk != null && !inventoryOpen && !tourOpen &&
            !showsEmptyRoomInvite(waitsForPet, showDogsLoading, inventoryOpen, tourOpen)
        doorSpot?.takeIf { doorBadgeVisible }?.let { spot ->
            DoorWalkBadge(spot.translate(-roomOrigin.x, -roomOrigin.y), boxSize) { doorSignal++ }
        }

        if (showTodayCard) {
            TodayCard(
                dateLabel = dateLabel,
                note = todayNote,
                icon = weatherIcon(outside),
                accent = theme.roomAccent,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp),
                expanded = weatherOpen,
                onToggle = onToggleWeather,
            )
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InventoryButton(open = inventoryOpen, onClick = onToggleInventory)
            // `BuildConfig.DEBUG` 로 감싸지 않는다 — **소스셋이 곧 가드다.**
            // 릴리스에는 아무것도 안 그리는 껍데기가 들어간다
            // (`app/src/release/.../DeveloperPanel.kt`). 여기에 검사를 하나 더
            // 두면 어느 쪽이 진짜인지 헷갈리고, 예전에 토글만 감싸고 패널은
            // 안 감쌌던 것도 그래서 생긴 일이다.
            DeveloperToggle(on = developer, onToggle = { developer = !developer })
        }

        if (developer) {
            DeveloperPanel(
                state = state,
                herd = herd,
                breedOverride = breedOverride,
                onPickBreed = {
                    breedOverride = it
                    herd.setBreedOverride(it)
                },
                profileBreed = profileBreed,
                onPickProfile = onPickProfile,
                onPickProfilePhoto = onPickDevPhoto,
                onClearProfilePhoto = onClearDevPhoto,
                hasProfilePhoto = hasDevPhoto,
                onPickDevPets = onPickDevPets,
                devPetCount = devPetCount,
                onToggleEmptyRoom = onToggleEmptyRoom,
                emptyRoom = waitsForPet,
                outside = outside,
                onPickOutside = onPickOutside,
                onOpenCutoutLab = onOpenCutoutLab,
                onMakeCard = onMakeCard,
                canMakeCard = canMakeCard,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 6.dp),
            )
        }

        // 이름표는 **상자가 아니라 방 그림**에 붙인다.
        //
        // 상자 오른쪽 끝(BottomEnd)에 두면 방이 상자보다 작아진 뒤로 이름표의
        // 3분의 2가 방 밖 배경 위에 떠 버린다 (에뮬 실측 64%). 방 크기는
        // 화면·카드 높이에 따라 계속 변하므로 상자 기준으로는 맞출 수가 없다.
        //
        // 정렬 자체는 BottomEnd 로 두고 offset 으로만 끌어온다 — 그래야
        // 이름표 크기를 재지 않아도 되고, 글꼴 크기가 커져도 안 흔들린다.
        NamePlate(
            label = roomName?.trim()?.takeIf(String::isNotEmpty) ?: defaultLabel,
            // 로그인 전에는 못 누른다 — 고쳐도 저장할 곳이 없다.
            onClick = onRenameRoom?.let { { renaming = true } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset {
                    if (boxSize.width == 0) return@offset IntOffset.Zero
                    val g = RoomGeometry.of(
                        boxSize.width.toFloat(),
                        boxSize.height.toFloat(),
                    )
                    // 가로는 안 건드린다. BottomCenter 가 방 상자의 가운데를 잡아
                    // 주는데, 방 그림도 상자 가운데에 놓이므로 결과가 같다.
                    IntOffset(
                        0,
                        (g.stage.top + NamePlateSpec.BOTTOM / 100f * g.stage.height
                            - boxSize.height).toInt(),
                    )
                },
        )

        // **판은 이름표보다 나중에 그린다.** Box 는 나중에 부른 것이 위로 올라오는데,
        // 이름표가 뒤에 있어서 판을 열면 그 위로 "○○이네" 가 떠 있었다 — 판이 방에서
        // 올라온 물건이 아니라 이름표 밑에 낀 종이처럼 보인다.
        if (turntableOpen) {
            TurntablePanel(
                onClose = { turntableOpen = false },
                drawn = drawnCards,
                onOpenDraw = onOpenDraw?.let { go ->
                    {
                        // 뽑으러 가면 판은 닫는다. 돌아왔을 때 덮여 있으면 방이 안 보인다.
                        turntableOpen = false
                        go()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (renaming && onRenameRoom != null) {
            RoomNameDialog(
                current = roomName,
                fallback = defaultLabel,
                busy = renameBusy,
                error = renameError,
                onConfirm = {
                    renameSubmitted = true
                    onRenameRoom(it)
                },
                onDismiss = {
                    renaming = false
                    onDismissRename?.invoke()
                },
            )
        }

        // **퀵 메뉴는 방 위 모든 것보다 나중에 그린다.** 앞에 두었더니 문 옆
        // 「산책 나가기」 알약이 「산책」 버튼을 덮어서 둘 다 안 읽혔다 (실기기에서 봤다).
        // 덮는 판이 있는 메뉴는 그리는 순서에서도 맨 위여야 한다.
        quickMenu?.let { target ->
            DogQuickMenu(
                head = target.head,
                actions = dogQuickActions(
                    onWalk = onOpenWalk?.let { go -> { quickMenu = null; go() } },
                    onDraw = onOpenDraw?.let { go -> { quickMenu = null; go() } },
                    onAsk = onOpenChat?.let { go -> { quickMenu = null; go() } },
                ),
                onDismiss = { quickMenu = null },
            )
        }

        // 선택된 가구 위에 뜨는 버튼. 캔버스가 아니라 오버레이라 터치·그림자가 공짜다.
        val selected = state.items.firstOrNull { it.instanceId == state.selectedId }
        val selectedArt = selected?.let { catalog[it.itemId] }
        if (inventoryOpen && selected != null && selectedArt != null && boxSize.width > 0) {
            val g = RoomGeometry.of(boxSize.width.toFloat(), boxSize.height.toFloat())
            val c = g.footprintCenter(
                selected.col, selected.row, selectedArt.box.footprintFacing(selected.facing),
            )
            val artTop = c.y - selectedArt.box.anchor.y * g.scale
            val artRight = c.x + selectedArt.box.size.width / 2f * g.scale
            ItemActions(
                onRotate = { state.rotate(selected.instanceId, catalog) },
                onStore = { state.returnToInventory(selected.instanceId) },
                modifier = Modifier.offset {
                    IntOffset(
                        // 화면 밖으로 안 나가게 살짝 물린다
                        (artRight - 24f).toInt().coerceIn(0, boxSize.width - 200),
                        (artTop - 44f).toInt().coerceAtLeast(0),
                    )
                },
            )
        }
    }
}

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DaengsTheme {
        HomeScreen(frameTimeMs = 400L, dateLabel = HomeDemoData.MOCK_DATE)
    }
}

@Preview(device = "spec:width=320dp,height=640dp", showBackground = true)
@Composable
private fun HomeScreenSmallPreview() {
    DaengsTheme {
        HomeScreen(frameTimeMs = 400L, dateLabel = HomeDemoData.MOCK_DATE)
    }
}

/**
 * 갤럭시 Z 플립 **커버 화면** (3.4", 720x748px @306dpi).
 *
 * 여기서 방이 통째로 사라졌었다 — 카드가 높이를 먼저 다 먹어 `weight(1f)` 에
 * 10dp 만 남았다. [homeScrolls] 가 스크롤 갈래를 골라 방에 [ROOM_MIN_HEIGHT] 를
 * 먼저 떼어 주는 것이 여기서 보여야 한다.
 */
@Preview(name = "Z 플립 커버", device = "spec:width=376dp,height=391dp", showBackground = true)
@Composable
private fun HomeScreenFlipCoverPreview() {
    DaengsTheme {
        HomeScreen(frameTimeMs = 400L, dateLabel = HomeDemoData.MOCK_DATE)
    }
}

/**
 * 갤럭시 Z 플립 **반접기에서 쓸 수 있는 위쪽 절반** (407x994dp 의 절반).
 *
 * ⚠️ **Preview 는 접힘을 흉내 내지 못한다.** `LocalInspectionMode` 에서는
 * [rememberFlexTopHeight] 가 null 을 돌려주므로, 실제로 접힌 것이 아니라
 * **그만큼 짧은 창**을 그린다. 배치가 같은 갈래로 가는지 보는 데까지가 이
 * Preview 의 쓸모고, 접힘 자체는 기기에서 본다.
 */
@Preview(name = "Z 플립 플렉스(위쪽 절반)", device = "spec:width=407dp,height=497dp", showBackground = true)
@Composable
private fun HomeScreenFlipFlexPreview() {
    DaengsTheme {
        HomeScreen(frameTimeMs = 400L, dateLabel = HomeDemoData.MOCK_DATE)
    }
}

/**
 * 갤럭시 Z 플립 **펼친 메인 화면** (6.7", 1080x2640px @425dpi).
 *
 * 폭이 기준 411 과 거의 같아 **원래 멀쩡한 화면이다.** 고치는 자리가 아니라
 * 안 건드렸음을 지키는 자리다 — 여기 스크롤이 생기면 뭔가 잘못된 것이다.
 */
@Preview(name = "Z 플립 펼침", device = "spec:width=407dp,height=994dp", showBackground = true)
@Composable
private fun HomeScreenFlipMainPreview() {
    DaengsTheme {
        HomeScreen(frameTimeMs = 400L, dateLabel = HomeDemoData.MOCK_DATE)
    }
}

/** @Preview 전용. 맑고 포근한 낮 — 시안이 그린 상태다. */
private val PREVIEW_OUTSIDE = OutsideSnapshot(OutsideView.DAY_CLEAR, temperatureC = 21f)
