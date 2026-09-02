package com.daengs.app.dogcard

import android.content.Context
import java.time.LocalDate

/**
 * 출시본에서는 아무것도 안 한다. **실제 사용자는 빈 도감에서 시작한다.**
 *
 * 진짜 시드는 `app/src/debug/.../SeedCards.kt` 에 있다. 소스셋으로 가른 덕에
 * 여기 바이트코드에는 카드 열두 장을 넣는 코드가 **아예 없다** (`DeveloperPanel` 과
 * 같은 방식). 시그니처를 한 글자라도 다르게 두면 `assembleRelease` 에서만 깨진다 —
 * 고칠 때 두 파일을 같이 고칠 것.
 */
@Suppress("UNUSED_PARAMETER")
suspend fun seedCards(
    context: Context,
    store: CardStore,
    dogId: String?,
    dogName: String,
    birthDate: LocalDate?,
) = Unit
