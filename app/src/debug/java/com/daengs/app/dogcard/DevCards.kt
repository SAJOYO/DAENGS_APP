package com.daengs.app.dogcard

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntRect
import com.daengs.app.ui.dogcard.CardTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 개발자 패널이 야채를 **지정해서** 카드를 만든다. **디버그 소스셋이다.**
 *
 * 짝이 되는 `app/src/release/.../DevCards.kt` 는 아무것도 안 한다 —
 * `DeveloperPanel` · `seedCards` 가 쓰는 그 수법이고, 출시본 바이트코드에는 이
 * 코드가 아예 안 들어간다.
 *
 * 왜 필요한가: 뽑기는 **12종 균등에 중복도 허용**이라(`drawTemplate`) 원하는 야채가
 * 안 나온다. 그런데 확인해야 할 것은 특정 야채에 몰려 있다 — 이머시브 무대는
 * 배추·고구마·상추 셋뿐이고(`ImmersiveScene`), 도감에서 튀어나오는 팝아웃은
 * **토마토 한 장**뿐이다(`CardPop`). 게다가 하루 세 번 제한이 걸려서, 토마토 한 장을
 * 보려고 며칠을 기다리게 된다.
 */

/** 하루. [devCardTime] 이 뒤로 미는 만큼이다. */
private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * 개발자 패널이 만든 카드에 적을 시각.
 *
 * **오늘로 넣으면 안 된다.** 하루 세 번 제한이 "오늘 자정 이후의 카드"를 세는데
 * (`drawsLeft`), 지정해서 만든 카드가 그 수를 먹으면 **정작 뽑기 연출을 못 본다** —
 * 뽑기 팝업은 진짜 뽑기 흐름에서만 뜨기 때문이다. `seedCards` 가 시드를 지난
 * 날짜로 두는 것과 같은 이유다.
 */
fun devCardTime(now: Long = System.currentTimeMillis()): Long = now - DAY_MS

/**
 * [template] 로 카드 한 장을 만들어 [cards] 에 넣는다.
 *
 * **얼굴은 이미 뽑아 둔 카드에서 빌린다.** 얼굴이 없으면 `DrawnCardArt.composed` 가
 * false 라 자리를 비운 판으로 물러서는데(`CardArt.kt`), 그러면 이름칸·번호판이 빈
 * 카드가 나와서 정작 확인하려던 것을 못 본다. 한 장이라도 사진으로 뽑아 두면 그
 * 얼굴이 나머지 열한 장에 그대로 들어간다.
 *
 * 빌릴 얼굴이 아예 없으면 얼굴 없이 만든다 — 그래도 무대·창틀 좌표는 볼 수 있다.
 */
suspend fun makeDevCard(
    context: Context,
    cards: CardHolder,
    template: CardTemplate,
    dogId: String?,
    dogName: String,
    codeText: String,
    appUserId: String?,
) {
    val files = CardFiles(context)
    val donor = cards.cards.firstOrNull { files.faceFile(it.id).exists() }
    val face = donor?.let {
        withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(files.faceFile(it.id).path) }.getOrNull()
        }
    }
    cards.draw(
        template = template,
        face = face,
        // 얼굴을 빌렸으면 그 카드가 재 둔 자리도 같이 빌린다. 구멍 크기는 야채마다
        // 다르지만 `core` 는 **얼굴 그림 안의 좌표**라 야채가 바뀌어도 그대로 맞는다.
        core = donor?.core ?: IntRect.Zero,
        dogId = dogId,
        dogName = dogName,
        codeText = codeText,
        appUserId = appUserId,
        now = devCardTime(),
    )
}
