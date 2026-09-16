package com.daengs.app.ui.dogcard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.rememberTextMeasurer
import com.daengs.app.dogcard.DrawnCard
import com.daengs.app.ui.dex.CardArt
import com.daengs.app.ui.dex.rememberCardImage
import com.daengs.app.ui.dex.rememberDrawnCardArt

/**
 * 뽑은 카드 한 장을 **한 장의 그림으로** 만들어 준다.
 *
 * 도감은 카드를 세 겹(얼굴 → 판 → 글자)으로 그리는데, 방의 액자처럼 **그림 한 장이
 * 필요한 자리**가 있다. 액자는 카드 안의 그림창만 잘라 넣느라 비트맵의 크기를 재야
 * 해서 그리는 순서를 받을 수가 없다.
 *
 * 파일로 굽지 않는다. 계획에는 `cacheDir/cards/<id>-full.png` 가 있었는데, 액자에
 * 들어가는 그림은 화면에서 200px 남짓이라 [width] 를 작게 잡으면 그리는 값이 캐시를
 * 읽는 값과 비슷하다. 파일을 두면 지워졌을 때 · 카드가 바뀌었을 때 · 탈퇴할 때를
 * 다 챙겨야 한다.
 *
 * @param width 그릴 가로 픽셀. 액자는 작으니 작게 준다
 * @return 못 만들면 null. 그림 하나 때문에 방이 안 열리면 안 된다
 */
@Composable
fun rememberComposedCard(card: DrawnCard?, width: Int = 360): ImageBitmap? {
    val drawn = if (card != null) rememberDrawnCardArt(card) else null
    val source = when {
        drawn == null -> null
        // 얼굴이 있으면 **자리를 비운 판**을 읽는다. 완성 카드에 얼굴을 또 끼우면
        // 저쪽 개 위에 우리 아이가 겹친다.
        drawn.composed -> CardArt.Asset(drawn.template!!.art)
        else -> drawn.fallback
    }
    val art = rememberCardImage(source)
    val measurer = rememberTextMeasurer()

    var made by remember(card?.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(card?.id, art, drawn?.composed) {
        val base = art
        made = when {
            base == null -> null
            drawn?.composed != true -> base
            else -> runCatching {
                renderCard(
                    art = base,
                    template = drawn.template!!,
                    face = drawn.face,
                    measurer = measurer,
                    name = drawn.name,
                    code = drawn.code,
                    width = width,
                ).asImageBitmap()
            }.getOrNull()
        }
    }
    return made
}

/**
 * 방 액자에 넣을 그림 한 장. 누끼 카드면 조립하고, **포토 카드면 받아 둔 파일을 그대로** 읽는다.
 *
 * 둘 다 null 이면 null — 액자는 발자국으로 돌아간다.
 */
@Composable
fun rememberFramePicture(drawn: DrawnCard?, photoFile: java.io.File?): ImageBitmap? {
    val composed = rememberComposedCard(drawn)
    val photo = rememberCardImage(photoFile?.let { CardArt.Local(it) }, sample = 2)
    return if (drawn != null) composed else photo
}
