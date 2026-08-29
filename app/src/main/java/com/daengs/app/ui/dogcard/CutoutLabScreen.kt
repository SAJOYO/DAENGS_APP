package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
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
// 카드를 만들기 전에 답이 나와야 하는 질문만 본다.
//
//   1. 세그멘테이션이 됐나, 타원으로 물러섰나 (물러섰으면 왜)
//   2. **어떤 마스크가 맞나.** 세그멘테이션은 상자 안에서 배경만 지운다. 상자에
//      목과 가슴이 들어와 있으면 그것도 피사체라 같이 남는다. 얼굴만 쓰려면 셋 중
//      골라야 하는데 (실루엣 · 원형 · 아래 페이드) **답이 강아지마다 다르다** —
//      귀가 처진 개는 원형에서 귀가 잘리고, 선 개는 안 잘린다. 그래서 하나를
//      고르지 않고 셋을 나란히 그려 놓고 보게 한다
//   3. **작게 줄여도 얼굴이 읽히나** — 도감 그리드에서 카드가 작아진다.
//      보더콜리 눈이 무너진 것과 같은 자리다 (HISTORY 11절). 실사 사진은 도트보다
//      더 잘 뭉개져서, 원본 크기로만 보면 이 문제가 안 보인다
// ---------------------------------------------------------------------------

private enum class Step { Pick, Box, Done }

private val MASKS = listOf(
    Cutout.Mask.Silhouette to "실루엣",
    Cutout.Mask.Circle to "원형",
    Cutout.Mask.SoftBottom to "아래 페이드",
)

private fun labelOf(mask: Cutout.Mask): String = MASKS.first { it.first == mask }.second

@Composable
fun CutoutLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.Pick) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var results by remember { mutableStateOf<List<Pair<Cutout.Mask, Cutout.Result>>>(emptyList()) }
    var picked by remember { mutableStateOf(Cutout.Mask.SoftBottom) }
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
            // **셋을 다 만든다.** 하나씩 눌러 가며 보면 앞의 것이 기억에 남지 않아
            // 비교가 안 된다. 세 번 도는 값은 나란히 놓고 고를 수 있는 것으로 갚는다.
            val outcome = runCatching {
                MASKS.map { (mask, _) -> mask to Cutout.of(source, box, mask) }
            }
            tookMs = System.currentTimeMillis() - started
            busy = false
            outcome
                .onSuccess {
                    results = it
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
                    results = emptyList()
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

        Lab("셋을 나란히 놓고 고른다 — 귀가 사는가, 목이 빠지는가.", 12.sp, Dim)

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

        if (results.isNotEmpty()) {
            val first = results.first().second
            val cut = first is Cutout.Result.Cut
            Lab(
                if (cut) "오려냈다 · 셋 합쳐 ${tookMs}ms" else "타원으로 물러섬 · ${tookMs}ms",
                13.sp,
                if (cut) Good else Color(0xFFFFB43F),
                FontWeight.Bold,
            )
            (first as? Cutout.Result.Ellipse)?.let {
                // 모델을 내려받는 중인 것과 이 기기에서 영영 안 되는 것은 다른 일이다.
                Lab(it.why, 11.sp, if (it.pending) Warn else Dim)
            }

            // 셋 나란히. 누르면 아래 큰 그림이 바뀐다.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                results.forEach { (mask, result) ->
                    Column(
                        Modifier.weight(1f).clickable { picked = mask },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Checkered(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .then(
                                    if (mask == picked) {
                                        Modifier.border(2.dp, Good, RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Shown(result.bitmap, labelOf(mask), Modifier.padding(3.dp))
                        }
                        Spacer(Modifier.height(3.dp))
                        Lab(labelOf(mask), 10.sp, if (mask == picked) Good else Faint)
                    }
                }
            }

            val chosen = results.first { it.first == picked }.second
            Lab(
                "${labelOf(picked)} · ${chosen.bitmap.width}x${chosen.bitmap.height}",
                12.sp,
                Dim,
            )
            Checkered(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Shown(chosen.bitmap, "고른 마스크")
            }

            // **작게 줄인 것들.** 카드가 도감 그리드에서 이만해진다.
            Lab("작게 줄이면 (도감 그리드 · 아바타 크기)", 12.sp, Dim)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(120, 72, 40).forEach { edge ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Checkered(Modifier.size(edge.dp)) { Shown(chosen.bitmap, null) }
                        Spacer(Modifier.height(3.dp))
                        Lab("${edge}dp", 10.sp, Faint)
                    }
                }
            }
        }
    }
}

private val Dim = Color(0xFF9AA3AE)
private val Faint = Color(0xFF6C7480)
private val Good = Color(0xFF8FD94A)
private val Warn = Color(0xFFFFD98A)

@Composable
private fun Shown(bitmap: Bitmap, label: String?, modifier: Modifier = Modifier) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = label,
        // **Fit 이어야 한다.** 마스크마다 결과 비율이 달라서(원형은 정사각에 가깝고
        // 실루엣은 세로로 길다), Crop 이면 셋을 나란히 놓고 비교할 수가 없다.
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize(),
        filterQuality = FilterQuality.High,
    )
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
