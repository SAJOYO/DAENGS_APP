package com.daengs.app.ui.dex

import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb

// ---------------------------------------------------------------------------
// 포일 11종 — `vendor/cards-css/<포일>.css` 한 장씩
//
// 원본: `SAJOYO/DAENGS_CARDS` `3c71ce5`. **숫자는 전부 그 파일의 것이다** — 정지점 ·
// 각도 · background-size/position 배수 · blend · filter · opacity. 고칠 때는 저쪽 파일을
// 옆에 열고 한 줄씩 맞춘다. 세기는 [webTune] 한 곳에서만 누른다.
//
// 한 포일은 이런 모양이다 (CSS 의 겹 구조를 그대로 둔다 — [Foil] 머리말 「격리 그룹」).
//
//     foilLayer(shine 의 mix-blend, opacity, filter) {
//         bg { 맨 아래 배경 }                        // background-image 의 마지막 것
//         bg(blend = …) { 그 위 배경 }               // background-blend-mode
//         foilLayer(::before 의 …) { … }             // shine 안에서 섞인다
//         foilLayer(::after 의 …) { … }
//     }
//     glare(…)                                      // __glare (+ ::after)
//
// 저쪽 `--card-opacity` 는 [FoilInput.intensity], `--tilt-x/y` 는 `rarity.css` 가 0 으로
// 고정해 둬서 여기서도 0 이다.
// ---------------------------------------------------------------------------

internal fun DrawScope.at(p: Offset) = Offset(p.x * size.width, p.y * size.height)

/** 상자 [r] 안에서의 포인터 자리. 상자가 요소와 다를 때 `at var(--pointer-x)` 는 상자 기준이다. */
private fun at(r: Rect, p: Offset) = Offset(r.left + r.width * p.x, r.top + r.height * p.y)

/**
 * 카드 그림 위에 포일을 얹는다.
 *
 * **오프스크린 레이어 안에서 불러야 한다.** 블렌드 모드가 "아래 있는 것"과 섞는
 * 것이라, 카드 그림과 같은 레이어가 아니면 앱 배경과 섞여 버린다.
 */
fun DrawScope.drawFoil(foil: Foil, input: FoilInput, tune: FoilTune) {
    if (input.intensity <= 0.001f) return
    when (foil) {
        Foil.Prism -> prism(input, tune)
        Foil.Crystal -> crystal(input, tune)
        Foil.Gold -> gold(input, tune)
        Foil.Oilslick -> oilslick(input, tune)
        Foil.Sunburst -> sunburst(input, tune)
        Foil.Holo -> holo(input, tune)
        Foil.Reverse -> reverse(input, tune)
        Foil.Aurora -> aurora(input, tune)
        Foil.Cosmos -> cosmos(input, tune)
        Foil.Mosaic -> mosaic(input, tune)
        Foil.Metal -> metal(input, tune)
    }
}

/** shine 요소의 filter. 저쪽 `brightness(calc(K * var(--hc-brightness)))` 꼴. */
private fun shineFilter(t: FoilTune, b: Float, c: Float, s: Float) =
    filterOf(b * t.brightness, c * t.contrast, s * t.saturate)

/** `__glare` — 포인터를 따라오는 둥근 빛. 기본 `mix-blend-mode: overlay`. */
private inline fun DrawScope.glare(
    i: FoilInput,
    alpha: Float,
    filter: ColorFilter?,
    stops: List<Pair<Float, Color>>,
    after: DrawScope.() -> Unit = {},
) {
    val c = at(i.p)
    foilLayer(BlendMode.Overlay, alpha, filter) {
        bg { cssRadial(it, c, stops) }
        after()
    }
}

/** 투명 → 색 → 투명 한 줄짜리 반복 띠. crystal 의 칼날 띠가 전부 이 모양이다. */
private fun stripe(color: Color, a: Float, b: Float, c: Float, d: Float, e: Float) = listOf(
    0f to CLEAR, a to CLEAR, b to color, c to color, d to CLEAR, e to CLEAR,
)

private val BLACK_45 = Color(0f, 0f, 0f, 0.45f)

// -- prism ------------------------------------------------------------------

/** 무지개가 각도로 쪼개지는 분광 + 얇은 흑백 살. */
private fun DrawScope.prism(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    foilLayer(BlendMode.ColorDodge, i.intensity * 0.9f * t.shineOpacity, shineFilter(t, 0.75f, 1.7f, 1.05f)) {
        bg { cssRadial(it, c, listOf(0.05f to white(0.5f), 0.45f to gray(0.45f, 0.35f), 1.25f to Color.Black)) }
        bg(blend = BlendMode.Multiply) {
            cssConic(
                c, 0f,
                listOf(
                    0f to sunpillarClr(1), 10f to sunpillarClr(2), 20f to sunpillarClr(3), 30f to sunpillarClr(4),
                    40f to sunpillarClr(5), 50f to sunpillarClr(6), 60f to sunpillarClr(1),
                ),
            )
        }
        // ::before — 얇은 흑백 살. 가장자리로 갈수록 세진다
        foilLayer(BlendMode.Hardlight, 0.4f + i.fromCenter * 0.5f, filterOf(1.1f, 1.4f, 1f)) {
            bg {
                cssConic(
                    c, 0f,
                    listOf(
                        0f to CLEAR, 9f to CLEAR, 10f to white(0.55f), 11f to white(0.55f),
                        12.5f to BLACK_45, 15f to BLACK_45, 16.5f to CLEAR, 24f to CLEAR,
                    ),
                )
            }
        }
        // ::after — 포인터 자리만 밝기를 살린다
        foilLayer(BlendMode.Luminosity, 1f, filterOf(0.62f, 3f, 1f)) {
            bg { cssRadial(it, c, listOf(0f to gray(0.92f, 0.7f), 0.25f to gray(0.76f, 0.1f), 0.9f to gray(0.06f))) }
        }
    }
    glare(
        i, i.intensity * 0.9f * t.glareOpacity, filterOf(0.85f, 1.7f, 1f),
        listOf(0.10f to white(0.8f), 0.40f to gray(0.65f, 0.35f), 1.10f to gray(0f, 0.6f)),
    )
}

// -- crystal ----------------------------------------------------------------

/** 결정 패싯 — 각도가 다른 칼날 띠 여섯 줄이 따로 밀리고, 포인터에서 십자 빛살이 뻗는다. */
private fun DrawScope.crystal(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    val x = i.p.x
    val y = i.p.y
    foilLayer(BlendMode.ColorDodge, i.intensity * 0.9f * t.shineOpacity, shineFilter(t, 0.75f, 1.8f, 1.05f)) {
        bg { cssRadial(it, c, listOf(0.05f to white(0.6f), 0.45f to gray(0.5f, 0.35f), 1.25f to Color.Black)) }
        bg(box(2f, 2f, slide(x, 1.6f), slide(y, -2.8f)), BlendMode.Screen) {
            cssLinear(it, 154f, stripe(sunpillarClr(6), 0.10f, 0.13f, 0.19f, 0.22f, 0.28f), repeating = true)
        }
        bg(box(2f, 2f, slide(x, -2.4f), slide(y, 2.6f)), BlendMode.Screen) {
            cssLinear(it, 67f, stripe(sunpillarClr(4), 0.17f, 0.20f, 0.27f, 0.30f, 0.42f), repeating = true)
        }
        bg(box(2f, 2f, slide(x, 3.2f), slide(y, 1.8f)), BlendMode.Screen) {
            cssLinear(it, 112f, stripe(sunpillarClr(2), 0.13f, 0.16f, 0.21f, 0.24f, 0.34f), repeating = true)
        }
        // ::before — 색 순서를 돌린 띠 세 줄을 반대로 민다
        foilLayer(BlendMode.Lighten, 0.7f, filterOf(1f, 1.4f, 1f)) {
            bg(box(2f, 2f, slide(x, -1.4f), slide(y, -2.4f))) {
                cssLinear(it, 169f, stripe(sunpillarClr(5, BEFORE), 0.18f, 0.21f, 0.26f, 0.29f, 0.44f), repeating = true)
            }
            bg(box(2f, 2f, slide(x, 2.6f), slide(y, -1.8f)), BlendMode.Screen) {
                cssLinear(it, 86f, stripe(sunpillarClr(3, BEFORE), 0.12f, 0.15f, 0.21f, 0.24f, 0.31f), repeating = true)
            }
            bg(box(2f, 2f, slide(x, -3f), slide(y, 2.2f)), BlendMode.Screen) {
                cssLinear(it, 131f, stripe(sunpillarClr(1, BEFORE), 0.15f, 0.18f, 0.23f, 0.26f, 0.38f), repeating = true)
            }
        }
        // ::after — 포인터에서 십자로 뻗는 흰 빛살. 가장자리로 갈수록 세진다
        foilLayer(BlendMode.Hardlight, 0.25f + i.fromCenter * 0.6f, filterOf(0.75f, 2.6f, 1f)) {
            bg { cssRadial(it, c, listOf(0f to white(0.75f), 0.20f to gray(0.55f, 0.15f), 0.9f to gray(0.04f))) }
            bg(blend = BlendMode.Screen) {
                cssConic(
                    c, 45f,
                    listOf(0f to CLEAR, 40f to CLEAR, 44.5f to white(0.8f), 45.5f to white(0.8f), 50f to CLEAR, 90f to CLEAR),
                )
            }
        }
    }
    glare(
        i, i.intensity * 0.9f * t.glareOpacity, filterOf(0.85f, 1.9f, 1f),
        listOf(0.08f to white(0.85f), 0.45f to hsl(220f, 0.2f, 0.6f, 0.3f), 1.15f to hsl(240f, 0.2f, 0.04f, 0.7f)),
    )
}

// -- gold -------------------------------------------------------------------

private val GOLD_1 = hsl(45f, 0.95f, 0.74f)
private val GOLD_2 = hsl(39f, 0.90f, 0.55f)
private val GOLD_3 = hsl(33f, 0.85f, 0.42f)
private val GOLD_4 = hsl(50f, 1f, 0.86f)

/** `repeating-radial-gradient(circle at …, color 0 Npx, transparent Npx Mpx)` — 금속 결. px 는 dp 로 읽는다. */
private fun DrawScope.rings(center: Offset, color: Color, ringDp: Float, periodDp: Float): Brush {
    val ring = ringDp / periodDp
    val clear = color.copy(alpha = 0f)
    return ShaderBrush(
        RadialGradient(
            center.x, center.y, periodDp * density,
            intArrayOf(color.toArgb(), color.toArgb(), clear.toArgb(), clear.toArgb()),
            floatArrayOf(0f, ring, ring, 1f),
            Shader.TileMode.REPEAT,
        ),
    )
}

/** 금박 — 금색 띠가 쓸려 가고, 결 위로 금빛 줄이 지나간다. hard-light 라 제일 세다. */
private fun DrawScope.gold(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    val x = i.p.x
    val y = i.p.y
    foilLayer(BlendMode.Hardlight, i.intensity * 0.9f * t.shineOpacity, shineFilter(t, 0.95f, 1.15f, 1.3f)) {
        bg {
            cssRadial(
                it, c,
                listOf(0.05f to hsl(48f, 1f, 0.88f, 0.85f), 0.45f to hsl(40f, 0.7f, 0.45f, 0.55f), 1.25f to hsl(30f, 0.65f, 0.14f)),
            )
        }
        // `calc(var(--angle) - 48deg)` = 133 - 48
        bg(box(3f, 3f, slide(x, 2.8f), slide(y, 2.2f)), BlendMode.Hardlight) {
            cssLinear(
                it, 85f,
                listOf(
                    0.05f to GOLD_3, 0.15f to GOLD_2, 0.25f to GOLD_1, 0.30f to GOLD_4,
                    0.35f to GOLD_1, 0.45f to GOLD_2, 0.55f to GOLD_3,
                ),
                repeating = true,
            )
        }
        // ::before — 결. 저쪽 `--grain` 두 장 + 원형 빛, 셋 다 color-burn.
        // size 목록이 `55%, cover` 둘뿐이라 세 번째(원형 빛)는 다시 55% 를 받는다 — CSS 목록 반복 규칙.
        foilLayer(BlendMode.Screen, 0.55f, filterOf(1f, 1.3f, 1.2f)) {
            val grainBox = box(0.55f, 0.55f, 0.5f + x * 0.08f, 0.5f + y * 0.08f)
            bg(grainBox) {
                cssRadial(
                    it, at(it, i.p),
                    listOf(0.10f to hsl(46f, 1f, 0.8f, 0.55f), 0.55f to hsl(38f, 0.6f, 0.3f, 0.45f), 1.10f to Color.Black),
                )
            }
            bg(blend = BlendMode.ColorBurn) { rings(Offset(size.width * 0.71f, size.height * 0.63f), gray(0f, 0.35f), 1f, 7f) }
            bg(grainBox, BlendMode.ColorBurn) {
                rings(Offset(it.left + it.width * 0.17f, it.top + it.height * 0.29f), white(0.5f), 1f, 5f)
            }
        }
        // ::after — 가로로 지나가는 금빛 줄
        foilLayer(BlendMode.Overlay, 0.25f + i.fromCenter * 0.4f, filterOf(1f, 1.2f, 1f)) {
            bg(box(2.2f, 2.2f, slide(x, -2.4f), 0.5f)) {
                cssLinear(
                    it, 90f,
                    listOf(
                        0.34f to hsl(45f, 1f, 0.7f, 0f), 0.46f to hsl(48f, 1f, 0.88f, 0.95f), 0.50f to hsl(52f, 1f, 0.96f),
                        0.54f to hsl(48f, 1f, 0.88f, 0.95f), 0.66f to hsl(45f, 1f, 0.7f, 0f),
                    ),
                )
            }
        }
    }
    glare(
        i, i.intensity * 0.85f * t.glareOpacity, filterOf(0.85f, 1.7f, 1.3f),
        listOf(0.08f to hsl(48f, 1f, 0.9f, 0.6f), 0.45f to hsl(40f, 0.8f, 0.55f, 0.3f), 1.10f to hsl(28f, 0.6f, 0.06f, 0.8f)),
    )
}

// -- oilslick ---------------------------------------------------------------

/** 기름막 — 포인터 반대편에서 퍼지는 동심원 무지개 두 장이 서로 엇갈린다. */
private fun DrawScope.oilslick(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    val x = i.p.x
    val y = i.p.y
    foilLayer(BlendMode.ColorDodge, i.intensity * 0.9f * t.shineOpacity, shineFilter(t, 0.85f, 1.5f, 1.3f)) {
        bg { cssRadial(it, c, listOf(0.05f to white(0.55f), 0.45f to gray(0.45f, 0.35f), 1.30f to gray(0.04f))) }
        bg(blend = BlendMode.Softlight) {
            cssRadial(
                it, Offset(size.width * (1.5f - x * 1.6f), size.height * (1.5f - y * 1.6f)),
                listOf(
                    0f to CSS_VIOLET, 0.022f to CSS_BLUE, 0.044f to CSS_GREEN,
                    0.066f to CSS_YELLOW, 0.088f to CSS_RED, 0.11f to CSS_VIOLET,
                ),
                repeating = true,
            )
        }
        // ::before — 반대쪽에서 오는 동심원. exclusion 이라 겹친 자리 색이 뒤집힌다
        foilLayer(BlendMode.Exclusion, 0.55f, filterOf(0.9f, 1.25f, 1.15f)) {
            bg {
                cssRadial(
                    it, Offset(size.width * (x * 1.6f - 0.5f), size.height * (y * 1.6f - 0.5f)),
                    listOf(
                        0f to CSS_RED, 0.026f to CSS_VIOLET, 0.052f to CSS_BLUE,
                        0.078f to CSS_GREEN, 0.104f to CSS_YELLOW, 0.13f to CSS_RED,
                    ),
                    repeating = true,
                )
            }
        }
        // ::after
        foilLayer(BlendMode.Softlight, 0.4f + i.fromCenter * 0.5f, filterOf(0.85f, 2.4f, 1f)) {
            bg {
                cssRadial(
                    it, c,
                    listOf(0f to white(0.85f), 0.35f to hsl(280f, 0.3f, 0.55f, 0.25f), 1.10f to hsl(220f, 0.4f, 0.04f, 0.95f)),
                    ellipse = true,
                )
            }
        }
    }
    glare(
        i, i.intensity * 0.85f * t.glareOpacity, filterOf(0.85f, 1.8f, 1.4f),
        listOf(0.08f to hsl(300f, 0.6f, 0.92f, 0.6f), 0.45f to hsl(250f, 0.4f, 0.45f, 0.28f), 1.10f to hsl(220f, 0.4f, 0.05f, 0.75f)),
    )
}

// -- sunburst ---------------------------------------------------------------

/** 광선 — 포인터에서 16도마다 뻗는 무지개 살과 흰 살. */
private fun DrawScope.sunburst(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    val dark = gray(0.04f, 0.9f)
    foilLayer(BlendMode.ColorDodge, i.intensity * 0.9f * t.shineOpacity, shineFilter(t, 0.85f, 1.6f, 1.15f)) {
        bg { cssRadial(it, c, listOf(0.03f to white(0.5f), 0.40f to gray(0.45f, 0.35f), 1.15f to Color.Black)) }
        bg(blend = BlendMode.Softlight) {
            cssConic(
                c, 0f,
                listOf(0f to sunpillarClr(1), 4f to dark, 8f to sunpillarClr(3), 12f to dark, 16f to sunpillarClr(5)),
            )
        }
        // ::before
        foilLayer(BlendMode.Overlay, 0.3f + i.fromCenter * 0.4f, filterOf(1.05f, 1.25f, 1f)) {
            bg { cssConic(c, 10f, listOf(0f to CLEAR, 7f to white(0.55f), 14f to CLEAR, 22f to CLEAR)) }
        }
        // ::after
        foilLayer(BlendMode.Luminosity, 0.25f + i.fromCenter * 0.4f, filterOf(0.72f, 2.4f, 1f)) {
            bg {
                cssRadial(
                    it, c,
                    listOf(0f to hsl(45f, 1f, 0.94f, 0.6f), 0.22f to hsl(30f, 0.6f, 0.6f, 0.15f), 0.95f to hsl(250f, 0.2f, 0.08f)),
                )
            }
        }
    }
    glare(
        i, i.intensity * 0.85f * t.glareOpacity, filterOf(0.88f, 1.6f, 1.15f),
        listOf(0.06f to hsl(45f, 1f, 0.92f, 0.6f), 0.40f to hsl(35f, 0.6f, 0.55f, 0.28f), 1.10f to hsl(255f, 0.25f, 0.06f, 0.75f)),
    )
}

// -- holo -------------------------------------------------------------------

/** 무지개 띠 + 촘촘한 세로 스캔라인 + 기울이면 움직이는 세로 막대. */
private fun DrawScope.holo(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    val x = i.p.x
    val y = i.p.y
    // `--scanlines-space: 0.5px` (폭 900px 이하). CSS px 는 dp 로 읽는다 — 예전 판은 기기 픽셀 4 로
    // 박아 둬서 결이 두 배 넘게 거칠었다.
    val s = 0.5f * density / size.width
    val bar = gray(0.7f)
    // shine 은 opacity 를 따로 안 적는다 → `rarity.css` 의 `card-opacity × hc-shine`
    foilLayer(BlendMode.ColorDodge, i.intensity * t.shineOpacity, shineFilter(t, 1.1f, 1.1f, 1.2f)) {
        bg {
            cssLinear(
                it, 90f,
                listOf(0f to Color.Black, 2 * s to Color.Black, 2 * s to gray(0.4f), 4 * s to gray(0.4f)),
                repeating = true,
            )
        }
        bg(box(4f, 4f, slide(x, 2.6f), slide(y, 3.5f)), BlendMode.Overlay) {
            cssLinear(
                it, 110f,
                // 다섯 색을 세 번 — 한 주기에 무지개가 세 번 돈다
                even(List(3) { listOf(CSS_VIOLET, CSS_BLUE, CSS_GREEN, CSS_YELLOW, CSS_RED) }.flatten()),
                repeating = true,
            )
        }
        // ::before — 세로 막대 두 벌이 서로 다른 속도로 지나간다 (`--bars: 3%`)
        foilLayer(BlendMode.Hardlight, 1f, filterOf(1.15f, 1.1f, 1f)) {
            bg(box(2f, 2f, slide(x, -0.9f) - y * 0.75f, y)) {
                cssLinear(
                    it, 90f,
                    listOf(0.06f to Color.Black, 0.09f to bar, 0.105f to Color.Black, 0.12f to bar, 0.15f to Color.Black, 0.30f to Color.Black),
                    repeating = true,
                )
            }
            bg(box(2f, 2f, slide(x, 1.65f) + y * 0.5f, x), BlendMode.Screen) {
                cssLinear(
                    it, 90f,
                    listOf(0.06f to Color.Black, 0.09f to bar, 0.105f to Color.Black, 0.12f to bar, 0.15f to Color.Black, 0.42f to Color.Black),
                    repeating = true,
                )
            }
        }
        // ::after
        foilLayer(BlendMode.Luminosity, 1f, filterOf(0.6f, 4f, 1f)) {
            bg { cssRadial(it, c, listOf(0f to gray(0.9f, 0.8f), 0.25f to gray(0.78f, 0.1f), 0.9f to Color.Black)) }
        }
    }
    // glare 는 그림을 안 바꾸고 `rarity.css` 기본 그림을 쓴다
    glare(
        i, i.intensity * 0.8f * t.glareOpacity, filterOf(0.8f, 1.5f, 1f),
        listOf(0.10f to white(0.8f), 0.20f to white(0.65f), 0.90f to gray(0f, 0.5f)),
    ) {
        foilLayer(BlendMode.Overlay, 1f, filterOf(0.6f, 3f, 1f)) {
            bg {
                cssRadial(it, c, listOf(0.05f to hsl(180f, 1f, 0.95f), 0.55f to gray(0.39f, 0.25f), 1.10f to gray(0f, 0.36f)))
            }
        }
    }
}

// -- reverse ----------------------------------------------------------------

/** 가운데를 죽이고 가장자리를 살리는 역전 폴오프. 겹이 하나뿐인 유일한 포일이다(저쪽도 그렇다). */
private fun DrawScope.reverse(i: FoilInput, t: FoilTune) {
    val x = i.p.x
    val y = i.p.y
    // **가운데일수록 약해진다.** 저쪽이 이 티어를 고른 이유라 정규화를 바꾸면 안 된다.
    val a = ((1.5f * i.intensity) - i.fromCenter) * t.shineOpacity
    foilLayer(BlendMode.ColorDodge, a, shineFilter(t, 0.55f, 1.5f, 1f)) {
        bg(box(2f, 2f, x, y)) {
            cssLinear(it, -45f, listOf(0.15f to Color.Black, 0.5f to Color.White, 0.85f to Color.Black))
        }
        bg(box(1.2f, 1.2f), BlendMode.Softlight) {
            cssRadial(it, at(it, i.p), listOf(0.05f to Color.White, 0.5f to Color.Black, 0.8f to Color.White))
        }
    }
    val c = at(i.p)
    glare(
        i, i.intensity * t.glareOpacity, filterOf(0.7f, 1.5f, 1f),
        listOf(0.10f to white(0.8f), 0.20f to white(0.5f), 0.90f to gray(0f, 0.75f)),
    ) {
        // ::after 는 mix-blend 가 없어 그냥 덮는다
        foilLayer(BlendMode.SrcOver, i.intensity, filterOf(1f, 1.5f, 1f)) {
            bg { cssRadial(it, c, listOf(0.10f to Color.White, 0.20f to white(0.5f), 1.20f to gray(0f, 0.5f))) }
        }
    }
}

// -- aurora -----------------------------------------------------------------

private val AURORA_1 = hsl(158f, 0.90f, 0.62f)
private val AURORA_2 = hsl(186f, 0.95f, 0.64f)
private val AURORA_3 = hsl(258f, 0.92f, 0.72f)
private val AURORA_4 = hsl(316f, 0.84f, 0.68f)

/** 넓고 부드러운 색 띠 두 장이 서로 반대로 흐른다. */
private fun DrawScope.aurora(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    val x = i.p.x
    val y = i.p.y
    foilLayer(BlendMode.ColorDodge, i.intensity * 0.9f * t.shineOpacity, shineFilter(t, 0.85f, 1.5f, 1.2f)) {
        bg {
            cssRadial(
                it, c,
                listOf(0.05f to hsl(180f, 1f, 0.9f, 0.55f), 0.45f to hsl(200f, 0.4f, 0.5f, 0.3f), 1.30f to hsl(240f, 0.3f, 0.05f)),
            )
        }
        // `calc(var(--angle) - 55deg)` = 133 - 55
        bg(box(3.4f, 7.2f, slide(x, 2.4f), slide(y, 3.2f)), BlendMode.Softlight) {
            cssLinear(
                it, 78f,
                listOf(0.05f to AURORA_1, 0.20f to AURORA_2, 0.35f to AURORA_3, 0.50f to AURORA_4, 0.65f to AURORA_1),
                repeating = true,
            )
        }
        // ::before — `calc(var(--angle) + 22deg)` = 155
        foilLayer(BlendMode.Overlay, 0.8f, filterOf(1.2f, 1.3f, 1.1f)) {
            bg(box(4.2f, 6.4f, slide(x, -1.6f), slide(y, 2.2f))) {
                cssLinear(
                    it, 155f,
                    listOf(0.05f to AURORA_3, 0.25f to AURORA_1, 0.45f to AURORA_4, 0.65f to AURORA_2, 0.85f to AURORA_3),
                    repeating = true,
                )
            }
        }
        // ::after
        foilLayer(BlendMode.Luminosity, 0.2f + i.fromCenter * 0.35f, filterOf(0.75f, 2.2f, 1f)) {
            bg {
                cssRadial(
                    it, c,
                    listOf(0f to hsl(160f, 1f, 0.92f, 0.45f), 0.40f to hsl(200f, 0.6f, 0.6f, 0.12f), 1.20f to hsl(240f, 0.4f, 0.06f, 0.9f)),
                    ellipse = true,
                )
            }
        }
    }
    glare(
        i, i.intensity * 0.8f * t.glareOpacity, filterOf(0.8f, 1.8f, 1f),
        listOf(0.08f to hsl(170f, 1f, 0.94f, 0.55f), 0.45f to hsl(210f, 0.6f, 0.55f, 0.25f), 1.10f to hsl(250f, 0.4f, 0.05f, 0.7f)),
    )
}

// -- cosmos -----------------------------------------------------------------

/** 저쪽 `--space: 4%` 로 적은 성운 색 열두 칸. 가운데서 되돌아 오는 대칭이다. */
private val COSMOS_STOPS: List<Pair<Float, Color>> = listOf(
    hsl(53f, 0.65f, 0.60f), hsl(93f, 0.56f, 0.50f), hsl(176f, 0.54f, 0.49f),
    hsl(228f, 0.59f, 0.55f), hsl(283f, 0.60f, 0.55f), hsl(326f, 0.59f, 0.51f),
).let { up -> (up + up.reversed()).mapIndexed { n, color -> 0.04f * (n + 1) to color } }

/** 성운 — 같은 색 띠 세 장을 조금씩 다른 폭으로 밀어 겹친다. */
private fun DrawScope.cosmos(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    val x = i.p.x
    val y = i.p.y
    fun band(r: Rect) = cssLinear(r, 82f, COSMOS_STOPS, repeating = true)
    // shine 은 opacity 를 안 적는다 → `rarity.css` 의 `card-opacity × hc-shine`
    foilLayer(BlendMode.ColorDodge, i.intensity * t.shineOpacity, shineFilter(t, 1f, 1f, 0.8f)) {
        bg { cssRadial(it, c, listOf(0.05f to hsl(180f, 1f, 0.89f, 0.5f), 0.40f to hsl(180f, 0.14f, 0.57f, 0.3f), 1.30f to Color.Black)) }
        bg(box(4f, 9f, 0.10f + x * 0.8f, 0.10f + y * 0.8f), BlendMode.Multiply) { band(it) }
        // ::before · ::after — 같은 띠를 좁은 폭으로 한 번 더, 또 한 번 더
        foilLayer(BlendMode.Overlay, 1f, filterOf(1.25f, 1.75f, 0.8f)) {
            bg(box(4f, 9f, 0.15f + x * 0.7f, 0.15f + y * 0.7f)) { band(it) }
        }
        foilLayer(BlendMode.Multiply, 1f, filterOf(1.25f, 1.75f, 0.8f)) {
            bg(box(4f, 9f, 0.20f + x * 0.6f, 0.20f + y * 0.6f)) { band(it) }
        }
    }
    glare(
        i, i.intensity * (0.25f + i.fromCenter) * t.glareOpacity, filterOf(0.75f, 2f, 2f),
        listOf(0.05f to hsl(204f, 1f, 0.95f, 0.8f), 1.5f to hsl(250f, 0.15f, 0.2f)),
    ) {
        foilLayer(BlendMode.Softlight, 1f - y * 0.75f, filterOf(0.75f, 2.5f, 2f)) {
            bg { cssRadial(it, c, listOf(0.05f to hsl(280f, 1f, 0.96f), 0.6f to gray(0.1f))) }
        }
    }
}

// -- mosaic -----------------------------------------------------------------

/** 격자 타일 — 체크 무늬 두 장이 반 칸 어긋나 겹치고, 그 아래 무지개가 반대로 흐른다. */
private fun DrawScope.mosaic(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    val x = i.p.x
    val y = i.p.y
    // `--mosaic-size: calc(25% * 0.55)`, 세로는 그 0.72 배. % 는 가로·세로 각자 기준이다
    val tw = 0.1375f
    val th = 0.1375f * 0.72f
    foilLayer(BlendMode.ColorDodge, i.intensity * 0.9f * t.shineOpacity, shineFilter(t, 0.82f, 1.9f, 0.95f)) {
        bg { cssRadial(it, c, listOf(0.05f to white(0.5f), 0.45f to gray(0.45f, 0.35f), 1.20f to Color.Black)) }
        bg(box(4f, 4f, slide(x, 2.6f), slide(y, 2f)), BlendMode.Multiply) {
            cssLinear(it, 133f, (0..6).map { n -> 0.05f * (n + 1) to sunpillarClr(n % 6 + 1) }, repeating = true)
        }
        checker(box(tw, th), Color.White, gray(0.22f), BlendMode.Overlay)
        // ::before — 반 칸 어긋난 반대색 체크 + 반대로 흐르는 무지개
        foilLayer(BlendMode.Lighten, 0.35f + i.fromCenter * 0.45f, filterOf(1.05f, 1.5f, 1f)) {
            bg(box(4f, 4f, slide(x, -2.2f), slide(y, -1.6f))) {
                cssLinear(
                    it, 313f,
                    (0..6).map { n -> 0.05f * (n + 1) to sunpillarClr((n + 3) % 6 + 1, BEFORE) },
                    repeating = true,
                )
            }
            checker(box(tw, th, 0.5f + tw / 2f, 0.5f + tw * 0.36f), gray(0.2f), gray(0.96f), BlendMode.Overlay)
        }
        // ::after
        foilLayer(BlendMode.Luminosity, 1f, filterOf(0.62f, 3f, 1f)) {
            bg { cssRadial(it, c, listOf(0f to gray(0.92f, 0.7f), 0.28f to gray(0.74f, 0.1f), 0.9f to gray(0.06f))) }
        }
    }
    glare(
        i, i.intensity * 0.9f * t.glareOpacity, filterOf(0.85f, 1.8f, 1f),
        listOf(0.10f to white(0.8f), 0.45f to gray(0.65f, 0.35f), 1.10f to gray(0f, 0.6f)),
    )
}

// -- metal ------------------------------------------------------------------

/** 브러시드 금속 — 촘촘한 결 위로 넓은 은빛 띠가 가로로, 좁은 빛줄이 세로로 지나간다. */
private fun DrawScope.metal(i: FoilInput, t: FoilTune) {
    val c = at(i.p)
    val x = i.p.x
    val y = i.p.y
    val steel = hsl(210f, 0.1f, 0.26f)
    val mid = hsl(210f, 0.08f, 0.64f)
    foilLayer(BlendMode.Hardlight, i.intensity * 0.9f * t.shineOpacity, shineFilter(t, 1.05f, 1.4f, 0.3f)) {
        bg { cssRadial(it, c, listOf(0.05f to white(0.8f), 0.45f to gray(0.58f, 0.5f), 1.30f to gray(0.24f))) }
        bg(box(2.6f, 1f, slide(x, -3f), 0.5f), BlendMode.Softlight) {
            cssLinear(it, 105f, listOf(0.18f to steel, 0.40f to mid, 0.50f to gray(0.94f), 0.60f to mid, 0.82f to steel))
        }
        // `calc(var(--angle) - 43deg)` = 90 — 세로 결
        bg(blend = BlendMode.Overlay) {
            cssLinear(it, 90f, listOf(0f to gray(0.36f), 0.002f to gray(0.74f), 0.004f to gray(0.36f)), repeating = true)
        }
        // ::before
        foilLayer(BlendMode.Overlay, 0.4f + i.fromCenter * 0.5f, filterOf(1.1f, 1.3f, 1f)) {
            bg(box(1f, 3f, 0.5f, slide(y, -3f))) {
                cssLinear(
                    it, 75f,
                    listOf(0.30f to CLEAR, 0.47f to white(0.5f), 0.50f to white(0.7f), 0.53f to white(0.5f), 0.70f to CLEAR),
                )
            }
        }
        // ::after
        foilLayer(BlendMode.Luminosity, 0.8f, filterOf(0.72f, 2.4f, 1f)) {
            bg { cssRadial(it, c, listOf(0f to gray(0.95f, 0.7f), 0.25f to gray(0.7f, 0.1f), 0.9f to gray(0.12f))) }
        }
    }
    glare(
        i, i.intensity * 0.85f * t.glareOpacity, filterOf(0.9f, 1.6f, 0.4f),
        listOf(0.08f to hsl(210f, 0.15f, 0.96f, 0.7f), 0.45f to hsl(210f, 0.08f, 0.55f, 0.28f), 1.10f to hsl(210f, 0.1f, 0.14f, 0.7f)),
    )
}
