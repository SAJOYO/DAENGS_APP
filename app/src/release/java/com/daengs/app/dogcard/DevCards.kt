package com.daengs.app.dogcard

import android.content.Context
import com.daengs.app.ui.dogcard.CardTemplate

/**
 * 릴리스용 빈 껍데기. **진짜는 `app/src/debug/` 에 있다.**
 *
 * `DeveloperPanel.kt` 와 같은 규칙이다 — `BuildConfig.DEBUG` 로
 * 감추면 R8 이 꺼져 있어(`release { optimization { enable = false } }`) 코드가
 * 그대로 스토어 APK 에 들어간다. 소스셋으로 가르면 **안 들어간다.**
 *
 * **시그니처가 debug 쪽과 한 글자도 달라지면 안 된다.** 어긋나면
 * `assembleRelease` 에서만 깨진다.
 *
 * `devCardTime` 은 여기 없다 — 부르는 쪽이 `app/src/debug/` 안뿐이라 릴리스에서는
 * 이름조차 필요 없다.
 */
suspend fun makeDevCard(
    context: Context,
    cards: CardHolder,
    template: CardTemplate,
    dogId: String?,
    dogName: String,
    codeText: String,
    appUserId: String?,
) = Unit
