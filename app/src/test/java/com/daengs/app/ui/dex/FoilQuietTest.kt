package com.daengs.app.ui.dex

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.Hole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 포일이 그림 위에서 약해지는가.
 *
 * **눈으로는 못 잡는 종류다.** 포일은 손가락을 대야 돌고 세기가 프레임마다 달라서,
 * 스크린샷 한 장으로는 "약해졌다"를 말할 수 없다. 여기서는 카드 대신 **평평한 회색
 * 판**을 깔고 포일을 돌린 뒤, 조용한 자리와 바깥의 변화량을 재서 비교한다.
 *
 * 회색을 쓰는 이유: 포일 대부분이 `color-dodge` 라 흰 바탕에서는 어디서나 흰색으로
 * 클리핑돼 **차이가 0 이 된다.** 중간 밝기여야 세기가 값으로 드러난다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoilQuietTest {

    private val side = 240

    /** 화면 밖 구석에 둔 구멍. 하나만 보고 싶은 시험에서 방해가 안 되게 한다. */
    private val farHole = Hole(cx = -50f, cy = -50f, rx = 1f, ry = 0.75f)

    private val middle = Hole(cx = 50f, cy = 50f, rx = 12f, ry = 9f)

    /** 회색 판 위에 포일을 돌린 결과. [quiet] 이 null 이면 예전 그림이다. */
    private fun render(quiet: FoilQuiet?, foil: Foil = Foil.Holo): Bitmap {
        val plate = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(128, 128, 128)) }
            .asImageBitmap()

        val out = ImageBitmap(side, side)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(out),
            Size(side.toFloat(), side.toFloat()),
        ) {
            drawImage(plate)
            drawFoil(foil, FoilInput.of(Offset(0.5f, 0.5f), intensity = 1f), FoilTune())
            if (quiet != null) drawFoilQuiet(quiet, plate, beneath = null)
        }
        return out.asAndroidBitmap()
    }

    /** 회색(128)에서 얼마나 밀려났나. 포일이 셀수록 커진다. */
    private fun shift(bmp: Bitmap, x: Int, y: Int): Int {
        val p = bmp.getPixel(x, y)
        val lum = (android.graphics.Color.red(p) + android.graphics.Color.green(p) +
            android.graphics.Color.blue(p)) / 3
        return abs(lum - 128)
    }

    // -- 자리 ---------------------------------------------------------------

    @Test
    fun `열두 장 모두 조용한 자리를 갖는다`() {
        CARD_TEMPLATES.forEach { t ->
            val q = foilQuietFor(t.id)
            assertNotNull("${t.id}", q)
            assertEquals("${t.id} 큰 얼굴", t.face, q!!.face)
            assertEquals("${t.id} 아바타", t.avatar, q.avatar)
        }
    }

    @Test
    fun `모르는 카드면 예전처럼 전체를 덮는다`() {
        assertNull(foilQuietFor("없는카드"))
    }

    /** 반지름을 폭에서만 재는 전제. 깨지면 두 구멍을 타원으로 만들어야 한다. */
    @Test
    fun `열두 장 모두 구멍이 화면에서 정원이다`() {
        CARD_TEMPLATES.forEach { t ->
            listOf("얼굴" to t.face, "아바타" to t.avatar).forEach { (what, h) ->
                assertEquals(
                    "${t.id} 의 $what 이 타원이다",
                    1080f * h.rx / 100f,
                    1440f * h.ry / 100f,
                    1.5f,
                )
            }
        }
    }

    /**
     * 재우는 원이 **구멍보다 넓어야 한다.** 얼굴은 구멍보다 크게 그려서 테두리가
     * 얼굴 가장자리를 물게 돼 있어서(`CardSlots.kt`), 딱 구멍만큼만 재우면 그 물린
     * 자리에 포일이 남아 얼굴에 링이 생긴다.
     */
    @Test
    fun `재우는 원이 구멍보다 넓다`() {
        assertTrue(FoilQuiet(middle, farHole).spread > 1.15f)
    }

    // -- 그림 ---------------------------------------------------------------

    @Test
    fun `얼굴 자리에서 포일이 약해진다`() {
        val bare = render(null)
        val quiet = render(FoilQuiet(middle, farHole))

        val before = shift(bare, side / 2, side / 2)
        val after = shift(quiet, side / 2, side / 2)

        assertTrue("포일이 애초에 안 보인다 (before=$before)", before > 4)
        assertTrue("얼굴이 안 약해졌다 (before=$before, after=$after)", after < before / 2)
    }

    /** 얼굴 밖은 그대로여야 한다. 여기까지 죽으면 포일을 끈 것과 같다. */
    @Test
    fun `얼굴 밖은 그대로 반짝인다`() {
        val bare = render(null)
        val quiet = render(FoilQuiet(middle, farHole))

        assertEquals("모서리가 달라졌다", shift(bare, 4, 4), shift(quiet, 4, 4))
    }

    /**
     * ⚠️ 여기서 한 번 틀렸다. 두 원을 `DstIn` 으로 **바로** 그리면 뒤에 그린 원이 앞의
     * 것을 지워서 **한 자리만 남는다.** 마스크를 자기 레이어 안에서 만들어야 한다.
     */
    @Test
    fun `얼굴 둘이 같이 조용해진다`() {
        val bare = render(null)
        val a = Hole(cx = 25f, cy = 25f, rx = 9f, ry = 6.75f)
        val b = Hole(cx = 75f, cy = 75f, rx = 9f, ry = 6.75f)
        val quiet = render(FoilQuiet(face = a, avatar = b))

        listOf(a, b).forEach { h ->
            val x = (side * h.cx / 100f).toInt()
            val y = (side * h.cy / 100f).toInt()
            assertTrue(
                "(${h.cx}, ${h.cy}) 가 안 약해졌다 — 하나만 남았다",
                shift(quiet, x, y) < shift(bare, x, y) / 2,
            )
        }
    }

    /** 경계가 **딱딱하면 안 된다.** 얼굴에 오려 붙인 자국이 생긴다. */
    @Test
    fun `가장자리로 갈수록 서서히 돌아온다`() {
        val bare = render(null)
        val quiet = render(FoilQuiet(middle, farHole))

        val c = shift(quiet, side / 2, side / 2)
        // 중심 → 원 끝 → 그 바깥 순으로 포일이 되살아나야 한다.
        val edgePx = (side * middle.rx / 100f * FoilQuiet(middle, farHole).spread).toInt()
        val mid = shift(quiet, side / 2 + edgePx * 3 / 4, side / 2)
        val out = shift(quiet, side / 2 + edgePx + 6, side / 2)

        assertTrue("가운데가 가장자리보다 세다 (c=$c mid=$mid)", c <= mid)
        assertTrue("바깥이 안 돌아왔다 (mid=$mid out=$out)", mid <= out)
        assertTrue("바깥이 원래와 다르다", out >= shift(bare, side / 2 + edgePx + 6, side / 2) - 2)
    }

    @Test
    fun `세기가 0 이면 아무것도 안 한다`() {
        val bare = render(null)
        val quiet = render(FoilQuiet(middle, farHole, strength = 0f))

        assertEquals(shift(bare, side / 2, side / 2), shift(quiet, side / 2, side / 2))
    }
}
