package com.daengs.app.ui.dex

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.daengs.app.dogcard.CardFiles
import com.daengs.app.dogcard.DrawnCard
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.CardFace
import com.daengs.app.ui.dogcard.CardTemplate
import com.daengs.app.miniroom.art.rememberAssetImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 카드 그림이 어디서 오는가.
 *
 * 카탈로그 열두 장은 `assets/` 에 있고, 뽑은 카드는 기기 안의 파일이다. `DexCard.art` 는
 * id 에서 assets 경로를 계산하는 프로퍼티라 파일 경로를 담을 수 없는데, 그 규칙은
 * `DexCardsTest` 가 잠그고 있고 잠글 만한 규칙이다. 그래서 **그 자리를 고치지 않고
 * 한 겹 위에 출처를 둔다.**
 */
sealed interface CardArt {
    /** `assets/` 아래. 저쪽이 그린 완성 카드 */
    data class Asset(val path: String) : CardArt

    /** 기기 안 파일. 내가 뽑은 카드의 얼굴 */
    data class Local(val file: File) : CardArt
}

/**
 * 카탈로그 카드의 그림 자리. **포토 카드는 null 이다** — 달별 틀 그림은 서버에만 있고,
 * 잠긴 칸은 빈 판에 자물쇠를 얹는다 (docs/photo-cards.md §2).
 */
val DexCard.artSource: CardArt? get() = if (isPhoto) null else CardArt.Asset(art)

/**
 * 내가 뽑은 카드 한 장을 그릴 재료.
 *
 * **얼굴 그림 한 장이 곧 카드가 아니다.** 카드는 `자리를 비운 원화 + 얼굴 + 글자` 를
 * 합쳐야 나온다 — 처음에 얼굴 PNG 를 그대로 카드 그림으로 넘겼다가, 도감에 카드 대신
 * 누끼 사진이 칸을 가득 채우는 화면이 나왔다.
 *
 * @param template 자리를 비운 원화. 없으면(저쪽이 카드를 갈아엎었으면) null
 * @param face 구멍에 끼울 얼굴. **없으면 저쪽 완성 카드로 물러선다** — 개발 기기에
 *   미리 넣어 둔 열두 장이 그 경우이고, 진짜 뽑은 카드인데 파일이 지워진 경우도 같다
 */
@Immutable
data class DrawnCardArt(
    val template: CardTemplate?,
    val face: CardFace?,
    val name: String,
    val code: String,
    /**
     * 합칠 수 없을 때 대신 그릴 그림.
     *
     * **저쪽 완성 카드가 아니라 자리를 비운 판이다.** 예전에는 완성 카드로 물러섰는데,
     * 그 판에는 `TOMATO NEO` 와 `NEO-0824` 가 인쇄돼 있어서 **얼굴 파일이 없는 카드의
     * 팝업에 남의 개 이름이 그대로 떴다** (개발 기기의 시드 열두 장이 그랬다).
     * 비운 판으로 물러서면 이름칸이 비어 있을 뿐 남의 것이 안 보인다.
     */
    val fallback: CardArt,
) {
    val composed: Boolean get() = template != null && face != null
}

@Composable
fun rememberDrawnCardArt(card: DrawnCard): DrawnCardArt {
    val context = LocalContext.current
    val template = remember(card.templateId) {
        CARD_TEMPLATES.firstOrNull { it.id == card.templateId }
    }
    // 자리를 비운 판이 있으면 그쪽으로 물러선다. 없을 때만 저쪽 완성 카드다 —
    // 저쪽이 카드를 갈아엎어 판이 사라진 경우이고, 그때는 빈 화면보다 낫다.
    val fallback = CardArt.Asset(
        template?.art
            ?: DEX_CARDS.firstOrNull { it.id == card.templateId }?.art
            ?: "neo-hologram/art/${card.templateId}.webp",
    )
    var face by remember(card.id) { mutableStateOf<CardFace?>(null) }
    LaunchedEffect(card.id) {
        face = withContext(Dispatchers.IO) {
            runCatching {
                val file = CardFiles(context).faceFile(card.id)
                if (!file.exists()) return@runCatching null
                BitmapFactory.decodeFile(file.path)?.let {
                    CardFace(it.asImageBitmap(), card.core)
                }
            }.getOrNull()
        }
    }
    return DrawnCardArt(template, face, card.dogName, card.codeText, fallback)
}

/**
 * 출처가 무엇이든 한 함수로 읽는다.
 *
 * `rememberAssetImage` 의 계약을 그대로 따른다 — **못 읽으면 null 이고 앱은 안 죽는다.**
 * 파일이 지워진 카드는 구멍이 빈 판으로 그려진다. 그림 하나가 없다고 도감이 통째로
 * 안 열리는 쪽이 훨씬 나쁘다.
 */
@Composable
fun rememberCardImage(art: CardArt?, sample: Int = 1): ImageBitmap? = when (art) {
    null -> null
    is CardArt.Asset -> rememberAssetImage(art.path, sample)
    is CardArt.Local -> rememberFileImage(art.file, sample)
}

@Composable
private fun rememberFileImage(file: File, sample: Int): ImageBitmap? {
    var image by remember(file.path, sample) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.path, sample) {
        image = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return image
}

/**
 * 칸이 가진 한 장을 그릴 재료. **여기서만 누끼와 포토가 갈린다.**
 *
 * @param drawn 누끼 카드면 채운다 (틀 + 얼굴 + 글자)
 * @param photoFile 포토 카드의 받아 둔 완성 그림. 만드는 중이면 null
 */
@Immutable
data class OwnedCardArt(val drawn: DrawnCardArt?, val photoFile: File?) {
    val composed: Boolean get() = drawn?.composed == true
}

@Composable
fun rememberOwnedCardArt(owned: OwnedCard?): OwnedCardArt? = when (owned) {
    null -> null
    is OwnedCard.Drawn -> OwnedCardArt(rememberDrawnCardArt(owned.card), null)
    is OwnedCard.Photo -> OwnedCardArt(null, owned.file)
}

/**
 * 칸 표지·확대 뷰에 깔 판. **null 이면 빈 판**(포토의 잠긴 칸 · 만드는 중)을 그린다.
 */
fun coverOf(card: DexCard, art: OwnedCardArt?): CardArt? = when {
    art == null -> card.artSource
    art.photoFile != null -> CardArt.Local(art.photoFile)
    art.drawn == null -> null
    art.drawn.composed -> CardArt.Asset(art.drawn.template!!.art)
    else -> art.drawn.fallback
}
