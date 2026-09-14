package com.daengs.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **앱 테마가 Material 색 칸을 하나도 비워 두지 않는다** — `docs/design-locks.md` 0절.
 *
 * ⛔ 깨지면 **테스트를 고치지 말고** 빈 칸을 이 앱 팔레트로 채운다.
 *
 * `lightColorScheme(...)` 는 안 준 칸을 M3 기본값(연보라 · 회보라)으로 메운다. 그 칸을 쓰는
 * 부품(FilterChip · Card · AlertDialog · 시트 · 메뉴)이 크림 · 분홍 사이에서 다른 앱처럼
 * 보였다. M3 가 칸을 새로 늘리면 그 칸도 여기서 걸린다.
 */
class ThemeColorLockTest {

    /** `ColorScheme` 의 색 칸 getter 들. Color 는 value class 라 getter 가 long 을 돌려준다. */
    private fun slots(scheme: ColorScheme): Map<String, Long> =
        ColorScheme::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE && it.name.startsWith("get") }
            .associate { it.name.substringBefore('-').removePrefix("get") to (it.invoke(scheme) as Long) }

    @Test
    fun `M3 기본값으로 남은 색 칸이 없다`() {
        val ours = slots(DaengsColorScheme)
        val defaults = slots(lightColorScheme())
        assertTrue("색 칸을 하나도 못 읽었다 — ColorScheme 구조가 바뀌었나", ours.size >= 30)
        // 흰색 · 검정은 우리 팔레트에도 있어 **우연히 같을 수 있다** (onPrimary = 흰 글자 등).
        // 문제는 M3 가 칠해 둔 **보라 · 회보라** 가 남는 것이라, 무채색 기본값은 빼고 본다.
        val neutral = setOf(Color.White, Color.Black, Color.Transparent).map { it.value.toLong() }.toSet()
        val leftovers = ours.filter { (name, value) -> defaults[name] == value && value !in neutral }.keys.sorted()
        assertTrue(
            "⛔ 잠긴 디자인 위반 — docs/design-locks.md 0절. M3 기본값(연보라)이 남은 칸: $leftovers. " +
                "Theme.kt 의 DaengsColorScheme 에 이 앱 팔레트로 채울 것.",
            leftovers.isEmpty(),
        )
    }
}
