package com.daengs.app.ui.chat

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.daengs.app.assistant.AssistantApi
import com.daengs.app.assistant.AssistantResponse
import com.daengs.app.assistant.WalkVerdict
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatHistoryCoordinator
import com.daengs.app.chat.ChatHistoryState
import com.daengs.app.chat.ChatLoadState
import com.daengs.app.chat.ChatSession
import com.daengs.app.chat.ChatTurn
import com.daengs.app.chat.ReportApi
import com.daengs.app.gait.GaitApi
import com.daengs.app.gait.GaitComparison
import com.daengs.app.gait.GaitProgress
import com.daengs.app.gait.GaitRecord
import com.daengs.app.gait.GaitVideo
import com.daengs.app.gait.PreparedVideo
import com.daengs.app.gait.rememberGaitHolder
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.screening.Photo
import com.daengs.app.screening.PreparedPhoto
import com.daengs.app.screening.ScreeningRecordApi
import com.daengs.app.screening.ScreeningRun
import com.daengs.app.screening.ScreeningReport
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DogAvatar
import kotlinx.coroutines.delay
import com.daengs.app.ui.DogFace
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.camera.CameraPreview
import com.daengs.app.ui.camera.hasCameraPermission
import com.daengs.app.ui.camera.rememberCameraController
import com.daengs.app.ui.camera.rememberVideoRecorder
import com.daengs.app.ui.camera.takePicture
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.gait.GaitCaptureScreen
import com.daengs.app.ui.gait.GaitCompareScreen
import com.daengs.app.ui.gait.GaitDetailScreen
import com.daengs.app.ui.gait.GaitIntroCard
import com.daengs.app.ui.gait.GaitPairPickSheet
import com.daengs.app.ui.gait.GaitPickSheet
import com.daengs.app.ui.gait.GaitTitleDialog
import com.daengs.app.ui.gait.GaitProgressCard
import com.daengs.app.ui.gait.GaitResultCard
import com.daengs.app.ui.home.HomeDemoData
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * 대화에 쌓이는 것들.
 *
 * 예전에는 `List<String>` 이었다. 사진과 진단 결과가 들어오면서 말풍선 종류가
 * 넷이 됐고, 문자열로는 "이건 사진이다"를 표현할 자리가 없다.
 */
internal sealed interface ChatEntry {
    data class Mine(val text: String) : ChatEntry

    /**
     * **AI 가 준 답.** 신고할 수 있는 말풍선은 이것뿐이다.
     *
     * @property turnId 서버에 저장된 turn. **null 이면 저장 안 된 대화의 답**이다
     *   (무상태 질문). 신고(`POST /app/reports`)가 이 id 를 요구하므로 없으면 메일로 간다.
     */
    data class Theirs(val text: String, val turnId: String? = null) : ChatEntry

    /**
     * **앱이 지어낸 안내.** 생김새는 [Theirs] 와 같은 말풍선이지만 AI 가 한 말이 아니다
     * ("영상이 준비되었어요", "보행 기록을 지웠어요").
     *
     * 갈라 둔 이유는 **신고**다. 예전에는 이것도 `Theirs` 라 길게 누르면 신고 메뉴가
     * 떴는데, 앱이 만든 문장을 운영자에게 보낼 이유가 없다.
     */
    data class Note(val text: String) : ChatEntry

    /** 오케스트레이션에 물어보는 중. 답이 오면 이 자리가 [Theirs] 나 [Failed] 로 바뀐다. */
    data object Thinking : ChatEntry

    /** 내가 올린 사진. 원본이 아니라 줄여 놓은 썸네일이다 ([Photo.THUMB_EDGE]). */
    data class MyPhoto(val image: Bitmap) : ChatEntry

    /** 서버에 물어보는 중. 답이 오면 이 자리가 [Report] 나 [Failed] 로 바뀐다. */
    data object Screening : ChatEntry

    data class Report(val report: ScreeningReport) : ChatEntry

    data class Failed(val message: String) : ChatEntry

    /**
     * 산책 판정 카드. 저쪽이 준 판정을 그대로 그린다 ([WalkVerdict] · [WalkVerdictCard]).
     *
     * 등급 · 이유 · 추천 시간대가 있어야 해서 말풍선이 아니라 카드다. 보행 카드와
     * 다른 점은 **누를 것이 없다는 것** — 여기서 시작되는 흐름이 없고 결과만 남는다.
     */
    data class WalkCard(val verdict: WalkVerdict) : ChatEntry

    /**
     * Place 검색 결과 카드들. [placeCards] 가 고른 후보(최대 [MAX_PLACE_CARDS])를 그대로 그린다.
     *
     * 산책 카드와 같은 이유로 말풍선이 아니라 카드다 — 담을 것이 산문이 아니라
     * 거리·주소·사실 목록이다.
     */
    data class PlaceCards(val presentation: PlaceCardsPresentation) : ChatEntry

    /**
     * 위치가 없어 못 찾겠다는 CLARIFY. 말풍선(저쪽 되묻기 질문)만으로는 사용자가 할 수
     * 있는 일이 없어서, 위치 권한을 다시 청하고 **같은 질문을 그대로 재전송**하는
     * 액션을 얹는다. [query] 는 그 재전송에 쓸 원문이다.
     */
    data class LocationNeeded(val query: String) : ChatEntry

    /** 보행 흐름의 첫 카드. 영상을 어디서 가져올지 고르는 자리다. */
    data object GaitIntro : ChatEntry

    /** 분석 중. 단계가 넘어갈 때마다 이 자리가 새 [GaitProgress] 로 갈린다. */
    data class GaitRunning(val progress: GaitProgress) : ChatEntry

    /**
     * 끝난 기록.
     *
     * **기록 자체가 아니라 id 를 든다.** 기록은 홀더가 진짜를 들고 있어서, 상세에서
     * 지우면 여기 남은 사본만 살아 있는 상태가 생긴다 (`WalkDetailScreen` 이 세션
     * id 만 받는 것과 같은 이유).
     */
    data class GaitDone(val recordId: String) : ChatEntry

    /** 비교 화면에서 "대화에 남기기" 로 내려온 결과. */
    data class GaitCompared(val comparison: GaitComparison) : ChatEntry
}

/** 서버가 준 turn 순서를 그대로 대화 말풍선으로 복원한다. */
internal fun restoredChatEntries(turns: List<ChatTurn>): List<ChatEntry> = buildList {
    turns.forEach { turn ->
        add(ChatEntry.Mine(turn.userContent))
        when (turn.processingStatus) {
            ChatTurn.ProcessingStatus.PROCESSING -> add(ChatEntry.Thinking)
            ChatTurn.ProcessingStatus.FAILED,
            ChatTurn.ProcessingStatus.UNKNOWN ->
                add(ChatEntry.Failed("이 답변은 완료되지 않았어요. 다시 질문해 주세요."))
            ChatTurn.ProcessingStatus.COMPLETED -> {
                val response = turn.publicResponse
                if (response == null) {
                    add(ChatEntry.Theirs(turn.assistantContent.orEmpty(), turn.id))
                } else {
                    add(ChatEntry.Theirs(response.walkSentence() ?: response.bubbleMessage(), turn.id))
                    response.walkCard()?.let { add(ChatEntry.WalkCard(it)) }
                    if (response.knownHandoff() == KnownHandoff.GAIT) add(ChatEntry.GaitIntro)
                }
            }
        }
    }
}

/**
 * 대화 UI 전용 화면. 자유 텍스트는 `POST /assistant/query` 오케스트레이션으로 간다 —
 * 자연어 해석·능력 실행·집계는 전부 저쪽이 하고, 앱은 상태(`status`)와
 * `handoffs` 만 보고 화면을 고른다. **여기서 텍스트를 보고 갈래를 나누지 않는다**
 * (예전 `GAIT_ASK` 키워드 라우팅은 그래서 지웠다).
 *
 * 사진 진단은 다르다 — 계약이 이미 있어서([ScreeningRecordApi]) 실제로 부른다. 서버
 * 주소가 비어 있으면 버튼이 스스로 그렇게 말한다.
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    /** 대표 강아지 얼굴. 모르는 견종(믹스)이거나 아직 못 받았으면 null 이다. */
    avatar: DogBreed? = null,
    /**
     * 올린 프로필 사진. **말풍선 얼굴에는 안 쓴다** — 거기는 학사모 쓴 "똑똑이"와
     * 앞발 괸 "곰곰이" 자리다. 보행 촬영 화면에서만 쓴다 (거기는 찍히는 그 아이다).
     */
    avatarPhoto: ImageBitmap? = null,
    /**
     * 대표 강아지의 id. **서버가 만든 `pets.id` UUID 다** — `MainActivity` 가
     * `pets.primary?.id` 를 그대로 넘긴다.
     *
     * 보행 기록을 묶는 열쇠이고, 이제 **소유권 검증의 근거이기도 하다** (#64):
     * 저쪽이 이 값으로 `pets.app_user_id` 까지 따라가 토큰의 주인이 맞는지 본다.
     * 없으면 올려도 목록으로 다시 못 찾아서, 보내기 전에 화면이 막는다.
     */
    dogId: String? = null,
    /**
     * 만료됐으면 재발급까지 하고 돌려주는 access token. **[MainActivity] 의
     * `freshToken` 을 그대로 받는다** — 여기서 `TokenStore` 를 직접 읽거나
     * 세션을 갱신하지 않는다. null 이면 로그인이 안 된 것이다.
     */
    accessTokenProvider: suspend () -> String? = { null },
    /** null 이면 기존 무상태 assistant 경로만 쓴다. 실제 앱은 Activity 생애의 조율기를 준다. */
    historyCoordinator: ChatHistoryCoordinator? = null,
    /**
     * 피부 **변화 기록**으로 가는 길. null 이면 그 줄을 안 보여 준다.
     *
     * 기록은 로그인해야 있는 것이라, 로그인 안 한 기기에서는 [MainActivity] 가
     * null 을 준다 — 눌러 봐야 빈 화면이면 안 누르게 하는 편이 낫다.
     */
    onOpenScreeningHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 산책·날씨가 쓰는 것과 같은 위치원. 챗봇도 여기서 좌표를 얻는다 —
    // 새 권한도 새 측위도 안 든다.
    val fused = remember(context) { FusedLocationSource(context) }

    // 폰의 뒤로가기. **여기가 없으면 앱이 꺼진다** — 챗봇으로 오는 순간 [HomeScreen]
    // 이 컴포지션에서 빠지면서 그쪽 BackHandler 도 같이 사라지고, 뒤로가기가 아무도
    // 받지 않은 채 시스템까지 흘러가 액티비티가 끝난다.
    //
    // ⚠️ **아래 오버레이들보다 먼저 등록한다.** 컴포즈는 나중에 등록된 핸들러가
    // 이기므로, 촬영·네모조정·상세·비교·시트가 열려 있으면 그쪽이 먼저 받는다.
    // 여기를 아래로 내리면 오버레이를 열어 둔 채 뒤로 눌렀을 때 대화가 통째로 닫힌다.
    BackHandler(onBack = onBack)

    var draft by rememberSaveable { mutableStateOf("") }
    val entries = remember { mutableStateListOf<ChatEntry>() }
    val historyState = historyCoordinator?.state?.collectAsState()?.value
        ?: ChatHistoryState(selectedPetId = dogId)
    var displayedSessionId by remember { mutableStateOf<String?>(null) }
    var recentOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<ChatSession?>(null) }
    var pendingPersistedSlot by remember { mutableStateOf<Int?>(null) }
    var pendingPersistedSessionId by remember { mutableStateOf<String?>(null) }
    var pendingPersistedQuery by remember { mutableStateOf("") }
    /** 답이 놓인 자리. 늦게 오는 turn id 를 여기에 채운다. */
    var answeredSlot by remember { mutableStateOf<Int?>(null) }
    var queryGeneration by remember { mutableStateOf(0L) }
    val scroll = rememberScrollState()
    // null 이면 닫힘. [ChooserMode.SkinOnly] 는 서버 skin HANDOFF 가 연 것이라
    // 보행 묶음을 감춘다 — 사용자가 그 질문에서 보행을 고를 이유가 없다.
    var chooserMode by remember { mutableStateOf<ChooserMode?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dogId, historyCoordinator) {
        queryGeneration++
        pendingPersistedSlot = null
        pendingPersistedSessionId = null
        answeredSlot = null
        displayedSessionId = null
        entries.clear()
        val coordinator = historyCoordinator ?: return@LaunchedEffect
        coordinator.selectPet(dogId)
        if (dogId == null) return@LaunchedEffect
        val token = accessTokenProvider() ?: return@LaunchedEffect
        coordinator.loadRecent(token)
        val selectedSessionId = coordinator.state.value.selectedSessionId
        if (selectedSessionId == null) coordinator.createOrReuseDraft(token)
        else coordinator.refreshCurrent(token)
    }
    DisposableEffect(historyCoordinator) {
        onDispose { historyCoordinator?.cancelPending() }
    }

    LaunchedEffect(historyState.selectedSessionId, historyState.selectedSession) {
        val detail = (historyState.selectedSession as? ChatLoadState.Ready)?.value
            ?: return@LaunchedEffect
        if (detail.session.id != displayedSessionId) {
            entries.clear()
            entries.addAll(restoredChatEntries(detail.turns))
            displayedSessionId = detail.session.id
            pendingPersistedSlot = null
            pendingPersistedSessionId = null
            answeredSlot = null
        }
    }
    LaunchedEffect(historyState.selectedSessionId) {
        val coordinator = historyCoordinator ?: return@LaunchedEffect
        if (dogId == null || historyState.selectedSessionId != null) return@LaunchedEffect
        val token = accessTokenProvider() ?: return@LaunchedEffect
        coordinator.createOrReuseDraft(token)
    }

    // 사진을 고르면 **바로 안 보낸다.** 가이드 프레임에서 병변 자리를 받아야
    // 저쪽이 학습과 같은 함수로 자를 수 있다 ([GuideFrameScreen] 참고).
    var pending by remember { mutableStateOf<PreparedPhoto?>(null) }
    val open: (Uri) -> Unit = { uri ->
        scope.launch {
            Photo.prepare(context, uri)
                .onSuccess { pending = it }
                .onFailure { notice = it.message ?: "사진을 읽지 못했어요." }
        }
    }

    // 진단 한 번. **새 계약이 되면 기록이 남고, 안 되면 옛 경로로 판정만 받는다.**
    val screeningRun = remember(accessTokenProvider) { ScreeningRun(accessTokenProvider) }

    // 프레임까지 맞춘 사진을 대화에 올리고 서버에 물어본다.
    //
    // **말풍선을 먼저 올리고 자리를 잡아 둔다.** 응답을 기다렸다가 한꺼번에 올리면
    // 몇 초 동안 아무 일도 안 일어난 화면이 된다 — 모델이 CPU 로 돌면 사진 한 장에
    // 1~3초 걸린다고 저쪽이 적어 뒀고, 첫 요청은 가중치를 올리느라 더 걸린다.
    val send: (PreparedPhoto, FloatArray) -> Unit = { photo, box ->
        scope.launch {
            // **말풍선에는 자른 자리를 올린다.** 잘라서 보냈는데 사진 전체가 뜨면
            // 자른 것이 안 먹은 것으로 읽힌다. 서버로 가는 `photo.jpeg` 는 그대로
            // 원본이다 — 저쪽이 `bbox` 로 학습과 같은 함수로 자른다 ([cropForBubble]).
            entries += ChatEntry.MyPhoto(cropForBubble(photo.thumbnail, box))
            val slot = entries.size
            entries += ChatEntry.Screening
            // **기록으로 남기되, 못 남겨도 진단은 한다.** 로그인 안 했거나 저쪽
            // 저장소가 아직 안 켜졌으면(503) 옛 경로로 물러선다 — 그 갈림은
            // [ScreeningRun] 이 정한다.
            //
            // ⚠️ **box 를 이제 실제로 보낸다.** 전에는 안 보내서 저쪽이 화면 중앙으로
            //    물러섰고, 1단계는 큰 차이가 없지만 2단계 분포가 학습 크롭과 어긋났다.
            when (val outcome = screeningRun.run(dogId, photo.jpeg, box)) {
                is ScreeningRun.Outcome.Screened -> entries[slot] = ChatEntry.Report(outcome.report)
                is ScreeningRun.Outcome.Failed -> entries[slot] = ChatEntry.Failed(outcome.message)
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) open(uri)
    }

    // ── 보행 ────────────────────────────────────────────────────────────────
    //
    // 보행 화면들은 **대화 위에 얹는다.** `MainActivity` 의 [Screen] 으로 빼면 촬영
    // 화면을 열었다 되돌아올 때 대화가 통째로 새로 만들어져, 방금 올린 카드가
    // 사라진다 — 가이드 프레임([GuideFrameScreen])을 대화 위에 덮은 것과 같은 이유다.
    // 보행도 backend 뒤로 들어왔다 (#64) — 올리는 것도 받아오는 것도 토큰이 있어야 한다.
    val gait = rememberGaitHolder(petId = dogId, accessToken = accessTokenProvider)

    // **목록의 정본은 서버다** (#64). 전에는 기기 안의 표본만 보고 있어서, 다른 기기에서
    // 올린 기록이 보이지 않았다. 대표 강아지가 정해지면 받아온다 — 실패해도 들고 있던
    // 것을 지우지 않는다 (`GaitHolder.load`).
    LaunchedEffect(dogId) {
        gait.load(dogId ?: return@LaunchedEffect)
    }

    /** 촬영 가이드 화면이 떠 있나. */
    var gaitCapture by remember { mutableStateOf(false) }
    // 앱 안 카메라로 피부 사진을 찍는 중.
    var skinCapture by remember { mutableStateOf(false) }
    // 앱 안 카메라의 가이드에 맞춰 찍은 사진인가. 확인 화면이 그 네모에서 시작한다.
    var guidedShot by remember { mutableStateOf(false) }

    // 카메라 권한. **찍는 동안 가이드를 보여 주려고** 든다 (시스템 카메라로 던질
    // 때는 필요 없었다). 이걸 선언한 순간 권한 없이는 찍는 길이 아예 없어진다 — [CAMERA_DENIED].
    var cameraGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    // 권한을 받고 나서 이어서 할 일. "사진찍기" 와 "영상 촬영" 이 같은 창을 쓴다.
    var afterCamera by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (granted) afterCamera?.invoke() else notice = CAMERA_DENIED
        afterCamera = null
    }

    /**
     * 권한이 있으면 바로, 없으면 묻고 나서 [action].
     *
     * ⚠️ **거부했을 때 시스템 카메라로 떨어질 수 없다.** 매니페스트에 `CAMERA` 를
     * 선언한 앱은 그 권한이 없으면 `ACTION_IMAGE_CAPTURE` 조차 못 띄운다 —
     * 안드로이드가 `SecurityException` 으로 막는다(실기기에서 앱이 죽었다).
     * 그래서 여기서 시스템 카메라를 부르지 않고, 갤러리로 안내한다.
     */
    val withCamera: (() -> Unit) -> Unit = { action ->
        if (cameraGranted) {
            action()
        } else {
            afterCamera = action
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    /** 비교할 지난 기록을 고르는 중. 값은 **비교의 기준이 되는 최근 기록 id** 다. */
    var gaitPicking by remember { mutableStateOf<String?>(null) }

    /** 저장된 기록끼리 비교(B 진입) — 둘을 한 시트에서 고르는 중. */
    var gaitPairPicking by remember { mutableStateOf(false) }

    /** AI 기능 선택 → 보행 "지난 기록 보기" 시트가 열려 있나. */
    var gaitHistoryOpen by remember { mutableStateOf(false) }

    /**
     * 읽어 둔 영상에 **제목을 묻는 중.** 촬영·업로드 둘 다 [runGait] 로 모이므로 이 하나로
     * 두 경로가 같은 다이얼로그를 탄다. 다이얼로그가 닫히면 분석이 시작된다.
     */
    var gaitTitlePending by remember { mutableStateOf<PreparedVideo?>(null) }

    /** 나란히 보는 중. */
    var gaitComparing by remember { mutableStateOf<GaitComparison?>(null) }

    /** 상세를 보는 중인 기록 id. */
    var gaitDetail by remember { mutableStateOf<String?>(null) }

    /**
     * 상세를 열면서 영상부터 틀까.
     *
     * **비교 화면의 영상 카드로 들어온 경우에만 참이다.** 그때 누른 뜻이 "이 기록의
     * 분석 영상을 크게 보겠다" 라서, 도착해서 재생을 또 눌러야 하면 흐름이 끊긴다.
     * 대화 카드에서 들어온 경우는 상세를 읽으러 온 것이라 멈춰 둔다.
     */
    var gaitDetailAutoPlay by remember { mutableStateOf(false) }

    /**
     * 영상 한 편을 대화에 태운다.
     *
     * 자리 잡는 방식은 사진 진단([send])과 같다 — 진행 카드를 먼저 올려 두고 그 자리를
     * 갈아 끼운다. 다만 끝났을 때 **진행 카드를 결과 카드로 바꾸지 않고, 완료 말풍선으로
     * 바꾼 뒤 결과 카드를 새로 얹는다.** 네 줄이 다 초록으로 찬 카드가 대화에 그대로
     * 남아 있으면, 아래에 붙은 결과 카드와 어느 쪽이 지금 것인지 겹쳐 보인다.
     */
    val startGaitAnalysis: (PreparedVideo, String?) -> Unit = { video, title ->
        scope.launch {
            entries += ChatEntry.Note("영상이 준비되었어요!\n이제 보행 분석을 시작할게요.")
            val slot = entries.size
            entries += ChatEntry.GaitRunning(GaitProgress.START)
            val record = gait.analyze(video, title) { entries[slot] = ChatEntry.GaitRunning(it) }
            if (record == null) {
                entries[slot] = ChatEntry.Failed(gait.error ?: "보행 영상을 분석하지 못했어요.")
                gait.clearError()
            } else {
                entries[slot] = ChatEntry.Note("분석이 완료되었어요!\n결과를 확인해볼까요?")
                entries += ChatEntry.GaitDone(record.id)
            }
        }
    }

    // **촬영과 업로드가 여기서 만난다.** 영상을 먼저 읽어 보고(못 읽는 파일이면 제목을
    // 물을 이유가 없다), 제목 다이얼로그를 띄운 뒤 분석을 시작한다 — 두 경로가 같은
    // 다이얼로그를 타는 이유는 이 함수가 하나라서다. 제목은 서버 `note` 로 같이 올라간다.
    val runGait: (Uri) -> Unit = { uri ->
        scope.launch {
            GaitVideo.prepare(context, uri)
                .onFailure { notice = it.message ?: "영상을 읽지 못했어요." }
                .onSuccess { video -> gaitTitlePending = video }
        }
    }

    /**
     * 자유 텍스트 한 줄을 오케스트레이션에 보낸다.
     *
     * **여기서 답을 지어내지 않는다.** 로그인이 안 됐을 때만 로컬 문구를 쓰고,
     * 나머지는 전부 서버가 준 `message`/`clarify`/`handoffs` 를 그대로 옮긴다.
     * **[dogId] 는 두 경로가 똑같이 싣는다.** 견종·나이는 앱이 지어 보내는 게 아니라
     * 저쪽이 그 id 로 `pets` 를 읽는다 (PR #112). 저장하는 질문에서는 조율기가 같은
     * 값을 실어 준다 — 화면이 고른 강아지 하나가 두 경로의 원본이다.
     *
     * 강아지와 세션이 있으면 저장 계약의 두 id 를 조율기에 맡기고, 둘 중 하나가 없는
     * 기존 호출자는 무상태 요청을 그대로 쓴다. 무상태는 대화 기록을 안 보낸다.
     */
    // 물어보는 중인가. **연타를 막는다** — 한 번이 의미 라우터 + 생성이라 값이 비싸고,
    // 두 번 누르면 90초짜리 요청이 둘 뜬 채 답이 뒤섞여 돌아온다.
    var asking by remember { mutableStateOf(false) }

    val showResponse: (Int, AssistantResponse, String) -> Unit = { slot, response, asked ->
        if (slot in entries.indices) {
            entries[slot] = ChatEntry.Theirs(response.walkSentence() ?: response.bubbleMessage())
            response.walkCard()?.let { entries += ChatEntry.WalkCard(it) }
            response.placeCards()?.let { entries += ChatEntry.PlaceCards(it) }
            // 좌표가 없어 되물은 것이라면 다시 물을 거리를 준다. 무상태 CLARIFY 는
            // 이어 물을 토큰이 없어서, 문장만 띄우면 사용자에게 막다른 길이다.
            if (response.isLocationClarify()) entries += ChatEntry.LocationNeeded(asked)
            when (response.knownHandoff()) {
                KnownHandoff.GAIT -> entries += ChatEntry.GaitIntro
                KnownHandoff.SKIN -> chooserMode = ChooserMode.SkinOnly
                null -> Unit
            }
        }
    }

    LaunchedEffect(historyState.lastResponse, historyState.sendError) {
        val slot = pendingPersistedSlot ?: return@LaunchedEffect
        if (historyState.selectedSessionId != pendingPersistedSessionId) return@LaunchedEffect
        historyState.lastResponse?.let { response ->
            showResponse(slot, response, pendingPersistedQuery)
            answeredSlot = slot
            pendingPersistedSlot = null
            pendingPersistedSessionId = null
            asking = false
        } ?: historyState.sendError?.let { error ->
            if (slot in entries.indices) {
                entries[slot] = ChatEntry.Failed(error.message ?: "AI 서버에 닿지 못했어요.")
            }
            pendingPersistedSlot = null
            pendingPersistedSessionId = null
            asking = false
        }
    }

    // 답이 먼저 오고 turn id 가 나중에 온다 — 조율기가 상세를 다시 받아야 알 수 있는
    // 값이라서다. 그동안 말풍선은 이미 화면에 있으므로, 늦게 온 id 를 그 자리에 채운다.
    // 이걸 안 하면 방금 받은 답변은 대화를 다시 열기 전까지 메일로만 신고된다.
    LaunchedEffect(historyState.lastTurnId) {
        val slot = answeredSlot ?: return@LaunchedEffect
        val turnId = historyState.lastTurnId ?: return@LaunchedEffect
        val shown = entries.getOrNull(slot) as? ChatEntry.Theirs ?: return@LaunchedEffect
        entries[slot] = shown.copy(turnId = turnId)
        answeredSlot = null
    }

    val reportApi = remember { ReportApi() }

    /**
     * 저장된 답변 하나를 운영자에게 신고한다 (`POST /app/reports`).
     *
     * 실패하면 **메일로 물러선다** — 서버가 죽어 있다고 신고할 길이 아예 없어지면 안
     * 된다. 그때 turn id 와 고른 사유를 메일에 같이 실어서, 운영자가 콘솔에서 그 답변을
     * 찾을 수 있게 한다.
     *
     * **409 만 예외다.** 이미 이 사람이 신고한 답변이라 실패가 아니라 두 번째이고,
     * 메일로 또 보내면 같은 신고가 두 벌이 된다.
     */
    val reportAnswer: (String, String, String) -> Unit = { turnId, reason, answer ->
        scope.launch {
            val token = accessTokenProvider()
            if (token == null) {
                notice = "로그인이 필요해요. 다시 로그인해 주세요."
            } else {
                reportApi.report(token, turnId, reason).fold(
                    onSuccess = { toast(context, "신고했어요. 운영자가 확인할게요.") },
                    onFailure = { failure ->
                        if ((failure as? ChatApiError)?.status == 409) {
                            toast(context, "이미 신고한 답변이에요.")
                        } else if (openReportEmail(context, answer, turnId, reason)) {
                            toast(context, "서버에 보내지 못해 메일 앱을 열었어요. 그대로 보내 주세요.")
                        } else {
                            toast(context, "신고를 보내지 못했어요. $REPORT_EMAIL 로 알려 주세요.")
                        }
                    },
                )
            }
        }
    }

    val sendQuery: (String) -> Unit = { text ->
        entries += ChatEntry.Mine(text)
        val slot = entries.size
        entries += ChatEntry.Thinking
        asking = true
        val generation = queryGeneration
        val selectedSessionId = historyState.selectedSessionId
        scope.launch {
            val token = accessTokenProvider()
            if (token == null && generation == queryGeneration) {
                entries[slot] = ChatEntry.Failed("로그인이 필요해요. 다시 로그인해 주세요.")
                asking = false
                return@launch
            }
            if (token == null) return@launch
            // 지금 있는 곳. **못 구해도 질문은 그냥 보낸다** — 위치가 필요한 질문은
            // 일부고, 좌표 때문에 훈련 질문까지 막으면 안 된다. 서버는 위치가
            // 필요한데 없으면 CLARIFY 로 되묻는데, 이어서 묻는 토큰이 없어서
            // (무상태) 그 되묻기는 사용자에게 막다른 길이다. 그래서 미리 싣는다.
            val where = runCatching { fused.currentLocation().point }.getOrNull()
            if (generation != queryGeneration) return@launch
            val coordinator = historyCoordinator
            if (coordinator != null && dogId != null) {
                val current = coordinator.state.value
                if (current.selectedPetId != dogId || current.selectedSessionId != selectedSessionId) {
                    if (slot in entries.indices) entries[slot] = ChatEntry.Failed("대화가 바뀌어 질문을 보내지 않았어요.")
                    asking = false
                    return@launch
                }
                pendingPersistedSlot = slot
                pendingPersistedSessionId = selectedSessionId
                pendingPersistedQuery = text
                if (!coordinator.send(token, text, where)) {
                    pendingPersistedSlot = null
                    pendingPersistedSessionId = null
                    if (slot in entries.indices) entries[slot] = ChatEntry.Failed("대화가 준비된 뒤 다시 보내 주세요.")
                    asking = false
                }
            } else {
                AssistantApi.query(token, text, where, activeDogId = dogId, persistence = null)
                    .onSuccess { response -> if (generation == queryGeneration) showResponse(slot, response, text) }
                    .onFailure {
                        if (generation == queryGeneration && slot in entries.indices) {
                            entries[slot] = ChatEntry.Failed(it.message ?: "AI 서버에 닿지 못했어요.")
                        }
                    }
                if (generation == queryGeneration) asking = false
            }
        }
    }

    // ── 위치-CLARIFY ────────────────────────────────────────────────────────
    //
    // [ChatEntry.LocationNeeded] 의 액션이 누르는 자리. 권한을 다시 청하고, 받으면
    // 같은 질문을 그대로 재전송한다 — 사용자가 문장을 다시 칠 이유가 없다.
    var pendingLocationRetry by remember { mutableStateOf<String?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val retryText = pendingLocationRetry
        pendingLocationRetry = null
        if (granted && retryText != null) sendQuery(retryText)
    }
    val retryWithLocation: (String) -> Unit = { query ->
        pendingLocationRetry = query
        locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            gaitCapture = false
            runGait(uri)
        }
    }
    val startGaitPicking: () -> Unit = {
        pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }
    // 앱 안 카메라가 찍어 넣을 자리. 콜백이 어디에 찍었는지 안 알려 주므로 **먼저
    // 만들어 두고** 그대로 읽는다.
    val target = remember { runCatching { Photo.cameraTarget(context) }.getOrNull() }

    // 새 말풍선이 생기면 아래로 따라간다. 안 하면 결과가 화면 밖에서 조용히 쌓인다.
    LaunchedEffect(entries.size) { scroll.animateScrollTo(scroll.maxValue) }

    // 아래를 **키보드와 내비게이션 바 중 큰 쪽**만큼 띄운다.
    //
    // imePadding() 만 쓰면 키보드가 내려갔을 때 입력줄이 안드로이드 내비바 밑으로
    // 들어가 반쯤 가린다. 그렇다고 imePadding 과 navigationBarsPadding 을 둘 다
    // 걸면 키보드가 올라왔을 때 이중으로 밀려서 키보드 위에 빈 띠가 생긴다 —
    // 키보드 인셋에는 내비바 높이가 이미 들어 있기 때문이다.
    //
    // safeDrawing 은 둘의 **합집합**이라 각 변에서 큰 쪽을 준다. 키보드가 올라오면
    // 키보드 높이, 내려가면 내비바 높이가 되어 한 줄로 둘 다 맞는다.
    // 위쪽은 헤더가 statusBarsPadding 으로 따로 챙기므로 아래만 쓴다.
    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        ChatHeader(
            onBack = onBack,
            avatar = avatar,
            onRecent = if (historyCoordinator != null && dogId != null) {
                { recentOpen = true }
            } else {
                null
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entries.isEmpty()) {
                when (val sessionState = historyState.selectedSession) {
                    ChatLoadState.Loading -> AssistantBubble("대화를 불러오고 있어요…", avatar)
                    is ChatLoadState.Failed -> {
                        AssistantBubble(sessionState.error.message ?: "대화를 불러오지 못했어요.", avatar)
                        Text(
                            "다시 시도",
                            color = DaengPinkDeep,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                val coordinator = historyCoordinator ?: return@clickable
                                scope.launch {
                                    val token = accessTokenProvider() ?: return@launch
                                    val sessionId = historyState.selectedSessionId
                                    if (sessionId == null) coordinator.createOrReuseDraft(token)
                                    else coordinator.openSession(token, sessionId)
                                }
                            }.padding(horizontal = 40.dp, vertical = 4.dp),
                        )
                    }
                    else -> AssistantBubble(
                        "반려견의 산책, 건강, 생활을 무엇이든 물어보세요.",
                        avatar,
                    )
                }
            } else {
                entries.forEach { entry ->
                    when (entry) {
                        is ChatEntry.Mine -> UserBubble(entry.text)
                        is ChatEntry.Theirs -> AssistantBubble(
                            entry.text,
                            avatar,
                            turnId = entry.turnId,
                            onReport = reportAnswer,
                        )
                        is ChatEntry.Note -> AssistantBubble(entry.text, avatar)
                        ChatEntry.Thinking -> ThinkingBubble(avatar, "생각 중…")
                        is ChatEntry.MyPhoto -> PhotoBubble(entry.image)
                        // 사진 진단도 몇 초 걸리는 자리라 같은 말풍선을 쓴다.
                        ChatEntry.Screening -> ThinkingBubble(avatar, "사진 보는 중…")
                        is ChatEntry.Failed -> AssistantBubble(entry.message, avatar)
                        is ChatEntry.Report -> ReportBubble(entry.report, avatar)

                        // 보행 카드는 **말풍선 안에 안 넣는다.** 카드가 이미 흰
                        // 바탕에 테두리를 가져서, 말풍선을 한 겹 더 두르면 흰 상자
                        // 안의 흰 상자가 된다. 대신 아바타 자리만큼 왼쪽을 비워
                        // 두어 "AI 가 준 것" 이라는 줄맞춤은 지킨다.
                        is ChatEntry.WalkCard -> BesideAvatar {
                            WalkVerdictCard(entry.verdict)
                        }

                        is ChatEntry.PlaceCards -> BesideAvatar {
                            PlaceSuggestionCards(
                                presentation = entry.presentation,
                                onOpenMap = { candidate ->
                                    val intent = placeMapIntent(candidate)
                                    if (intent == null) {
                                        notice = "이 장소는 지도에서 열 좌표 정보가 없어요."
                                    } else {
                                        runCatching { context.startActivity(intent) }
                                            .onFailure { notice = "지도 앱을 열지 못했어요." }
                                    }
                                },
                            )
                        }

                        is ChatEntry.LocationNeeded -> BesideAvatar {
                            LocationClarifyAction(onRetry = { retryWithLocation(entry.query) })
                        }

                        ChatEntry.GaitIntro -> BesideAvatar {
                            GaitIntroCard(
                                onCapture = { gaitCapture = true },
                                onPick = startGaitPicking,
                                // 저장된 기록이 둘 이상일 때만 줄이 생긴다 (B 진입).
                                onCompareSaved = if (gait.comparablePairExists) {
                                    { gaitPairPicking = true }
                                } else {
                                    null
                                },
                            )
                        }

                        is ChatEntry.GaitRunning -> BesideAvatar { GaitProgressCard(entry.progress) }

                        is ChatEntry.GaitDone -> gait.find(entry.recordId)?.let { record ->
                            BesideAvatar {
                                GaitResultCard(
                                    record = record,
                                    canCompare = gait.hasComparable(record.id),
                                    onOpen = { gaitDetail = record.id },
                                    onCompare = { gaitPicking = record.id },
                                )
                            }
                        }

                        is ChatEntry.GaitCompared -> BesideAvatar {
                            GaitComparedBubble(entry.comparison) {
                                gaitComparing = entry.comparison
                            }
                        }
                    }
                }
            }
        }
        ChatInput(
            value = draft,
            onValueChange = { draft = it },
            busy = asking || historyState.sending ||
                (historyCoordinator != null && dogId != null && !historyState.canSend),
            onSend = {
                val text = draft.trim()
                // 물어보는 중에는 안 받는다 — 위 [asking] 주석.
                if (text.isNotEmpty() && !asking) {
                    draft = ""
                    sendQuery(text)
                }
            },
            // 음성은 **아직 껍데기다.** 버튼 자리와 크기를 먼저 잡아 두고, 녹음과
            // 인식이 붙을 때 여기만 갈아 끼운다. 눌러도 아무 일이 없으면 고장으로
            // 보이므로 준비 중이라고 말은 한다.
            onVoice = { notice = "음성 입력은 준비 중이에요." },
            // **시트는 항상 연다.** 기능이 둘이 되면서 진단 서버 유무로 시트 전체를
            // 막으면 보행 쪽까지 같이 닫힌다. 못 하는 이유는 그 줄을 눌렀을 때 말한다.
            onDiagnose = { chooserMode = ChooserMode.Full },
        )
    }

    if (recentOpen && historyCoordinator != null) {
        Dialog(onDismissRequest = { recentOpen = false }) {
            Surface(color = CreamBg, shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("최근 대화", color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            "닫기",
                            color = DaengPinkDeep,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { recentOpen = false }.padding(8.dp),
                        )
                    }
                    RecentChatsContent(
                        state = historyState.recentSessions,
                        selectedSessionId = historyState.selectedSessionId,
                        pendingDeletion = pendingDeletion,
                        onRetry = {
                            scope.launch {
                                val token = accessTokenProvider() ?: return@launch
                                historyCoordinator.loadRecent(token)
                            }
                        },
                        onSelect = { session ->
                            recentOpen = false
                            queryGeneration++
                            asking = false
                            pendingPersistedSlot = null
                            pendingPersistedSessionId = null
                            displayedSessionId = null
                            entries.clear()
                            scope.launch {
                                val token = accessTokenProvider() ?: return@launch
                                historyCoordinator.openSession(token, session.id)
                            }
                        },
                        onRequestDelete = { pendingDeletion = it },
                        onDismissDelete = { pendingDeletion = null },
                        onConfirmDelete = { session ->
                            pendingDeletion = null
                            scope.launch {
                                val token = accessTokenProvider() ?: return@launch
                                historyCoordinator.deleteSession(token, session.id)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }

    chooserMode?.let { mode ->
        AiActionDialog(
            skinOnly = mode == ChooserMode.SkinOnly,
            onDismiss = { chooserMode = null },
            onOpenHistory = onOpenScreeningHistory?.let {
                {
                    chooserMode = null
                    it()
                }
            },
            // 남긴 기록이 없으면 줄을 안 그린다 — 눌러도 빈 시트만 뜨는 줄은 사용자가
            // 자기가 뭘 잘못했나 생각하게 한다 ([기록 비교] 줄과 같은 규칙).
            onOpenGaitHistory = if (gait.records.isNotEmpty()) {
                {
                    chooserMode = null
                    gaitHistoryOpen = true
                }
            } else {
                null
            },
            onCamera = {
                chooserMode = null
                when {
                    // 설정이 없을 때 화면이 스스로 알려 주는 결은 랜딩의 카카오
                    // 로그인 버튼과 같다.
                    !ScreeningRecordApi.configured -> notice = SCREEN_NOT_SET
                    target == null -> notice = "카메라를 열 수 없어요."
                    // 앱 안에서 찍는다. 그래야 병변에 맞출 네모를 찍는 동안 보여 준다.
                    else -> withCamera { skinCapture = true }
                }
            },
            onAttach = {
                chooserMode = null
                guidedShot = false
                if (!ScreeningRecordApi.configured) {
                    notice = SCREEN_NOT_SET
                } else {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            // 보행은 **진단 서버와 다른 서버**다 (backend 뒤, `/app/gait/…`). 그래도
            // 피부 두 줄과 **같은 규칙**을 따른다 — 꺼져 있으면 화면이 스스로 말한다.
            //
            // ⚠️ 예전에는 여기서 안 봤다. 보행이 기기 안에서 돌던 시절의 주석이 남아
            //    있었는데, 이제는 서버가 필요하다. 안 보면 꺼졌을 때 [MockGaitAnalyzer]
            //    로 조용히 떨어져 **가짜 결과가 진짜처럼 보인다** (#64).
            onGaitCapture = {
                chooserMode = null
                if (!GaitApi.configured) notice = GAIT_NOT_SET
                else withCamera { gaitCapture = true }
            },
            onGaitPick = {
                chooserMode = null
                if (!GaitApi.configured) notice = GAIT_NOT_SET else startGaitPicking()
            },
        )
    }

    // 가이드 프레임은 대화 **위를 통째로 덮는다.** 사진 한 장에 집중하는 화면이라
    // 뒤가 보이면 네모를 맞추는 일이 흐려진다.
    pending?.let { photo ->
        GuideFrameScreen(
            photo = photo.thumbnail,
            guided = guidedShot,
            onCancel = { pending = null },
            onConfirm = { box ->
                pending = null
                send(photo, box)
            },
        )
    }

    if (skinCapture) {
        val controller = rememberCameraController(videoEnabled = false)
        var shooting by remember { mutableStateOf(false) }
        SkinCaptureScreen(
            controller = controller,
            onBack = { skinCapture = false },
            onPick = {
                skinCapture = false
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            busy = shooting,
            onShutter = {
                if (!shooting && target != null) {
                    shooting = true
                    takePicture(
                        context = context,
                        controller = controller,
                        file = Photo.cameraFile(context),
                        onSaved = {
                            shooting = false
                            skinCapture = false
                            guidedShot = true
                            open(target)
                        },
                        onError = {
                            shooting = false
                            notice = "사진을 저장하지 못했어요."
                        },
                    )
                }
            },
        )
    }

    // 보행 화면들. **덮는 순서가 곧 되돌아가는 순서다** — 촬영이 제일 위고,
    // 상세와 비교는 그 아래, 시트는 대화 바로 위다.
    if (gaitCapture) {
        // 여기까지 왔으면 권한이 있다 ([withCamera] 가 받고 나서 연다).
        run {
            val controller = rememberCameraController(videoEnabled = true)
            val recorder = rememberVideoRecorder(
                controller = controller,
                file = remember { GaitVideo.cameraFile(context) },
                onDone = { uri ->
                    gaitCapture = false
                    runGait(uri)
                },
                onError = { notice = it },
            )
            GaitCaptureScreen(
                onBack = { gaitCapture = false },
                onRecord = recorder::toggle,
                onPick = startGaitPicking,
                avatar = avatar,
                photo = avatarPhoto,
                recording = recorder.recording,
                preview = { CameraPreview(controller, Modifier.fillMaxSize()) },
            )
        }
    }

    gaitComparing?.let { comparison ->
        GaitCompareScreen(
            comparison = comparison,
            onBack = { gaitComparing = null },
            // 카드를 누르면 그 기록의 상세가 **비교 위에 얹힌다.** 비교를 닫지 않으므로
            // 뒤로 가면 보던 비교로 돌아온다 — 영상 하나 크게 보려고 누른 것이지
            // 비교를 그만두려던 것이 아니다.
            onOpenRecord = { record ->
                gaitDetailAutoPlay = true
                gaitDetail = record.id
            },
            // 기준은 그대로 두고 상대만 다시 고른다. 시트는 A 진입이 쓰던 것이다.
            onCompareAnother = {
                gaitComparing = null
                gaitPicking = comparison.recent.id
            },
            onSaveToChat = {
                gaitComparing = null
                entries += ChatEntry.GaitCompared(comparison)
            },
        )
    }

    // **비교보다 뒤에 그린다.** 앞에 두면 비교 화면이 상세를 덮어서, 카드를 눌러도
    // 아무 일도 안 일어난 것처럼 보인다.
    gaitDetail?.let { id ->
        // 저장된 기록은 오버레이 주소가 목록에 없다. 상세를 열 때 한 번 채워, 재생기가
        // 원본 대신 스켈레톤 영상을 틀 수 있게 한다. 방금 분석한 기록은 이미 들고 있어
        // 조회가 그냥 건너뛴다.
        LaunchedEffect(id) { gait.ensureOverlay(id) }
        gait.find(id)?.let { record ->
            GaitDetailScreen(
                record = record,
                canCompare = gait.hasComparable(record.id),
                onBack = {
                    gaitDetail = null
                    gaitDetailAutoPlay = false
                },
                onCompare = { gaitPicking = record.id },
                onDelete = {
                    // 서버에서도 지운다. 화면은 기다리지 않는다 — 홀더가 먼저 빼고
                    // 실패하면 되돌린다.
                    scope.launch { gait.remove(record.id) }
                    gaitDetail = null
                    gaitDetailAutoPlay = false
                    // **지운 기록을 낀 비교도 같이 닫는다.** 안 닫으면 뒤로 갔을 때
                    // 없는 기록 두 편을 나란히 놓은 화면으로 돌아간다.
                    if (gaitComparing?.let { record.id in listOf(it.recent.id, it.past.id) } == true) {
                        gaitComparing = null
                    }
                    // 카드가 가리키던 기록이 없어졌다. 카드를 지우지 않고 자리를
                    // 말풍선으로 바꾼다 — 대화에서 줄이 통째로 사라지면 무엇이
                    // 있었는지 알 수 없다.
                    val slot = entries.indexOfFirst {
                        it is ChatEntry.GaitDone && it.recordId == record.id
                    }
                    if (slot >= 0) {
                        entries[slot] = ChatEntry.Note("${record.dateLabel} 「${record.displayTitle}」 기록을 지웠어요.")
                    }
                },
                autoPlay = gaitDetailAutoPlay,
                // 제목만 바뀐다. 날짜는 홀더가 손대지 않는다 (`GaitHolder.rename`).
                onRename = { title -> gait.rename(record.id, title) },
            )
        } ?: run { gaitDetail = null }
    }

    // ── 기록 제목 묻기 — **촬영·업로드 공통** ───────────────────────────────
    //
    // 영상을 읽어 둔 뒤, 분석을 시작하기 전에 한 번 묻는다. 건너뛰거나 비워 두면 null 이
    // 가고 화면이 "보행 기록" 을 그린다. 다이얼로그가 닫히는 순간 분석이 시작된다.
    gaitTitlePending?.let { video ->
        GaitTitleDialog(initial = null) { title ->
            gaitTitlePending = null
            startGaitAnalysis(video, title)
        }
    }

    gaitPicking?.let { recentId ->
        GaitPickSheet(
            // 기준이 되는 기록은 목록에서 뺀다. 자기 자신과 비교하는 줄이 있으면
            // 눌러 보게 되고, 눌러 보면 늘 "차이 없음" 이 나온다.
            records = gait.records.filterNot { it.id == recentId },
            onDismiss = { gaitPicking = null },
            onConfirm = { past ->
                gaitPicking = null
                gaitDetail = null
                // 비교는 서버가 한다. 문장도 저쪽 message_for_ui 가 온다.
                scope.launch { gaitComparing = gait.compare(recentId, past.id) }
            },
        )
    }

    // ── 저장된 기록끼리 비교 (B 진입) — **한 시트에서 둘을 고른다** ──────────
    //
    // 예전에는 같은 시트를 두 번 열었다(기준 → 상대). 두 번째 시트에서 첫 것을 빼는
    // 것까지는 맞았는데 "방금 골랐는데 또?" 가 됐다. 둘을 체크하고 한 번에 넘어간다.
    if (gaitPairPicking) {
        GaitPairPickSheet(
            records = gait.records,
            onDismiss = { gaitPairPicking = false },
            onConfirm = { a, b ->
                gaitPairPicking = false
                // 순서는 신경 쓰지 않는다 — 어느 쪽이 최근인지는 날짜가 정한다.
                scope.launch { gaitComparing = gait.compare(a.id, b.id) }
            },
        )
    }

    // ── 지난 보행 기록 보기 (AI 기능 선택 시트) ────────────────────────────
    //
    // 피부 쪽 "지난 기록 보기" 와 같은 자리다. 비교 시트를 그대로 쓰되 **어느 기록이든**
    // 열 수 있다 — 비교 지표가 없어도 영상은 볼 수 있으니까. 고르면 상세로 간다.
    if (gaitHistoryOpen) {
        GaitPickSheet(
            records = gait.records,
            title = "지난 보행 기록",
            confirmLabel = "기록 열기",
            requireComparable = false,
            onDismiss = { gaitHistoryOpen = false },
            onConfirm = { record ->
                gaitHistoryOpen = false
                gaitDetail = record.id
            },
        )
    }

    notice?.let { message -> NoticeDialog(message) { notice = null } }
}

/**
 * 아바타 자리를 비우고 카드를 놓는다.
 *
 * 아바타를 **매 카드마다 다시 그리지 않는다.** 보행 흐름은 카드가 연달아 서너 개
 * 쌓이는데, 그때마다 같은 얼굴이 붙으면 한 사람이 네 번 말한 것처럼 보인다.
 * 자리만 비워 두면 줄맞춤은 말풍선과 같으면서 화면이 조용하다.
 */
@Composable
private fun BesideAvatar(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(40.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * 비교 결과를 대화에 남긴 줄.
 *
 * 비교 화면을 통째로 옮겨 오지 않는다 — 대화는 흘러가는 곳이라 표 세 줄이 매번
 * 다시 펼쳐지면 위에 있던 이야기가 밀려난다. 여기에는 **어느 두 날을 봤는지와
 * 그때 나온 문장**만 남기고, 표는 눌러서 다시 연다.
 */
@Composable
private fun GaitComparedBubble(comparison: GaitComparison, onOpen: () -> Unit) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PinkSoft),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${comparison.recent.dateLabel} · ${comparison.past.dateLabel} 비교",
                color = TextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(comparison.verdict.title, color = TextDark, fontSize = 13.sp, lineHeight = 19.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다시 보기", color = DaengPinkDeep, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(13.dp), tint = DaengPinkDeep)
            }
        }
    }
}

/**
 * [ChatEntry.LocationNeeded] 가 쓰는 카드. 저쪽 되묻기 질문은 이미 말풍선에 있으니
 * 여기는 **할 수 있는 일** — 권한을 켜고 같은 질문을 다시 보내는 버튼 — 만 얹는다.
 */
@Composable
private fun LocationClarifyAction(onRetry: () -> Unit) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PinkSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "위치 권한을 켜면 주변 장소를 찾을 수 있어요.",
                color = TextDark,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            DaengsWideButton("위치 켜고 다시 묻기", onRetry, accent = true)
        }
    }
}

@Composable
private fun ChatHeader(onBack: () -> Unit, avatar: DogBreed?, onRecent: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = TextDark, fontSize = 34.sp, lineHeight = 30.sp) }
        ChatFace(avatar, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("댕스 AI", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("반려견 생활 도우미", color = TextMuted, fontSize = 12.sp)
        }
        Box(Modifier.size(9.dp).background(DaengPink, RoundedCornerShape(50)))
        if (onRecent != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                "최근",
                color = DaengPinkDeep,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onRecent)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ChatHeaderPreview() = DaengsTheme {
    ChatHeader(onBack = {}, avatar = HomeDemoData.DOG_BREED, onRecent = {})
}

/**
 * AI 쪽 말풍선. **여기서만** [assistantMarkdown] 을 부른다 — 이 화면의 다른 텍스트
 * (내 말풍선, 구조화 카드)는 서버 자유 텍스트가 아니라 마크다운을 볼 이유가 없다.
 */
/**
 * 챗봇이 한 말. AI가 생성한 답변은 **길게 누르면 복사·신고 메뉴가 뜹니다.**
 *
 * 메신저와 같은 손버릇이라 따로 안내하지 않아도 찾는다. 말풍선마다 복사 아이콘을
 * 달면 대화가 길어질수록 화면이 아이콘으로 덮인다.
 *
 * 진행("생각하는 중이에요…")이나 실패 문구도 같이 복사된다. 해로울 것이 없고,
 * 무엇이 진짜 답인지 갈라내려면 분기가 하나 더 는다.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AssistantBubble(
    text: String,
    avatar: DogBreed?,
    /** 이 답변의 turn. **null 이면 서버 신고를 못 한다** — 메일로 간다. */
    turnId: String? = null,
    /**
     * 서버 신고를 실행할 자리. **null 이면 신고 메뉴 자체가 안 뜬다** — 앱이 지어낸
     * 안내([ChatEntry.Note])와 진행·실패 문구가 그렇다.
     */
    onReport: ((turnId: String, reason: String, answer: String) -> Unit)? = null,
) {
    val reportable = onReport != null
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shown = assistantMarkdown(text)
    var actionsOpen by remember { mutableStateOf(false) }
    var reportConfirmOpen by remember { mutableStateOf(false) }
    val copyShownText = {
        // 원문 마크다운이 아니라 화면에 보이는 글자를 복사합니다.
        clipboard.setText(AnnotatedString(shown.text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "복사했어요", Toast.LENGTH_SHORT).show()
        }
    }
    Row(verticalAlignment = Alignment.Top) {
        ChatFace(avatar, 32.dp)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = CardWhite,
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .combinedClickable(
                    // 짧게 누르는 것은 아무 일도 안 한다. 말풍선은 누르는 것이 아니다.
                    onClick = {},
                    onLongClick = {
                        if (reportable) actionsOpen = true else copyShownText()
                    },
                ),
        ) {
            Text(
                shown,
                color = TextDark,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
    if (actionsOpen) {
        AssistantActionsDialog(
            onCopy = {
                actionsOpen = false
                copyShownText()
            },
            onReport = {
                actionsOpen = false
                reportConfirmOpen = true
            },
            onDismiss = { actionsOpen = false },
        )
    }
    if (reportConfirmOpen) {
        ReportAnswerDialog(
            turnId = turnId,
            onSubmit = { reason ->
                reportConfirmOpen = false
                if (turnId != null && onReport != null) {
                    onReport(turnId, reason, shown.text)
                } else {
                    // 저장 안 된 대화의 답변. 사유는 사용자가 메일 본문에 적는다.
                    val opened = openReportEmail(context, shown.text)
                    toast(
                        context,
                        if (opened) {
                            "메일 앱을 열었어요. 신고 이유를 적고 보내 주세요."
                        } else {
                            "메일 앱을 찾을 수 없어요. $REPORT_EMAIL 로 신고해 주세요."
                        },
                    )
                }
            },
            onDismiss = { reportConfirmOpen = false },
        )
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

@Composable
private fun AssistantActionsDialog(
    onCopy: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AssistantActionsContent(onCopy, onReport)
    }
}

@Composable
private fun AssistantActionsContent(onCopy: () -> Unit, onReport: () -> Unit) {
    Surface(color = CardWhite, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            AssistantAction("복사", TextDark, onCopy)
            AssistantAction("신고", DaengsColors.Error, onReport)
        }
    }
}

@Composable
private fun AssistantAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label,
        color = tint,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun ReportAnswerDialog(
    turnId: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ReportAnswerContent(byMail = turnId == null, onSubmit = onSubmit, onDismiss = onDismiss)
    }
}

/**
 * 신고 확인.
 *
 * **고지가 이 화면의 전제다** — 무엇이 운영자에게 가는지 누르기 전에 말한다
 * (`DAENGS_dev` 의 `D-053` ④). 문구를 실제보다 넓게 적지 않는다: 운영자가 여는 것은
 * 신고된 문답 한 건이고, 그 대화의 다른 문답은 관리자 화면에서도 안 열린다.
 *
 * @param byMail 저장 안 된 대화라 메일로 가는 경우. 사유를 여기서 안 받는다 —
 *   사용자가 메일 본문에 적는다.
 */
@Composable
private fun ReportAnswerContent(
    byMail: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(ReportReason.WRONG) }
    var written by remember { mutableStateOf("") }
    val reason = if (byMail) "" else reportReasonText(choice, written)
    Surface(color = CardWhite, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(22.dp)) {
            Text(
                "이 AI 답변을 신고할까요?",
                color = TextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (byMail) {
                    "답변 내용이 신고 메일에 포함돼요. 메일 앱에서 이유를 적고 보내 주세요."
                } else {
                    "신고하면 이 질문과 답변이 운영자에게 전달돼요. " +
                        "대화의 다른 내용은 전달되지 않아요."
                },
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            if (!byMail) {
                Spacer(Modifier.height(16.dp))
                ReportReason.entries.forEach { option ->
                    ReportReasonRow(option.label, option == choice) { choice = option }
                }
                if (choice == ReportReason.OTHER) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = written,
                        // 상한을 넘으면 저쪽이 422 로 버린다. 잘라 내지 말고 안 받는다.
                        onValueChange = { if (it.length <= REPORT_REASON_MAX) written = it },
                        placeholder = { Text("무엇이 문제였는지 적어 주세요", fontSize = 13.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AssistantDialogAction("취소", TextMuted, onDismiss)
                Spacer(Modifier.width(6.dp))
                if (byMail) {
                    AssistantDialogAction("메일 열기", DaengsColors.Error) { onSubmit("") }
                } else {
                    // 직접 적기를 골라 놓고 아무것도 안 썼으면 보낼 것이 없다.
                    AssistantDialogAction(
                        "신고",
                        if (reason == null) TextMuted else DaengsColors.Error,
                    ) { reason?.let(onSubmit) }
                }
            }
        }
    }
}

@Composable
private fun ReportReasonRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, color = TextDark, fontSize = 14.sp)
    }
}

@Composable
private fun AssistantDialogAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label,
        color = tint,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ReportActionsPreview() {
    DaengsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AssistantActionsContent({}, {})
            ReportAnswerContent(byMail = false, onSubmit = {}, onDismiss = {})
            ReportAnswerContent(byMail = true, onSubmit = {}, onDismiss = {})
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = PinkSoft, shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)) {
            Text(text, color = TextDark, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(14.dp))
        }
    }
}

/**
 * 답을 만드는 동안 뜨는 말풍선.
 *
 * **글자 한 줄이면 멈춘 것과 구분이 안 된다.** 서버가 몇 초 걸리는 자리라 그 사이가
 * 제일 불안하다. 우리 아이 얼굴이 곰곰이 판과 번갈아 바뀌면서 "돌고 있다" 를 글자
 * 없이도 말한다. 그림 두 장을 오가는 것만으로 모션이 되어서, 프레임 시트를 따로
 * 받을 필요가 없었다.
 *
 * **왼쪽 얼굴은 안 바꾼다.** 거기는 학사모 쓴 똑똑이이고 누가 말하는지를 가리키는
 * 자리다 (#92 에서 정한 것). 바뀌는 것은 말풍선 **안**이다.
 *
 * 그림이 없는 견종(믹스)이면 이 자리도 글자만 남는다 — 아무 얼굴이나 갖다 쓰면
 * 사용자가 자기 개가 아닌 얼굴을 본다 ([PawAvatar] 와 같은 규칙).
 */
@Composable
private fun ThinkingBubble(avatar: DogBreed?, text: String) {
    var pondering by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(THINKING_FRAME_MS)
            pondering = !pondering
        }
    }
    Row(verticalAlignment = Alignment.Top) {
        ChatFace(avatar, 32.dp)
        Spacer(Modifier.width(8.dp))
        Surface(color = CardWhite, shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (avatar != null) {
                    DogAvatar(
                        avatar,
                        Modifier.size(28.dp),
                        face = if (pondering) DogFace.Thinking else DogFace.Portrait,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = TextDark, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

/**
 * 두 얼굴을 오가는 간격.
 *
 * 빠르면 깜빡임으로 읽히고, 느리면 멈춘 것으로 읽힌다. 그림이 둘뿐이라 걷는 느낌이
 * 아니라 **숨 쉬는 느낌**이 되어야 한다.
 */
private const val THINKING_FRAME_MS = 700L

/**
 * 대기 말풍선. **모션은 프리뷰에서 안 돈다** — 여기서 보는 것은 두 얼굴이 앉는
 * 자리와 크기다. 움직임이 거슬리는지는 실기기에서 본다.
 *
 * 그림 없는 견종(믹스)은 얼굴 없이 글자만 남는 것도 같이 본다.
 */
@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun ThinkingBubblePreview() {
    DaengsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ThinkingBubble(DogBreed.BEAGLE, "생각 중…")
            ThinkingBubble(DogBreed.TOY_POODLE_SILVER, "사진 보는 중…")
            ThinkingBubble(null, "생각 중…")
        }
    }
}

/**
 * 내가 올린 사진. 말풍선 대신 그림 자체가 모서리를 갖는다.
 *
 * **폭을 고정한다.** 예전에는 상한만 걸어 두고 그림 크기대로 그렸는데, 말풍선에
 * 올라가는 것이 통짜 사진에서 **자른 조각**으로 바뀌면서 그 크기가 확 줄었다
 * ([cropForBubble]). 권장 네모가 사진 가로의 45% 쯤이라 512px 썸네일에서 잘라내면
 * 200px 남짓이고, 실기기에서 60dp 짜리 말풍선이 됐다 — 무엇을 보냈는지가 안 보인다.
 *
 * 높이는 비율대로 두고 상한만 건다. 고정하면 세로로 긴 조각이 잘려서, 자른 자리를
 * 보여 주려다 또 자르는 셈이 된다.
 */
@Composable
private fun PhotoBubble(image: Bitmap) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "보낸 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(PHOTO_BUBBLE_WIDTH)
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)),
        )
    }
}

/**
 * 내가 올린 사진 말풍선의 폭.
 *
 * 화면 폭의 절반쯤이다. [Photo.THUMB_EDGE] 가 "말풍선이 화면 폭의 절반쯤" 이라는
 * 이유로 512 를 잡고 있으므로, **이 숫자를 키우면 그쪽도 같이 봐야 한다** — 잘린
 * 조각을 더 크게 늘리면 그만큼 부드러워진다.
 */
private val PHOTO_BUBBLE_WIDTH = 200.dp

/**
 * 진단 결과 말풍선.
 *
 * **병명을 하나 골라 크게 쓰지 않는다.** 저쪽 계약에 "1등" 필드가 없는 것이 그 이유고
 * (`src/agent.py` 의 `contract()` 주석), 여기서 우리가 분포의 첫 줄을 뽑아 제목으로
 * 올리면 계약이 막으려던 일을 앱이 대신 하는 셈이 된다. 분포는 **분포로만** 그린다.
 *
 * 문장 셋(머리말·본문·권고)도 서버 것을 그대로 옮긴다. 앱에서 고쳐 쓰면 모델이 아는
 * 한계와 화면이 하는 말이 갈라진다.
 */
@Composable
private fun ReportBubble(report: ScreeningReport, avatar: DogBreed?) {
    val accent = when (report.verdict) {
        ScreeningReport.Verdict.ABNORMAL -> DaengPinkDeep
        ScreeningReport.Verdict.NORMAL -> TextDark
        ScreeningReport.Verdict.RETAKE -> TextMuted
    }
    Row(verticalAlignment = Alignment.Top) {
        ChatFace(avatar, 32.dp)
        Spacer(Modifier.width(8.dp))
        Surface(color = CardWhite, shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ★ 덩어리 경보. **카드에서 제일 먼저 보여야 하는 줄**이라 머리말보다
                //   위에 둔다. 안 뜨면 null 이고 그러면 통째로 안 그린다.
                //
                //   계약 전체가 "병변 이름을 말하지 마라" 인데 **여기만 예외**다.
                //   저쪽 `config.A6_ALERT_MIN` 에 이유가 적혀 있다 — 임상 해설이
                //   "결절·종괴로 오탐하는 건 상대적으로 안전" 이라 했고(병원에 가서
                //   확인하면 되니까) **놓치는 쪽이 훨씬 나쁘다.**
                //
                //   ⚠️ 문턱을 앱에서 다시 재지 않는다. 켤지 말지는 서버가 이미 정했다.
                report.alert?.let { a ->
                    Surface(color = PinkFaint, shape = RoundedCornerShape(12.dp)) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                listOf(a.text, a.action).filter { it.isNotBlank() }.joinToString(" "),
                                color = DaengPinkDeep,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (a.caveat.isNotBlank()) {
                                Text(a.caveat, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }

                Text(
                    report.headline,
                    color = accent,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                )

                report.stage1.abnormalPercent?.let { percent ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("이상 소견 확률", color = TextMuted, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                percent.percentText(),
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        MeterBar(percent / 100f, accent)
                        // 기준값과 "보정 여부"는 저쪽이 굳이 계약에 실어 보낸 값이다.
                        // 보정 안 된 확률은 순서만 뜻이 있고 숫자 자체는 못 믿는다 —
                        // 그걸 빼고 퍼센트만 크게 보여 주면 과하게 믿게 된다.
                        val notes = buildList {
                            // ⚠️ **`기준 X%` 를 여기 넣지 않는다** (2026-09-10). 보호자가
                            //    그 숫자로 할 일이 없고, 확률 옆에 다른 숫자가 붙으면
                            //    둘을 견주게 된다. 계약에는 그대로 온다 — 관리자 콘솔이 쓴다.
                            //    ⚠️ 아래 보정 경고는 **남긴다.** 면책이 아니라 *확률 자체를
                            //       못 믿을 때만* 뜨는 경고다.
                            if (!report.stage1.calibrated) add("보정 전 값이라 순서만 참고하세요")
                        }
                        if (notes.isNotEmpty()) {
                            Text(notes.joinToString(" · "), color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }

                // 계열 한 줄. **null 이면 통째로 안 그린다** — 확신이 모자라면
                // 서버가 아예 안 보낸다 (셋에 하나쯤). 그때는 아래 본문만 남는다.
                //
                // ⚠️ 여기에 긴급도('조기 진료' 같은 말)를 붙이지 않는다. 묶음의
                //    긴급도는 높은 쪽으로 잡혀 있어서, 붙이면 말한 것의 절반이 한
                //    단계 부풀려진다 (저쪽 실측 과잉 52.4%).
                report.group?.let { g ->
                    // ★ 2026-09-10 — **말을 덜어냈다.**
                    //   ⚠️ `g.text`("모양만 보면 …에 가깝습니다")를 **안 그린다.** 바로 아래
                    //      막대의 1등이 같은 이름이라 같은 말이 두 번이었다.
                    //   ⚠️ `g.caveat`("진단이 아닙니다 …")도 **안 그린다.** 같은 뜻의 면책이
                    //      이 카드에 **네 군데**(caveat · 자세히보기 · body · disclaimer) 있었다.
                    //      맨 아래 "자세히 보기" 한 문단만 남긴다.
                    //   ⚠️ 둘 다 계약에는 그대로 온다 — **관리자 콘솔이 쓴다.** 지운 게 아니다.
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        // ★ 병원에서 쓰는 이름 (2026-09-10). `솟아오른 변화` 만 들고 가면
                        //    수의사가 못 알아듣는다. ⚠️ 순서는 코드순 고정이라 확률과 무관하고,
                        //    단정이 아니라 용어 풀이다.
                        if (g.labels.isNotBlank()) {
                            Text(
                                "(${g.labels})",
                                color = TextDark, fontSize = 12.sp, lineHeight = 18.sp,
                            )
                        }
                        // ★ 보호자가 **사진에서 직접 확인할 수 있는** 특징 (2026-09-10).
                        //    이름만 띄우면 자기 개 사진과 대조할 방법이 없다 —
                        //    `표면 변화` 는 뜻이 안 잡히고 `딱지, 둥근 비늘` 은 바로 보인다.
                        if (g.feature.isNotBlank()) {
                            Text(
                                "${g.feature} 같은 모습이 보이는 상태예요.",
                                color = TextDark, fontSize = 12.sp, lineHeight = 18.sp,
                            )
                        }
                    }
                }

                // ★ **계열 문장이 없으면 body 가 그 자리를 채운다** (2026-09-10).
                //   확률 바로 아래 한 문장은 **어떤 판정에서도 비지 않는다** — 그래야
                //   확신이 높든 낮든, 정상이든 재촬영이든 카드의 뼈대가 같다:
                //
                //     이상·확신 있음  `g.feature` "돌기, 넓게 솟은 부위… 상태예요"
                //     이상·확신 낮음  `body`      "이 사진만으로 정확하게 알 수 없습니다."
                //     정상            `body`      "사진으로 확인할 수 있는 범위에는 한계가…"
                //     재촬영          `body`      "이상한 부위가 잘 보이도록 … 다시 찍어주세요."
                //
                //   ⚠️ 그래서 조건이 `group == null` **하나**다. 판정별로 가르지 않는다 —
                //      가르면 새 판정이 생길 때마다 이 자리를 다시 손봐야 한다.
                //   ⚠️ 앱이 문장을 **짓지 않는다.** 둘 다 서버가 준 것이다. 확신이 낮다고
                //      앱이 대신 말을 지어내면 저쪽과 표현이 갈린다.
                if (report.group == null && report.body.isNotBlank()) {
                    Text(report.body, color = TextDark, fontSize = 13.sp, lineHeight = 19.sp)
                }

                // ★ 2026-09-08 — 6종(report.stage2) 대신 **계열 네 묶음**을 그린다.
                //   6종 이름은 저쪽 holdout 커버리지 41.1% 라 못 쓰는데 네 묶음은 66.5% 다.
                //
                //   ⚠️ 자른 게 아니라 **더한 것**이다. 여섯 개가 전부 어딘가에 들어가
                //      있어 숨기는 게 없다 — "상위 몇 개로 자르지 마라" 와 다른 이야기다.
                //   ⚠️ report.stage2 는 그대로 파싱해 두되 **화면에는 안 쓴다.**
                //      관리자 콘솔이 6종을 본다.
                if (report.groups.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        // ⚠️ **"의심 정도" 로 쓰지 않는다** (2026-09-11). `의심` 은 임상적
                        //    의심으로 읽혀서, 네 줄에 의심 순위를 매기는 말이 된다 —
                        //    1등을 안 주기로 한 이유가 그것이다(저쪽 holdout 46.3% 오답).
                        //    게다가 `의심` 은 지금 **덩어리 경보 한 곳에서만** 쓴다
                        //    ("덩어리가 의심됩니다"). 거기 말고도 쓰면 그 한 마디가 흔해진다.
                        //    `모양이` 를 남기는 것이 요점이다 — 무엇과 비슷한지를 못 박는다.
                        Text("모양이 비슷한 정도", color = TextMuted, fontSize = 12.sp)
                        report.groups.forEach { g ->
                            // 전부 같은 글꼴·같은 굵기다. 첫 줄만 굵게 하면 그게 곧
                            // "1등" 이라, 계약이 그 필드를 안 준 뜻이 없어진다.
                            //
                            // 이름과 막대를 **위아래로** 둔다. 옆으로 나란히 두면
                            // 이름이 두 줄로 접히면서 막대와 높이가 어긋난다 —
                            // 이름은 저쪽 표에서 오므로 길이를 우리가 정할 수 없다.
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        g.name,
                                        color = TextDark,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(g.percent.percentText(), color = TextMuted, fontSize = 11.sp)
                                }
                                // ⚠️ **[PinkSoft] 를 쓰지 않는다.** 막대 바탕이 [PinkFaint]
                                //    (#F7EEEA) 인데 PinkSoft 는 #FBE4E0 라 **대비가
                                //    1.06:1** 이다 — 실기기에서 막대가 배경과 안 갈렸다
                                //    (2026-09-09 확인). [DaengPink] 는 2.12:1 이다.
                                //
                                //    네 줄 **모두 같은 색**인 것은 그대로다 — 1등을
                                //    강조하지 않는다는 규칙은 색이 진해져도 안 깨진다.
                                //    1단계 막대([DaengPinkDeep], 2.65:1)보다는 한 단계
                                //    옅게 둬서 무엇이 주인지도 남긴다.
                                MeterBar(g.percent / 100f, DaengPink)
                            }
                        }
                    }
                }

                // ★ 이 카드에서 **유일한 행동**이라 접지 않는다 (2026-09-10). 접으면
                //   안 펴는 사람에게는 아무 말도 안 한 것이 된다. 다만 면책을 걷어내고 나면
                //   혼자 굵을 이유가 없어서 **굵기를 뺐다** — 위 헤드라인만 강조로 남긴다.
                //   ⚠️ **정상에서는 안 그린다** (2026-09-11) — 정상 `body` 가 이미 같은 권고로
                //      끝나서 두 번 말하게 된다. 규칙은 [ScreeningReport.showsAction] 한 곳이다.
                if (report.showsAction) {
                    Text(report.action, color = TextDark, fontSize = 13.sp, lineHeight = 19.sp)
                }

                // ★ 면책은 **여기 한 곳뿐이다** (2026-09-10). 예전에는 같은 뜻이 네 군데였다.
                //   ⚠️ `report.group` **밖에** 둔다. 안에 두면 확신이 낮아 `group` 이 null 인
                //      날에는 면책이 통째로 사라진다 — 확신이 낮을수록 더 필요한 말인데.
                //   ⚠️ `report.disclaimer` 를 여기 겹쳐 쓰지 않는다. 계약에는 그대로 오고
                //      **관리자 콘솔이 쓴다** — 화면에서 뺀 것이지 지운 것이 아니다.
                //   ⚠️ `g.detail`(수의학적 의미)도 넣지 않는다. "주로 일차 병변…" 은 보호자가
                //      읽을 문장이 아니다. 접어 뒀다고 아무 말이나 넣어도 되는 자리가 아니다.
                run {
                    val open = remember { mutableStateOf(false) }
                    Text(
                        if (open.value) "접기" else "자세히 보기",
                        color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp,
                        modifier = Modifier.clickable { open.value = !open.value },
                    )
                    if (open.value) {
                        Text(
                            "이 결과는 사진에서 관찰되는 피부 형태를 분류한 스크리닝 " +
                                "정보이며 질병을 진단하지 않습니다. 정확한 원인 확인에는 " +
                                "수의사의 신체검사와 피부 세포검사, 피부 긁기 검사 또는 " +
                                "조직검사 등이 필요할 수 있습니다.",
                            color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp,
                        )
                        // ★ 네 묶음이 **병원에서 뭐라고 불리는지** (2026-09-11).
                        //   본문의 괄호는 1등에만 붙어서, 확신이 낮아 `group` 이 null 인
                        //   날에는 들고 갈 말이 하나도 없었다 — 하필 그때가 막대만 남는 때다.
                        //
                        //   ⚠️ **이름과 라벨을 한 줄에 두지 않는다.** 묶음 이름에도
                        //      (`피부 표면·색·두께 변화`) 라벨에도 `·` 가 있어서, 한 줄에
                        //      두면 어디서 끊기는지 안 보인다. 줄을 나누고 **색으로** 가른다.
                        //   ⚠️ 카드 본문에 넣지 않는다. 네 줄이 다섯 줄을 차지한다 —
                        //      접혀 있으니 펼친 사람만 본다.
                        val named = report.groups.filter { it.labels.isNotBlank() }
                        if (named.isNotEmpty()) {
                            Column(
                                Modifier.padding(top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "병원에서 쓰는 이름",
                                    color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp,
                                )
                                named.forEach { g ->
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            g.name,
                                            color = TextDark, fontSize = 11.sp, lineHeight = 16.sp,
                                        )
                                        Text(
                                            g.labels,
                                            color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 결과 말풍선 — **세 경우를 같이 본다.** 셋이 다른 화면이라 하나만 보면 못 잡는다.
 *
 * 1. 덩어리 경보 + 계열 한 줄
 * 2. 계열 한 줄만 (경보 없음)
 * 3. **확신이 낮아 계열 한 줄이 없는 경우** — 셋에 하나쯤 이 모양이다.
 *    막대만 남고 문장이 사라지는데, 그때도 카드가 허전해 보이지 않는지 본다
 *
 * 여기서 보는 것은 자리와 무게다. 긴 계열 이름이 접히는지, 경보 상자가 머리말을
 * 밀어내지 않는지. 실기기 색감은 폰에서 본다.
 */
@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0, heightDp = 1100)
@Composable
private fun ReportBubblePreview() {
    fun report(
        groups: List<ScreeningReport.Group>,
        group: ScreeningReport.GroupLine?,
        alert: ScreeningReport.Alert?,
        verdict: ScreeningReport.Verdict = ScreeningReport.Verdict.ABNORMAL,
        headline: String = "피부에 이상 소견이 보입니다.",
        // 확신이 낮아 `group` 이 null 인 날 확률 아래를 채우는 문장.
        body: String = "이 사진만으로 정확하게 알 수 없습니다.",
        action: String = "수의사 진료를 받아보시기를 권합니다.",
    ) = ScreeningReport(
        contractVersion = "1.0",
        verdict = verdict,
        headline = headline,
        body = body,
        action = action,
        stage1 = ScreeningReport.Stage1(83.0f, 14.7f, calibrated = true),
        stage2 = emptyList(),
        groups = groups,
        group = group,
        alert = alert,
        disclaimer = "이 결과는 수의학적 진단이 아니며, 수의사의 진료를 대체하지 않습니다.",
    )

    // 서랍의 "병원에서 쓰는 이름" 이 실제 화면과 같게 보이도록 라벨을 채운다.
    fun g(name: String, percent: Float, labels: String) =
        ScreeningReport.Group(name, percent, labels)
    val RAISED = "솟아오른 변화" to "구진·플라크·농포·여드름"
    val SURFACE = "피부 표면·색·두께 변화" to "비듬·각질·상피성잔고리·태선화·과다색소침착"
    val ERODED = "벗겨지거나 패인 상처" to "미란·궤양"
    val LUMP = "깊거나 단단한 혹" to "결절·종괴"
    fun row(p: Pair<String, String>, percent: Float) = g(p.first, percent, p.second)

    val surface = listOf(
        row(SURFACE, 75.0f), row(RAISED, 17.0f), row(ERODED, 5.0f), row(LUMP, 3.0f),
    )
    val lump = listOf(
        row(LUMP, 62.0f), row(SURFACE, 25.0f), row(RAISED, 9.0f), row(ERODED, 4.0f),
    )
    val flat = listOf(
        row(SURFACE, 39.0f), row(RAISED, 37.0f), row(ERODED, 13.0f), row(LUMP, 11.0f),
    )
    val caveat = "진단이 아닙니다. 같은 계열 안에서도 원인 질환은 여럿입니다."

    DaengsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ReportBubble(
                report(
                    lump,
                    ScreeningReport.GroupLine(
                        "깊거나 단단한 혹", 62.0f, "모양만 보면 깊거나 단단한 혹에 가깝습니다.", caveat,
                        feature = "피부 안쪽 또는 표면의 덩어리",
                        labels = "결절·종괴",
                    ),
                    ScreeningReport.Alert(
                        "A6", "덩어리가 의심됩니다.", "빠른 진료를 권합니다.",
                        "진단이 아닙니다. 덩어리처럼 보이는 다른 것일 수 있습니다.",
                        0.72f, 0.40f,
                    ),
                ),
                DogBreed.BEAGLE,
            )
            ReportBubble(
                report(
                    surface,
                    ScreeningReport.GroupLine(
                        "피부 표면·색·두께 변화", 75.0f, "모양만 보면 피부 표면·색·두께 변화에 가깝습니다.", caveat,
                        feature = "딱지, 둥근 비늘, 검어진 피부",
                        labels = "비듬·각질·상피성잔고리·태선화·과다색소침착",
                    ),
                    null,
                ),
                DogBreed.BEAGLE,
            )
            // 확신이 낮은 경우 — 계열 문장 대신 `body` 가 그 자리를 채운다.
            // **뼈대는 위 둘과 같다**: 헤드라인 · 확률 · 한 문장 · 막대 · 권고 · 자세히 보기
            ReportBubble(report(flat, null, null), DogBreed.BEAGLE)
            // 정상 — 막대가 없고, 같은 자리를 `body` 가 채운다
            ReportBubble(
                report(
                    emptyList(), null, null,
                    verdict = ScreeningReport.Verdict.NORMAL,
                    headline = "뚜렷한 피부 병변 소견은 보이지 않습니다.",
                    body = "사진으로 확인할 수 있는 범위에는 한계가 있습니다. " +
                        "가려워하거나, 냄새가 나거나, 계속 핥는 등 평소와 다른 행동이 " +
                        "있다면 결과와 무관하게 병원에 가보시는 것을 권합니다.",
                    action = "평소와 다른 점이 있으면 진료를 받아보세요.",
                ),
                DogBreed.BEAGLE,
            )
        }
    }
}

/** 0~1 을 채우는 막대. 값이 0 이어도 바탕은 남아서 "재긴 쟀다"가 보인다. */
@Composable
private fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(PinkFaint)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

/** 소수 첫째 자리. 서버가 이미 반올림해 주지만 Float 를 그냥 찍으면 12.300001 이 된다. */
private fun Float.percentText(): String = String.format("%.1f%%", this)

/** [ChatScreen] 의 위치-CLARIFY 액션이 청하는 권한. `WalkRoute.kt` 의 것과 같다. */
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * 진단 서버 주소가 없을 때. **문장을 여기서 새로 쓰지 않는다** — [ScreeningRun] 도
 * 같은 상황에서 사용자에게 말해야 해서, 갈라 두면 두 문장이 서로 다른 주소를 가리킨다
 * (예전 `daengs.screenUrl` 이 그렇게 남아 있었다).
 */
private val SCREEN_NOT_SET = ScreeningRun.NOT_CONFIGURED

/**
 * 보행이 꺼져 있을 때 (`daengs.gaitUrl`, 릴리즈는 `daengs.gaitUrlRelease` 가 빈 경우).
 *
 * ⚠️ **꺼졌을 때 조용히 [MockGaitAnalyzer] 로 도는 것을 막으려고 있는 문구다.** 그러면
 *    4단계가 다 차오르고 결과 카드까지 떠서, 서버를 한 번도 안 거쳤는데 분석이 끝난 것처럼
 *    보인다. 테스터에게 그 화면이 나가면 안 된다 (#64).
 */
private const val GAIT_NOT_SET = "보행 분석은 아직 준비 중이에요."

/**
 * 카메라 권한을 거부했을 때.
 *
 * **시스템 카메라로 떨어질 수 없다.** 매니페스트에 `CAMERA` 를 선언한 앱은 그 권한이
 * 없으면 `ACTION_IMAGE_CAPTURE` 조차 못 띄운다 — 안드로이드가 막는다. 그래서 대신
 * 갤러리를 가리킨다. 그쪽은 권한 없이 된다.
 */
private const val CAMERA_DENIED =
    "카메라 권한이 꺼져 있어요. 설정에서 켜거나, 갤러리에서 골라 주세요."

/**
 * [AiActionDialog] 를 여는 두 자리.
 *
 * '+' 버튼은 늘 [Full] 이다. [SkinOnly] 는 서버 skin HANDOFF 가 열 때만 쓴다 —
 * 그 문답은 이미 피부 얘기였으니 보행 묶음을 보여줄 이유가 없다.
 */
private enum class ChooserMode { Full, SkinOnly }

/**
 * 카메라 버튼이 여는 시트. **기능이 둘이라 묶음으로 나눈다.**
 *
 * 피부는 사진, 보행은 영상이라 고르는 소재가 다르다. 한 줄로 넷을 늘어놓으면
 * "사진찍기"와 "영상 촬영"이 같은 층으로 보여서 무엇을 하는 화면인지가 흐려진다.
 * 묶음 제목을 먼저 읽고 그 안에서 고르는 순서가 되도록 카드를 갈랐다.
 *
 * @param skinOnly true 면 보행 묶음을 감춘다 ([ChooserMode.SkinOnly] 참고). '+'
 *   버튼에서 열 때는 항상 false 라 기존 동작은 그대로다.
 */
@Composable
private fun AiActionDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onAttach: () -> Unit,
    onGaitCapture: () -> Unit,
    onGaitPick: () -> Unit,
    skinOnly: Boolean = false,
    /** 지난 기록으로. null 이면 줄을 안 그린다 (로그인 안 한 기기). */
    onOpenHistory: (() -> Unit)? = null,
    /** 보행 쪽 지난 기록으로. null 이면 줄을 안 그린다 (남긴 기록이 없을 때). */
    onOpenGaitHistory: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = CardWhite, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                Text(
                    "AI 기능 선택",
                    color = TextDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
                )
                Surface(color = PinkFaint, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        SourceGroup("피부 사진으로 살펴보기") {
                            SourceRow(DaengsIcon.Camera, "사진찍기", onCamera)
                            RowSeparator()
                            SourceRow(DaengsIcon.Gallery, "첨부하기", onAttach)
                            if (onOpenHistory != null) {
                                RowSeparator()
                                // 진단은 말풍선으로 지나가고 사라진다. 지난 것을
                                // 나란히 놓고 보는 자리로 가는 길이다.
                                SourceRow(DaengsIcon.Gallery, "지난 기록 보기", onOpenHistory)
                            }
                            RowSeparator()
                            // 어떻게 찍어야 쓸 수 있는 사진이 되는지는 **고르기 전에**
                            // 알려야 한다. 보행 묶음이 같은 이유로 아래 줄을 달고 있다.
                            //
                            // 멀리서 찍은 사진은 네모를 아무리 맞춰도 "너무 작아요" 에
                            // 걸린다. 그 밴드는 서버 판정의 사본이라 앱만 풀어줘도
                            // 서버가 재촬영으로 돌려보낸다 ([Band] 참고).
                            Text(
                                Band.CAPTURE_HINT,
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                        if (!skinOnly) {
                            DashedSeparator()
                            SourceGroup("보행 영상 분석하기") {
                                SourceRow(DaengsIcon.Video, "영상 촬영", onGaitCapture)
                                RowSeparator()
                                SourceRow(DaengsIcon.VideoLibrary, "불러오기", onGaitPick)
                                if (onOpenGaitHistory != null) {
                                    RowSeparator()
                                    // 피부 묶음의 "지난 기록 보기" 와 같은 자리. 분석 카드는
                                    // 대화 위로 흘러가 버려서, 지난 영상을 다시 열 길이 이것뿐이다.
                                    SourceRow(DaengsIcon.Gallery, "지난 기록 보기", onOpenGaitHistory)
                                }
                                RowSeparator()
                                // 어떻게 찍어야 쓸 수 있는 영상이 되는지는 **고르기 전에**
                                // 알려야 한다. 찍고 나서 알려주면 다시 찍어야 한다.
                                //
                                // 숫자를 여기 박지 않는다. 저쪽 기준(§21)에서 끌어낸
                                // 상수라, 박아 두면 기준이 바뀌어도 이 줄만 안 따라온다 —
                                // 실제로 "10초 이상" 이 그렇게 남아 있었다.
                                Text(
                                    "💡 뒤에서 걷는 모습 / ${GaitRecord.RECOMMENDED_SECONDS}초 내외로 권장",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 묶음 제목 + 흰 카드 하나. 제목은 카드 밖에 둬서 카드가 목록임이 드러난다. */
@Composable
private fun SourceGroup(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        color = TextDark,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 8.dp),
    )
    Surface(color = CardWhite, shape = RoundedCornerShape(14.dp)) {
        Column { content() }
    }
}

@Composable
private fun RowSeparator() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(DaengsColors.BorderNeutral))
}

/** 묶음 사이. 실선으로 그으면 카드 테두리와 같은 층으로 읽혀서 점선으로 둔다. */
@Composable
private fun DashedSeparator() {
    Canvas(Modifier.fillMaxWidth().height(21.dp)) {
        val y = size.height / 2f
        drawLine(
            color = DaengsColors.BorderNeutral,
            start = Offset(6.dp.toPx(), y),
            end = Offset(size.width - 6.dp.toPx(), y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
    }
}

@Composable
private fun SourceRow(icon: DaengsIcon, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaengsIconView(icon, Modifier.size(21.dp), tint = DaengPink)
        Spacer(Modifier.width(14.dp))
        Text(label, color = TextDark, fontSize = 15.sp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun AiActionDialogPreview() {
    // Dialog 는 @Preview 에 안 그려져서 속만 그대로 띄운다.
    Surface(color = CardWhite, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Text(
                "AI 기능 선택",
                color = TextDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            )
            Surface(color = PinkFaint, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    SourceGroup("피부 사진으로 살펴보기") {
                        SourceRow(DaengsIcon.Camera, "사진찍기") {}
                        RowSeparator()
                        SourceRow(DaengsIcon.Gallery, "첨부하기") {}
                        RowSeparator()
                        Text(
                            Band.CAPTURE_HINT,
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                    DashedSeparator()
                    SourceGroup("보행 영상 분석하기") {
                        SourceRow(DaengsIcon.Video, "영상 촬영") {}
                        RowSeparator()
                        SourceRow(DaengsIcon.VideoLibrary, "불러오기") {}
                        RowSeparator()
                        Text(
                            "💡 뒤에서 걷는 모습 / ${GaitRecord.RECOMMENDED_SECONDS}초 내외로 권장",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 아직 안 되는 것을 알리는 자리. 눌러도 조용하면 고장으로 읽힌다. */
@Composable
private fun NoticeDialog(message: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = CardWhite, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text(message, color = TextDark, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "확인",
                    color = DaengPink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onDiagnose: () -> Unit,
    /** 물어보는 중인가. 보내기 단추를 눌러도 안 되는 상태를 **눈에도 보이게** 한다. */
    busy: Boolean = false,
) {
    Surface(color = CardWhite, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 진단 버튼은 입력칸 **왼쪽**이다. 오른쪽은 보내기 자리라, 셋을 그쪽에
            // 몰아 세우면 보내려다 카메라가 열린다. 왼쪽은 늘 비어 있던 자리다.
            InputAction(DaengsIcon.Camera, onDiagnose)
            Box(
                Modifier.weight(1f).height(48.dp).background(PinkFaint, RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, end = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text("메시지를 입력하세요", color = TextMuted, fontSize = 14.sp)
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextDark, fontSize = 14.sp),
                            cursorBrush = SolidColor(DaengPink),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // 음성은 "입력하세요" 바로 옆이다 — 말로 넣는 것도 입력이라,
                    // 입력칸 안에 있는 편이 무엇을 대신하는 버튼인지 바로 읽힌다.
                    InputAction(DaengsIcon.Mic, onVoice, size = 36.dp, iconSize = 19.dp)
                }
            }
            Spacer(Modifier.width(6.dp))
            // 눌러도 아무 일이 없으면 고장으로 읽힌다. 잠긴 동안은 흐리게 둔다.
            val sendable = value.isNotBlank() && !busy
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50))
                    .background(if (sendable) DaengPink else PinkSoft)
                    .clickable(enabled = sendable, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { DaengsIconView(DaengsIcon.Send, Modifier.size(22.dp), tint = CardWhite) }
        }
    }
}

/**
 * 입력줄의 아이콘 버튼.
 *
 * 그림은 작아도 **누르는 자리는 넉넉히** 잡는다. 여기서 빗나가면 옆의 입력칸이
 * 눌려서 키보드가 올라온다.
 */
@Composable
private fun InputAction(
    icon: DaengsIcon,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { DaengsIconView(icon, Modifier.size(iconSize), tint = TextMuted) }
}

@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun ChatScreenPreview() {
    DaengsTheme { ChatScreen({}) }
}

/**
 * 말하는 쪽 얼굴. 헤더와 말풍선이 다 여기를 지난다.
 *
 * **`smart = true` 다.** 여기 뜨는 것은 내 개가 아니라 "댕스 AI" 다. 헤더에 그렇게
 * 적어 놓고 얼굴만 내 개면 누가 말하는 건지 흐려진다. 견종은 대표 강아지를 따라가므로
 * 남의 개도 아니다 — 같은 견종의, 학사모 쓴 다른 얼굴이다.
 *
 * **모르는 견종(믹스)이면 발자국이다.** 아무 얼굴이나 골라 쓰면 사용자는 자기 개가
 * 아닌 얼굴과 대화하게 된다 (마이 탭 `PetFace` 와 같은 규칙).
 */
@Composable
private fun ChatFace(avatar: DogBreed?, size: Dp) {
    if (avatar != null) DogAvatar(avatar, Modifier.size(size), face = DogFace.Smart)
    else PawAvatar(size = size)
}
