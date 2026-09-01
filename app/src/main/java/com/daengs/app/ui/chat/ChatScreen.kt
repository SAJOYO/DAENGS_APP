package com.daengs.app.ui.chat

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.screening.Photo
import com.daengs.app.screening.PreparedPhoto
import com.daengs.app.screening.ScreeningApi
import com.daengs.app.screening.ScreeningReport
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.home.HomeDemoData
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
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

    /** 내가 올린 사진. 원본이 아니라 줄여 놓은 썸네일이다 ([Photo.THUMB_EDGE]). */
    data class MyPhoto(val image: Bitmap) : ChatEntry

    /** 서버에 물어보는 중. 답이 오면 이 자리가 [Report] 나 [Failed] 로 바뀐다. */
    data object Screening : ChatEntry

    data class Report(val report: ScreeningReport) : ChatEntry

    data class Failed(val message: String) : ChatEntry
}

/**
 * 대화 UI 전용 화면. 실제 RAG 호출은 아직 연결하지 않아, 전송된 질문만 기기 안에서
 * 보여 준다. 네트워크 계약이 붙을 때 이 화면의 [ChatEntry.Theirs] 자리에만 연결하면 된다.
 *
 * 사진 진단은 다르다 — 계약이 이미 있어서([ScreeningApi]) 실제로 부른다. 다만 서버
 * 주소가 아직 없어, 주소가 비어 있으면 버튼이 스스로 그렇게 말한다.
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    /** 대표 강아지 얼굴. 모르는 견종(믹스)이거나 아직 못 받았으면 null 이다. */
    avatar: DogBreed? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    val entries = remember { mutableStateListOf<ChatEntry>() }
    val scroll = rememberScrollState()
    var chooser by remember { mutableStateOf(false) }
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
    // 카메라가 찍어 넣을 자리. **먼저 만들어 두고** 찍기 버튼에서 그대로 쓴다 —
    // 결과 콜백이 성공 여부(Boolean)만 주고 어디에 찍었는지는 안 알려 준다.
    val target = remember { runCatching { Photo.cameraTarget(context) }.getOrNull() }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken && target != null) open(target)
    }

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
                    "반려견의 산책, 건강, 생활을 무엇이든 물어보세요.\n답변 데이터 연결은 준비 중이에요.",
                    avatar,
                )
                Text("추천 질문", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                HomeDemoData.SUGGESTIONS.take(2).forEach { question ->
                    Surface(
                        color = CardWhite,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PinkSoft),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { draft = question },
                    ) {
                        Text(question, color = TextDark, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
                    }
                }
            } else {
                entries.forEach { entry ->
                    when (entry) {
                        is ChatEntry.Mine -> UserBubble(entry.text)
                        is ChatEntry.Theirs -> AssistantBubble(entry.text, avatar)
                        is ChatEntry.MyPhoto -> PhotoBubble(entry.image)
                        ChatEntry.Screening -> AssistantBubble("사진을 살펴보는 중이에요…", avatar)
                        is ChatEntry.Failed -> AssistantBubble(entry.message, avatar)
                        is ChatEntry.Report -> ReportBubble(entry.report, avatar)
                    }
                }
            }
        }
        ChatInput(
            value = draft,
            onValueChange = { draft = it },
            onSend = {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    entries += ChatEntry.Mine(text)
                    entries += ChatEntry.Theirs("AI 답변 연결을 준비 중이에요.")
                    draft = ""
                }
            },
            // 음성은 **아직 껍데기다.** 버튼 자리와 크기를 먼저 잡아 두고, 녹음과
            // 인식이 붙을 때 여기만 갈아 끼운다. 눌러도 아무 일이 없으면 고장으로
            // 보이므로 준비 중이라고 말은 한다.
            onVoice = { notice = "음성 입력은 준비 중이에요." },
            onDiagnose = {
                if (ScreeningApi.configured) {
                    chooser = true
                } else {
                    // 설정이 없을 때 화면이 스스로 알려 주는 결은 랜딩의 카카오
                    // 로그인 버튼과 같다.
                    notice = "진단 서버가 아직 없어요.\nlocal.properties 의 daengs.screenUrl 을 채우면 열려요."
                }
            },
        )
    }

    if (chooser) {
        PhotoSourceDialog(
            onDismiss = { chooser = false },
            onCamera = {
                chooser = false
                if (target == null) notice = "카메라를 열 수 없어요." else capture.launch(target)
            },
            onAttach = {
                chooser = false
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }

    // 가이드 프레임은 대화 **위를 통째로 덮는다.** 사진 한 장에 집중하는 화면이라
    // 뒤가 보이면 네모를 맞추는 일이 흐려진다.
    pending?.let { photo ->
        GuideFrameScreen(
            photo = photo.thumbnail,
            onCancel = { pending = null },
            onConfirm = { box ->
                pending = null
                send(photo, box)
            },
        )
    }

    notice?.let { message -> NoticeDialog(message) { notice = null } }
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

@Composable
private fun AssistantBubble(text: String, avatar: DogBreed?) {
    Row(verticalAlignment = Alignment.Top) {
        ChatFace(avatar, 32.dp)
        Spacer(Modifier.width(8.dp))
        Surface(color = CardWhite, shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)) {
            Text(text, color = TextDark, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(14.dp))
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

/** 사진찍기 / 첨부하기. */
@Composable
private fun PhotoSourceDialog(onDismiss: () -> Unit, onCamera: () -> Unit, onAttach: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = CardWhite, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(
                    "피부 사진으로 살펴보기",
                    color = TextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
                SourceRow(DaengsIcon.Camera, "사진찍기", onCamera)
                SourceRow(DaengsIcon.Gallery, "첨부하기", onAttach)
            }
        }
    }
}

@Composable
private fun SourceRow(icon: DaengsIcon, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaengsIconView(icon, Modifier.size(21.dp), tint = DaengPink)
        Spacer(Modifier.width(14.dp))
        Text(label, color = TextDark, fontSize = 15.sp)
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
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(DaengPink)
                    .clickable(enabled = value.isNotBlank(), onClick = onSend),
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
