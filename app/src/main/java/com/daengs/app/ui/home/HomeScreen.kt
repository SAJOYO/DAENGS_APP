package com.daengs.app.ui.home

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
import androidx.compose.ui.unit.dp
import com.daengs.app.miniroom.MiniRoomCanvas
import com.daengs.app.miniroom.MiniRoomState
import com.daengs.app.miniroom.RoomDefaults
import com.daengs.app.miniroom.rememberDogHerd
import com.daengs.app.miniroom.RoomGeometry
import com.daengs.app.miniroom.RoomTheme
import com.daengs.app.miniroom.rememberRoomStore
import com.daengs.app.miniroom.art.ItemCatalog
import com.daengs.app.miniroom.art.footprintFacing
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.miniroom.art.rememberItemCatalog
import com.daengs.app.miniroom.rememberMiniRoomState
import com.daengs.app.ui.theme.CreamBg
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
 * 이름표가 앉는 자리 — **방 그림 기준 백분율**이다.
 *
 * 울타리가 방 앞쪽 오른편에 서 있으므로 그 위에 얹히도록 잡았다.
 * 바닥 앞 꼭짓점(FloorQuad.front, 43%)보다 오른쪽이라 앞모서리를 가리지 않는다.
 *
 * 화면 크기가 바뀌어도 방 그림 안에서의 자리는 그대로다.
 */
private object NamePlateSpec {
    /** 이름표 오른쪽 끝 (방 그림 폭 %) */
    const val RIGHT = 80f

    /** 이름표 아래 끝 (방 그림 높이 %) */
    const val BOTTOM = 95f
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
    dateLabel: String = HomeDemoData.todayLabel(),
    /** 방 벽의 액자를 눌렀을 때. 도감으로 들어간다. */
    onOpenDex: (() -> Unit)? = null,
    /** 카카오로 로그인한 상태인가. 개발자 패널이 로그아웃을 띄울지 정한다. */
    signedIn: Boolean = false,
    onSignOut: (() -> Unit)? = null,
) {
    var topTab by rememberSaveable { mutableStateOf(TopTab.Home) }
    var bottomTab by rememberSaveable { mutableStateOf(BottomTab.Home) }
    var inventoryOpen by rememberSaveable { mutableStateOf(false) }

    // 프로필 얼굴의 견종. 개발자 패널에서 바꿀 수 있다.
    //
    // 상단바와 챗봇 카드 둘 다 이걸 쓴다. 그 둘은 방 밖에 있어서 상태를
    // 방 안에 두면 닿지 않는다 — 그래서 견종 고르기(방 안)와 달리 여기 있다.
    // rememberSaveable 이 아니다 — 개발자 도구로 바꿔 본 것은 앱을 다시 켜면 지워진다.
    var profileBreed by remember { mutableStateOf(HomeDemoData.DOG_BREED) }

    val herd = rememberDogHerd(RoomDefaults.DOG_COUNT)
    val store = rememberRoomStore()
    // 테마는 id 만 저장한다 — 원시값이라 화면 회전에도 그대로 남는다
    var themeId by rememberSaveable { mutableStateOf(store.loadThemeId() ?: RoomTheme.DEFAULT.id) }
    val roomTheme = RoomTheme.byId(themeId)

    // 저장된 배치가 있으면 그걸로 시작한다. 없거나 못 읽으면 기본 배치.
    // rememberSaveable 이 화면 회전을, 이쪽이 앱 재시작을 담당한다.
    val roomState = rememberMiniRoomState(
        initial = remember { store.loadItems() ?: RoomDefaults.STARTER_ROOM },
    )

    // 배치가 바뀔 때마다 저장. 드래그는 놓을 때 한 번만 커밋되고 회전도 탭 한 번이라
    // 쓰기가 잦지 않다. apply() 는 비동기라 UI 를 막지도 않는다.
    LaunchedEffect(roomState, store) {
        snapshotFlow { roomState.items.toList() }
            .collect { store.saveItems(it) }
    }
    // 테마마다 소품 그림이 다르므로 카탈로그가 테마를 알아야 한다.
    val catalog = rememberItemCatalog(roomTheme)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CreamBg,
        // 인셋은 Scaffold 에 맡기지 않고 각 자식이 직접 처리한다.
        // 방 배경은 상태바 아래까지 흘려보내고 싶기 때문이다.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Box(Modifier.background(CreamBg).statusBarsPadding()) {
                DaengsTopBar(
                    selected = topTab,
                    onSelect = { topTab = it },
                    onBell = {},
                    onProfile = {},
                    avatar = profileBreed,
                )
            }
        },
        bottomBar = {
            DaengsBottomBar(
                selected = bottomTab,
                onSelect = { bottomTab = it },
                onCenter = {},
            )
        },
    ) { inner ->
        // 스크롤 없음 — 전부 한 화면에 들어간다.
        // 카드 두 장은 필요한 만큼만 쓰고, 남는 세로는 방이 전부 가져간다.
        // 방은 RoomGeometry.of(width, height) 로 받은 상자에 맞춰 스스로 줄어든다.
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize(),
        ) {
            RoomSection(
                state = roomState,
                catalog = catalog,
                dateLabel = dateLabel,
                frameTimeMs = frameTimeMs,
                inventoryOpen = inventoryOpen,
                onToggleInventory = { inventoryOpen = !inventoryOpen },
                theme = roomTheme,
                herd = herd,
                onOpenDex = onOpenDex,
                signedIn = signedIn,
                onSignOut = onSignOut,
                profileBreed = profileBreed,
                onPickProfile = { profileBreed = it },
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
                ChatbotCard(slot, avatar = profileBreed)
            }
            Spacer(Modifier.height(10.dp))
            WalkSummaryCard(Modifier.padding(horizontal = 14.dp))
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
    theme: RoomTheme,
    herd: com.daengs.app.miniroom.DogHerd,
    onOpenDex: (() -> Unit)?,
    signedIn: Boolean,
    onSignOut: (() -> Unit)?,
    profileBreed: DogBreed,
    onPickProfile: (DogBreed) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 개발자 도구는 **저장하지 않는다.** 실수로 켠 채 배포되면 안 된다.
    var developer by remember { mutableStateOf(false) }
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
            // 문이 활짝 열린 순간. 산책 게임 화면이 생기면 여기서 넘기면 된다.
            // (CONTEXT.md 4번: 미니룸(홈) -> [방문 클릭] -> 산책 게임)
            onDoorOpened = {},
            // 벽의 액자 -> 네오 채소 도감. 편집 중에는 안 받는다 — 가구를 옮기다가
            // 화면이 넘어가면 하던 일을 잃는다.
            onFrameTap = if (inventoryOpen) null else onOpenDex,
        )
        TodayCard(
            dateLabel = dateLabel,
            note = HomeDemoData.TODAY_NOTE,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp),
        )
        Column(
            Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CameraButton(onClick = {})
            Spacer(Modifier.height(9.dp))
            InventoryButton(open = inventoryOpen, onClick = onToggleInventory)
            Spacer(Modifier.height(9.dp))
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
                signedIn = signedIn,
                onSignOut = onSignOut,
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
            label = HomeDemoData.ROOM_LABEL,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset {
                    if (boxSize.width == 0) return@offset IntOffset.Zero
                    val g = RoomGeometry.of(
                        boxSize.width.toFloat(),
                        boxSize.height.toFloat(),
                    )
                    IntOffset(
                        (g.stage.left + NamePlateSpec.RIGHT / 100f * g.stage.width
                            - boxSize.width).toInt(),
                        (g.stage.top + NamePlateSpec.BOTTOM / 100f * g.stage.height
                            - boxSize.height).toInt(),
                    )
                },
        )

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
