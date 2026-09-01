package com.daengs.app.ui.theme

/**
 * 화면 폭을 기준 폭과 견준 배율.
 *
 * **이 숫자 하나가 앱의 모든 `dp` 와 `sp` 를 움직인다** — [DaengsTheme] 이
 * `LocalDensity` 를 이 값으로 갈아끼우기 때문이다. 리터럴 천 개를 고치는 대신
 * 뿌리에서 자를 바꾸는 방식이다.
 *
 * 순수 함수로 빼 둔 이유는 **테스트로 잡으려고**다. 화면 전체를 좌우하는 값이라
 * 눈으로만 보면 어디가 틀렸는지 못 찾는다.
 */
fun uiScale(screenWidthDp: Int): Float {
    if (screenWidthDp <= 0) return MIN_SCALE
    return (screenWidthDp / REFERENCE_WIDTH_DP).coerceIn(MIN_SCALE, MAX_SCALE)
}

/**
 * 화면을 그린 기준 폭(dp).
 *
 * 근거 둘이다. 이 저장소의 `@Preview` 26개가 `widthDp = 411` 이라 사실상의 기준이고,
 * 실기기(SM-S938N)가 411.4dp 로 그린다(1080px / 420dpi 실측). **그 폰에서는 지금과
 * 똑같이 보이고**, 다른 폰이 여기에 맞춰 온다.
 */
const val REFERENCE_WIDTH_DP = 411f

/**
 * 배율 하한·상한.
 *
 * 폰이 아닌 폭(태블릿·폴더블 펼침)에서 그대로 비례하면 800dp 에서 1.95배가 되어
 * 글자가 거대해진다. 세로로 고정해 두어 그럴 일이 드물지만, 안 막으면 언젠가 본다.
 *
 * 흔한 폰 폭(360~430dp)은 전부 이 안쪽이라 **잘리지 않고 그대로 비례한다** —
 * 상한·하한은 "폰이 아닌 것" 만 막는 자리다.
 */
const val MIN_SCALE = 0.85f
const val MAX_SCALE = 1.25f
