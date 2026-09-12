package com.daengs.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

internal val DaengsColorScheme = lightColorScheme(
    primary = DaengPink,
    onPrimary = CardWhite,
    primaryContainer = PinkSoft,
    onPrimaryContainer = DaengPinkDeep,
    secondary = DaengPinkDeep,
    onSecondary = CardWhite,
    tertiary = PinkSoft,
    background = CreamBg,
    onBackground = TextDark,
    surface = CardWhite,
    onSurface = TextDark,
    surfaceVariant = PinkFaint,
    onSurfaceVariant = TextMuted,
    outline = DaengsColors.BorderNeutral,
    // 오류 색도 **반드시 채운다.**
    //
    // lightColorScheme 는 안 준 자리를 M3 기본값으로 메운다. 나머지는 다 덮어써서
    // 안 보였는데 error 계열만 빠져 있어서, 지도 화면이
    // `MaterialTheme.colorScheme.errorContainer` 를 쓰는 순간 크림·핑크 사이에
    // M3 기본 빨강·보라가 튀어나왔다. 팔레트에 Error 가 이미 있으므로 그걸 쓴다.
    error = DaengsColors.Error,
    onError = CardWhite,
    errorContainer = DaengsColors.ErrorSoft,
    onErrorContainer = DaengsColors.Error,

    // 🔒 **나머지 칸도 전부 채운다** — `docs/design-locks.md` 0절 (2026-09-12).
    //
    // 위의 error 와 같은 사고가 **열일곱 칸**에 남아 있었다. 안 채운 칸은 M3 기본값(연보라 ·
    // 회보라)으로 메워지는데, 그 칸을 쓰는 부품이 이 앱에 흔하다:
    //   FilterChip 고른 칩 → secondaryContainer     Card(채움) · 입력칸 → surfaceContainerHighest
    //   AlertDialog → surfaceContainerHigh          ModalBottomSheet → surfaceContainerLow
    //   DropdownMenu → surfaceContainer              구분선 → outlineVariant
    //   높이가 있는 면(메뉴·시트) → surfaceTint 로 분홍 물이 든다
    // 점령·산책 화면에 Card 40곳 · FilterChip 31곳 · AlertDialog 12곳이 있다.
    // `ThemeColorLockTest` 가 **M3 기본값과 같은 칸이 하나라도 있으면** 깨진다.
    secondaryContainer = PinkSoft,
    onSecondaryContainer = TextDark,
    tertiaryContainer = PinkFaint,
    onTertiaryContainer = TextDark,
    // 높이가 있는 면에 섞는 색. 면과 같게 두어 분홍 물이 들지 않게 한다.
    surfaceTint = CardWhite,
    inverseSurface = TextDark,
    inverseOnSurface = CreamBg,
    inversePrimary = PinkSoft,
    outlineVariant = DaengsColors.BorderNeutral,
    // 어둡게 덮는 막. 검정 대신 이 앱의 진한 갈색 — 뒤가 차갑게 꺼지지 않는다.
    scrim = TextDark,
    surfaceBright = CardWhite,
    surfaceDim = PinkFaint,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CreamBg,
    surfaceContainer = CreamBg,
    surfaceContainerHigh = CardWhite,
    surfaceContainerHighest = PinkFaint,
    // Material 1.4 의 Fixed 칸. 쓰는 부품은 드물지만 비워 두면 같은 보라가 남는다.
    primaryFixed = PinkSoft,
    primaryFixedDim = DaengPink,
    onPrimaryFixed = TextDark,
    onPrimaryFixedVariant = DaengPinkDeep,
    secondaryFixed = PinkSoft,
    secondaryFixedDim = PinkFaint,
    onSecondaryFixed = TextDark,
    onSecondaryFixedVariant = TextMuted,
    tertiaryFixed = PinkFaint,
    tertiaryFixedDim = DaengsColors.BorderNeutral,
    onTertiaryFixed = TextDark,
    onTertiaryFixedVariant = TextMuted,
)

/**
 * dynamicColor 를 지원하지 않는다.
 *
 * 안드로이드 12+ 에서 dynamic color 를 켜면 기기 배경화면에서 뽑은 색이
 * colorScheme 를 덮어써서 시안의 핑크/크림 톤이 기기마다 달라진다.
 * 이 데모는 시안 재현이 목적이므로 라이트 스킴 하나로 고정한다.
 * (다크 모드도 동일 스킴 — 시안이 라이트 전용이다.)
 *
 * **크기도 같은 이유로 고정한다.** 색을 기기마다 같게 맞춰 놓고 크기는 제각각이면
 * 반쪽이다. `dp` 와 `sp` 는 전부 `LocalDensity` 를 거쳐 픽셀이 되므로, 리터럴
 * 천 개를 고치는 대신 **여기서 자를 한 번 바꾼다**:
 *
 *  - 화면의 짧은 변을 기준 폭(411dp)과 견주어 전체를 비례로 키우거나 줄인다
 *    ([uiScaleForWindow]). 가로 산책 화면에서도 긴 변 때문에 UI가 부풀지 않는다
 *    그래야 같은 `16.dp` 가 어느 폰에서나 화면의 **같은 몫**을 차지한다
 *  - `fontScale = 1f` 로 **시스템 글자 크기 설정을 무시한다.** 안 그러면 글자만
 *    커지고 상자는 그대로라 칸을 넘는다 — 하단 바가 `64.dp` 상자에 `10.sp`
 *    라벨이라 대표적이었다
 *
 * ⚠️ **글자를 키워 쓰는 사람에게는 작게 보인다.** 접근성과 "어느 기기에서나 같은
 * 화면" 을 맞바꾼 것이고, 후자를 골랐다. 되돌리려면 `fontScale` 만 빼면 된다.
 *
 * ⚠️ `WindowInsets` 는 픽셀로 저장돼 이 자와 함께 왕복하므로 상태바 여백은 그대로
 * 맞는다. `@Preview` 도 `widthDp = 411` 이라 배율이 1.0 이다.
 */
@Composable
fun DaengsTheme(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scale = uiScaleForWindow(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
    )
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, fontScale = 1f),
    ) {
        MaterialTheme(
            colorScheme = DaengsColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
