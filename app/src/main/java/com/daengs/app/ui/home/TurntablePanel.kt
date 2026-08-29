package com.daengs.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.daengs.app.ui.dex.DEX_CARDS
import com.daengs.app.ui.dex.DexCard
import com.daengs.app.ui.dex.IMMERSIVE_SCENES
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

/** 곡이 있는 카드 한 장. */
data class CardTune(val card: DexCard, val asset: String)

/**
 * 곡이 있는 카드들. **이머시브 장면에서 뽑는다** — 곡을 두 군데 적어 두면 어긋난다.
 *
 * 지금은 배추·고구마·상추 셋이다. 나머지 아홉 장은 저쪽에서 곡이 오면 늘어난다.
 */
val CARD_TUNES: List<CardTune> = DEX_CARDS.mapNotNull { card ->
    IMMERSIVE_SCENES[card.no]?.bgm?.let { CardTune(card, it) }
}

@Composable
fun TurntablePanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
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

        CARD_TUNES.forEach { tune ->
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

        val left = DEX_CARDS.size - CARD_TUNES.size
        if (left > 0) {
            Text("나머지 ${left}장은 아직 곡이 없습니다", color = TextMuted, fontSize = 11.sp)
        }
        note?.let { Text(it, color = DaengPink, fontSize = 11.sp) }
    }
}

@Composable
private fun TuneRow(
    tune: CardTune,
    playing: Boolean,
    onToggle: () -> Unit,
    onSave: () -> Unit,
) {
    // 그리드보다 더 작게 뜨므로 4분의 1로 읽는다. 열두 장을 원본으로 들 이유가 없다.
    val art = rememberAssetImage(tune.card.art, sample = 4)
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
                if (playing) "재생 중" else "No. %02d".format(tune.card.no),
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
