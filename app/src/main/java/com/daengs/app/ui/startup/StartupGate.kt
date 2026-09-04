package com.daengs.app.ui.startup

import com.daengs.app.pet.Pet

/**
 * 로딩 화면에서 나갈 곳.
 *
 * **[Wait] 가 있는 것이 요점이다.** "아직 모른다" 와 "빈 목록" 은 다른 상태인데,
 * 둘을 같은 값으로 다루면 강아지를 못 받은 사이에 온보딩이 뜬다.
 */
enum class StartupTarget { Wait, Home, Onboarding }

/**
 * 저장된 세션으로 켠 앱이 **로딩을 떠나도 되는가.**
 *
 * 예전에는 저장된 토큰이 있으면 곧장 홈으로 갔다. 그런데 강아지 목록은 그때 아직
 * 안 왔고([Pet] 목록이 `null`), 방은 목록이 비면 **데모로 채워진다** — 그래서 켤 때마다
 * 남의 강아지 넉 마리가 잠깐 서 있다가 사라지고 날씨가 바뀌었다.
 *
 * @param pets `null` 은 **아직 못 받았다**는 뜻이다 (빈 목록과 다르다)
 * @param petsError 목록을 못 받은 이유. **이 줄이 없으면 로딩에 갇힌다** — 서버가
 *   죽어 있으면 [pets] 는 영영 `null` 이다. 그때는 홈으로 보낸다. 방이 비어 보이는
 *   것이 정지 화면보다 낫다
 */
fun startupTarget(pets: List<Pet>?, petsError: String?): StartupTarget = when {
    pets == null -> if (petsError != null) StartupTarget.Home else StartupTarget.Wait
    pets.isEmpty() -> StartupTarget.Onboarding
    else -> StartupTarget.Home
}

/**
 * 로딩 화면을 **적어도 이만큼은** 보여 준다.
 *
 * 목록이 빨리 오는 날에는 이 화면이 두어 프레임만 스쳤다. 그러면 화면이 바뀐 것으로
 * 안 읽히고 **끊긴 것처럼** 보인다 — "너무 짧아서 아예 안 보이고 렉 걸린 것 같다".
 *
 * 스플래시(0.2~0.5초)까지 하면 켜서 홈까지 1초 남짓이다.
 */
const val MIN_LOADING_MS = 700L

/**
 * 지금 로딩을 떠나려면 **얼마나 더 기다려야 하나.** 이미 지났으면 0 이다.
 *
 * **시작 시각을 받는 것이 요점이다.** 그냥 `delay(700)` 을 넣으면 안 된다 — 로딩을
 * 떠나는 [kotlinx.coroutines.delay] 자리는 강아지 목록이 바뀔 때마다 다시 도는데,
 * 그때마다 700 을 새로 기다리면 목록이 여러 번 갱신되는 날에 몇 초씩 잡혀 있는다.
 * 시작 시각에서 재면 몇 번을 다시 돌든 **총 [minMs]** 다.
 *
 * @param startedAt 로딩이 뜬 시각. **`SystemClock.elapsedRealtime()` 을 넘긴다** —
 *   `System.currentTimeMillis()` 는 사용자가 시계를 바꾸거나 NTP 가 맞추면 튄다
 * @return 0 이상 [minMs] 이하. 위 끝을 막아 두는 것은 시계가 뒤로 갔을 때
 *   [minMs] 보다 오래 잡혀 있지 않게 하려는 것이다
 */
fun loadingHoldMs(startedAt: Long, now: Long, minMs: Long = MIN_LOADING_MS): Long =
    (startedAt + minMs - now).coerceIn(0L, minMs)
