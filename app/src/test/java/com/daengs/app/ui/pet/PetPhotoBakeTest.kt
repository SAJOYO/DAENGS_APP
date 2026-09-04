package com.daengs.app.ui.pet

import android.graphics.Bitmap
import android.graphics.Color
import com.daengs.app.pet.PetPhotos
import com.daengs.app.ui.dogcard.FaceFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 맞춘 사진을 굽는 자리.
 *
 * **`bakeFramed` 는 원본 밖을 투명으로 남긴다** — 카드 구멍에 끼우려고 그렇게 만든
 * 것이라 그쪽에서는 맞다. 그런데 프로필은 JPEG 으로 저장하고 **JPEG 에는 알파가
 * 없어서**, 사용자가 작게 줄이면 빈 가장자리가 그대로 **까맣게** 나온다.
 *
 * 화면에서 보면 "왜 테두리가 검지" 로만 보이고 원인이 안 보인다. 여기서 잡는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetPhotoBakeTest {

    private fun source(): Bitmap =
        Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    @Test
    fun `작게 줄여도 가장자리가 비지 않는다`() {
        // 원본이 원의 절반만 덮는 자리. 모서리는 원본 밖이다.
        val baked = bakeProfile(source(), FaceFrame(scale = 0.4f, cx = 0.5f, cy = 0.5f))

        listOf(0 to 0, baked.width - 1 to 0, 0 to baked.height - 1, baked.width - 1 to baked.height - 1)
            .forEach { (x, y) ->
                val pixel = baked.getPixel(x, y)
                assertEquals("($x, $y) 가 불투명해야 한다", 255, Color.alpha(pixel))
                assertTrue("($x, $y) 가 까맣지 않아야 한다", Color.red(pixel) > 200)
            }
    }

    @Test
    fun `가운데는 사진 그대로다`() {
        val baked = bakeProfile(source(), FaceFrame(scale = 0.4f, cx = 0.5f, cy = 0.5f))

        val middle = baked.getPixel(baked.width / 2, baked.height / 2)
        assertEquals(Color.RED, middle)
    }

    @Test
    fun `정해진 크기로 굽는다`() {
        val baked = bakeProfile(source(), FaceFrame.CENTER)

        assertEquals(PetPhotos.SIDE, baked.width)
        assertEquals(PetPhotos.SIDE, baked.height)
    }
}
