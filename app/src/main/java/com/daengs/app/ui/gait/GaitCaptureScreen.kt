package com.daengs.app.ui.gait

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.gait.GaitRecord
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.PetAvatar
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlin.math.min

/**
 * 보행 영상을 찍는 화면.
 *
 * **판정하지 않는다.** 시안에는 가이드가 초록/빨강으로 바뀌는 그림도 있었지만, 그건
 * 강아지가 제자리에 있는지를 센서로 재야 하는 일이고 지금은 그 판정이 없다. 없는
 * 판정을 색으로 흉내 내면 "초록이었는데 왜 못 쓰는 영상이냐" 가 된다 — 그래서 가이드는
 * **한 가지 색으로 가만히 있고**, 맞추는 일은 사람이 한다.
 *
 * ### 프리뷰 자리
 * [preview] 는 비워 둔 슬롯이다. 지금은 [GaitPreviewPlaceholder] 가 들어가 있고,
 * CameraX 를 넣게 되면 `PreviewView` 를 `AndroidView` 로 감싸 여기에 넘기면 된다 —
 * 가이드 오버레이·안내 문구·버튼은 프리뷰 위에 얹혀 있어서 **한 줄도 안 고쳐도 된다.**
 * 지금 CameraX 를 안 넣는 이유는 의존성이 넷(core·camera2·lifecycle·video)이고,
 * 찍는 일 자체는 시스템 카메라([onRecord])로 이미 되기 때문이다.
 *
 * @param onRecord 촬영 단추. 시스템 카메라를 열어 영상을 받아 온다
 * @param onPick 갤러리에서 고르기. 찍을 상황이 아닐 때 여기서 빠져나간다
 */
@Composable
fun GaitCaptureScreen(
    onBack: () -> Unit,
    onRecord: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 대표 강아지 얼굴. 모르는 견종(믹스)이거나 아직 못 받았으면 null 이다. */
    avatar: DogBreed? = null,
    /** 올린 프로필 사진. 여기는 **찍히는 그 아이**라 사진이 있으면 사진이 맞다. */
    photo: ImageBitmap? = null,
    /**
     * 지금 찍는 중인가. 앱 안 카메라로 찍을 때만 뜻이 있다 — 시스템 카메라로 던지면
     * 그쪽 화면이 덮으므로 이 화면은 늘 false 다.
     */
    recording: Boolean = false,
    preview: @Composable BoxScope.() -> Unit = { GaitPreviewPlaceholder() },
) {
    BackHandler(onBack = onBack)

    // 촬영 요령은 접어 둔다. 네 줄을 늘 띄워 두면 프리뷰가 그만큼 줄어서, 정작
    // 맞춰야 할 강아지가 작게 보인다. 필요할 때만 프리뷰 위에 잠깐 덮는다.
    var tips by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        CaptureHeader(onBack = onBack, avatar = avatar, photo = photo)

        // 프리뷰는 남는 높이를 다 먹는다. 고정 비율(16:9 등)로 두면 기기마다
        // 아래 버튼이 밀려 내려가거나 위로 붕 뜬다.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(22.dp)),
        ) {
            preview()
            GaitGuideOverlay(Modifier.fillMaxSize())
            if (tips) GaitTipsPanel(onClose = { tips = false }, modifier = Modifier.align(Alignment.Center))
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (recording) "찍는 중이에요" else "뒤에서 걷는 모습이 보이게 맞춰주세요",
                color = TextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                // 찍는 중에는 **멈추는 법**이 먼저다. 권장 길이는 찍기 전에
                // 정하는 것이라, 이미 찍고 있는 사람에게는 소용이 없다.
                if (recording) {
                    "다 찍었으면 가운데 단추를 한 번 더 누르세요"
                } else {
                    "걷는 모습 ${GaitRecord.MIN_WALKING_SECONDS}초 이상 · " +
                        "${GaitRecord.RECOMMENDED_SECONDS}초 내외 촬영 권장"
                },
                color = TextMuted,
                fontSize = 13.sp,
            )
        }

        // 셋을 같은 무게로 놓지 않는다. 가운데가 이 화면이 하러 온 일이고,
        // 양옆은 빠져나가는 길이라 크기로 순서를 준다.
        Row(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideButton(DaengsIcon.Gallery, "갤러리에서 고르기", onClick = onPick)
            ShutterButton(recording, onRecord)
            SideButton(DaengsIcon.Bulb, "촬영 요령", on = tips) { tips = !tips }
        }
    }
}

@Composable
private fun CaptureHeader(onBack: () -> Unit, avatar: DogBreed?, photo: ImageBitmap?) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = TextDark, fontSize = 34.sp, lineHeight = 30.sp) }
        // 대화 헤더의 ChatFace 와 같은 물러섬이다 — 견종을 모르면 발바닥을 세운다.
        // 여기는 챗봇이 아니라 **지금 찍는 그 아이**라 올린 사진이 있으면 그것을 쓴다.
        PetAvatar(photo, avatar, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("보행 영상 촬영", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("강아지를 가이드 안에 맞춰주세요", color = TextMuted, fontSize = 12.sp)
        }
        Box(Modifier.size(9.dp).background(DaengPink, RoundedCornerShape(50)))
    }
}

/**
 * 카메라가 아직 안 붙은 자리.
 *
 * **검은 네모로 두지 않는다.** 검은 화면은 카메라가 고장 난 것으로 읽힌다. 방 아트와
 * 같은 따뜻한 색을 옅게 깔아서 "여기 그림이 들어올 자리" 로 보이게 하고, 가운데에
 * 무엇을 기다리는지 한 줄 적는다.
 */
@Composable
fun GaitPreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF6B564C), Color(0xFF4A3B36))),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            "카메라 미리보기 자리",
            color = CardWhite.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }
}

/**
 * 강아지를 맞출 자리.
 *
 * 세로로 긴 틀 하나와, 그 안의 **뒷모습 실루엣**이다. 틀만 있으면 어느 방향으로
 * 서야 하는지가 안 담긴다 — 이 기능이 요구하는 건 "가운데" 가 아니라 "뒤에서" 라서,
 * 꼬리가 위로 선 뒷모습을 그려야 그 말이 그림으로 전해진다.
 *
 * 그림은 시안 **개선안 2** 다: 코너 표시가 있는 틀 · 채운 후면 실루엣 · 가운데 세로
 * 점선 · 뒷다리 관절점. 관절점은 분석이 실제로 잡는 점(`GaitJoint` 의 고관절 · 무릎 ·
 * 뒷발, 좌우)과 같은 자리라 "이 점들이 보이게 찍어라" 가 그림으로 전해진다.
 * 크기는 **개선안 1 의 1.1배**다 — 치수와 근거는 [GaitGuide].
 *
 * 선은 **두 번 긋는다.** 먼저 흰색으로 굵게, 그 위에 분홍으로 가늘게. 카메라 화면은
 * 무엇이 찍힐지 모르는 배경이라 한 색으로만 그으면 비슷한 색 위에서 사라진다.
 * 시안은 주황을 썼는데, 흰 테두리를 두르면 앱 색을 그대로 쓰고도 같은 만큼 읽힌다.
 */
@Composable
fun GaitGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val frame = GaitGuide.frame(size.width, size.height)
        val corner = CornerRadius(20.dp.toPx(), 20.dp.toPx())
        val line = 3.dp.toPx()

        // 틀 안만 조금 밝힌다. 바깥을 어둡게 덮으면 프리뷰가 안 보여서
        // 강아지가 틀 밖 어디에 있는지 못 찾는다.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = frame.topLeft,
            size = frame.size,
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = frame.topLeft,
            size = frame.size,
            cornerRadius = corner,
            style = Stroke(line),
        )
        drawCornerMarks(frame, line)

        val dog = GaitGuide.dog(frame)
        val body = smoothPath(GaitGuide.OUTLINE, dog, closed = true)
        // 시안의 채움은 배경 (145,133,123) 위에서 (205,175,170) 이다. PinkSoft 로 풀면
        // 채널마다 알파 0.44~0.57 이라 그 가운데를 쓴다. 프리뷰가 비쳐야 강아지와 겹쳐 본다.
        drawPath(body, PinkSoft.copy(alpha = 0.5f))
        drawGlowLine(body)
        // 점선을 먼저 긋는다. 시안에서 꼬리 가장자리가 점선 위를 지나간다.
        drawSpine(dog)
        GaitGuide.INNER_LINES.forEach { drawGlowLine(smoothPath(it, dog, closed = false)) }
        drawHindLegJoints(dog)
    }
}

/**
 * 촬영 가이드의 치수. **전부 시안에서 픽셀로 잰 값**이다 — 저장소에는 개선안 수치가
 * 따로 없어서, 시안 한 장(1536×1024, 현재 · 개선안 1 · 개선안 2 나란히)이 원본이다.
 * 셋 다 프리뷰 영역 높이는 756px 이다.
 *
 * | | 틀 (px) | 프리뷰 대비 폭 × 높이 | 틀 세로/가로 | 강아지 (px) |
 * | --- | --- | --- | --- | --- |
 * | 개선안 1 | 235×446 | 0.467 × 0.590 | 1.898 | 139×339 |
 * | 개선안 2 | 221×392 | 0.453 × 0.519 | 1.774 | 133×319 |
 *
 * **그림은 개선안 2, 크기는 개선안 1 의 [SCALE] 배다.** 개선안 2 를 틀째로, 비율을 그대로
 * 둔 채 틀 폭이 개선안 1 과 같아지게(×1.063) 키우면 강아지가 133×319 → 141×339 가 되어
 * **개선안 1 의 강아지와 같은 크기**가 된다. 높이까지 개선안 1 에 맞추면 틀 비율이
 * 1.774 → 1.898 로 깨지거나, 틀은 두고 강아지만 키우게 된다.
 * 그 크기를 에뮬레이터에서 본 뒤 틀과 강아지를 함께 [SCALE] 배 더 키웠다.
 *
 * ### 기기마다 비율이 안 바뀌게
 * 예전에는 프리뷰 폭 × 0.50 과 높이 × 0.74 를 **따로** 곱해서, 프리뷰가 길쭉한 기기일수록
 * 틀과 실루엣이 세로로 늘어났다. 이제는 틀 비율 [FRAME_ASPECT] 를 고정하고, 개선안 1 이
 * 차지하던 자리(폭 [WIDTH_SHARE] × 높이 [HEIGHT_SHARE])를 [SCALE] 배 한 자리 안에 들어가는
 * 가장 큰 크기로 잡는다. 먼저 닿는 쪽이 크기를 정한다 — 세로 화면은 대개 폭이다.
 *
 * 선 굵기(틀 3dp · 실루엣 5dp/2.2dp)와 틀 모서리(20dp)는 예전 값을 그대로 쓴다.
 */
internal object GaitGuide {
    /** 틀의 세로 ÷ 가로. 개선안 2 틀 221×392px. */
    const val FRAME_ASPECT = 392f / 221f

    /** 틀이 프리뷰 폭에서 차지하는 몫. 개선안 1 틀 235px ÷ 프리뷰 503px. */
    const val WIDTH_SHARE = 235f / 503f

    /** 틀이 프리뷰 높이에서 차지할 수 있는 몫. 개선안 1 틀 446px ÷ 프리뷰 756px. */
    const val HEIGHT_SHARE = 446f / 756f

    /**
     * 개선안 1 크기에서 틀과 강아지를 함께 키우는 배율.
     *
     * **시안에서 잰 값이 아니다.** 개선안 1 크기를 에뮬레이터 촬영 화면에서 보고 사용자가
     * "약 1.1배" 로 정했다 (2026-09-14). 폭 · 높이 한도에 같이 곱하므로 비율은 그대로다.
     */
    const val SCALE = 1.1f

    /** 코너 표시가 틀 선에서 들어온 거리. 틀 폭에 대한 몫 (15px ÷ 221px). */
    const val CORNER_INSET = 15f / 221f

    /** 코너 표시의 팔 길이. 틀 폭에 대한 몫 (22px ÷ 221px). */
    const val CORNER_ARM = 22f / 221f

    // 틀 안에서 강아지가 차지하는 자리. 틀 폭 · 높이에 대한 몫이다.
    // 윤곽 추적 상자라 흰 테두리까지 들어 있다 (분홍 채움 133×319 → 134×321).
    const val DOG_LEFT = 0.1991f
    const val DOG_TOP = 0.1199f
    const val DOG_WIDTH = 0.6063f
    const val DOG_HEIGHT = 0.8189f

    /** [width] × [height] 프리뷰의 가운데에 놓인 틀. */
    fun frame(width: Float, height: Float): Rect {
        val w = min(width * WIDTH_SHARE * SCALE, height * HEIGHT_SHARE * SCALE / FRAME_ASPECT)
        val h = w * FRAME_ASPECT
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        return Rect(left, top, left + w, top + h)
    }

    /** [frame] 안에서 강아지가 들어갈 상자. 아래 좌표들은 전부 이 상자의 0~1 이다. */
    fun dog(frame: Rect): Rect {
        val left = frame.left + frame.width * DOG_LEFT
        val top = frame.top + frame.height * DOG_TOP
        return Rect(left, top, left + frame.width * DOG_WIDTH, top + frame.height * DOG_HEIGHT)
    }

    /**
     * 실루엣 바깥 윤곽 85점 (x, y 번갈아). 개선안 2 강아지를 윤곽 추적해 1.2px 오차로
     * 줄였다. 점 사이는 [smoothPath] 가 매끄럽게 잇는다.
     *
     * **견종별로 안 바꾼다** — 미니룸의 강아지는 견종마다 덩치가 다르지만(`DogShapes.kt`),
     * 이건 화면에 맞출 자리를 알려 주는 눈금이지 우리 아이의 그림이 아니다.
     */
    val OUTLINE = floatArrayOf(
        0.556f, 0.002f, 0.623f, 0.008f, 0.698f, 0.026f, 0.780f, 0.064f, 0.840f, 0.120f, 0.847f, 0.198f, 0.825f, 0.241f, 0.922f, 0.279f,
        0.937f, 0.310f, 0.899f, 0.338f, 0.884f, 0.366f, 0.862f, 0.375f, 0.810f, 0.372f, 0.802f, 0.388f, 0.884f, 0.435f, 0.929f, 0.475f,
        0.959f, 0.531f, 0.981f, 0.547f, 0.996f, 0.603f, 0.944f, 0.684f, 0.907f, 0.712f, 0.869f, 0.793f, 0.840f, 0.818f, 0.847f, 0.855f,
        0.817f, 0.899f, 0.825f, 0.933f, 0.847f, 0.952f, 0.832f, 0.980f, 0.780f, 0.995f, 0.735f, 0.998f, 0.668f, 0.992f, 0.623f, 0.967f,
        0.623f, 0.942f, 0.646f, 0.914f, 0.646f, 0.877f, 0.631f, 0.855f, 0.646f, 0.827f, 0.631f, 0.815f, 0.638f, 0.808f, 0.563f, 0.724f,
        0.481f, 0.715f, 0.444f, 0.727f, 0.369f, 0.824f, 0.384f, 0.840f, 0.369f, 0.914f, 0.392f, 0.945f, 0.392f, 0.967f, 0.354f, 0.989f,
        0.265f, 0.998f, 0.198f, 0.989f, 0.168f, 0.967f, 0.198f, 0.927f, 0.198f, 0.905f, 0.183f, 0.902f, 0.168f, 0.871f, 0.175f, 0.824f,
        0.123f, 0.768f, 0.093f, 0.709f, 0.056f, 0.693f, 0.063f, 0.690f, 0.026f, 0.656f, 0.026f, 0.634f, 0.004f, 0.618f, 0.004f, 0.581f,
        0.056f, 0.516f, 0.071f, 0.478f, 0.123f, 0.428f, 0.198f, 0.391f, 0.190f, 0.375f, 0.146f, 0.375f, 0.116f, 0.363f, 0.108f, 0.341f,
        0.063f, 0.310f, 0.063f, 0.298f, 0.078f, 0.282f, 0.220f, 0.229f, 0.317f, 0.229f, 0.414f, 0.210f, 0.593f, 0.210f, 0.608f, 0.204f,
        0.638f, 0.164f, 0.653f, 0.114f, 0.608f, 0.055f, 0.519f, 0.014f, 0.526f, 0.005f,
    )

    /**
     * 몸 안쪽 선. 바깥 윤곽만으로는 꼬리가 머리 위를 지나는 것 · 귀가 늘어진 것 ·
     * 등이 어디서 시작하는지가 안 보여서, 시안에 그어진 선을 그대로 옮겼다.
     */
    val INNER_LINES = listOf(
        // 꼬리 왼쪽 가장자리 — 머리 위를 지나 등선까지.
        floatArrayOf(0.622f, 0.206f, 0.590f, 0.240f, 0.548f, 0.271f, 0.499f, 0.302f, 0.467f, 0.333f, 0.452f, 0.364f, 0.449f, 0.374f),
        // 꼬리 오른쪽 가장자리.
        floatArrayOf(0.819f, 0.237f, 0.785f, 0.271f, 0.741f, 0.302f, 0.674f, 0.333f, 0.570f, 0.364f, 0.556f, 0.368f),
        // 왼 귀 안쪽.
        floatArrayOf(0.259f, 0.316f, 0.247f, 0.343f, 0.222f, 0.364f, 0.203f, 0.389f),
        // 오른 귀 안쪽.
        floatArrayOf(0.741f, 0.315f, 0.756f, 0.343f, 0.785f, 0.364f, 0.800f, 0.389f),
        // 등선 왼쪽 — 꼬리 뿌리까지.
        floatArrayOf(0.200f, 0.399f, 0.274f, 0.374f, 0.348f, 0.363f, 0.430f, 0.360f),
        // 등선 오른쪽.
        floatArrayOf(0.563f, 0.368f, 0.644f, 0.360f, 0.733f, 0.364f, 0.793f, 0.389f, 0.807f, 0.399f),
    )

    // 뒷다리 관절점. 시안의 흰 점 중심과 지름을 잰 것이다 (고관절 16px · 무릎 12.5px ·
    // 뒷발 14.5px, 강아지 폭 134px). x 는 왼 · 오른 다리, y 는 위에서부터.
    const val LEFT_LEG_X = 0.272f
    const val RIGHT_LEG_X = 0.735f
    const val HIP_Y = 0.718f
    const val KNEE_Y = 0.849f
    const val PAW_Y = 0.952f

    // 반지름과 선 굵기는 강아지 폭에 대한 몫이다. 강아지가 커지면 점도 같이 커진다.
    const val HIP_RADIUS = 0.060f
    const val KNEE_RADIUS = 0.047f
    const val PAW_RADIUS = 0.054f
    const val LEG_LINE = 0.011f
    const val HIP_BAR = 0.015f

    /** 두 고관절을 잇는 가로선이 끊기는 자리. 시안에서도 가랑이 사이로는 안 지나간다. */
    const val HIP_BAR_GAP_LEFT = 0.430f
    const val HIP_BAR_GAP_RIGHT = 0.570f

    /** 가운데 세로 점선의 x. 틀 한가운데라서 강아지 상자 안의 좌표로 옮겨 적는다. */
    const val SPINE_X = (0.5f - DOG_LEFT) / DOG_WIDTH
    const val SPINE_TOP = 0.243f
    const val SPINE_BOTTOM = 0.713f

    // 점선 한 칸과 빈칸, 굵기. 강아지 폭에 대한 몫 (5.5px · 3.5px · 1.5px ÷ 134px).
    const val SPINE_DASH = 0.041f
    const val SPINE_GAP = 0.026f
    const val SPINE_LINE = 0.011f
}

/**
 * 0~1 점들을 [box] 에 펴서 매끄럽게 잇는다 (Catmull-Rom 을 3차 베지어로 푼 것).
 *
 * [closed] 면 마지막 점과 첫 점을 이어 닫고, 아니면 양 끝을 제자리에 붙잡는다.
 * 점을 곧은 선으로 이으면 85점이어도 각이 보여서 그림이 아니라 도형처럼 읽힌다.
 */
private fun smoothPath(points: FloatArray, box: Rect, closed: Boolean): Path {
    val n = points.size / 2
    fun at(i: Int) = if (closed) i.mod(n) else i.coerceIn(0, n - 1)
    fun x(i: Int) = box.left + points[at(i) * 2] * box.width
    fun y(i: Int) = box.top + points[at(i) * 2 + 1] * box.height

    return Path().apply {
        moveTo(x(0), y(0))
        for (i in 0 until if (closed) n else n - 1) {
            cubicTo(
                x(i) + (x(i + 1) - x(i - 1)) / 6f, y(i) + (y(i + 1) - y(i - 1)) / 6f,
                x(i + 1) - (x(i + 2) - x(i)) / 6f, y(i + 1) - (y(i + 2) - y(i)) / 6f,
                x(i + 1), y(i + 1),
            )
        }
        if (closed) close()
    }
}

/** 흰색으로 굵게, 그 위에 분홍으로 가늘게 — [GaitGuideOverlay] 의 "두 번 긋기". */
private fun DrawScope.drawGlowLine(path: Path) {
    drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, DaengPinkDeep, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * 틀 네 귀퉁이 안쪽의 ㄱ자 표시.
 *
 * 틀 선만 있으면 "틀 안 어디쯤" 이 흐릿하다. 귀퉁이를 한 번 더 찍어 두면 강아지를
 * 넣을 자리가 카메라 뷰파인더처럼 읽힌다.
 */
private fun DrawScope.drawCornerMarks(frame: Rect, line: Float) {
    val inset = frame.width * GaitGuide.CORNER_INSET
    val arm = frame.width * GaitGuide.CORNER_ARM
    listOf(
        Offset(frame.left + inset, frame.top + inset) to Offset(1f, 1f),
        Offset(frame.right - inset, frame.top + inset) to Offset(-1f, 1f),
        Offset(frame.left + inset, frame.bottom - inset) to Offset(1f, -1f),
        Offset(frame.right - inset, frame.bottom - inset) to Offset(-1f, -1f),
    ).forEach { (corner, toward) ->
        val mark = Path().apply {
            moveTo(corner.x + toward.x * arm, corner.y)
            lineTo(corner.x, corner.y)
            lineTo(corner.x, corner.y + toward.y * arm)
        }
        drawPath(mark, Color.White.copy(alpha = 0.92f), style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 가운데 세로 점선. 강아지가 틀 가운데에 똑바로 섰는지 보는 기준이다. */
private fun DrawScope.drawSpine(dog: Rect) {
    val x = dog.left + dog.width * GaitGuide.SPINE_X
    drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = Offset(x, dog.top + dog.height * GaitGuide.SPINE_TOP),
        end = Offset(x, dog.top + dog.height * GaitGuide.SPINE_BOTTOM),
        strokeWidth = dog.width * GaitGuide.SPINE_LINE,
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dog.width * GaitGuide.SPINE_DASH, dog.width * GaitGuide.SPINE_GAP),
        ),
    )
}

/** 뒷다리 관절점. 고관절 · 무릎 · 뒷발을 세로로 잇고, 두 고관절을 가로로 잇는다. */
private fun DrawScope.drawHindLegJoints(dog: Rect) {
    fun x(t: Float) = dog.left + dog.width * t
    fun y(t: Float) = dog.top + dog.height * t
    val legs = listOf(GaitGuide.LEFT_LEG_X, GaitGuide.RIGHT_LEG_X)
    val hipY = y(GaitGuide.HIP_Y)

    legs.forEach { leg ->
        drawLine(Color.White, Offset(x(leg), hipY), Offset(x(leg), y(GaitGuide.PAW_Y)), strokeWidth = dog.width * GaitGuide.LEG_LINE)
    }
    val bar = dog.width * GaitGuide.HIP_BAR
    drawLine(Color.White, Offset(x(GaitGuide.LEFT_LEG_X), hipY), Offset(x(GaitGuide.HIP_BAR_GAP_LEFT), hipY), strokeWidth = bar)
    drawLine(Color.White, Offset(x(GaitGuide.HIP_BAR_GAP_RIGHT), hipY), Offset(x(GaitGuide.RIGHT_LEG_X), hipY), strokeWidth = bar)

    // 점은 선 위에 찍는다. 선이 점을 가로지르면 관절이 아니라 구슬 꿴 줄로 보인다.
    legs.forEach { leg ->
        drawCircle(Color.White, dog.width * GaitGuide.HIP_RADIUS, Offset(x(leg), hipY))
        drawCircle(Color.White, dog.width * GaitGuide.KNEE_RADIUS, Offset(x(leg), y(GaitGuide.KNEE_Y)))
        drawCircle(Color.White, dog.width * GaitGuide.PAW_RADIUS, Offset(x(leg), y(GaitGuide.PAW_Y)))
    }
}

/**
 * 촬영 단추.
 *
 * 흰 테를 두른 분홍 원이다. 프리뷰가 어떤 색이든 이 아래는 크림 바탕이라 흰 테가
 * 장식처럼 보이지만, 눌리는 자리를 원보다 크게 잡아 두는 실용적인 몫도 한다.
 */
@Composable
private fun ShutterButton(recording: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .clip(RoundedCornerShape(50))
            .background(CardWhite)
            .border(3.dp, PinkSoft, RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 찍는 중이면 **네모**다. 같은 자리에서 시작하고 멈추는 단추라, 모양이 안
        // 바뀌면 눌러도 되는지 아닌지를 알 수 없다 (녹화 단추의 오랜 약속이다).
        val inner by animateDpAsState(
            targetValue = if (recording) 30.dp else 58.dp,
            animationSpec = tween(durationMillis = 160),
            label = "shutter",
        )
        val corner by animateDpAsState(
            targetValue = if (recording) 8.dp else 29.dp,
            animationSpec = tween(durationMillis = 160),
            label = "shutterCorner",
        )
        Box(Modifier.size(inner).clip(RoundedCornerShape(corner)).background(DaengPinkDeep))
    }
}

/**
 * 촬영 단추 양옆의 작은 네모 단추.
 *
 * @param on 켜져 있는 상태면 분홍으로 찬다. 요령 패널이 떠 있는 동안 어느 단추가
 *   그걸 띄웠는지 보여서, 다시 눌러 닫을 곳을 찾게 된다
 */
@Composable
private fun SideButton(
    icon: DaengsIcon,
    description: String,
    on: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) PinkSoft else CardWhite)
            .border(1.dp, if (on) DaengPink else PinkSoft, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { DaengsIconView(icon, Modifier.size(22.dp), tint = if (on) DaengPinkDeep else DaengPink) }
}

/**
 * 접혀 있던 촬영 요령.
 *
 * 네 줄 다 **찍기 전에 정하는 것**이다. 찍고 나서야 알면 다시 찍어야 하는 일만
 * 골라 적었다 — 조명이나 옷차림 같은 건 안 적는다. 지킬 게 많아 보이면 안 찍는다.
 */
@Composable
private fun GaitTipsPanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = CardWhite.copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.padding(horizontal = 22.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("촬영 요령", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { DaengsIconView(DaengsIcon.Close, Modifier.size(14.dp), tint = TextMuted) }
            }
            listOf(
                "강아지 뒤에서, 같은 속도로 따라 걸어요.",
                "네 다리가 다 보이게 화면에 담아요.",
                "쉬지 않고 걷는 모습이 ${GaitRecord.MIN_WALKING_SECONDS}초 이상 담겨야 해요.",
                "서다 걷다 하면 그만큼 빠지니 ${GaitRecord.RECOMMENDED_SECONDS}초 내외로 찍어요.",
                "지난 기록과 비슷한 곳에서 찍으면 나란히 보기 좋아요.",
            ).forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("·", color = DaengPink, fontSize = 13.sp)
                    Spacer(Modifier.width(7.dp))
                    Text(line, color = TextDark, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitCaptureScreenPreview() {
    DaengsTheme { GaitCaptureScreen(onBack = {}, onRecord = {}, onPick = {}) }
}

/** 찍는 중. 셔터가 네모가 되고 아래 두 줄이 바뀐다. */
@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitCaptureRecordingPreview() {
    DaengsTheme {
        GaitCaptureScreen(onBack = {}, onRecord = {}, onPick = {}, recording = true)
    }
}

/** 오버레이만 크게 본다. 실루엣 곡선을 고칠 때 이걸 본다. */
@Preview(widthDp = 300, heightDp = 420)
@Composable
private fun GaitGuideOverlayPreview() {
    Box(Modifier.fillMaxSize()) {
        GaitPreviewPlaceholder()
        GaitGuideOverlay(Modifier.fillMaxSize())
    }
}

/**
 * 가로로 누운 프리뷰. 여기서는 높이가 먼저 닿아서 틀이 작아지는데, **비율은 그대로**여야
 * 한다 — 예전처럼 폭 · 높이를 따로 곱했다면 여기서 강아지가 옆으로 퍼진다.
 */
@Preview(widthDp = 420, heightDp = 300)
@Composable
private fun GaitGuideOverlayWidePreview() {
    Box(Modifier.fillMaxSize()) {
        GaitPreviewPlaceholder()
        GaitGuideOverlay(Modifier.fillMaxSize())
    }
}
