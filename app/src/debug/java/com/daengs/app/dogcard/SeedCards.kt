package com.daengs.app.dogcard

import android.content.Context
import androidx.compose.ui.unit.IntRect
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.birthCode
import java.time.LocalDate
import java.util.UUID

/**
 * 개발 기기에만 카드 열두 장을 미리 넣어 둔다. **디버그 소스셋이다.**
 *
 * 짝이 되는 `app/src/release/.../SeedCards.kt` 는 아무것도 안 한다 —
 * `DeveloperPanel` 이 쓰는 그 수법이다. `BuildConfig.DEBUG` 로 감싸는 것과 달리
 * **출시본 바이트코드에는 이 코드가 아예 안 들어간다.**
 *
 * 왜 필요한가: 도감이 "검은 카드를 뽑아서 채우는" 것으로 바뀌면서 **모두가 빈
 * 도감에서 시작한다.** 그게 실제 사용자에게는 맞지만, 이 저장소를 만드는 기기에는
 * 저쪽이 그려 준 네오 카드 열두 장이 이미 화면에 있었고 그게 사라지면 안 된다.
 *
 * **누끼 그림을 안 만든다.** 얼굴 파일이 없으면 `DrawnCard` 가 저쪽이 그린 완성
 * 카드(`art/<id>.webp`)로 물러서게 돼 있어서, 시드 카드는 지금까지 보던 그 그림
 * 그대로 보인다. 컬럼을 하나도 더 안 쓰고 끝난다.
 */
suspend fun seedCards(
    context: Context,
    store: CardStore,
    dogId: String?,
    dogName: String,
    birthDate: LocalDate?,
) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    // 한 번만 넣는다. 안 그러면 카드를 전부 지운 다음 켤 때마다 되살아난다.
    if (prefs.getBoolean(KEY_DONE, false)) return
    if (!store.isEmpty()) {
        prefs.edit().putBoolean(KEY_DONE, true).apply()
        return
    }

    val code = birthDate?.let { birthCode(it.monthValue, it.dayOfMonth) } ?: birthCode(8, 24)
    // **오늘로 넣으면 안 된다.** 하루 세 번 제한이 "오늘 자정 이후의 카드"를 세는데,
    // 시드를 오늘로 넣으면 앱을 처음 켠 날 바로 "오늘 뽑기를 다 썼어요"가 뜬다.
    // 이건 뽑은 게 아니라 원래 있던 것이라 지난 날짜로 둔다.
    val now = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    CARD_TEMPLATES.forEachIndexed { index, template ->
        store.add(
            DrawnCard(
                id = UUID.randomUUID().toString(),
                appUserId = null,
                templateId = template.id,
                dogId = dogId,
                dogName = dogName,
                // 도감 순서대로 보이게 1초씩 벌린다. 목록이 최근 순이라 뒤집어 넣는다.
                drawnAtMillis = now - index * 1_000L,
                codeText = code,
                core = IntRect.Zero,
            ),
            face = null,
        )
    }
    prefs.edit().putBoolean(KEY_DONE, true).apply()
}

private const val PREFS = "dogcard"
private const val KEY_DONE = "seeded"
