package com.daengs.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.daengs.app.dogcard.DrawnCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.art.rememberAssetImage
import com.daengs.app.ui.dogcard.rememberComposedCard
import com.daengs.app.ui.dex.DEX_CARDS
import com.daengs.app.ui.dex.DexCard
import com.daengs.app.ui.dex.bgmFor
import com.daengs.app.ui.dex.SceneMusic
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// 턴테이블 — 내 카드의 음악을 튼다
//
// 방 뒷벽의 턴테이블을 누르면 방 위로 올라온다. **방을 덮지 않는다** — 이 방의
// 전축을 튼 것이라, 방과 턴테이블이 계속 보여야 그 맥락이 산다.
//
// ## 곡은 이머시브 장면에서 온다
//
// 곡을 따로 들고 있지 않는다. 이머시브 장면(`IMMERSIVE_SCENES`)이 이미 카드마다
// 곡을 하나씩 갖고 있으므로 거기서 뽑는다. 카드가 늘면 이 목록도 저절로 는다.
//
// ## 재생은 `SceneMusic` 을 그대로 쓴다
//
// 오디오 포커스·생명주기·홈 버튼 처리가 그 파일에 이미 다 있다. 여기서는 **어떤
// 곡을 넘길지**만 정한다. `asset = null` 이면 조용히 멈추는 것도 그쪽 계약이다.
// ---------------------------------------------------------------------------

/**
 * 곡이 있는 카드 한 장.
 *
 * [mine] 은 **내가 뽑은 그 카드**다. 카탈로그 목록([CARD_TUNES])에는 없고
 * [ownedTunes] 가 채운다 — 목록에 그릴 그림이 카탈로그 원화가 아니라 내 카드여야
 * 하기 때문이다 (아래 [TuneRow] 주석).
 */
data class CardTune(val card: DexCard, val asset: String, val mine: DrawnCard? = null)

/**
 * 곡이 있는 카드들. **`CARD_BGM` 에서 뽑는다** — 곡을 두 군데 적어 두면 어긋난다.
 *
 * 예전에는 이머시브 장면에서 뽑았다. 그때는 곡이 있는 카드가 곧 무대가 있는 카드라
 * 같은 말이었는데 이제 아니다 — 시금치·당근은 무대 없이 곡만 있다.
 */
val CARD_TUNES: List<CardTune> = DEX_CARDS.mapNotNull { card ->
    bgmFor(card.id)?.let { CardTune(card, it) }
}

@Composable
fun TurntablePanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 내가 뽑은 카드. 곡 목록이 여기서 나온다 */
    drawn: List<DrawnCard> = emptyList(),
    /** 뽑으러 간다. null 이면 그 자리가 안 뜬다 — `@Preview` 가 그렇게 쓴다 */
    onOpenDraw: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playing by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<CardTune?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    // 저장은 **권한을 안 쓴다.** 사용자가 저장 위치를 직접 고르는 방식이라
    // (SAF) `WRITE_EXTERNAL_STORAGE` 를 새로 넣지 않아도 되고, minSdk 26 에서도 된다.
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/ogg"),
    ) { uri ->
        val tune = saving
        saving = null
        if (uri == null || tune == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(tune.asset).use { input ->
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            input.copyTo(out)
                        } ?: error("저장할 곳을 열지 못했습니다.")
                    }
                }.isSuccess
            }
            note = if (ok) "${tune.card.name} 곡을 내려받았습니다" else "내려받지 못했습니다"
        }
    }

    SceneMusic(asset = playing, volume = 1f)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("♫  내 카드의 음악", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "닫기",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        playing = null
                        onClose()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // **내가 뽑은 카드의 곡만.** 카탈로그를 다 늘어놓으면 한 장도 안 뽑은 사람에게도
        // 다 들려서 카드를 뽑을 이유가 없어진다.
        val mine = remember(drawn) { ownedTunes(drawn) }

        if (mine.isEmpty()) {
            Empty(onOpenDraw)
        } else {
            mine.forEach { tune ->
                TuneRow(
                    tune = tune,
                    playing = playing == tune.asset,
                    onToggle = { playing = if (playing == tune.asset) null else tune.asset },
                    onSave = {
                        saving = tune
                        save.launch("${tune.card.name}.ogg")
                    },
                )
            }

            val left = CARD_TUNES.size - mine.size
            if (left > 0) {
                Text("곡이 있는 카드가 ${left}장 더 있어요", color = TextMuted, fontSize = 11.sp)
            }
        }
        note?.let { Text(it, color = DaengPink, fontSize = 11.sp) }
    }
}

/**
 * 내가 가진 곡. **카탈로그 순서를 그대로 따른다** — 뽑은 순서로 늘어놓으면 어제 뽑은
 * 곡이 매번 자리를 옮긴다.
 *
 * 같은 종류를 여러 장 뽑았으면 **가장 최근 것**을 싣는다. 도감이 칸 안에서 최근을
 * 앞에 두는 것과 같은 규칙이다 (`dexSlots`).
 */
internal fun ownedTunes(drawn: List<DrawnCard>): List<CardTune> {
    val newest = drawn.groupBy { it.templateId }
        .mapValues { (_, copies) -> copies.maxBy { it.drawnAtMillis } }
    return CARD_TUNES.mapNotNull { tune -> newest[tune.card.id]?.let { tune.copy(mine = it) } }
}

/**
 * 목록에 찍는 번호. **어느 벌인지를 앞에 붙인다.**
 *
 * 도감이 두 벌이라 **번호가 겹친다** — No.01 이 야채에는 배추, 과일에는 사과다.
 * 도감 화면에서는 탭이 어느 벌인지 말해 주지만 여기는 야채와 과일이 **한 줄에 섞여
 * 선다.** 번호만 찍으면 같은 카드가 두 번 뜬 것처럼 읽힌다.
 */
internal fun tuneNumberLabel(card: DexCard): String =
    "${card.deck.label} No. %02d".format(card.no)

/**
 * 한 곡도 없을 때.
 *
 * **빈 채로 두지 않는다.** 턴테이블은 방의 붙박이라 아무나 누르는데, 열었더니 아무것도
 * 없으면 고장으로 읽힌다. 무엇을 하면 곡이 생기는지 말하고 그 자리로 보낸다.
 */
@Composable
private fun Empty(onOpenDraw: (() -> Unit)?) {
    Text(
        "노래를 가진 카드를 뽑아보세요",
        color = TextDark,
        fontSize = 13.sp,
    )
    Text(
        "카드마다 곡이 있는 것은 아니에요. 뽑으면 여기에 쌓여요.",
        color = TextMuted,
        fontSize = 11.sp,
        lineHeight = 17.sp,
    )
    onOpenDraw?.let { go ->
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DaengPink)
                .clickable(onClick = go),
            contentAlignment = Alignment.Center,
        ) {
            Text("뽑으러 가기", color = CardWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 목록 그림의 가로 픽셀. 34dp 짜리라 크게 그릴 이유가 없다 (도감 `COPY_THUMB_PX` 와 같다). */
private const val TUNE_THUMB_PX = 160

@Composable
private fun TuneRow(
    tune: CardTune,
    playing: Boolean,
    onToggle: () -> Unit,
    onSave: () -> Unit,
) {
    // **내가 뽑은 카드를 그린다. 카탈로그 원화가 아니다.**
    //
    // 원화를 그리다가 곡이 과일까지 늘면서 드러났다 — 야채 원화에는 저쪽이 그린 네오
    // 강아지가 구워져 있는데 **과일 원화는 구멍만 뚫린 판**이라, 목록에 얼굴 없는
    // 빈 구멍 카드가 떴다. 곡이 야채뿐일 때는 티가 안 났다.
    //
    // 도감이 쓰는 그 함수다(`CopyStrip` 의 `rememberComposedCard`). 얼굴 파일이 없으면
    // 그쪽이 알아서 비운 판으로 물러서므로 여기서 갈래를 또 만들지 않는다.
    val composed = rememberComposedCard(tune.mine, width = TUNE_THUMB_PX)
    // 내 카드가 없을 때(`@Preview`)만 카탈로그 원화다. 4분의 1로 읽는다.
    val catalog = rememberAssetImage(tune.card.art, sample = 4)
    val art = composed ?: catalog
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 34.dp, height = 44.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(TextMuted.copy(alpha = 0.15f)),
        ) {
            art?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.size(width = 34.dp, height = 44.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(tune.card.name, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (playing) "재생 중" else tuneNumberLabel(tune.card),
                color = if (playing) DaengPink else TextMuted,
                fontSize = 11.sp,
            )
        }
        RoundButton(if (playing) "❚❚" else "▶", tune.card.accent, onToggle)
        Spacer(Modifier.width(2.dp))
        RoundButton("⤓", TextMuted.copy(alpha = 0.25f), onSave)
    }
}

@Composable
private fun RoundButton(label: String, fill: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(50))
            .background(fill.copy(alpha = 0.30f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(name = "턴테이블 · 음악 목록", widthDp = 380, heightDp = 320)
@Composable
private fun TurntablePanelPreview() {
    Box(Modifier.background(Color(0xFFEFE3DC))) {
        TurntablePanel(onClose = {})
    }
}
