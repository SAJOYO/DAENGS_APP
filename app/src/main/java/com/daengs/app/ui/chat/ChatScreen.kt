package com.daengs.app.ui.chat

import android.Manifest
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.daengs.app.assistant.WalkVerdict
import com.daengs.app.gait.GaitApi
import com.daengs.app.gait.GaitComparison
import com.daengs.app.gait.GaitProgress
import com.daengs.app.gait.GaitRecord
import com.daengs.app.gait.GaitVideo
import com.daengs.app.gait.rememberGaitHolder
import com.daengs.app.location.FusedLocationSource
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.screening.Photo
import com.daengs.app.screening.PreparedPhoto
import com.daengs.app.screening.ScreeningApi
import com.daengs.app.screening.ScreeningReport
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.camera.CameraPreview
import com.daengs.app.ui.camera.hasCameraPermission
import com.daengs.app.ui.camera.rememberCameraController
import com.daengs.app.ui.camera.rememberVideoRecorder
import com.daengs.app.ui.camera.takePicture
import com.daengs.app.ui.gait.GaitCaptureScreen
import com.daengs.app.ui.gait.GaitCompareScreen
import com.daengs.app.ui.gait.GaitDetailScreen
import com.daengs.app.ui.gait.GaitIntroCard
import com.daengs.app.ui.gait.GaitPickSheet
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
private sealed interface ChatEntry {
    data class Mine(val text: String) : ChatEntry

    data class Theirs(val text: String) : ChatEntry

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

/**
 * 대화 UI 전용 화면. 자유 텍스트는 `POST /assistant/query` 오케스트레이션으로 간다 —
 * 자연어 해석·능력 실행·집계는 전부 저쪽이 하고, 앱은 상태(`status`)와
 * `handoffs` 만 보고 화면을 고른다. **여기서 텍스트를 보고 갈래를 나누지 않는다**
 * (예전 `GAIT_ASK` 키워드 라우팅은 그래서 지웠다).
 *
 * 사진 진단은 다르다 — 계약이 이미 있어서([ScreeningApi]) 실제로 부른다. 다만 서버
 * 주소가 아직 없어, 주소가 비어 있으면 버튼이 스스로 그렇게 말한다.
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    /** 대표 강아지 얼굴. 모르는 견종(믹스)이거나 아직 못 받았으면 null 이다. */
    avatar: DogBreed? = null,
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
    val scroll = rememberScrollState()
    // null 이면 닫힘. [ChooserMode.SkinOnly] 는 서버 skin HANDOFF 가 연 것이라
    // 보행 묶음을 감춘다 — 사용자가 그 질문에서 보행을 고를 이유가 없다.
    var chooserMode by remember { mutableStateOf<ChooserMode?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

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

    // 프레임까지 맞춘 사진을 대화에 올리고 서버에 물어본다.
    //
    // **말풍선을 먼저 올리고 자리를 잡아 둔다.** 응답을 기다렸다가 한꺼번에 올리면
    // 몇 초 동안 아무 일도 안 일어난 화면이 된다 — 모델이 CPU 로 돌면 사진 한 장에
    // 1~3초 걸린다고 저쪽이 적어 뒀고, 첫 요청은 가중치를 올리느라 더 걸린다.
    val send: (PreparedPhoto, FloatArray) -> Unit = { photo, box ->
        scope.launch {
            entries += ChatEntry.MyPhoto(photo.thumbnail)
            val slot = entries.size
            entries += ChatEntry.Screening
            ScreeningApi.screen(photo.jpeg, box)
                .onSuccess { entries[slot] = ChatEntry.Report(it) }
                .onFailure { entries[slot] = ChatEntry.Failed(it.message ?: "진단 서버에 닿지 못했어요.") }
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

    /** 나란히 보는 중. */
    var gaitComparing by remember { mutableStateOf<GaitComparison?>(null) }

    /** 상세를 보는 중인 기록 id. */
    var gaitDetail by remember { mutableStateOf<String?>(null) }

    /**
     * 영상 한 편을 대화에 태운다.
     *
     * 자리 잡는 방식은 사진 진단([send])과 같다 — 진행 카드를 먼저 올려 두고 그 자리를
     * 갈아 끼운다. 다만 끝났을 때 **진행 카드를 결과 카드로 바꾸지 않고, 완료 말풍선으로
     * 바꾼 뒤 결과 카드를 새로 얹는다.** 네 줄이 다 초록으로 찬 카드가 대화에 그대로
     * 남아 있으면, 아래에 붙은 결과 카드와 어느 쪽이 지금 것인지 겹쳐 보인다.
     */
    val runGait: (Uri) -> Unit = { uri ->
        scope.launch {
            GaitVideo.prepare(context, uri)
                .onFailure { notice = it.message ?: "영상을 읽지 못했어요." }
                .onSuccess { video ->
                    entries += ChatEntry.Theirs("영상이 준비되었어요!\n이제 보행 분석을 시작할게요.")
                    val slot = entries.size
                    entries += ChatEntry.GaitRunning(GaitProgress.START)
                    val record = gait.analyze(video) { entries[slot] = ChatEntry.GaitRunning(it) }
                    if (record == null) {
                        entries[slot] = ChatEntry.Failed(gait.error ?: "보행 영상을 분석하지 못했어요.")
                        gait.clearError()
                    } else {
                        entries[slot] = ChatEntry.Theirs("분석이 완료되었어요!\n결과를 확인해볼까요?")
                        entries += ChatEntry.GaitDone(record.id)
                    }
                }
        }
    }

    /**
     * 자유 텍스트 한 줄을 오케스트레이션에 보낸다.
     *
     * **여기서 답을 지어내지 않는다.** 로그인이 안 됐을 때만 로컬 문구를 쓰고,
     * 나머지는 전부 서버가 준 `message`/`clarify`/`handoffs` 를 그대로 옮긴다.
     * 대화 기록도 안 보낸다 — v1 오케스트레이션은 상태가 없다.
     */
    // 물어보는 중인가. **연타를 막는다** — 한 번이 의미 라우터 + 생성이라 값이 비싸고,
    // 두 번 누르면 90초짜리 요청이 둘 뜬 채 답이 뒤섞여 돌아온다.
    var asking by remember { mutableStateOf(false) }

    val sendQuery: (String) -> Unit = { text ->
        entries += ChatEntry.Mine(text)
        val slot = entries.size
        entries += ChatEntry.Thinking
        asking = true
        scope.launch {
            val token = accessTokenProvider()
            if (token == null) {
                entries[slot] = ChatEntry.Failed("로그인이 필요해요. 다시 로그인해 주세요.")
                asking = false
                return@launch
            }
            // 지금 있는 곳. **못 구해도 질문은 그냥 보낸다** — 위치가 필요한 질문은
            // 일부고, 좌표 때문에 훈련 질문까지 막으면 안 된다. 서버는 위치가
            // 필요한데 없으면 CLARIFY 로 되묻는데, 이어서 묻는 토큰이 없어서
            // (무상태) 그 되묻기는 사용자에게 막다른 길이다. 그래서 미리 싣는다.
            val where = runCatching { fused.currentLocation().point }.getOrNull()
            AssistantApi.query(token, text, where)
                .onSuccess { response ->
                    // 대기 자리를 답으로 갈아 끼운다. 산책만 물었으면 저쪽 한 줄
                    // ("현재 산책 판단: GOOD") 대신 대화체 문장을 쓰고 ([walkSentence]),
                    // 근거와 시간대는 아래 카드가 맡는다.
                    entries[slot] = ChatEntry.Theirs(
                        response.walkSentence() ?: response.bubbleMessage(),
                    )
                    response.walkCard()?.let { entries += ChatEntry.WalkCard(it) }
                    when (response.knownHandoff()) {
                        // 실행하지 않는다 — 기존 흐름을 그대로 연다. 보행은 카드를
                        // 하나 더 얹고, 피부는 이미 있는 선택 시트를 스킨 전용으로 연다.
                        KnownHandoff.GAIT -> entries += ChatEntry.GaitIntro
                        KnownHandoff.SKIN -> chooserMode = ChooserMode.SkinOnly
                        null -> Unit
                    }
                }
                .onFailure { entries[slot] = ChatEntry.Failed(it.message ?: "AI 서버에 닿지 못했어요.") }
            asking = false
        }
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
        ChatHeader(onBack = onBack, avatar = avatar)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entries.isEmpty()) {
                AssistantBubble(
                    "반려견의 산책, 건강, 생활을 무엇이든 물어보세요.",
                    avatar,
                )
            } else {
                entries.forEach { entry ->
                    when (entry) {
                        is ChatEntry.Mine -> UserBubble(entry.text)
                        is ChatEntry.Theirs -> AssistantBubble(entry.text, avatar)
                        ChatEntry.Thinking -> AssistantBubble("생각하는 중이에요…", avatar)
                        is ChatEntry.MyPhoto -> PhotoBubble(entry.image)
                        ChatEntry.Screening -> AssistantBubble("사진을 살펴보는 중이에요…", avatar)
                        is ChatEntry.Failed -> AssistantBubble(entry.message, avatar)
                        is ChatEntry.Report -> ReportBubble(entry.report, avatar)

                        // 보행 카드는 **말풍선 안에 안 넣는다.** 카드가 이미 흰
                        // 바탕에 테두리를 가져서, 말풍선을 한 겹 더 두르면 흰 상자
                        // 안의 흰 상자가 된다. 대신 아바타 자리만큼 왼쪽을 비워
                        // 두어 "AI 가 준 것" 이라는 줄맞춤은 지킨다.
                        is ChatEntry.WalkCard -> BesideAvatar {
                            WalkVerdictCard(entry.verdict)
                        }

                        ChatEntry.GaitIntro -> BesideAvatar {
                            GaitIntroCard(
                                onCapture = { gaitCapture = true },
                                onPick = startGaitPicking,
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
            busy = asking,
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

    chooserMode?.let { mode ->
        AiActionDialog(
            skinOnly = mode == ChooserMode.SkinOnly,
            onDismiss = { chooserMode = null },
            onCamera = {
                chooserMode = null
                when {
                    // 설정이 없을 때 화면이 스스로 알려 주는 결은 랜딩의 카카오
                    // 로그인 버튼과 같다.
                    !ScreeningApi.configured -> notice = SCREEN_NOT_SET
                    target == null -> notice = "카메라를 열 수 없어요."
                    // 앱 안에서 찍는다. 그래야 병변에 맞출 네모를 찍는 동안 보여 준다.
                    else -> withCamera { skinCapture = true }
                }
            },
            onAttach = {
                chooserMode = null
                guidedShot = false
                if (!ScreeningApi.configured) {
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
                recording = recorder.recording,
                preview = { CameraPreview(controller, Modifier.fillMaxSize()) },
            )
        }
    }

    gaitDetail?.let { id ->
        gait.find(id)?.let { record ->
            GaitDetailScreen(
                record = record,
                canCompare = gait.hasComparable(record.id),
                onBack = { gaitDetail = null },
                onCompare = { gaitPicking = record.id },
                onDelete = {
                    // 서버에서도 지운다. 화면은 기다리지 않는다 — 홀더가 먼저 빼고
                    // 실패하면 되돌린다.
                    scope.launch { gait.remove(record.id) }
                    gaitDetail = null
                    // 카드가 가리키던 기록이 없어졌다. 카드를 지우지 않고 자리를
                    // 말풍선으로 바꾼다 — 대화에서 줄이 통째로 사라지면 무엇이
                    // 있었는지 알 수 없다.
                    val slot = entries.indexOfFirst {
                        it is ChatEntry.GaitDone && it.recordId == record.id
                    }
                    if (slot >= 0) {
                        entries[slot] = ChatEntry.Theirs("${record.dateLabel} 보행 기록을 지웠어요.")
                    }
                },
            )
        } ?: run { gaitDetail = null }
    }

    gaitComparing?.let { comparison ->
        GaitCompareScreen(
            comparison = comparison,
            onBack = { gaitComparing = null },
            onOpenDetail = {
                gaitComparing = null
                gaitDetail = comparison.recent.id
            },
            onSaveToChat = {
                gaitComparing = null
                entries += ChatEntry.GaitCompared(comparison)
            },
        )
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
            Text(comparison.verdict.sentence, color = TextDark, fontSize = 13.sp, lineHeight = 19.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다시 보기", color = DaengPinkDeep, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(13.dp), tint = DaengPinkDeep)
            }
        }
    }
}

@Composable
private fun ChatHeader(onBack: () -> Unit, avatar: DogBreed?) {
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
    }
}

/**
 * AI 쪽 말풍선. **여기서만** [assistantMarkdown] 을 부른다 — 이 화면의 다른 텍스트
 * (내 말풍선, 구조화 카드)는 서버 자유 텍스트가 아니라 마크다운을 볼 이유가 없다.
 */
/**
 * 챗봇이 한 말. **길게 누르면 복사된다.**
 *
 * 메신저와 같은 손버릇이라 따로 안내하지 않아도 찾는다. 말풍선마다 복사 아이콘을
 * 달면 대화가 길어질수록 화면이 아이콘으로 덮인다.
 *
 * 진행("생각하는 중이에요…")이나 실패 문구도 같이 복사된다. 해로울 것이 없고,
 * 무엇이 진짜 답인지 갈라내려면 분기가 하나 더 는다.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AssistantBubble(text: String, avatar: DogBreed?) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shown = assistantMarkdown(text)
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
                        // ⚠️ **원문이 아니라 화면에 보이는 글자를 담는다.**
                        //    서버 답변은 마크다운이라 원문을 그대로 복사하면 붙여넣은
                        //    곳에 `**굵게**` 의 별표가 같이 간다. [assistantMarkdown] 이
                        //    이미 표시를 걷어낸 문자열을 들고 있으므로 그것을 쓴다.
                        clipboard.setText(AnnotatedString(shown.text))
                        // ⚠️ **안드로이드 13(API 33)부터는 시스템이 알아서 알린다.**
                        //    거기서 우리 것까지 띄우면 "복사됨" 이 두 번 뜬다.
                        //    minSdk 가 26이라 그 아래 기기에서는 우리가 알려야 한다.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, "복사했어요", Toast.LENGTH_SHORT).show()
                        }
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
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = PinkSoft, shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)) {
            Text(text, color = TextDark, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(14.dp))
        }
    }
}

/** 내가 올린 사진. 말풍선 대신 그림 자체가 모서리를 갖는다. */
@Composable
private fun PhotoBubble(image: Bitmap) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "보낸 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 200.dp)
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)),
        )
    }
}

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
                            report.stage1.thresholdPercent?.let { add("기준 ${it.percentText()}") }
                            if (!report.stage1.calibrated) add("보정 전 값이라 순서만 참고하세요")
                        }
                        if (notes.isNotEmpty()) {
                            Text(notes.joinToString(" · "), color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }

                Text(report.body, color = TextDark, fontSize = 13.sp, lineHeight = 19.sp)

                if (report.stage2.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("모델이 비슷하다고 본 정도", color = TextMuted, fontSize = 12.sp)
                        report.stage2.forEach { lesion ->
                            // 전부 같은 글꼴·같은 굵기다. 첫 줄만 굵게 하면 그게 곧
                            // "1등" 이라, 계약이 그 필드를 안 준 뜻이 없어진다.
                            //
                            // 이름과 막대를 **위아래로** 둔다. 옆으로 나란히 두면
                            // "비듬·각질·상피성잔고리" 같은 이름이 두 줄로 접히면서
                            // 막대와 높이가 어긋난다 — 이름은 저쪽 표에서 오므로
                            // 길이를 우리가 정할 수 없다.
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        lesion.nameKo,
                                        color = TextDark,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(lesion.percent.percentText(), color = TextMuted, fontSize = 11.sp)
                                }
                                MeterBar(lesion.percent / 100f, PinkSoft)
                            }
                        }
                    }
                }

                Text(report.action, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (report.disclaimer.isNotBlank()) {
                    Text(report.disclaimer, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
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

private const val SCREEN_NOT_SET =
    "진단 서버가 아직 없어요.\nlocal.properties 의 daengs.screenUrl 을 채우면 열려요."

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
                        }
                        if (!skinOnly) {
                            DashedSeparator()
                            SourceGroup("보행 영상 분석하기") {
                                SourceRow(DaengsIcon.Video, "영상 촬영", onGaitCapture)
                                RowSeparator()
                                SourceRow(DaengsIcon.VideoLibrary, "불러오기", onGaitPick)
                                RowSeparator()
                                // 어떻게 찍어야 쓸 수 있는 영상이 되는지는 **고르기 전에**
                                // 알려야 한다. 찍고 나서 알려주면 다시 찍어야 한다.
                                //
                                // 숫자를 여기 박지 않는다. 저쪽 기준(§21)에서 끌어낸
                                // 상수라, 박아 두면 기준이 바뀌어도 이 줄만 안 따라온다 —
                                // 실제로 "10초 이상" 이 그렇게 남아 있었다.
                                Text(
                                    "💡 뒤에서 걷는 모습 / ${GaitRecord.RECOMMENDED_SECONDS}초 넘게 권장",
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
                    }
                    DashedSeparator()
                    SourceGroup("보행 영상 분석하기") {
                        SourceRow(DaengsIcon.Video, "영상 촬영") {}
                        RowSeparator()
                        SourceRow(DaengsIcon.VideoLibrary, "불러오기") {}
                        RowSeparator()
                        Text(
                            "💡 뒤에서 걷는 모습 / ${GaitRecord.RECOMMENDED_SECONDS}초 넘게 권장",
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
 * 말하는 쪽 얼굴.
 *
 * **모르는 견종(믹스)이면 발자국이다.** 아무 얼굴이나 골라 쓰면 사용자는 자기 개가
 * 아닌 얼굴과 대화하게 된다 (마이 탭 `PetFace` 와 같은 규칙).
 */
@Composable
private fun ChatFace(avatar: DogBreed?, size: Dp) {
    if (avatar != null) DogAvatar(avatar, Modifier.size(size)) else PawAvatar(size = size)
}
