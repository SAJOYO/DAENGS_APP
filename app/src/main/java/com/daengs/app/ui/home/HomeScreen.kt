package com.daengs.app.ui.home

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.BuildConfig
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
    onSignOut: (() -> Unit)? = null,
    /** 카드 실험실. 개발자 패널에서만 열린다. */
    onOpenCutoutLab: (() -> Unit)? = null,
    /** 둘러보기 상태에서 로그인하러 갈 때. 랜딩으로 되돌린다. */
    onSignIn: (() -> Unit)? = null,
    /** 내 강아지. null 이면 아직 못 받아 온 것이다. */
    pets: List<Pet>? = null,
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
    var devBreed by remember { mutableStateOf<DogBreed?>(null) }
    val profileBreed = devBreed ?: pets?.firstOrNull { it.isPrimary }?.breedArt

    // 방에 서는 강아지 = 등록한 강아지. 목록이 바뀌면 자리를 지킨 채 갈아끼운다.
    val herd = rememberDogHerd(roomRoster(pets), departedInRoom(pets))
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CreamBg,
        // 인셋은 Scaffold 에 맡기지 않고 각 자식이 직접 처리한다.
        // 방 배경은 상태바 아래까지 흘려보내고 싶기 때문이다.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Box(Modifier.background(CreamBg).statusBarsPadding()) {
                DaengsTopBar(
                    // 알림 화면이 아직 없다. 없는 데로 보내는 것보다 안 눌리는 게 낫다.
                    onBell = {},
                    onProfile = { onOpenMy?.invoke() },
                    avatar = profileBreed,
                )
            }
        },
        bottomBar = {
            DaengsBottomBar(
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
                breed = profileBreed,
                roomLabel = roomLabel(roomName, pets?.firstOrNull { it.isPrimary }?.name),
                pets = pets,
                canAddMore = canAddMore,
                onAddPet = { onAddPet?.invoke() },
                onEditPet = { onEditPet?.invoke(it) },
                onPickPrimary = { onPickPrimary?.invoke(it) },
                onDeletePet = { onDeletePet?.invoke(it) },
                onFarewell = onFarewell,
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
            StorageComingSoon(Modifier.padding(inner))
            return@Scaffold
        }

        // 스크롤 없음 — 전부 한 화면에 들어간다.
        // 카드 두 장은 필요한 만큼만 쓰고, 남는 세로는 방이 전부 가져간다.
        // 방은 RoomGeometry.of(width, height) 로 받은 상자에 맞춰 스스로 줄어든다.
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize(),
        ) {
            RoomSection(
                framePicture = framePicture,
                weatherOpen = weatherOpen,
                onToggleWeather = onToggleWeather,
                drawnCards = drawnCards,
                onOpenDraw = onOpenDraw,
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
                onPickProfile = { devBreed = it },
                onOpenCutoutLab = onOpenCutoutLab,
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
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            // 인벤토리를 방 위에 겹치면 바닥을 가려서 방금 놓은 물건이 안 보인다.
            // 편집 중에는 챗봇 카드 자리를 대신 쓴다 — 방은 그대로 다 보인다.
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
                ChatbotCard(onOpenChat = { onOpenChat?.invoke() }, modifier = slot, avatar = profileBreed)
            }
            Spacer(Modifier.height(10.dp))
            WalkSummaryCard(
                Modifier.padding(horizontal = 14.dp),
                todayWalks,
                words.daily,
                onOpenWalkHistory,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun RoomSection(
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
    profileBreed: DogBreed,
    onPickProfile: (DogBreed) -> Unit,
    /** 카드 실험실. 개발자 패널에서만 열린다. */
    onOpenCutoutLab: (() -> Unit)?,
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

    Box(modifier.onSizeChanged { boxSize = it }) {
        MiniRoomCanvas(
            state = state,
            catalog = catalog,
            theme = theme,
            outside = outside,
            herd = herd,
            // 인벤토리가 열려 있는 동안이 편집 모드. 강아지는 확 숨는다.
            editing = inventoryOpen,
            modifier = Modifier.fillMaxSize(),
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
            framePicture = framePicture,
        )
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
        TodayCard(
            dateLabel = dateLabel,
            note = todayNote,
            icon = weatherIcon(outside),
            accent = theme.roomAccent,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp),
            expanded = weatherOpen,
            onToggle = onToggleWeather,
        )
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
                outside = outside,
                onPickOutside = onPickOutside,
                onOpenCutoutLab = onOpenCutoutLab,
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

/** @Preview 전용. 맑고 포근한 낮 — 시안이 그린 상태다. */
private val PREVIEW_OUTSIDE = OutsideSnapshot(OutsideView.DAY_CLEAR, temperatureC = 21f)
