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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

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
        CaptureHeader(onBack = onBack, avatar = avatar)

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
                        "${GaitRecord.RECOMMENDED_SECONDS}초 넘게 촬영 권장"
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
private fun CaptureHeader(onBack: () -> Unit, avatar: DogBreed?) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = TextDark, fontSize = 34.sp, lineHeight = 30.sp) }
        // 대화 헤더의 ChatFace 와 같은 물러섬이다 — 견종을 모르면 발바닥을 세운다.
        if (avatar != null) DogAvatar(avatar, Modifier.size(38.dp)) else PawAvatar(size = 38.dp)
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
 * 세로로 긴 네모 하나와, 그 안의 **뒷모습 실루엣**이다. 네모만 있으면 어느 방향으로
 * 서야 하는지가 안 담긴다 — 이 기능이 요구하는 건 "가운데" 가 아니라 "뒤에서" 라서,
 * 꼬리가 위로 선 뒷모습을 그려야 그 말이 그림으로 전해진다.
 *
 * 선은 **두 번 긋는다.** 먼저 흰색으로 굵게, 그 위에 분홍으로 가늘게. 카메라 화면은
 * 무엇이 찍힐지 모르는 배경이라 한 색으로만 그으면 비슷한 색 위에서 사라진다.
 * 시안은 주황을 썼는데, 흰 테두리를 두르면 앱 색을 그대로 쓰고도 같은 만큼 읽힌다.
 */
@Composable
fun GaitGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // 세로 네모. 화면 폭의 절반, 높이의 3/4 을 가운데에 둔다.
        val boxW = size.width * 0.50f
        val boxH = size.height * 0.74f
        val left = (size.width - boxW) / 2f
        val top = (size.height - boxH) / 2f

        // 네모 안만 조금 밝힌다. 바깥을 어둡게 덮으면 프리뷰가 안 보여서
        // 강아지가 프레임 밖 어디에 있는지 못 찾는다.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(left, top),
            size = Size(boxW, boxH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx()),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(left, top),
            size = Size(boxW, boxH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx()),
            style = Stroke(3.dp.toPx()),
        )

        // 실루엣은 네모 안쪽에 여백을 두고 앉힌다. 선에 딱 붙이면 "맞췄다" 의
        // 기준이 네모인지 실루엣인지 헷갈린다.
        val padX = boxW * 0.10f
        val padY = boxH * 0.08f
        val silhouette = dogFromBehind(
            left = left + padX,
            top = top + padY,
            width = boxW - padX * 2f,
            height = boxH - padY * 2f,
        )
        drawPath(silhouette, Color.White.copy(alpha = 0.85f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(silhouette, DaengPinkDeep, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * 뒤에서 본 강아지 윤곽.
 *
 * 0~1 좌표로 그려 두고 주어진 네모에 맞춰 늘린다. **견종별로 안 바꾼다** — 미니룸의
 * 강아지는 견종마다 덩치가 다르지만(`DogShapes.kt`), 이건 화면에 맞출 자리를 알려
 * 주는 눈금이지 우리 아이의 그림이 아니다. 치와와가 대형견 윤곽에 맞춰 서야 하는
 * 것도 아니고, 반대로 윤곽이 작아지면 멀리서 찍으라는 뜻이 돼 버린다.
 */
private fun DrawScope.dogFromBehind(left: Float, top: Float, width: Float, height: Float): Path {
    fun x(t: Float) = left + width * t
    fun y(t: Float) = top + height * t

    return Path().apply {
        // 꼬리. 몸에서 나와 위로 섰다가 끝이 앞으로 꺾인다.
        moveTo(x(0.50f), y(0.42f))
        cubicTo(x(0.47f), y(0.24f), x(0.52f), y(0.09f), x(0.63f), y(0.05f))
        cubicTo(x(0.70f), y(0.03f), x(0.72f), y(0.09f), x(0.66f), y(0.12f))

        // 엉덩이 왼쪽 — 꼬리 뿌리에서 몸통 바깥으로.
        moveTo(x(0.52f), y(0.40f))
        cubicTo(x(0.40f), y(0.36f), x(0.22f), y(0.44f), x(0.20f), y(0.62f))
        cubicTo(x(0.19f), y(0.72f), x(0.22f), y(0.78f), x(0.24f), y(0.82f))

        // 왼 뒷다리 — 안쪽으로 좁아졌다가 발에서 다시 벌어진다.
        cubicTo(x(0.25f), y(0.88f), x(0.25f), y(0.94f), x(0.26f), y(0.98f))
        lineTo(x(0.38f), y(0.98f))
        cubicTo(x(0.39f), y(0.92f), x(0.39f), y(0.86f), x(0.40f), y(0.80f))

        // 두 다리 사이. 가랑이가 있어야 다리 둘로 읽힌다.
        cubicTo(x(0.44f), y(0.76f), x(0.56f), y(0.76f), x(0.60f), y(0.80f))

        // 오른 뒷다리.
        cubicTo(x(0.61f), y(0.86f), x(0.61f), y(0.92f), x(0.62f), y(0.98f))
        lineTo(x(0.74f), y(0.98f))
        cubicTo(x(0.75f), y(0.94f), x(0.75f), y(0.88f), x(0.76f), y(0.82f))

        // 엉덩이 오른쪽 — 다시 꼬리 뿌리로 닫는다.
        cubicTo(x(0.78f), y(0.78f), x(0.81f), y(0.72f), x(0.80f), y(0.62f))
        cubicTo(x(0.78f), y(0.44f), x(0.60f), y(0.36f), x(0.52f), y(0.40f))
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
                "서다 걷다 하면 그만큼 빠지니 ${GaitRecord.RECOMMENDED_SECONDS}초 넘게 찍어요.",
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
