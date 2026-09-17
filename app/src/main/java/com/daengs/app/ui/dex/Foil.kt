package com.daengs.app.ui.dex

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// 홀로그램 포일 — `vendor/cards-css/*.css` 를 Compose 로 옮긴 것
//
// 원본은 `SAJOYO/DAENGS_CARDS` 의 `vendor/cards-css/<포일>.css` 와 `rarity.css` 다.
// 웹판은 카드 그림 위에 요소 두 개(`__shine`, `__glare`)를 얹고, 요소마다
// `::before` / `::after` 를 한 장씩 더 붙여 **세 겹**으로 섞는다. 한 겹은 이렇게 생겼다.
//
//     background-image: <그라디언트 A>, <그라디언트 B>;   <- 위에 적은 것이 위에 깔린다
//     background-size / background-position;              <- 상자 크기·자리, 넘치면 타일로 반복
//     background-blend-mode: multiply;                   <- A 를 B 위에 섞고
//     filter: brightness() contrast() saturate();        <- 그 순서대로 먹이고
//     mix-blend-mode: color-dodge;                       <- 결과를 아래와 섞는다
//     opacity: ...;
//
// Compose 로는 이렇게 옮긴다.
//
//   background-image      -> [cssLinear] · [cssRadial] · [cssConic] · [checker]
//   background-size/pos   -> [box] 로 상자를 만들고 [bg] 가 타일로 깐다
//   background-blend-mode -> 레이어 안에서 아래 것부터 그리고 위 것을 blendMode 로
//   filter + opacity + mix-blend-mode -> [foilLayer] 한 번
//
// ## 겹은 서로 안에서 섞인다 (격리 그룹)
//
// `filter` · `opacity` · `mix-blend-mode` 가 걸린 요소는 CSS 에서 **격리 그룹**이다.
// 그래서 `::before` 의 `mix-blend-mode: lighten` 은 카드 그림이 아니라 **shine 자신의
// 배경**과 섞이고, 그렇게 합친 덩어리가 shine 의 filter·opacity 를 받은 뒤에야 카드와
// `color-dodge` 로 섞인다. Compose 로는 [foilLayer] 안에 [foilLayer] 를 넣으면 똑같다 —
// `saveLayer` 는 투명한 새 판에서 시작하므로 그 안의 섞기는 바깥 판을 못 본다.
//
// 예전 판은 이 겹을 전부 카드에 직접 섞었고, 11종 중 9종은 `::before`/`::after` 를 아예
// 안 옮겼다. 포일마다 다르게 보이게 하는 게 바로 그 겹이라 **다 거기서 거기**가 됐다.
//
// ## 카드 전체가 한 레이어여야 한다
//
// `mix-blend-mode` 는 "아래 있는 것과 섞는다"는 뜻이라, 카드 그림과 포일이 같은
// 오프스크린 레이어 안에 있어야 한다. 밖에 있으면 앱 배경과 섞여 버린다.
// [HoloCard] 가 `CompositingStrategy.Offscreen` 으로 그 레이어를 만든다.
// ---------------------------------------------------------------------------

/** 카드가 쓰는 포일. 이름은 저쪽 `rarity` 값 그대로다. */
enum class Foil { Prism, Crystal, Gold, Oilslick, Sunburst, Holo, Reverse, Aurora, Cosmos, Mosaic, Metal }

/**
 * 손가락(또는 자이로)이 만든 입력. 전부 0~1 이다.
 *
 * @param p 카드 안에서의 위치. 저쪽 `--pointer-x/y` · `--background-x/y` · `--pointer-from-left/top`
 * @param fromCenter 가운데에서 얼마나 멀리 있나. **반지름 0.5 를 1 로 본다** —
 *   저쪽 주석대로 `reverse` 가 이걸로 가운데를 죽이고 가장자리를 살리므로 정규화를
 *   바꾸면 그 티어가 무너진다
 * @param intensity 포일 세기. 저쪽 `--card-opacity` 자리다. 손을 떼면 0 으로 잦아든다
 */
data class FoilInput(val p: Offset, val fromCenter: Float, val intensity: Float) {
    companion object {
        val Idle = FoilInput(Offset(0.5f, 0.5f), 0f, 0f)

        fun of(p: Offset, intensity: Float) = FoilInput(
            p = p,
            fromCenter = min(hypot(p.x - 0.5f, p.y - 0.5f) / 0.5f, 1f),
            intensity = intensity,
        )
    }
}

/**
 * 세기 손잡이. 저쪽 `--hc-*` 와 같은 자리이고 **기본값도 저쪽과 같은 1** 이다.
 *
 * 포일마다 누른 값은 [webTune] 에 있다. 예전에는 모든 카드를 0.30 으로 한꺼번에 눌렀는데,
 * 저쪽은 얼굴이 날아가는 다섯 포일만 누르고 나머지는 1 로 뒀다 — 한꺼번에 누르니 누를
 * 필요가 없던 포일까지 서너 배 약해져 밋밋해졌다.
 */
data class FoilTune(
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturate: Float = 1f,
    val shineOpacity: Float = 1f,
    val glareOpacity: Float = 1f,
)

/**
 * 포일마다 저쪽 `rarity.css` 가 누른 세기. 숫자와 이유를 그대로 옮긴다.
 *
 * **여기 없는 포일은 일부러 1 이다.** prism·crystal·oilslick 은 무지개가 다 뜨는 쪽이
 * 낫다고 사람이 골랐다(저쪽 2026-08-26). 얼굴이 뜨는 것은 앱에서 [FoilQuiet] 가 얼굴
 * 자리를 따로 재우므로 웹보다 덜 걱정해도 된다.
 */
val Foil.webTune: FoilTune
    get() = when (this) {
        // 유일하게 hard-light 라 제일 세다. brightness 로 누르면 탁해지므로 opacity 로 뺀다
        Foil.Gold -> FoilTune(brightness = 0.92f, saturate = 0.8f, shineOpacity = 0.28f, glareOpacity = 0.6f)
        // 광선. 바탕(버섯 베이지)이 이미 밝다
        Foil.Sunburst -> FoilTune(brightness = 0.52f, shineOpacity = 0.4f, glareOpacity = 0.7f)
        // 브러시드 금속. hard-light 라 세고, 결이 대비로 생겨 brightness 로 누르면 결이 사라진다
        Foil.Metal -> FoilTune(brightness = 0.9f, shineOpacity = 0.3f, glareOpacity = 0.65f)
        // 격자 타일. 타일끼리의 대비가 무늬라 brightness 는 두고 opacity 로만 누른다
        Foil.Mosaic -> FoilTune(shineOpacity = 0.5f, glareOpacity = 0.8f)
        // 민트~시안 띠가 넓게 깔린다. 초록 카드에서 제일 심하게 떴다
        Foil.Aurora -> FoilTune(brightness = 0.52f, saturate = 0.85f, shineOpacity = 0.42f, glareOpacity = 0.7f)
        else -> FoilTune()
    }

/** 저쪽 `--sunpillar-1..6`. 무지개 한 주기를 이루는 여섯 색이다. */
internal val SUNPILLAR = listOf(
    hsl(2f, 1f, 0.73f),
    hsl(53f, 1f, 0.69f),
    hsl(93f, 1f, 0.69f),
    hsl(176f, 1f, 0.76f),
    hsl(228f, 1f, 0.74f),
    hsl(283f, 1f, 0.73f),
)

/**
 * 저쪽 `--sunpillar-clr-1..6`. `rarity.css` 가 `::before` 는 네 칸, `::after` 는 다섯 칸
 * 돌려 준다 — 이게 있어야 세 겹이 서로 다른 위상으로 겹쳐 무지개가 층진다.
 *
 * @param shift 0 = 요소 자신, 4 = `::before`, 5 = `::after`
 * @param n 1..6
 */
internal fun sunpillarClr(n: Int, shift: Int = 0): Color = SUNPILLAR[(n - 1 + shift) % 6]

internal const val BEFORE = 4
internal const val AFTER = 5

// 저쪽 `--red` ~ `--violet`
internal val CSS_RED = Color(0xFFF80E35)
internal val CSS_YELLOW = Color(0xFFEEDF10)
internal val CSS_GREEN = Color(0xFF21E985)
internal val CSS_BLUE = Color(0xFF0DBDE9)
internal val CSS_VIOLET = Color(0xFFC929F1)

// -- 상자 ------------------------------------------------------------------

/**
 * `background-size` · `background-position` 을 푼 상자.
 *
 * @param sw 폭. 1 = 100% (`cover` 는 1, 1)
 * @param px 위치 비율. CSS 의 `background-position: 30%` 는 `(요소 - 상자) × 0.3` 이다
 */
internal fun DrawScope.box(sw: Float = 1f, sh: Float = 1f, px: Float = 0.5f, py: Float = 0.5f): Rect {
    val w = size.width * sw
    val h = size.height * sh
    return Rect(Offset((size.width - w) * px, (size.height - h) * py), Size(w, h))
}

/** 저쪽 `calc(((50% - var(--background-x)) * k) + 50%)`. 포인터에 따라 무늬를 미는 위치. */
internal fun slide(b: Float, k: Float) = (0.5f - b) * k + 0.5f

/**
 * 배경 한 장. `background-repeat: repeat` 대로 상자를 이어 붙여 요소를 덮는다.
 *
 * 상자가 요소보다 크면 대개 한두 장이고, 포인터가 멀리 밀면 이음매가 보이는 것까지 저쪽과 같다.
 */
internal inline fun DrawScope.bg(
    tile: Rect = Rect(Offset.Zero, size),
    blend: BlendMode = BlendMode.SrcOver,
    brush: (Rect) -> Brush,
) {
    val w = tile.width
    val h = tile.height
    if (w < 1f || h < 1f) return
    val x0 = tile.left - ceil(tile.left / w) * w
    val y0 = tile.top - ceil(tile.top / h) * h
    var y = y0
    var rows = 0
    while (y < size.height && rows++ < MAX_TILES) {
        var x = x0
        var cols = 0
        while (x < size.width && cols++ < MAX_TILES) {
            val r = Rect(Offset(x, y), tile.size)
            drawRect(brush(r), topLeft = r.topLeft, size = r.size, blendMode = blend)
            x += w
        }
        y += h
    }
}

/** 타일을 한 줄에 이보다 많이 깔지 않는다. 상자가 1px 에 가깝게 작아져도 멈추게. */
internal const val MAX_TILES = 16

/**
 * `repeating-conic-gradient(A 0% 25%, B 0% 50%)` — 네 칸짜리 체크. 오른쪽 위·왼쪽 아래가 [a].
 *
 * 원뿔 그라디언트로도 되지만 타일이 수십 장이라 칸을 직접 칠한다. 경계가 칼 같아야
 * 격자로 읽힌다.
 */
internal fun DrawScope.checker(tile: Rect, a: Color, b: Color, blend: BlendMode) {
    foilLayer(blend, 1f, null) {
        drawRect(b)
        val w = tile.width
        val h = tile.height
        if (w < 1f || h < 1f) return@foilLayer
        val hw = w / 2f
        val hh = h / 2f
        var y = tile.top - ceil(tile.top / h) * h
        while (y < size.height) {
            var x = tile.left - ceil(tile.left / w) * w
            while (x < size.width) {
                drawRect(a, Offset(x + hw, y), Size(hw, hh))
                drawRect(a, Offset(x, y + hh), Size(hw, hh))
                x += w
            }
            y += h
        }
    }
}

// -- 그라디언트 ---------------------------------------------------------------

/**
 * 투명 정지점의 색을 옆 색으로 바꾼다.
 *
 * CSS 는 색을 **미리 곱한(premultiplied)** 채로 보간해서 `transparent → 노랑` 이 옅은 노랑으로
 * 번진다. 안드로이드 그라디언트는 그냥 보간해서 그 사이가 **검게** 탁해진다 — 칼날 같아야 할
 * crystal 띠에 검은 테가 생긴다. 투명 쪽 색을 이웃 색(알파 0)으로 맞추면 둘이 같아진다.
 * 양쪽 이웃이 다르면 같은 자리에 정지점을 둘 둔다.
 */
internal fun premultiplied(stops: List<Pair<Float, Color>>): List<Pair<Float, Color>> {
    val out = ArrayList<Pair<Float, Color>>(stops.size + 4)
    for ((i, s) in stops.withIndex()) {
        if (s.second.alpha > 0f) {
            out += s
            continue
        }
        val prev = stops.subList(0, i).lastOrNull { it.second.alpha > 0f }?.second
        val next = stops.subList(i + 1, stops.size).firstOrNull { it.second.alpha > 0f }?.second
        when {
            prev != null && next != null && prev != next -> {
                out += s.first to prev.copy(alpha = 0f)
                out += s.first to next.copy(alpha = 0f)
            }
            else -> out += s.first to (prev ?: next ?: s.second).copy(alpha = 0f)
        }
    }
    return out
}

private fun List<Pair<Float, Color>>.colors() = IntArray(size) { this[it].second.toArgb() }

/**
 * `linear-gradient(angle, ...)` · `repeating-linear-gradient`. 정지점은 그라디언트 선 길이의 비율.
 *
 * 선 길이는 CSS 대로 `|w·sinθ| + |h·cosθ|` 이고 상자 가운데를 지난다. 0deg 는 위쪽이다.
 */
internal fun cssLinear(
    box: Rect,
    angleDeg: Float,
    stops: List<Pair<Float, Color>>,
    repeating: Boolean = false,
): Brush {
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = sin(a).toFloat()
    val dy = -cos(a).toFloat()
    val len = abs(box.width * dx) + abs(box.height * dy)
    val c = box.center
    val sx = c.x - dx * len / 2f
    val sy = c.y - dy * len / 2f
    val s = premultiplied(stops)
    val f0 = if (repeating) s.first().first else 0f
    val f1 = if (repeating) s.last().first else 1f
    val span = max(f1 - f0, 1e-4f)
    val shader = LinearGradient(
        sx + dx * len * f0, sy + dy * len * f0,
        sx + dx * len * f1, sy + dy * len * f1,
        s.colors(),
        FloatArray(s.size) { ((s[it].first - f0) / span).coerceIn(0f, 1f) },
        if (repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP,
    )
    return ShaderBrush(shader)
}

/** 색만 적고 자리를 안 적은 CSS 정지점 — 0..1 에 고르게 편다. */
internal fun even(colors: List<Color>): List<Pair<Float, Color>> =
    colors.mapIndexed { i, c -> i.toFloat() / (colors.size - 1) to c }

/**
 * `radial-gradient(farthest-corner circle|ellipse at center, ...)` · `repeating-radial-gradient`.
 *
 * 반지름은 [box] 의 가장 먼 모서리까지다. 정지점이 100% 를 넘으면(저쪽 `125%` 같은 것)
 * 반지름을 그만큼 늘려서 담는다. 타원은 `farthest-side` 비율 그대로 모서리를 지나게 √2 배다.
 */
internal fun cssRadial(
    box: Rect,
    center: Offset,
    stops: List<Pair<Float, Color>>,
    ellipse: Boolean = false,
    repeating: Boolean = false,
): Brush {
    val s = premultiplied(stops)
    val rx0 = max(abs(center.x - box.left), abs(box.right - center.x))
    val ry0 = max(abs(center.y - box.top), abs(box.bottom - center.y))
    val r = if (ellipse) rx0 * SQRT2 else hypot(rx0, ry0)
    val last = s.last().first
    val scale = if (repeating || last > 1f) last else 1f
    val shader = RadialGradient(
        center.x, center.y, max(r * scale, 1f),
        s.colors(),
        FloatArray(s.size) { (s[it].first / scale).coerceIn(0f, 1f) },
        if (repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP,
    )
    if (ellipse && rx0 > 0f) {
        shader.setLocalMatrix(Matrix().apply { setScale(1f, ry0 / rx0, center.x, center.y) })
    }
    return ShaderBrush(shader)
}

private val SQRT2 = sqrt(2f)

/**
 * `repeating-conic-gradient(from A at center, ...)`. 정지점은 **도** 단위, 마지막 정지점이 주기다.
 *
 * 안드로이드 원뿔은 한 바퀴만 돌아서 주기를 360도까지 손으로 이어 붙인다. 360 이 주기로
 * 나눠떨어지지 않으면(광선 16도) 저쪽처럼 0도 자리에 이음매가 남는다. CSS 는 위쪽이 0도,
 * 안드로이드는 오른쪽이 0도라 90도 돌린다.
 */
internal fun cssConic(center: Offset, fromDeg: Float, stops: List<Pair<Float, Color>>): Brush {
    val s = premultiplied(stops)
    val period = max(s.last().first, 1f)
    val expanded = ArrayList<Pair<Float, Color>>()
    var base = 0f
    while (base < 360f) {
        for ((deg, color) in s) {
            val at = base + deg
            if (at > 360f) {
                val prev = expanded.last()
                val t = (360f - prev.first) / max(at - prev.first, 1e-4f)
                expanded += 360f to lerpColor(prev.second, color, t)
                break
            }
            expanded += at to color
        }
        base += period
    }
    val shader = SweepGradient(
        center.x, center.y,
        expanded.colors(),
        FloatArray(expanded.size) { (expanded[it].first / 360f).coerceIn(0f, 1f) },
    )
    shader.setLocalMatrix(Matrix().apply { setRotate(fromDeg - 90f, center.x, center.y) })
    return ShaderBrush(shader)
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t,
)

/**
 * `filter: brightness() contrast() saturate()` 를 행렬 하나로.
 *
 * **CSS 는 적힌 순서대로 먹인다 — 밝기 → 대비 → 채도.** 행렬 곱은 오른쪽이 먼저라
 * `S · C · B` 로 곱한다. 예전 판은 거꾸로(채도 → 대비 → 밝기) 곱해서, 밝기 0.55 에 대비
 * 1.9 인 포일이 저쪽보다 훨씬 밝게 나왔다 — 대비가 먼저 벌린 것을 밝기가 누르는 것과
 * 밝기로 누른 것을 대비가 벌리는 것은 다르다.
 */
internal fun filterOf(brightness: Float, contrast: Float, saturate: Float): ColorFilter {
    val b = ColorMatrix(
        floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    // 대비: 가운데(0.5)를 축으로 벌린다
    val t = (1f - contrast) * 0.5f * 255f
    val c = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val m = ColorMatrix().apply { setToSaturation(saturate) }
    m.timesAssign(c)
    m.timesAssign(b)
    return ColorFilter.colorMatrix(m)
}

internal fun hsl(h: Float, s: Float, l: Float, a: Float = 1f): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (h % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m, a)
}

internal fun white(alpha: Float) = Color(1f, 1f, 1f, alpha)
internal fun gray(level: Float, alpha: Float = 1f) = Color(level, level, level, alpha)
internal val CLEAR = Color(0f, 0f, 0f, 0f)

/**
 * 요소 한 장(또는 `::before`/`::after` 한 장). `filter` + `opacity` + `mix-blend-mode` 를 한 덩어리로.
 *
 * `saveLayer` 를 쓰는 이유: `filter` 는 배경을 섞은 **뒤**에 걸려야 하고,
 * `mix-blend-mode` 는 그 결과를 아래와 합칠 때 걸려야 한다. 한 번의 drawRect 로는
 * 두 시점을 나눌 수 없다. **안에 또 부르면 CSS 의 격리 그룹이 된다** (파일 머리말).
 */
internal inline fun DrawScope.foilLayer(
    blend: BlendMode,
    alpha: Float,
    filter: ColorFilter?,
    body: DrawScope.() -> Unit,
) {
    if (alpha <= 0.001f) return
    val paint = Paint().apply {
        this.blendMode = blend
        this.alpha = alpha.coerceIn(0f, 1f)
        this.colorFilter = filter
    }
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(Offset.Zero, size), paint)
    body()
    canvas.restore()
}
