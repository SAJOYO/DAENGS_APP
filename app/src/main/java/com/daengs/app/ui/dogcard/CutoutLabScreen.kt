package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.screening.Photo
import com.daengs.app.ui.chat.GuideFrameScreen
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// 누끼 실험실 — **개발자 패널에서만 열린다**
//
// 카드를 만들기 전에 답이 나와야 하는 것만 본다.
//
//   1. 세그멘테이션이 됐나, 타원으로 물러섰나 (물러섰으면 왜)
//   2. **목을 어디서 자를 것인가.** 세그멘테이션은 상자 안에서 배경만 지운다.
//      상자에 목과 가슴이 들어와 있으면 그것도 피사체라 같이 남는다. 자동으로
//      목을 찾아보긴 하지만 털 많은 개에서는 빗나가서, **사람이 선을 끌어 고친다**
//   3. **작게 줄여도 얼굴이 읽히나** — 도감 그리드에서 카드가 작아진다.
//      보더콜리 눈이 무너진 것과 같은 자리다 (HISTORY 11절)
//
// 선을 끄는 동안에는 비트맵을 다시 만들지 않는다. 900px 짜리를 프레임마다 다시
// 칠하면 손가락을 못 따라온다 — **그리기로만 흉내 내고**, 굽는 것은 정해진 뒤
// [Cutout.fadedBelow] 가 한 번 한다.
// ---------------------------------------------------------------------------

private enum class Step { Pick, Box, Done }

@Composable
fun CutoutLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.Pick) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Cutout.Result?>(null) }
    var neck by remember { mutableStateOf(1f) }
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
                    // 자동으로 찍은 자리에서 시작한다. 맞으면 그대로 두고, 아니면 끈다.
                    neck = (it as? Cutout.Result.Cut)?.neck ?: 1f
                    step = Step.Done
                }
                .onFailure { error = it.message ?: "누끼를 뜨지 못했습니다." }
        }
    }

    // 갤러리만 연다. 권한이 필요 없고(PickVisualMedia), 실험에 쓸 사진은 이미 폰에 있다.
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
            title = "얼굴만 원 안에 넣어 주세요",
            confirmLabel = "이 얼굴로 누끼",
            circle = true,
            guidance = "목 아래는 빼고 얼굴만 담습니다. 모서리 손잡이로 크기를 바꿉니다.",
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF14161A))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Lab("누끼 실험실", 16.sp, Color.White, FontWeight.Bold)
            LabButton("닫기", onBack)
        }

        Lab("목선을 끌어서 얼굴만 남긴다.", 12.sp, Dim)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton(if (photo == null) "사진 고르기" else "다른 사진") {
                pick.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            if (shot != null) {
                LabButton("얼굴 원으로") { step = Step.Box }
                LabButton("사진 전체로") { run(null) }
            }
        }

        if (busy) Lab("도는 중…", 13.sp, Warn)
        error?.let { Lab(it, 13.sp, Color(0xFFFF9A9A)) }

        val done = result
        if (done != null) {
            val cut = done as? Cutout.Result.Cut
            Lab(
                if (cut != null) {
                    "오려냈다 · ${done.bitmap.width}x${done.bitmap.height} · ${tookMs}ms"
                } else {
                    "타원으로 물러섬 · ${tookMs}ms"
                },
                13.sp,
                if (cut != null) Good else Color(0xFFFFB43F),
                FontWeight.Bold,
            )
            (done as? Cutout.Result.Ellipse)?.let {
                // 모델을 내려받는 중인 것과 이 기기에서 영영 안 되는 것은 다른 일이다.
                Lab(it.why, 11.sp, if (it.pending) Warn else Dim)
            }
            if (cut != null) {
                Lab(
                    "자동으로 찍은 목선 ${"%.0f".format(cut.neck * 100)}%" +
                        " · 지금 ${"%.0f".format(neck * 100)}%",
                    11.sp,
                    Dim,
                )
            }

            NeckPicker(done.bitmap, neck) { neck = it }
            Lab("가로선을 위아래로 끌면 그 아래가 사라진다.", 11.sp, Faint)

            // **작게 줄인 것들.** 카드가 도감 그리드에서 이만해진다.
            Lab("작게 줄이면 (도감 그리드 · 아바타 크기)", 12.sp, Dim)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(120, 72, 40).forEach { edge ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Checkered(Modifier.size(edge.dp)) {
                            FadedImage(done.bitmap, neck, null)
                        }
                        Spacer(Modifier.height(3.dp))
                        Lab("${edge}dp", 10.sp, Faint)
                    }
                }
            }
        }
    }
}

/**
 * 누끼 위에 목선을 얹고 끌게 한다.
 *
 * 선 아래는 **그리기로만** 지운다 — 비트맵을 다시 만들면 손가락을 못 따라온다.
 * 실제로 굽는 것은 [Cutout.fadedBelow] 이고, 자리가 정해진 뒤 한 번만 부른다.
 */
@Composable
private fun NeckPicker(bitmap: Bitmap, neck: Float, onChange: (Float) -> Unit) {
    // **`pointerInput(Unit)` 안에서 `neck` 을 그냥 읽으면 안 된다.** 그 블록은 처음
    // 한 번만 만들어지므로 첫 조합 때의 값이 박제되고, 한 번 끈 다음부터는 늘 처음
    // 자리를 기준으로 계산해서 선이 튄다. 갱신되는 참조를 따로 들고 읽는다.
    val latest by rememberUpdatedState(neck)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, drag ->
                    change.consume()
                    onChange((latest + drag / size.height).coerceIn(0.05f, 1f))
                }
            },
    ) {
        Checkered(Modifier.fillMaxSize()) { FadedImage(bitmap, neck, "누끼 결과") }
        // 선은 그림 위에 그린다. 사라지는 자리와 눈금이 어긋나면 못 맞춘다.
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * neck
            drawLine(
                Good,
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
            )
            drawCircle(Good, 9.dp.toPx(), Offset(size.width - 18.dp.toPx(), y))
        }
    }
}

/** [neck] 아래가 서서히 사라지게 그린다. 비트맵은 안 건드린다. */
@Composable
private fun FadedImage(bitmap: Bitmap, neck: Float, label: String?) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = label,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
        modifier = Modifier
            .fillMaxSize()
            // 지우개가 그림하고만 섞여야 한다. 레이어를 안 뜨면 뒤 체커보드까지 지운다.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (neck >= 1f) return@drawWithContent
                val top = size.height * neck
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black,
                        startY = top,
                        endY = (top + size.height * 0.16f).coerceAtMost(size.height),
                    ),
                    blendMode = BlendMode.DstOut,
                )
            },
    )
}

private val Dim = Color(0xFF9AA3AE)
private val Faint = Color(0xFF6C7480)
private val Good = Color(0xFF8FD94A)
private val Warn = Color(0xFFFFD98A)

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
private fun Lab(text: String, size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal) =
    Text(text, fontSize = size, color = color, fontWeight = weight)

@Composable
private fun LabButton(label: String, onClick: () -> Unit) {
    Text(
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
// 누끼 자체는 프리뷰에서 못 돈다 (Play 서비스가 없다). 그래서 **껍데기만** 본다.
// 실제 판단은 실기기에서 한다.

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
