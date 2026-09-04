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

    /** 화면 밖 구석에 둔 아바타. 창만 보고 싶은 시험에서 방해가 안 되게 한다. */
    private val farAvatar = Hole(cx = -50f, cy = -50f, rx = 1f, ry = 0.75f)

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
        CARD_TEMPLATES.forEach { assertNotNull("${it.id}", foilQuietFor(it.id)) }
    }

    @Test
    fun `모르는 카드면 예전처럼 전체를 덮는다`() {
        assertNull(foilQuietFor("없는카드"))
    }

    /**
     * **얼굴 구멍이 창 안에 들어와야 한다.** 삐져나오면 그 조각에만 포일이 남아서
     * 강아지 얼굴 한쪽만 누렇게 뜬다 — 화면에서는 "얼굴이 이상하다" 로만 보인다.
     * [ART_WINDOW] 를 손으로 맞춘 값이라 여기서 지킨다.
     */
    @Test
    fun `열두 장 모두 얼굴 구멍이 그림창 안에 있다`() {
        CARD_TEMPLATES.forEach { t ->
            val f = t.face
            assertTrue("${t.id} 왼쪽이 샌다", f.cx - f.rx >= ART_WINDOW.x0)
            assertTrue("${t.id} 오른쪽이 샌다", f.cx + f.rx <= ART_WINDOW.x1)
            assertTrue("${t.id} 위가 샌다", f.cy - f.ry >= ART_WINDOW.y0)
            assertTrue("${t.id} 아래가 샌다", f.cy + f.ry <= ART_WINDOW.y1)
        }
    }

    /**
     * 아바타를 창과 **따로** 더하는 이유. 이 폭이 0 에 가까워지면 창 하나로 덮을 수
     * 있다는 뜻이라 [FoilQuiet.avatar] 를 지워도 된다.
     */
    @Test
    fun `아바타는 카드마다 창 안팎으로 갈린다`() {
        val tops = CARD_TEMPLATES.map { it.avatar.cy - it.avatar.ry }
        assertTrue("아바타 위치가 고르다 (${tops.min()}~${tops.max()})", tops.max() - tops.min() > 2f)
    }

    /** 반지름을 폭에서만 재는 전제. 깨지면 아바타 원이 타원이 되어야 한다. */
    @Test
    fun `열두 장 모두 아바타 구멍이 화면에서 정원이다`() {
        CARD_TEMPLATES.forEach { t ->
            assertEquals(
                "${t.id} 의 아바타가 타원이다",
                1080f * t.avatar.rx / 100f,
                1440f * t.avatar.ry / 100f,
                1.5f,
            )
        }
    }

    // -- 그림 ---------------------------------------------------------------

    @Test
    fun `창 안에서 포일이 약해진다`() {
        val bare = render(null)
        val quiet = render(FoilQuiet(Slot25to75, farAvatar))

        val before = shift(bare, side / 2, side / 2)
        val after = shift(quiet, side / 2, side / 2)

        assertTrue("포일이 애초에 안 보인다 (before=$before)", before > 4)
        assertTrue("안쪽이 안 약해졌다 (before=$before, after=$after)", after < before / 2)
    }

    /** 창 밖은 그대로여야 한다. 여기까지 죽으면 포일을 끈 것과 같다. */
    @Test
    fun `창 밖은 그대로 반짝인다`() {
        val bare = render(null)
        val quiet = render(FoilQuiet(Slot25to75, farAvatar))

        assertEquals("모서리가 달라졌다", shift(bare, 4, 4), shift(quiet, 4, 4))
    }

    /**
     * ⚠️ 한 번 밟을 뻔한 자리다. 창과 아바타를 **따로 두 번 자르면** 두 번째 자르기가
     * 첫 번째를 대체해서 창이 통째로 사라진다. 한 [androidx.compose.ui.graphics.Path]
     * 에 넣어야 합집합이 된다.
     */
    @Test
    fun `아바타 자리도 같이 조용해진다`() {
        val bare = render(null)
        // 창은 오른쪽 아래 구석에 두고, 아바타만 왼쪽 위에 둔다.
        val quiet = render(
            FoilQuiet(
                window = com.daengs.app.ui.dogcard.Slot(80f, 80f, 95f, 95f),
                avatar = Hole(cx = 20f, cy = 20f, rx = 10f, ry = 7.5f),
            )
        )

        val x = (side * 0.20f).toInt()
        val y = (side * 0.20f).toInt()
        assertTrue("아바타 자리가 안 약해졌다", shift(quiet, x, y) < shift(bare, x, y) / 2)
        // 창도 살아 있어야 한다 — 두 번 자르면 여기가 안 죽는다.
        val wx = (side * 0.87f).toInt()
        assertTrue("창이 사라졌다", shift(quiet, wx, wx) < shift(bare, wx, wx) / 2)
    }

    @Test
    fun `세기가 0 이면 아무것도 안 한다`() {
        val bare = render(null)
        val quiet = render(FoilQuiet(Slot25to75, farAvatar, strength = 0f))

        assertEquals(shift(bare, side / 2, side / 2), shift(quiet, side / 2, side / 2))
    }

    private companion object {
        val Slot25to75 = com.daengs.app.ui.dogcard.Slot(25f, 25f, 75f, 75f)
    }
}
