package com.daengs.app.ui.dex

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 포일 11종이 **서로 다르게**, **눈에 띄게** 그려지는가.
 *
 * 예쁜지는 못 잡는다 — 그건 실기기에서 본다. 여기서 잡는 것은 2026-09-18 에 실제로 났던 병이다:
 * 웹판 세 겹 중 한 겹만 옮겨 9종이 "무지개 그라디언트 한 장"으로 뭉개져 다 거기서 거기였다.
 *
 * 판은 [FoilQuietTest] 처럼 중간 밝기를 쓴다 — 포일 대부분이 `color-dodge` 라 흰 바탕에서는
 * 어디서나 흰색으로 클리핑돼 차이가 0 이 된다. 대신 가로로 어두워지는 그라디언트라 무늬가
 * 판의 밝기에 따라 달리 먹히는 것까지 드러난다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoilLayersTest {

    private val w = 160
    private val h = 240

    /** 가장자리 쪽 포인터. 가운데면 reverse 가 거의 꺼지고 `fromCenter` 를 읽는 겹이 잠잠하다. */
    private val pointer = Offset(0.3f, 0.35f)

    private val plate: ImageBitmap by lazy {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (x in 0 until w) {
            val v = 150 - x * 60 / w
            for (y in 0 until h) bmp.setPixel(x, y, android.graphics.Color.rgb(v, v, v + 10))
        }
        bmp.asImageBitmap()
    }

    private fun render(foil: Foil?): Bitmap {
        val out = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(out), Size(w.toFloat(), h.toFloat())) {
            drawImage(plate)
            if (foil != null) drawFoil(foil, FoilInput.of(pointer, intensity = 1f), foil.webTune)
        }
        return out.asAndroidBitmap()
    }

    /** 두 그림의 픽셀당 평균 RGB 차이 (0~255). */
    private fun diff(a: Bitmap, b: Bitmap): Double {
        var sum = 0L
        for (x in 0 until w step 2) for (y in 0 until h step 2) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            sum += abs(android.graphics.Color.red(p) - android.graphics.Color.red(q)) +
                abs(android.graphics.Color.green(p) - android.graphics.Color.green(q)) +
                abs(android.graphics.Color.blue(p) - android.graphics.Color.blue(q))
        }
        return sum.toDouble() / ((w / 2) * (h / 2) * 3)
    }

    @Test
    fun `포일마다 판을 눈에 띄게 바꾼다`() {
        val bare = render(null)
        val weak = Foil.entries.map { it to diff(bare, render(it)) }.filter { it.second < MIN_CHANGE }
        assertTrue("판을 거의 안 바꾼 포일: $weak", weak.isEmpty())
    }

    @Test
    fun `포일 11종이 서로 다른 그림을 낸다`() {
        val drawn = Foil.entries.associateWith { render(it) }
        val alike = mutableListOf<String>()
        val foils = Foil.entries
        for (m in foils.indices) for (n in m + 1 until foils.size) {
            val d = diff(drawn.getValue(foils[m]), drawn.getValue(foils[n]))
            if (d < MIN_APART) alike += "${foils[m]}~${foils[n]}=${"%.1f".format(d)}"
        }
        assertTrue("서로 구별이 안 되는 짝: $alike", alike.isEmpty())
    }

    /** 누른 다섯과 안 누른 여섯 — 저쪽 `rarity.css` 그대로다. 한꺼번에 누르던 옛 판으로 돌아가면 깨진다. */
    @Test
    fun `세기는 rarity css 의 다섯 포일만 누른다`() {
        val pressed = Foil.entries.filter { it.webTune != FoilTune() }.toSet()
        assertEquals(setOf(Foil.Gold, Foil.Sunburst, Foil.Metal, Foil.Mosaic, Foil.Aurora), pressed)
        assertEquals(0.28f, Foil.Gold.webTune.shineOpacity)
        assertEquals(1f, Foil.Crystal.webTune.shineOpacity)
    }

    @Test
    fun `포토 카드도 같은 세기 표를 쓴다`() {
        PHOTO_FOIL.values.forEach { assertEquals(it.foil.webTune, it.tune) }
    }

    /** `transparent → 노랑` 이 검게 탁해지지 않게 투명 쪽을 이웃 색으로 맞춘다. */
    @Test
    fun `투명 정지점은 이웃 색을 알파 0 으로 받는다`() {
        val yellow = Color(1f, 1f, 0f)
        val blue = Color(0f, 0f, 1f)
        val out = premultiplied(listOf(0f to CLEAR, 0.5f to yellow, 0.7f to CLEAR, 1f to blue))
        assertEquals(yellow.copy(alpha = 0f), out[0].second)
        // 양쪽이 다르면 같은 자리에 둘
        assertEquals(listOf(0.7f to yellow.copy(alpha = 0f), 0.7f to blue.copy(alpha = 0f)), out.subList(2, 4))
    }

    /**
     * CSS `filter: brightness(.5) contrast(2)` 는 밝기부터 먹인다 — 흰색 → 0.5 → (0.5-0.5)×2+0.5 = 0.5.
     * 거꾸로 먹이면 흰색 → 1.5 → 0.75 가 된다. 예전 판이 그랬다.
     */
    @Test
    fun `필터는 밝기 대비 채도 순서다`() {
        val out = ImageBitmap(1, 1)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(out), Size(1f, 1f)) {
            drawRect(Color.White, colorFilter = filterOf(0.5f, 2f, 1f))
        }
        val red = android.graphics.Color.red(out.asAndroidBitmap().getPixel(0, 0))
        assertTrue("흰색이 $red 로 나왔다 (128 근처여야 한다)", abs(red - 128) <= 3)
    }

    private companion object {
        /** 판에서 평균 이만큼은 밀어야 "포일이 있다". 0~255 중. 09-18 실측 최소는 sunburst 21 */
        const val MIN_CHANGE = 12.0

        /** 두 포일이 평균 이만큼은 달라야 "다른 포일"이다. 09-18 실측 최소는 sunburst~metal 16 */
        const val MIN_APART = 8.0
    }
}
