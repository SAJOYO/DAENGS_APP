package com.daengs.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DaengsColorScheme = lightColorScheme(
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
)

/**
 * dynamicColor 를 지원하지 않는다.
 *
 * 안드로이드 12+ 에서 dynamic color 를 켜면 기기 배경화면에서 뽑은 색이
 * colorScheme 를 덮어써서 시안의 핑크/크림 톤이 기기마다 달라진다.
 * 이 데모는 시안 재현이 목적이므로 라이트 스킴 하나로 고정한다.
 * (다크 모드도 동일 스킴 — 시안이 라이트 전용이다.)
 */
@Composable
fun DaengsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DaengsColorScheme,
        typography = Typography,
        content = content,
    )
}
