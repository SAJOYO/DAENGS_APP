package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.screening.Photo
import com.daengs.app.ui.chat.GuideFrameScreen
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// 누끼 실험실 — **개발자 패널에서만 열린다**
//
// 카드를 만들기 전에 답이 나와야 하는 질문 하나만 본다: **털 가장자리가 쓸 만한가.**
// 여기서 안 나오면 뽑기 연출을 아무리 잘 만들어도 헛일이라, 카드보다 이걸 먼저 만든다.
//
// 그래서 화면이 답해야 하는 것도 셋뿐이다.
//
//   1. 세그멘테이션이 됐나, 타원으로 물러섰나 (물러섰으면 왜)
//   2. 사진 전체로 넘기는 것과 얼굴 상자를 먼저 주는 것 중 뭐가 나은가
//   3. **작게 줄여도 얼굴이 읽히나** — 도감 그리드에서 카드가 작아진다.
//      보더콜리 눈이 무너진 것과 같은 자리다 (HISTORY 11절). 실사 사진은 도트보다
//      더 잘 뭉개져서, 원본 크기로만 보면 이 문제가 안 보인다
// ---------------------------------------------------------------------------

private enum class Step { Pick, Box, Done }

@Composable
fun CutoutLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.Pick) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Cutout.Result?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tookMs by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }

    // 사진을 고르는 동안 모델을 미리 받아 둔다. 안 그러면 이 기기에서 처음 누를 때
    // 반드시 실패한다 — 실측했다.
    LaunchedEffect(Unit) { Cutout.warmUp() }

    fun run(box: FloatArray?) {
        val source = photo ?: return
        busy = true
        error = null
        scope.launch {
            val started = System.currentTimeMillis()
            val outcome = runCatching { Cutout.of(source, box) }
            tookMs = System.currentTimeMillis() - started
            busy = false
            outcome
                .onSuccess {
                    result = it
                    step = Step.Done
                }
                .onFailure { error = it.message ?: "누끼를 뜨지 못했습니다." }
        }
    }

    // 갤러리만 연다. 권한이 필요 없고(PickVisualMedia), 실험에 쓸 사진은 이미
    // 폰에 있다 — 카메라까지 붙이면 볼 것이 아니라 배선이 늘어난다.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        scope.launch {
            runCatching { Photo.decodeUpright(context, uri, Photo.MAX_EDGE) }
                .onSuccess {
                    photo?.recycle()
                    photo = it
                    result = null
                    step = Step.Box
                    busy = false
                }
                .onFailure {
                    busy = false
                    error = it.message ?: "사진을 읽지 못했습니다."
                }
        }
    }

    val shot = photo
    if (step == Step.Box && shot != null) {
        GuideFrameScreen(
            photo = shot,
            onCancel = { step = Step.Pick },
            onConfirm = { box -> run(box) },
            title = "강아지 얼굴을 네모 안에 넣어 주세요",
            confirmLabel = "이 얼굴로 누끼",
            guidance = "강아지 얼굴에 네모를 맞춥니다. 모서리를 끌면 크기가 바뀝니다.",
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF14161A))
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("누끼 실험실", 16.sp, Color.White, FontWeight.Bold)
            LabButton("닫기", onBack)
        }

        Text(
            "카드를 만들기 전에 이것부터 본다 — 털 가장자리가 쓸 만한가.",
            12.sp,
            Color(0xFF9AA3AE),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton(if (photo == null) "사진 고르기" else "다른 사진") {
                pick.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            if (shot != null) {
                LabButton("얼굴 상자로") { step = Step.Box }
                LabButton("사진 전체로") { run(null) }
            }
        }

        if (busy) Text("도는 중…", 13.sp, Color(0xFFFFD98A))
        error?.let { Text(it, 13.sp, Color(0xFFFF9A9A)) }

        val done = result
        if (done != null) {
            val badge = when (done) {
                is Cutout.Result.Cut -> "오려냈다"
                is Cutout.Result.Ellipse -> "타원으로 물러섬"
            }
            val tint = when (done) {
                is Cutout.Result.Cut -> Color(0xFF8FD94A)
                is Cutout.Result.Ellipse -> Color(0xFFFFB43F)
            }
            Text(
                "$badge · ${done.bitmap.width}x${done.bitmap.height} · ${tookMs}ms",
                13.sp,
                tint,
                FontWeight.Bold,
            )
            if (done is Cutout.Result.Ellipse) {
                // 모델을 내려받는 중인 것과 이 기기에서 영영 안 되는 것은 다른 일이다.
                // 앞은 다시 눌러 볼 만하고, 뒤는 눌러도 소용이 없다.
                Text(done.why, 11.sp, if (done.pending) Color(0xFFFFD98A) else Color(0xFF9AA3AE))
            }

            // 큰 것 한 장.
            Checkered(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Image(
                    bitmap = done.bitmap.asImageBitmap(),
                    contentDescription = "누끼 결과",
                    modifier = Modifier.fillMaxSize(),
                    filterQuality = FilterQuality.High,
                )
            }

            // **작게 줄인 것들.** 이 줄이 이 화면의 요점이다.
            Text("작게 줄이면 (도감 그리드 · 아바타 크기)", 12.sp, Color(0xFF9AA3AE))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(120, 72, 40).forEach { edge ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Checkered(Modifier.size(edge.dp)) {
                            Image(
                                bitmap = done.bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                filterQuality = FilterQuality.High,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("${edge}dp", 10.sp, Color(0xFF6C7480))
                    }
                }
            }
        }
    }
}

/** 알파를 눈으로 보려면 뒤에 무늬가 있어야 한다. 단색이면 흰 털과 구분이 안 된다. */
@Composable
private fun Checkered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = 12.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = 0f
                var col = 0
                while (x < size.width) {
                    val dark = (row + col) % 2 == 0
                    drawRect(
                        color = if (dark) Color(0xFF3A3F46) else Color(0xFF2A2E34),
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                    )
                    x += cell
                    col++
                }
                y += cell
                row++
            }
        }
        content()
    }
}

@Composable
private fun Text(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
) = androidx.compose.material3.Text(text, fontSize = size, color = color, fontWeight = weight)

@Composable
private fun LabButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.Text(
        label,
        color = Color(0xFF14161A),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFFE7ECF2))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

// -- 프리뷰 ------------------------------------------------------------------
//
// 누끼 자체는 프리뷰에서 못 돈다 (Play 서비스가 없다). 그래서 **껍데기만** 본다 —
// 버튼 줄과 체커보드가 어떻게 앉는지. 실제 판단은 실기기에서 한다.

@Preview(name = "누끼 실험실 · 사진 고르기 전", widthDp = 380, heightDp = 720)
@Composable
private fun CutoutLabEmptyPreview() {
    CutoutLabScreen(onBack = {})
}

@Preview(name = "체커보드 · 작은 칸", widthDp = 160, heightDp = 160)
@Composable
private fun CheckeredPreview() {
    Checkered(Modifier.size(120.dp)) {}
}
