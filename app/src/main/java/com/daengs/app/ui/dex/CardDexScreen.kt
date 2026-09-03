package com.daengs.app.ui.dex

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.daengs.app.dogcard.cardFileName
import com.daengs.app.ui.dogcard.rememberCardSaver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.dogcard.DrawnCard
import com.daengs.app.ui.dogcard.drawCardFace
import com.daengs.app.ui.dogcard.drawCardText
import com.daengs.app.miniroom.art.rememberAssetImage
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import kotlin.math.roundToInt
import com.daengs.app.ui.dogcard.rememberComposedCard
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.Image

// ---------------------------------------------------------------------------
// 네오 채소 도감
//
// 카드 12장을 모아 두고, 눌러서 크게 보고, 문지르거나 폰을 기울여 홀로그램 포일을
// 구경한다. 이머시브인 카드는 꾹 누르면 카드 "안으로" 들어간다 ([ImmersiveScreen]).
// 지금은 No.01 배추와 No.10 고구마 둘이다 ([IMMERSIVE_SCENES]).
//
// ## 어디서 왔나
//
// 원본은 팀 저장소 `SAJOYO/DAENGS_dev` 의 웹 데모(`frontend/public/neo-hologram/`)다.
// 한동안 그 폴더를 통째로 앱에 싣고 WebView 로 띄웠는데, **앱으로 통일하기로 하고
// 네이티브로 다시 만들었다.** 웹판은 커밋 3e1bf6c 까지 있으니 필요하면 꺼내 볼 수 있다.
//
// 카드 그림(`assets/neo-hologram/art/`)만 저쪽에서 그대로 가져다 쓴다. 프레임·제목·
// 기술명·수치가 전부 구워져 있어서, 우리가 만드는 건 **그 위에 얹는 효과뿐**이다.
// 포일을 어떻게 옮겼는지는 [Foil] 에 적어 뒀다.
//
// 화면 톤은 앱을 따른다 — 웹판은 어두운 배경(#0E0B05)이라 방에서 넘어올 때 분위기가
// 확 바뀌었는데, 네이티브로 온 이유가 그걸 없애는 것이기도 하다.
// ---------------------------------------------------------------------------

/** 도감 배경. 앱 크림색보다 살짝 가라앉혀 카드가 떠 보이게 한다. */
private val DexBg = Color(0xFFF6E9E3)

/**
 * 아직 안 뽑은 카드를 덮는 색. **불투명하다.**
 *
 * 처음에는 94% 만 덮어 윤곽이 비치게 해 봤는데, 홀로그램 원화가 워낙 밝아서 6% 만
 * 남겨도 `CABBAGE NEO` 도 `CRUNCH 820` 도 다 읽혔다. 그러면 안 뽑고도 카드를 다 본
 * 셈이라 뽑을 이유가 없다.
 *
 * 완전히 덮어도 **카드 모양은 남는다** — `SrcAtop` 은 원화의 알파를 존중해서 둥근
 * 모서리와 테두리 굴곡이 그대로 실루엣으로 나온다. 뽑기의 뒷면과 같은 인상이다.
 */
private val CardLock = Color(0xFF2A1E1B)

/**
 * 잠긴 카드 한가운데의 자물쇠.
 *
 * 아이콘 파일을 안 만든다 — 획 몇 개라 카드 크기에 맞춰 그리는 편이 낫다. 그리드에서
 * 카드가 40dp 까지 줄어드는데 비트맵이면 그때 뭉갠다.
 */
private fun DrawScope.drawLock() {
    // 카드 폭의 18%. 크면 잠금이 주인공이 되고, 작으면 티가 안 난다.
    val w = size.width * 0.18f
    val body = androidx.compose.ui.geometry.Size(w, w * 0.78f)
    val left = (size.width - body.width) / 2f
    val top = (size.height - body.height) / 2f + body.height * 0.18f
    val stroke = (w * 0.11f).coerceAtLeast(1.5f)
    val tint = Color(0x59FFF3EE)

    // 고리. **몸통보다 좁고 높아야 자물쇠로 읽힌다** — 넓고 낮으면 손잡이가 되어
    // 가방처럼 보인다. 처음에 폭 56% · 높이 28% 로 그렸다가 그렇게 나왔다.
    val ringW = body.width * 0.48f
    val ringH = body.height * 0.62f
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(left + (body.width - ringW) / 2f, top - ringH),
        // 위 반원만 그리므로 상자 높이의 절반이 곧 고리 높이다.
        size = androidx.compose.ui.geometry.Size(ringW, ringH * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    // **몸통을 채운다.** 비워 두면 고리 끝이 몸통 안까지 그려져 보여서 고리가
    // 몸통을 뚫고 나온 것처럼 된다. 채우면 끝이 뒤로 들어간 것으로 읽힌다.
    drawRoundRect(
        color = CardLock,
        topLeft = Offset(left, top),
        size = body,
        cornerRadius = CornerRadius(body.width * 0.18f),
    )
    drawRoundRect(
        color = tint,
        topLeft = Offset(left, top),
        size = body,
        cornerRadius = CornerRadius(body.width * 0.18f),
        style = Stroke(width = stroke),
    )
}

/** 뽑은 날. 기기 시간대로 읽는다 — 뽑은 사람의 하루가 기준이다. */
private fun drawnOn(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .let { "%d월 %d일".format(it.monthValue, it.dayOfMonth) }

/** 카드 칸의 세로 비율. 웹판 `.slot .frame { aspect-ratio: 4/5 }` 와 같다. */
private const val SLOT_RATIO = 1.25f



@Composable
fun CardDexScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 내가 뽑은 카드. 비어 있으면 열두 칸이 다 잠긴다 */
    drawn: List<DrawnCard> = emptyList(),
    /**
     * 뽑기 화면을 띄운다. null 이면 "카드 뽑기" 자리가 안 보인다 —
     * `@Preview` 와 테스트가 그렇게 부른다.
     */
    draw: (@Composable (onDone: () -> Unit) -> Unit)? = null,
    startInDraw: Boolean = false,
    /** 지금 방 액자에 걸려 있는 카드. null 이면 발자국이 걸려 있다 */
    framedCardId: String? = null,
    /**
     * 액자에 건다. null 을 주면 내리고 발자국으로 돌아간다.
     *
     * null 이면 그 자리가 안 뜬다 — `@Preview` 와 테스트가 그렇게 부른다.
     */
    onFrame: ((DrawnCard?) -> Unit)? = null,
    /**
     * 카드 한 장을 지운다. **되돌릴 수 없다.**
     *
     * null 이면 그 자리가 안 뜬다 — `@Preview` 와 테스트가 그렇게 부른다.
     */
    onDelete: ((DrawnCard) -> Unit)? = null,
) {
    var opened by remember { mutableStateOf<Int?>(null) }
    // **어느 장면인지가 곧 이머시브인지 여부다.** 예전에는 켜짐/꺼짐 불리언 하나였는데,
    // 카드가 둘이 되면서 "켜졌다"만으로는 무엇을 그릴지 모른다.
    var scene by remember { mutableStateOf<ImmersiveScene?>(null) }
    // 이머시브가 출발할 자리. 그리드가 사라진 뒤에도 써야 하므로 여기 둔다.
    var from by remember { mutableStateOf(Rect.Zero) }
    // 무대에 쓸 이름. 들어간 칸의 내 카드 이름이고, 아직 안 뽑았으면 null 이다.
    var sceneName by remember { mutableStateOf<String?>(null) }
    // 무대 주인공에 끼울 얼굴. 들어간 칸의 내 카드에서 온다.
    var sceneFace by remember { mutableStateOf<SubjectFace?>(null) }
    // **화면을 안 늘린다.** 뽑기는 `Screen` 에 새 갈래를 내지 않고 도감 위에 덮인다 —
    // 확대 뷰·이머시브가 이미 그 방식이라 결이 맞고, `MainActivity` 를 안 건드린다.
    var drawing by remember { mutableStateOf(startInDraw && draw != null) }
    val slots = remember(drawn) { dexSlots(drawn = drawn) }

    BackHandler {
        when {
            drawing -> drawing = false
            scene != null -> scene = null
            opened != null -> opened = null
            else -> onClose()
        }
    }

    if (drawing && draw != null) {
        draw { drawing = false }
        return
    }

    scene?.let { showing ->
        ImmersiveScreen(
            scene = showing,
            // 무대에도 카드와 같은 이름을 쓴다. 꾹 눌러 들어간 그 칸의 내 카드다.
            titleOverride = sceneName,
            hero = sceneFace,
            from = from,
            onClose = { scene = null },
        )
        return
    }

    Box(modifier.fillMaxSize().background(DexBg)) {
        DexGrid(
            slots = slots,
            onOpen = { opened = it },
            onClose = onClose,
            onDraw = draw?.let { { drawing = true } },
            onImmersive = { at, picked, whose, theirFace ->
                from = at
                scene = picked
                sceneName = whose
                sceneFace = theirFace
            },
        )

        AnimatedVisibility(
            visible = opened != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val start = opened ?: 0
            CardViewer(
                slots = slots,
                startIndex = start,
                onClose = { opened = null },
                framedCardId = framedCardId,
                onFrame = onFrame,
                onDelete = onDelete,
            )
        }
    }
}

// -- 그리드 -----------------------------------------------------------------

@Composable
private fun DexGrid(
    slots: List<DexSlot>,
    onOpen: (Int) -> Unit,
    onClose: () -> Unit,
    onImmersive: (Rect, ImmersiveScene, String?, SubjectFace?) -> Unit,
    onDraw: (() -> Unit)? = null,
) {
    LazyVerticalGrid(
        // 두 칸. 웹판에서 한 칸이면 카드가 화면을 꽉 채워 무거웠다.
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            DexHeader(
                kinds = slots.collectedKinds(),
                total = slots.ownedTotal(),
                onClose = onClose,
                onDraw = onDraw,
            )
        }
        itemsIndexed(slots) { index, slot ->
            GridCard(
                slot = slot,
                onOpen = { onOpen(index) },
                // 이머시브인 카드는 [IMMERSIVE_SCENES] 가 정한다. 없으면 null 이 가고,
                // 그러면 꾹 누르기도 캡션 아래 배지도 안 붙는다.
                //
                // **아직 안 뽑은 칸에서는 안 들어간다.** 잠긴 카드가 무대까지 열어
                // 주면 뽑을 이유가 없다.
                // 이머시브는 v1 에 안 들어간다 (`IMMERSIVE_IN_BUILD` 주석 참고).
                // null 이면 꾹 누르기도, 캡션 아래 배지도 안 붙는다.
                onImmersive = IMMERSIVE_SCENES[slot.card.no]
                    ?.takeIf { IMMERSIVE_IN_BUILD && !slot.locked }
                    ?.let { picked ->
                        { at: Rect, hero: SubjectFace? ->
                            onImmersive(at, picked, slot.owned.firstOrNull()?.dogName, hero)
                        }
                    },
            )
        }
    }
}

@Composable
private fun DexHeader(kinds: Int, total: Int, onClose: () -> Unit, onDraw: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "← 방으로",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("채소가 된 우리 아이", color = TextDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            // **여기가 오래 거짓말을 하던 자리다.** 분자·분모가 둘 다 `DEX_CARDS.size` 라
            // 뽑지도 않은 카드를 12/12 수집이라고 말했다.
            if (kinds == 0) {
                "카드를 뽑아 도감을 채워 보세요"
            } else {
                "$kinds / ${DEX_CARDS.size} 수집 · 내 카드 ${total}장"
            },
            color = TextMuted,
            fontSize = 12.sp,
        )
        onDraw?.let { go ->
            Spacer(Modifier.height(12.dp))
            Text(
                "＋ 카드 뽑기",
                color = DaengPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .clickable(onClick = go)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GridCard(
    slot: DexSlot,
    onOpen: () -> Unit,
    onImmersive: ((Rect, SubjectFace?) -> Unit)?,
) {
    val card = slot.card
    // 그리드에서는 작게 그리므로 절반 크기로 읽는다. 12장을 원본으로 들면 55MB 다.
    //
    // **표지는 가장 최근에 뽑은 것이다.** 방금 뽑은 카드가 도감에 안 보이면 뽑은 것
    // 같지가 않다. 아직 안 뽑았으면 카탈로그 원화를 어둡게 덮는다.
    val mine = slot.owned.firstOrNull()
    // **얼굴 한 장이 곧 카드가 아니다.** 자리를 비운 원화 위에 얼굴을 깔고 글자를
    // 얹어야 카드가 된다 — 뽑기 화면이 하는 것과 같은 순서다.
    val drawn = if (mine != null) rememberDrawnCardArt(mine) else null
    val cover = when {
        drawn == null -> card.artSource
        drawn.composed -> CardArt.Asset(drawn.template!!.art)
        else -> drawn.fallback
    }
    val art = rememberCardImage(cover, sample = 2)
    val measurer = rememberTextMeasurer()

    // 이머시브가 **이 카드 자리에서** 출발하도록 화면 위 사각형을 들고 있는다.
    // 창 위 좌표라 이머시브가 자기 자리를 빼서 쓴다 — 둘 다 같은 창이라 그걸로 맞는다.
    var at by remember { mutableStateOf(Rect.Zero) }
    // 무대 주인공에 끼울 얼굴. **합쳐 놓은 카드가 있을 때만** 넘긴다 — 시드는 얼굴이
    // 없어서 누끼가 뚫린 채로 그려진다. 자리는 카드 구멍에서 계산해 온다.
    val hero = drawn?.takeIf { it.composed }?.let { d ->
        IMMERSIVE_SCENES[card.no]?.let { sc ->
            SubjectFace(
                face = d.face!!,
                hole = sc.faceInSubject(d.template!!),
                // 진입 연출도 우리 카드로 녹는다. 그리드가 이미 읽어 둔 판을 그대로 쓴다.
                entryArt = art,
                template = d.template,
                name = d.name,
                code = d.code,
            )
        }
    }
    val enter = onImmersive?.let { go -> { go(at, hero) } }
    val rub = rememberRubState(onTap = onOpen, onHold = enter)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // **카드 높이를 칸마다 똑같이 맞춘다.** 그림 비율이 두 종류라(0.72 와 0.80)
        // 폭을 맞추면 높이가 제각각이 되어 줄이 어긋난다. 웹판이 "실물 카드 바인더와
        // 같은 정렬" 이라고 부르는 배치를 그대로 쓴다 — 높이는 같고 폭만 비율만큼
        // 달라지며, 그림은 한 픽셀도 안 잘린다.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HoloCard(
                art = art,
                foil = card.foil,
                input = rub.input,
                // 그리드에서는 기울이지 않는다. 열두 장이 한꺼번에 도는 건 산만하다.
                tilt = false,
                // 꾹 누르는 동안 차오르는 테두리. **없으면 카드가 멈춘 줄 안다** —
                // 꾹 누르기는 눌러보기 전엔 알 수가 없다.
                hold = rub.hold,
                holdColor = card.accent,
                veil = if (slot.locked) CardLock else null,
                beneath = if (drawn?.composed == true) {
                    { drawCardFace(drawn.face!!, drawn.template!!) }
                } else {
                    null
                },
                above = if (drawn?.composed == true) {
                    { drawCardText(measurer, drawn.template!!, drawn.name, drawn.code) }
                } else {
                    null
                },
                // 칸 폭의 4:5. 웹판 `.slot .frame` 과 같은 비율이다.
                modifier = Modifier
                    .height(maxWidth * SLOT_RATIO)
                    .onGloballyPositioned { at = it.boundsInWindow() }
                    // **드래그를 안 먹는다.** 먹으면 카드를 짚고 쓸어내릴 때 목록이
                    // 안 움직인다.
                    .rubbable(rub, consume = false),
            )
            // **잠긴 카드에는 자물쇠를 얹는다.** 통째로 덮고 나면 그냥 검은 네모라
            // 그림을 못 받아 온 칸인지 안 뽑은 칸인지 구분이 안 된다.
            if (slot.locked) {
                Canvas(Modifier.matchParentSize()) { drawLock() }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("No. %02d".format(card.no), color = TextMuted, fontSize = 10.sp)
        if (slot.locked) {
            // **이름을 안 알려 준다.** 무엇인지 모르는 게 뽑을 이유다.
            Text("???", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else {
            // **카드의 이름은 우리 아이 이름이다.** 카탈로그의 `Cabbage Neo` 는 저쪽
            // 개(네오)의 카드 이름이라, 내가 뽑은 카드에 그대로 두면 남의 개 이름이 된다.
            Text(
                mine?.dogName ?: card.name,
                color = TextDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            // 어느 야채인지는 여기 남긴다. 그림과 번호만으로는 헷갈린다.
            Text(
                "${card.ko} · ${card.statLine}",
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            if (slot.drawnCount > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "×${slot.drawnCount}",
                    color = DaengPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(CardWhite)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        // **캡션 아래에 둔다.** 카드와 캡션 사이에 끼우면 이 칸만 캡션이 밀려 내려가
        // 옆 칸과 줄이 어긋난다. 웹판도 캡션 다음이다.
        if (onImmersive != null) {
            Spacer(Modifier.height(6.dp))
            // 꾹 누르기는 발견해야 아는 손짓이라 유일한 길이면 안 된다 (저쪽 주석).
            Text(
                "★★★ 꾹 눌러서 들어가기",
                color = TextDark,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(card.accent.copy(alpha = 0.25f))
                    .clickable { onImmersive(at, hero) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// -- 확대 뷰 ----------------------------------------------------------------

/**
 * 카드를 화면 가득 보는 뷰.
 *
 * 저쪽이 정한 손짓을 그대로 따른다 — **문지르면 포일 구경**, 좌우 버튼으로 넘기기.
 * 쓸어서 넘기기는 저쪽이 해보고 버렸다 (포일을 구경하다 보면 손이 저절로 빨라져서
 * 속도로는 못 가른다).
 */
@Composable
private fun CardViewer(
    slots: List<DexSlot>,
    startIndex: Int,
    onClose: () -> Unit,
    framedCardId: String? = null,
    onFrame: ((DrawnCard?) -> Unit)? = null,
    /**
     * 카드 한 장을 지운다. **되돌릴 수 없다.**
     *
     * null 이면 그 자리가 안 뜬다 — `@Preview` 와 테스트가 그렇게 부른다.
     */
    onDelete: ((DrawnCard) -> Unit)? = null,
) {
    var index by remember { mutableIntStateOf(startIndex) }
    val slot = slots[index]
    val card = slot.card
    // **한 칸 안에서 몇 번째 장인가.** 같은 야채를 여러 번 뽑으면 사진마다 표정이
    // 달라서 전부 다른 카드다 — 한 장만 보여 주면 나머지는 뽑은 보람이 없다.
    // 종류를 넘기는 것은 좌우라, 같은 칸 안은 위아래로 넘긴다.
    var copy by remember(index) { mutableIntStateOf(0) }
    val mine = slot.owned.getOrNull(copy)
    val drawn = if (mine != null) rememberDrawnCardArt(mine) else null
    val cover = when {
        drawn == null -> card.artSource
        drawn.composed -> CardArt.Asset(drawn.template!!.art)
        else -> drawn.fallback
    }
    // 확대 뷰는 한 장뿐이라 원본 해상도로 읽는다.
    val art = rememberCardImage(cover)
    val measurer = rememberTextMeasurer()
    // 카드를 파일로 꺼낸다. 뽑은 카드가 있을 때만 쓸 자리가 생긴다.
    val saver = rememberCardSaver()

    // 카드를 한 번 더 누르면 설명이 열린다. **문지르면 안 열린다** — 안 움직이고
    // 뗐을 때만 탭이다 (`Modifier.rubbable`). 포일을 구경하다 설명이 튀어나오면
    // 그건 방해다.
    var showDetail by remember { mutableStateOf(false) }
    // 카드를 넘기면 설명은 그 카드의 것으로 바뀐다. 닫지 않는다 — 웹판도 그렇고,
    // 설명을 보며 넘기는 것이 이 시트의 쓸모다.
    // **잠긴 칸은 설명을 안 연다.** 안 가진 카드의 기술·수치를 다 보여 주면 뽑을
    // 이유가 사라진다.
    val rub = rememberRubState(onTap = { if (!slot.locked) showDetail = !showDetail })

    // 설명이 열려 있으면 뒤로가기가 그것부터 닫는다. 바깥(도감)의 BackHandler 보다
    // 안쪽이라 저절로 먼저 잡힌다.
    BackHandler(enabled = showDetail) { showDetail = false }

    // 폰을 기울이면 카드가 따라 기운다. **확대 뷰에서만** 켠다 — 그리드에서 열두 장이
    // 한꺼번에 도는 건 산만하고 비싸다.
    val tiltTracker = remember { TiltTracker() }
    DeviceTilt { beta, gamma -> tiltTracker.feed(beta, gamma) }
    // 카드를 넘기면 지금 자세가 다시 정면이 된다
    LaunchedEffect(index) { tiltTracker.reset() }

    // **손가락이 이긴다.** 두 입력이 같은 카드를 두고 매 프레임 싸우면 화면이 떤다.
    val input = if (rub.input.intensity > 0f) rub.input else (tiltTracker.input ?: rub.input)

    // 누끼 팝아웃. **짚고 있는 동안** 주인공이 카드 밖으로 떠오른다.
    //
    // 저쪽은 마우스를 올려놓으면(hover) 나는데 **터치에는 hover 가 없다.** 저쪽도
    // 그래서 "폰에서는 안 난다 — 아직 안 정했다" 고 남겨 뒀다. 짚고 있는 동안이
    // 제일 가깝다: 문지르면 포일이 도는 동작과 같이 나서 보상처럼 읽히고, 손을 떼면
    // 저절로 돌아간다. 누르는 동작을 새로 쓰지 않으므로 탭(닫기)·꾹(이머시브)과도
    // 안 겹친다. 그리드에 걸지 않은 것은, 터치에서는 탭이 곧바로 확대 뷰를 열어
    // 그리드의 팝아웃을 볼 겨를이 없기 때문이다.
    // `let` 안에서는 @Composable 을 못 부른다. 조건은 if 로 갈라야 한다.
    val popPath = card.pop?.subject
    val hero = if (popPath != null) rememberAssetImage(popPath) else null
    val popped by animateFloatAsState(
        targetValue = if (rub.input.intensity > 0f) 1f else 0f,
        animationSpec = tween(durationMillis = if (rub.input.intensity > 0f) 520 else 320),
        label = "pop",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE8241C1A))
            // 카드 밖을 누르면 닫힌다
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.systemBarsPadding().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 설명이 열리면 **카드를 죽인다.** 글씨가 포일 위에 얹히면 둘 다 안 읽힌다.
            //
            // 검은 사각형을 덮지 않고 투명도를 내린다 — 카드 그림은 모서리가 둥글게
            // 뚫린 알파 이미지라, 사각형을 얹으면 그 뚫린 자리가 네모로 도드라진다.
            // 바탕이 이미 어두워서 투명도만 내려도 어두워진다.
            val cardAlpha by animateFloatAsState(
                targetValue = if (showDetail) 0.5f else 1f,
                animationSpec = tween(durationMillis = 180),
                label = "cardDim",
            )
            // 바깥 상자가 **카드의 자리**다. 설명 시트가 이 상자를 꽉 채워서 카드와
            // 정확히 같은 크기가 된다.
            Box(Modifier.fillMaxWidth()) {
            // 죽는 것은 카드와 팝아웃뿐이다. 시트는 이 겹 밖에 있어서 안 죽는다.
            //
            // ⚠️ 투명도를 `rubbable` 과 **같은 사슬에 걸면 안 된다.** 그러면 탭이
            // 안 먹혀서 카드를 눌렀을 때 뒤의 "밖을 눌러 닫기" 가 대신 발동한다 —
            // 실기기에서 그렇게 나왔다. 겹을 따로 둔다.
            Box(Modifier.fillMaxWidth().graphicsLayer { alpha = cardAlpha }) {
            HoloCard(
                art = art,
                foil = card.foil,
                input = input,
                // 잠긴 카드는 안 기울인다. 포일도 안 도는데 기울면 그냥 흔들리는 검은 판이다.
                tilt = !slot.locked,
                veil = if (slot.locked) CardLock else null,
                beneath = if (drawn?.composed == true) {
                    { drawCardFace(drawn.face!!, drawn.template!!) }
                } else {
                    null
                },
                above = if (drawn?.composed == true) {
                    { drawCardText(measurer, drawn.template!!, drawn.name, drawn.code) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().rubbable(rub),
            )
            // 팝아웃은 카드 **위로 넘어가야** 하므로 카드와 같은 크기의 덧그림 판에서
            // 음수 좌표로 그린다. Compose 는 기본으로 안 자르므로 그대로 보인다.
            if (hero != null && card.pop != null && !slot.locked) {
                Canvas(Modifier.matchParentSize()) { drawPopOut(hero, card.pop.fit, popped) }
            }
            if (slot.locked) {
                Canvas(Modifier.matchParentSize()) { drawLock() }
            }
            }

            if (showDetail) {
                CardDetailSheet(
                    card = card,
                    mine = mine,
                    // **그림을 다 읽은 뒤에만 저장이 뜬다.** 아직 안 읽혔는데 눌리면
                    // 빈 카드가 파일로 나간다.
                    onSave = if (mine != null && art != null) {
                        {
                            saver.save(
                                fileName = cardFileName(
                                    templateId = mine.templateId,
                                    cardId = mine.id,
                                    at = mine.drawnAtMillis,
                                ),
                                art = art,
                                // 얼굴이 없는 카드(시드 열두 장)는 원화가 곧 그림이라
                                // 합치지 않고 그대로 내보낸다.
                                template = if (drawn?.composed == true) drawn.template else null,
                                face = drawn?.face,
                                name = mine.dogName,
                                code = mine.codeText,
                            )
                        }
                    } else {
                        null
                    },
                    saveBusy = saver.busy,
                    saveNote = saver.note,
                    // 이 카드가 지금 액자에 걸려 있나. 걸려 있으면 내리는 자리가 된다.
                    framed = mine != null && mine.id == framedCardId,
                    onFrame = if (mine != null && onFrame != null) {
                        { onFrame(if (mine.id == framedCardId) null else mine) }
                    } else {
                        null
                    },
                    onDelete = if (mine != null && onDelete != null) {
                        { onDelete(mine) }
                    } else {
                        null
                    },
                    lastCopy = slot.count <= 1,
                    onPrev = { index = (index - 1 + slots.size) % slots.size },
                    onNext = { index = (index + 1) % slots.size },
                    onClose = { showDetail = false },
                    // **카드와 같은 크기.** 카드 자리를 그대로 덮는다.
                    modifier = Modifier.matchParentSize(),
                )
            }
            }
            Spacer(Modifier.height(14.dp))
            // 설명이 열리면 이 줄은 시트에 가린다. 넘기기는 시트 안으로 옮겨 간다.
            if (!showDetail) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NavButton("‹") { index = (index - 1 + slots.size) % slots.size }
                    Spacer(Modifier.size(18.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (slot.locked) {
                            Text("???", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("아직 안 뽑았어요", color = Color(0xFFD9C9C3), fontSize = 12.sp)
                        } else {
                            Text(
                                mine?.dogName ?: card.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${card.ko} · ${card.statLine}",
                                color = Color(0xFFD9C9C3),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(Modifier.size(18.dp))
                    NavButton("›") { index = (index + 1) % slots.size }
                }
                // **같은 칸에 여러 장이면 그 안에서 넘긴다.** 좌우는 이미 종류를
                // 넘기는 데 쓰이므로 여기서 또 쓰면 뜻이 겹친다. 점을 눌러 고른다.
                if (slot.count > 1) {
                    Spacer(Modifier.height(10.dp))
                    CopyStrip(
                        owned = slot.owned,
                        picked = copy,
                        onPick = { copy = it },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${copy + 1} / ${slot.count} · 이 야채로 ${slot.drawnCount}장 뽑았어요",
                        color = Color(0xFF9E8B84),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 안 알려 주면 아무도 두 번 안 누른다. 웹판에도 있던 힌트다.
                if (!slot.locked) {
                    Text("탭하여 상세보기", color = Color(0xFF9E8B84), fontSize = 12.sp)
                }
            }
        }

        Text(
            "✕",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .clickable(onClick = onClose)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * 짚고 있는 동안 주인공이 카드 밖으로 떠오른다.
 *
 * 저쪽 값 그대로다 — 위로 **카드 높이의 16%(최대 90dp)** 만큼 넘고 **1.06배**로
 * 커진다. 저쪽은 3D 라 원근(1150)이 한 번 더 키우므로 실제 배율은
 * `1.06 x 1150/(1150-74) = 1.133` 이고, 우리는 평면이라 그 값을 그대로 쓴다.
 *
 * 커지는 만큼은 **전부 위로** 간다. 저쪽 `transform-origin: 50% 100%` 이라 아래가
 * 제자리에 붙어 있어야 카드에서 자라 나온 것으로 보인다.
 *
 * 떠오른 동안 카드를 어둡게 죽인다. **안 죽이면 유령이 하나 더 보인다** — 카드
 * 그림에도 같은 주인공이 인쇄돼 있기 때문이다.
 */
private fun DrawScope.drawPopOut(hero: ImageBitmap, fit: ImmersiveScene.Fit, t: Float) {
    if (t <= 0.001f) return
    val w = size.width * fit.w / 100f
    val h = size.height * fit.h / 100f
    val left = size.width * fit.x / 100f
    val top = size.height * fit.y / 100f

    drawRect(Color.Black.copy(alpha = 0.38f * t))

    val grow = 1f + (1.133f - 1f) * t
    val rise = minOf(size.height * 0.16f, 90.dp.toPx()) * t
    val gw = w * grow
    val gh = h * grow
    val x = left - (gw - w) / 2f
    val y = top - (gh - h) - rise

    // 그림자. 저쪽 `drop-shadow(0 18px 26px)` 을 어두운 사본 한 장으로 흉내 낸다 —
    // 진짜 블러는 API 31 부터라 minSdk 26 에서 못 쓴다.
    drawImage(
        image = hero,
        dstOffset = IntOffset(x.roundToInt(), (y + 18.dp.toPx()).roundToInt()),
        dstSize = IntSize(gw.roundToInt(), gh.roundToInt()),
        alpha = 0.34f * t,
        colorFilter = ColorFilter.tint(Color.Black),
        filterQuality = FilterQuality.High,
    )
    drawImage(
        image = hero,
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(gw.roundToInt(), gh.roundToInt()),
        alpha = t,
        filterQuality = FilterQuality.High,
    )
}

/**
 * 카드 설명 시트.
 *
 * **웹판에 있던 것을 되살린 것이다.** Compose 로 옮길 때 조용히 빠졌다 — 카드 그림에
 * 제목·기술·수치가 구워져 있어서 "다시 만들 게 효과뿐" 이라고 보았는데, 그림에 안
 * 구워진 **글**(부제·코드·플레이버·에디션)이 같이 사라졌다 (HISTORY 12절).
 *
 * 저쪽 `main.js` 의 `detailMarkup` 과 같은 순서다. 순서를 바꾸면 웹과 앱이 다른
 * 카드처럼 보인다.
 *
 * **[Dialog] 를 안 쓴다.** 도감은 이미 자기 손으로 배경을 죽이고 있어서 한 겹 더
 * 얹으면 되고, Dialog 는 별도 창이라 그 아래 카드의 포일·기울기와 안 겹친다.
 *
 * 작은 폰에서 잘리므로 **세로로 스크롤된다** (웹도 `overflow-y: auto`).
 */
@Composable
private fun CardDetailSheet(
    card: DexCard,
    /** 이 칸에서 지금 보고 있는 내 카드. null 이면 카탈로그 설명만 보여 준다 */
    mine: DrawnCard? = null,
    /** 이미지로 저장한다. null 이면 그 줄이 안 뜬다 — 아직 안 뽑은 칸이 그렇다 */
    onSave: (() -> Unit)? = null,
    saveBusy: Boolean = false,
    /** 저장하고 나서 한 줄. 잠시 뒤 사라진다 */
    saveNote: String? = null,
    /** 이 카드가 지금 방 액자에 걸려 있나 */
    framed: Boolean = false,
    /** 액자에 걸거나 내린다. null 이면 그 자리가 안 뜬다 */
    onFrame: (() -> Unit)? = null,
    /** 이 카드를 지운다. **되돌릴 수 없다.** null 이면 그 자리가 안 뜬다 */
    onDelete: (() -> Unit)? = null,
    /** 이 칸의 마지막 한 장인가. 지우면 도감 칸이 다시 잠긴다 */
    lastCopy: Boolean = false,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // **한 번 더 묻는다.** 카드는 서버에 사본이 없어서 지우면 그것으로 끝이다.
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) {
        DeleteCardDialog(
            lastCopy = lastCopy,
            onCancel = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        // **살짝 비친다.** 뒤의 카드가 어렴풋이 보여야 "그 카드의 설명" 으로 읽힌다.
        // 더 투명하게 하면 포일 위에서 글씨가 안 읽힌다 — 여기가 그 경계다.
        color = Color(0xDE1C1614),
        modifier = modifier,
    ) {
        // 카드 한가운데에 앉힌다. 위에 붙여 두면 아래가 휑하게 남는다.
        //
        // 스크롤을 감싸는 상자가 카드 크기를 잡아 주므로, 글이 길어지면 그 안에서
        // 스크롤되고 짧으면 가운데로 모인다.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
            Text(card.tagline, color = card.accent, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(4.dp))
            // **내 카드면 카드에 인쇄된 이름을 그대로 쓴다.** 카드 그림에는 우리 아이
            // 이름이 찍혀 있는데 설명만 `Cabbage Neo` 라고 하면 같은 카드가 두 이름을
            // 갖는다. 아직 안 뽑은 칸에서는 저쪽 이름이 그대로 나온다.
            Text(
                mine?.dogName ?: card.name,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            // 어느 야채인지.
            Text(card.ko, color = Color(0xFFD9C9C3), fontSize = 13.sp)
            mine?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${drawnOn(it.drawnAtMillis)}에 뽑았어요",
                    color = Color(0xFF9E8B84),
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(14.dp))
            // **번호판도 내 카드의 것으로.** 카탈로그의 `NEO-0824` 는 저쪽 카드에
            // 인쇄된 값이고, 우리 카드에는 아이 생일에서 만든 번호가 찍혀 있다.
            // 번호판은 **내 카드의 것만** 보여 준다. 카탈로그의 `NEO-0824` 는 저쪽
            // 카드에 인쇄돼 있던 값이라 우리 화면에 나올 이유가 없다.
            card.detailRows(code = mine?.codeText).forEach { row ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(
                        row.label,
                        color = Color(0xFF9E8B84),
                        fontSize = 12.sp,
                        modifier = Modifier.width(64.dp),
                    )
                    Column {
                        Text(row.value, color = Color.White, fontSize = 13.sp)
                        // 기술 부연. 없는 카드가 있어서 있을 때만 붙는다.
                        if (row.note.isNotBlank()) {
                            Text(
                                row.note,
                                color = Color(0xFF9E8B84),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                card.flavor,
                color = Color(0xFFD9C9C3),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontStyle = FontStyle.Italic,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                card.edition,
                color = card.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )

            if (onFrame != null) {
                Spacer(Modifier.height(14.dp))
                // **거는 것과 내리는 것을 한 자리에 둔다.** 버튼을 둘 두면 안 걸린
                // 카드에서도 "내리기" 가 보이고, 그게 무엇을 내린다는 건지 알 수 없다.
                SheetAction(if (framed) "액자에서 내리기" else "방 액자에 걸기", onFrame)
                if (framed) {
                    Spacer(Modifier.height(6.dp))
                    Text("지금 방에 걸려 있어요", color = Color(0xFF9E8B84), fontSize = 12.sp)
                }
            }

            if (onSave != null) {
                Spacer(Modifier.height(14.dp))
                // **넘기기와 한 줄에 안 둔다.** 넘기기는 구경하는 동작이고 저장은
                // 파일이 하나 생기는 동작이라, 같은 줄에 있으면 다음 카드를 누르려다
                // 저장 창이 뜬다.
                SheetAction(
                    if (saveBusy) "저장하는 중…" else "이미지로 저장",
                    onClick = if (saveBusy) ({}) else onSave,
                )
                // 저장은 창이 닫히고 나면 아무 표시가 없다 — 됐는지 안 됐는지를
                // 여기서 말해 준다.
                if (saveNote != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(saveNote, color = Color(0xFF9E8B84), fontSize = 12.sp)
                }
            }

            if (onDelete != null) {
                Spacer(Modifier.height(10.dp))
                // **조용한 글씨다.** 되돌릴 수 없는 자리를 저장과 같은 무게로 두면
                // 다음 카드를 누르려다 눌린다.
                Text(
                    "이 카드 지우기",
                    color = Color(0xFFC98C86),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            // **넘기기를 여기 둔다.** 설명이 열리면 카드 옆 ‹ › 는 가려지므로, 설명을
            // 보면서 다음 카드로 가려면 이 줄이 있어야 한다 (웹판도 같다).
            Row(verticalAlignment = Alignment.CenterVertically) {
                    SheetAction("‹ 이전", onPrev)
                    Spacer(Modifier.size(10.dp))
                    SheetAction("다음 ›", onNext)
                    Spacer(Modifier.weight(1f))
                    SheetAction("닫기", onClose)
                }
            }
        }
    }
}

/**
 * 지우기 전에 한 번 더 묻는다.
 *
 * **되돌릴 수 없다는 것과, 도감 칸이 어떻게 되는지를 같이 말한다.** 마지막 한 장을
 * 지우면 그 칸이 다시 잠기는데(`DexSlot.locked` 이 `owned.isEmpty()` 다), 그걸 모르고
 * 지우면 모은 것이 줄어든 이유를 알 수가 없다.
 */
@Composable
private fun DeleteCardDialog(
    lastCopy: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("이 카드를 지울까요?") },
        text = {
            Text(
                if (lastCopy) {
                    "되돌릴 수 없어요. 이 야채의 마지막 한 장이라 도감 칸이 다시 잠겨요."
                } else {
                    "되돌릴 수 없어요."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("지우기", color = Color(0xFFC0554E)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("그대로 두기") } },
    )
}

/**
 * 같은 야채를 여러 장 뽑았을 때 고르는 줄.
 *
 * 예전에는 7~9dp 짜리 **점**이었다. 누를 자리가 너무 작았고, 무엇보다 **어떤 카드들인지
 * 안 보였다** — 같은 야채라도 얼굴과 뽑은 날이 다른데 점은 그걸 말해 주지 않는다.
 *
 * 좌우 화살표는 **다른 야채**라는 뜻으로 이미 쓰이고 있어서 여기 못 쓴다. 그래서
 * 작은 카드를 늘어놓고 고르게 한다.
 *
 * 그림은 `rememberComposedCard` 가 굽는다 — 방 액자가 쓰는 그것이다. 장수가 많아야
 * 서너 장이라 다 구워도 부담이 없다.
 */
@Composable
private fun CopyStrip(
    owned: List<DrawnCard>,
    picked: Int,
    onPick: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        owned.forEachIndexed { i, card ->
            val thumb = rememberComposedCard(card, width = COPY_THUMB_PX)
            Box(
                Modifier
                    .height(52.dp)
                    .aspectRatio(COPY_THUMB_RATIO)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x22FFFFFF))
                    // 고른 것만 테두리를 두른다. 밝기로만 가르면 포일 위에서 안 보인다.
                    .border(
                        width = if (i == picked) 2.dp else 0.dp,
                        color = if (i == picked) DaengPink else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onPick(i) },
            ) {
                thumb?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** 작은 카드를 구울 가로 픽셀. 52dp 높이로 뜨는 자리라 이만하면 넉넉하다. */
private const val COPY_THUMB_PX = 160

/** 카드 비율. 열두 장이 1080x1440 한 판이다. */
private const val COPY_THUMB_RATIO = 1080f / 1440f

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = TextDark,
        fontSize = 20.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(PinkFaint)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Suppress("unused")
private val unusedPalette = listOf(CreamBg, DaengPink)
