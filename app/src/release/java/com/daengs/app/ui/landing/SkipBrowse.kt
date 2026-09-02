package com.daengs.app.ui.landing

import androidx.compose.runtime.Composable

/**
 * 릴리스에는 둘러보기가 없다. **출시 앱은 로그인이 필수다.**
 *
 * 진짜는 `app/src/debug/.../SkipBrowse.kt` 에 있고, 왜 디버그에는 남기는지도
 * 거기 적어 뒀다.
 *
 * ⚠️ 로그인이 필수라는 것은 **서버가 안 되면 앱을 아예 못 쓴다**는 뜻이기도 하다.
 * 예전에는 이 버튼으로 우회가 됐다. 그래서 서버 TLS 는 이제 "있으면 좋은 것"이
 * 아니라 앱이 켜지기 위한 조건이다 (`STATUS.md` 알려진 문제).
 */
@Composable
fun SkipBrowse(enabled: Boolean, onSkip: () -> Unit) = Unit
