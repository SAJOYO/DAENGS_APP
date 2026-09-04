package com.daengs.app.pet

/**
 * 릴리스용 빈 껍데기. **진짜는 `app/src/debug/` 에 있다.**
 *
 * `DevCards.kt` · `DeveloperPanel.kt` 와 같은 규칙이다 — `BuildConfig.DEBUG` 로
 * 감추면 R8 이 꺼져 있어 코드가 그대로 스토어 APK 에 들어간다. 소스셋으로 가르면
 * **안 들어간다.**
 *
 * **시그니처가 debug 쪽과 한 글자도 달라지면 안 된다.** 어긋나면 `assembleRelease`
 * 에서만 깨지므로, 확인 절차에 릴리스 빌드가 들어 있다.
 */
val DEV_PET_COUNTS = listOf(0)

fun devPets(count: Int): List<Pet> = emptyList()
