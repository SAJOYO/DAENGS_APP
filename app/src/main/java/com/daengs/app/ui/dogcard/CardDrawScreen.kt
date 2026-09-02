package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.dogcard.DAILY_DRAWS
import com.daengs.app.dogcard.DrawnCard
import com.daengs.app.dogcard.drawTemplate
import com.daengs.app.screening.Photo
import com.daengs.app.ui.chat.GuideFrameScreen
import com.daengs.app.ui.dex.DEX_CARDS
import com.daengs.app.ui.dex.IMMERSIVE_SCENES
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 사진 한 장으로 카드를 뽑는 화면.
 *
 * **실험실(`CutoutLabScreen`)을 승격하지 않고 부품만 가져왔다.** 그쪽은 값을 보는
 * 도구다 — 검은 배경에 "타원으로 물러섬 · 1240ms" 를 띄우고 축소 비교판을 늘어놓는다.
 * 여기 목적은 카드 한 장을 받는 것이라 정보 구조가 반대다. 그리고 실험실은 앞으로도
 * 필요하다 — 배경이 복잡한 실사 사진에서 누끼가 견디는지 볼 유일한 창구다.
 *
 * 가져온 것 넷: 모델 미리 받기 · 얼굴 원 고르기 · 목선 끌기 · 굽기를 늦추는 장치.
 *
 * @param onDrawn 뽑힌 카드를 저장한다. **연출보다 먼저 부른다** — 아래 참고
 */
@Composable
fun CardDrawScreen(
    /** 고를 수 있는 아이들. 비어 있으면 이름 없이 뽑는다 (둘러보기). */
    dogs: List<DrawDog>,
    drawsLeft: Int,
    onCancel: () -> Unit,
    onDrawn: suspend (dog: DrawDog?, template: CardTemplate, face: Bitmap, core: IntRect) -> DrawnCard?,
    onOpenDex: () -> Unit,
    onExport: ((DrawnCard) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(DrawStep.Intro) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Cutout.Result?>(null) }
    var neck by remember { mutableStateOf(1f) }
    // 목선까지 반영해 구운 얼굴. **이것을 저장한다** — 민판을 저장하면 목 아래가
    // 안 지워진 채로 남아서, 다음에 열 때 카드 구멍에 목이 삐져나온다.
    var baked by remember { mutableStateOf<Cutout.Face?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var won by remember { mutableStateOf<Pair<CardTemplate, DrawnCard>?>(null) }
    var left by remember { mutableStateOf(drawsLeft) }
    // **누구로 뽑을지 사용자가 고른다.** 대표가 기본이지만 세 마리를 키우는 사람에게
    // 말없이 대표만 쓰면 나머지 아이로는 못 뽑는 것처럼 읽힌다. 한 마리면 안 묻는다.
    var dog by remember { mutableStateOf(dogs.firstOrNull { it.isPrimary } ?: dogs.firstOrNull()) }

    // 사진을 고르는 동안 모델을 미리 받아 둔다. **안 하면 이 기기에서 처음 누를 때
    // 반드시 실패한다** — 실측했다. 사용자의 첫 카드가 가장 나쁜 결과를 받는다.
    LaunchedEffect(Unit) { Cutout.warmUp() }

    // 목선이 멈추면 그때 한 번 굽는다. 키가 바뀔 때마다 앞의 것이 취소되므로 끄는
    // 동안에는 delay 에서 잘려 나가고, 손을 멈춘 뒤에만 실제로 돈다 — 900px 짜리를
    // 프레임마다 다시 칠하지 않으려는 것이다.
    //
    // **구멍에는 민판을 넣는다.** 테두리 두른 판을 넣으면 실루엣이 타원 안으로
    // 파고드는 자리마다 흰 띠가 드러나서 카드가 찢어져 보인다.
    val source = (result as? Cutout.Result.Cut)?.plain ?: result?.bitmap
    LaunchedEffect(source, neck) {
        if (source == null) {
            baked = null
            return@LaunchedEffect
        }
        delay(140)
        baked = Cutout.faceFor(source, neck)
    }

    val shown = baked?.let {
        CardFace(
            it.bitmap.asImageBitmap(),
            IntRect(it.core.left, it.core.top, it.core.right, it.core.bottom),
        )
    }

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
                    step = DrawStep.Box
                }
                .onFailure { error = it.message ?: "사진을 읽지 못했어요." }
            busy = false
        }
    }

    fun cut(box: FloatArray?) {
        val shot = photo ?: return
        busy = true
        error = null
        scope.launch {
            runCatching { Cutout.of(shot, box) }
                .onSuccess {
                    result = it
                    // 자동으로 찍은 자리에서 시작한다. 맞으면 그대로 두고, 아니면 끈다.
                    neck = (it as? Cutout.Result.Cut)?.neck ?: 1f
                    step = DrawStep.Neck
                }
                .onFailure { error = it.message ?: "얼굴을 오려내지 못했어요." }
            busy = false
        }
    }

    /**
     * **연출을 시작하기 전에 저장한다.** 뒤집기를 먼저 돌리고 저장하면, 그 0.8초
     * 사이에 프로세스가 죽었을 때 **뽑기 횟수만 쓰고 카드는 없는** 상태가 된다.
     */
    fun drawNow() {
        val face = baked ?: return
        busy = true
        error = null
        scope.launch {
            val template = drawTemplate()
            val card = onDrawn(
                dog,
                template,
                face.bitmap,
                IntRect(face.core.left, face.core.top, face.core.right, face.core.bottom),
            )
            busy = false
            if (card == null) {
                error = "카드를 저장하지 못했어요."
                return@launch
            }
            won = template to card
            left = (left - 1).coerceAtLeast(0)
            step = DrawStep.Flip
        }
    }

    val shot = photo
    if (step == DrawStep.Box && shot != null) {
        GuideFrameScreen(
            photo = shot,
            onCancel = { step = DrawStep.Intro },
            onConfirm = { box -> cut(box) },
            title = "얼굴만 원 안에 넣어 주세요",
            confirmLabel = "이 얼굴로",
            circle = true,
            guidance = "목 아래는 빼고 얼굴만 담아 주세요. 모서리를 끌어 크기를 바꿉니다.",
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("카드 뽑기", color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "닫기",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onCancel),
            )
        }

        when (step) {
            DrawStep.Intro, DrawStep.Box -> IntroBody(
                dogs = dogs,
                picked = dog,
                onPickDog = { dog = it },
                left = left,
                busy = busy,
                onPick = {
                    pick.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            DrawStep.Neck -> {
                val done = result
                if (done == null) {
                    Text("얼굴을 굽는 중이에요…", color = TextMuted, fontSize = 13.sp)
                } else {
                    Text(
                        "선을 끌어서 얼굴만 남겨 주세요.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    NeckPicker(
                        bitmap = done.bitmap,
                        neck = neck,
                        onChange = { neck = it },
                        lineColor = DaengPink,
                        background = { Box(Modifier.fillMaxSize().background(PinkFaint)) },
                    )
                    PinkButton(
                        label = if (busy) "뽑는 중…" else "이 얼굴로 뽑기",
                        enabled = !busy && baked != null,
                        onClick = { drawNow() },
                    )
                }
            }

            DrawStep.Flip -> won?.let { (template, _) ->
                FlipToCard(template, shown, dog?.name.orEmpty(), dog?.codeText.orEmpty()) {
                    step = DrawStep.Result
                }
            }

            DrawStep.Result -> won?.let { (template, card) ->
                ResultBody(
                    template = template,
                    card = card,
                    face = shown,
                    dogName = dog?.name.orEmpty(),
                    codeText = dog?.codeText.orEmpty(),
                    left = left,
                    onAgain = {
                        result = null
                        baked = null
                        won = null
                        step = DrawStep.Intro
                    },
                    onOpenDex = onOpenDex,
                    onExport = onExport,
                )
            }
        }

        error?.let {
            Text(it, color = Color(0xFFC45E5E), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

private enum class DrawStep { Intro, Box, Neck, Flip, Result }

/**
 * 뽑기에 쓸 아이 하나.
 *
 * [codeText] 는 카드 번호판에 찍히는 글자다 — 생일에서 만든다. 카드마다 굳어야 하는
 * 값이라 여기서 미리 정해 넘긴다 (`DrawnCardRow.codeText` 주석 참고).
 */
@androidx.compose.runtime.Immutable
data class DrawDog(
    val id: String?,
    val name: String,
    val codeText: String,
    val isPrimary: Boolean = false,
)

@Composable
private fun IntroBody(
    dogs: List<DrawDog>,
    picked: DrawDog?,
    onPickDog: (DrawDog) -> Unit,
    left: Int,
    busy: Boolean,
    onPick: () -> Unit,
) {
    Spacer(Modifier.height(4.dp))
    Text(
        // **이름을 안 부른다.** 여러 마리를 키우는 사람에게 "네옹 사진으로" 라고 하면
        // 나머지 아이로는 못 뽑는 것처럼 읽힌다. 누구로 뽑을지는 아래에서 고른다.
        if (left > 0) "내 강아지 사진으로 야채 카드를 뽑아요" else "오늘 뽑기를 다 썼어요",
        color = TextDark,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
    )
    Text(
        if (left > 0) {
            "정면으로 찍힌 사진이 제일 잘 나와요. 오늘 ${left}번 남았어요."
        } else {
            "자정이 지나면 다시 ${DAILY_DRAWS}번 뽑을 수 있어요."
        },
        color = TextMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )
    // 한 마리면 안 묻는다. 물어봐야 고를 게 없다.
    if (dogs.size > 1 && left > 0) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dogs.forEach { one ->
                val on = one.id == picked?.id
                Text(
                    one.name,
                    color = if (on) Color.White else TextDark,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) DaengPink else CardWhite)
                        .clickable { onPickDog(one) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    PinkButton(
        label = if (busy) "여는 중…" else "사진 고르기",
        enabled = !busy && left > 0,
        onClick = onPick,
    )
}

/**
 * 카드가 뒤집히며 나온다.
 *
 * `rotationY` 로 돌리고 90도를 넘는 순간 뒷면에서 앞면으로 바꾼다. 넘긴 뒤에는
 * 좌우가 뒤집혀 있어서 한 번 더 뒤집어 바로 세운다. `cameraDistance` 를 안 키우면
 * 원근이 과해서 카드가 화면 밖으로 튀어나오는 것처럼 일그러진다.
 */
@Composable
private fun FlipToCard(
    template: CardTemplate,
    face: CardFace?,
    dogName: String,
    codeText: String,
    onDone: () -> Unit,
) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(template.id) {
        turn.animateTo(180f, tween(760))
        onDone()
    }
    Box(
        Modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(template.ratio)
            .graphicsLayer {
                rotationY = turn.value
                cameraDistance = 14f * density
            },
        contentAlignment = Alignment.Center,
    ) {
        if (turn.value < 90f) {
            CardBack(Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                PersonalCard(template, face, dogName, codeText, Modifier.fillMaxSize())
            }
        }
    }
}

/** 뒷면. 무엇이 나올지 모르는 상태를 그린다 — 도감의 잠금판과 같은 결이다. */
@Composable
private fun CardBack(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF3A2A28)),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = PinkSoft, fontSize = 56.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultBody(
    template: CardTemplate,
    card: DrawnCard,
    face: CardFace?,
    dogName: String,
    codeText: String,
    left: Int,
    onAgain: () -> Unit,
    onOpenDex: () -> Unit,
    onExport: ((DrawnCard) -> Unit)?,
) {
    val dex = DEX_CARDS.firstOrNull { it.id == template.id }
    Box(Modifier.fillMaxWidth(0.72f)) {
        PersonalCard(template, face, dogName, codeText, Modifier.fillMaxWidth())
    }
    Text(dex?.ko ?: template.label, color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)

    // **곡이 있는지 미리 말해 준다.** 열두 장 중 여덟 장은 곡도 무대도 없어서,
    // 아무 말이 없으면 "뽑았는데 아무 일도 안 일어난다" 로 읽힌다.
    val hasTune = dex?.no?.let { IMMERSIVE_SCENES[it]?.bgm } != null
    Text(
        if (hasTune) {
            "♫ 이 카드에는 노래가 있어요 — 턴테이블에서 들을 수 있어요"
        } else {
            "이 카드에는 아직 노래가 없어요"
        },
        color = if (hasTune) DaengPink else TextMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(2.dp))
    PinkButton(label = "도감에서 보기", enabled = true, onClick = onOpenDex)
    onExport?.let { export ->
        QuietButton(label = "파일로 저장", onClick = { export(card) })
    }
    QuietButton(
        label = if (left > 0) "한 번 더 (${left}번 남음)" else "오늘 뽑기를 다 썼어요",
        enabled = left > 0,
        onClick = onAgain,
    )
}

@Composable
private fun PinkButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) DaengPink else PinkSoft)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color.White else TextMuted, fontSize = 15.sp)
    }
}

@Composable
private fun QuietButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) TextDark else TextMuted, fontSize = 14.sp)
    }
}

// -- 프리뷰 ------------------------------------------------------------------

@Preview(name = "뽑기 · 남음", widthDp = 411, heightDp = 400)
@Composable
private fun DrawIntroPreview() {
    DaengsTheme {
        Column(
            Modifier.fillMaxSize().background(CreamBg).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IntroBody(
                dogs = listOf(
                    DrawDog("1", "네옹", birthCode(8, 24), isPrimary = true),
                    DrawDog("2", "몰리", birthCode(3, 3)),
                    DrawDog("3", "찰리", birthCode(11, 9)),
                ),
                picked = DrawDog("1", "네옹", birthCode(8, 24), isPrimary = true),
                onPickDog = {},
                left = 2,
                busy = false,
                onPick = {},
            )
        }
    }
}

@Preview(name = "뽑기 · 다 씀", widthDp = 411, heightDp = 400)
@Composable
private fun DrawEmptyPreview() {
    DaengsTheme {
        Column(
            Modifier.fillMaxSize().background(CreamBg).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IntroBody(
                dogs = listOf(DrawDog("1", "네옹", birthCode(8, 24), isPrimary = true)),
                picked = DrawDog("1", "네옹", birthCode(8, 24), isPrimary = true),
                onPickDog = {},
                left = 0,
                busy = false,
                onPick = {},
            )
        }
    }
}

@Preview(name = "뽑기 결과 · 노래 있는 카드", widthDp = 411, heightDp = 760)
@Composable
private fun DrawResultPreview() {
    DaengsTheme {
        Column(
            Modifier.fillMaxSize().background(CreamBg).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ResultBody(
                template = CABBAGE_CARD,
                card = DrawnCard(
                    id = "preview",
                    appUserId = null,
                    templateId = "cabbage",
                    dogId = null,
                    dogName = "네옹",
                    drawnAtMillis = 0L,
                    codeText = birthCode(8, 24),
                    core = IntRect.Zero,
                ),
                face = null,
                dogName = "네옹",
                codeText = birthCode(8, 24),
                left = 2,
                onAgain = {},
                onOpenDex = {},
                onExport = {},
            )
        }
    }
}

@Preview(name = "카드 뒷면", widthDp = 220, heightDp = 310)
@Composable
private fun CardBackPreview() {
    DaengsTheme { CardBack(Modifier.size(200.dp, 278.dp)) }
}
