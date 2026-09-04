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
import com.daengs.app.dogcard.cardFileName
import com.daengs.app.dogcard.drawTemplate
import com.daengs.app.miniroom.art.rememberAssetImage
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 뽑은 카드를 사진첩에 넣거나 남에게 보낸다. **도감의 `CardViewer` 와 같은 자리다** —
    // 부르는 쪽에서 들고 다닐 것이 없어서 화면이 자기 것을 만든다.
    val saver = rememberCardSaver()

    var step by remember { mutableStateOf(DrawStep.Intro) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Cutout.Result?>(null) }
    // 사용자가 원 안에 맞춘 자리. **이것을 구워서 저장한다.**
    var frame by remember { mutableStateOf(FaceFrame.CENTER) }
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

    // **구멍에는 민판을 넣는다.** 테두리 두른 판을 넣으면 실루엣이 타원 안으로
    // 파고드는 자리마다 흰 띠가 드러나서 카드가 찢어져 보인다.
    val source = (result as? Cutout.Result.Cut)?.plain ?: result?.bitmap

    // 미리 보기는 **굽지 않고 그린다.** 손가락을 따라 900px 짜리를 프레임마다 다시
    // 칠하면 못 따라온다 — 굽는 것은 확인을 누른 뒤 한 번이다 (`drawNow`).

    // 뒤집기 연출과 결과 화면이 쓰는 얼굴. **뽑은 뒤에만 있다** — 그 전에는 사용자가
    // 아직 맞추는 중이라 구운 것이 없다.
    var shown by remember { mutableStateOf<CardFace?>(null) }

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
                    // **앱이 알아서 하던 그 자리에서 시작한다.** 처음부터 다 맞추게 하면
                    // 대부분은 손도 안 대고 넘긴다. 마음에 안 들 때만 만지면 된다.
                    val plain = (it as? Cutout.Result.Cut)?.plain ?: it.bitmap
                    val core = (it as? Cutout.Result.Cut)?.let { c -> Cutout.faceFor(c.plain, 1f).core }
                        ?: android.graphics.Rect(0, 0, plain.width, plain.height)
                    frame = initialFrame(plain.width, plain.height, core)
                    step = DrawStep.Frame
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
        val plain = source ?: return
        busy = true
        error = null
        scope.launch {
            // **맞춘 대로 굽는다.** 여기서 한 번만 굽고, 그리는 쪽은 가운데 정렬
            // 한 번으로 끝난다 (`drawInHoleOf` 의 `framed` 갈래).
            val square = withContext(Dispatchers.Default) { bakeFramed(plain, frame) }
            val core = IntRect(0, 0, square.width, square.height)
            shown = CardFace(square.asImageBitmap(), core, framed = true)
            val template = drawTemplate()
            val card = onDrawn(dog, template, square, core)
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
            guidance = "목 아래는 빼고 얼굴만 담아 주세요. 두 손가락으로 키우거나 모서리를 끌면 됩니다.",
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

            DrawStep.Frame -> {
                val plain = source
                if (plain == null) {
                    Text("얼굴을 오려내는 중이에요…", color = TextMuted, fontSize = 13.sp)
                } else {
                    Text(
                        "원 안에 얼굴을 맞춰 주세요. 이대로 카드에 들어가요.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    FaceFrameStep(
                        face = plain,
                        frame = frame,
                        onChange = { frame = it },
                    )
                    PinkButton(
                        label = if (busy) "뽑는 중…" else "이 얼굴로 뽑기",
                        enabled = !busy,
                        onClick = { drawNow() },
                    )
                    // **되돌아갈 길을 둔다.** 없으면 다시 자르려고 사진 고르기부터
                    // 시작해야 했다.
                    QuietButton("다시 자르기", enabled = !busy) {
                        result = null
                        step = DrawStep.Box
                    }
                }
            }

            // **글자는 뽑은 카드에서 읽는다.** 고른 강아지(`dog`)를 보면 둘러보기처럼
            // 고를 아이가 없을 때 빈 문자열이 되어, 방금 만든 카드가 이름칸도 번호판도
            // 빈 채로 뒤집힌다 — 도감에 가서야 글자가 나타났다. 저장되는 카드는 그
            // 순간에 이미 이름과 번호를 들고 있으므로(`MainActivity` 의 `onDrawn` 이
            // `"우리 아이"`·`birthCode` 로 채운다) 그쪽을 그대로 쓴다.
            DrawStep.Flip -> won?.let { (template, card) ->
                FlipToCard(template, shown, card.dogName, card.codeText) {
                    step = DrawStep.Result
                }
            }

            DrawStep.Result -> won?.let { (template, card) ->
                ResultBody(
                    template = template,
                    card = card,
                    face = shown,
                    left = left,
                    onAgain = {
                        result = null
                        shown = null
                        won = null
                        step = DrawStep.Intro
                    },
                    onOpenDex = onOpenDex,
                    saver = saver,
                )
            }
        }

        error?.let {
            Text(it, color = Color(0xFFC45E5E), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

private enum class DrawStep { Intro, Box, Frame, Flip, Result }

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
    left: Int,
    onAgain: () -> Unit,
    onOpenDex: () -> Unit,
    /** 사진첩에 넣거나 남에게 보낸다. **뽑은 직후가 자랑하고 싶은 순간이다** */
    saver: CardSaver,
) {
    val dex = DEX_CARDS.firstOrNull { it.id == template.id }
    Box(Modifier.fillMaxWidth(0.72f)) {
        // **이름·번호를 따로 안 받는다.** 받던 시절에 부르는 쪽이 카드가 아니라
        // 고른 강아지를 넘겨서, 같은 카드가 팝업에서는 비어 있고 도감에서는 이름이
        // 있었다. 카드를 이미 들고 있으니 여기서 읽으면 둘이 갈릴 수가 없다.
        PersonalCard(template, face, card.dogName, card.codeText, Modifier.fillMaxWidth())
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

    // 빈 판. 도감이 저장할 때 읽는 것과 같은 그림이다. 아직 안 읽혔으면 저장·공유가
    // 안 뜬다 — 눌리면 빈 카드가 나간다.
    val art = rememberAssetImage(template.art)
    art?.let { plate ->
        val shot = CardShot(
            fileName = cardFileName(template.id, card.id, card.drawnAtMillis),
            art = plate,
            template = template,
            face = face,
            name = card.dogName,
            code = card.codeText,
        )
        QuietButton(
            label = when {
                saver.busy -> "저장하는 중…"
                saver.toGallery -> "갤러리에 저장"
                else -> "이미지로 저장"
            },
            onClick = { saver.save(shot) },
        )
        QuietButton(label = "공유하기", onClick = { saver.share(shot) })
    }
    saver.note?.let {
        Text(it, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
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
                left = 2,
                onAgain = {},
                onOpenDex = {},
                saver = rememberCardSaver(),
            )
        }
    }
}

@Preview(name = "카드 뒷면", widthDp = 220, heightDp = 310)
@Composable
private fun CardBackPreview() {
    DaengsTheme { CardBack(Modifier.size(200.dp, 278.dp)) }
}
