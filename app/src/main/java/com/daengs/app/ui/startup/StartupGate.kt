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
