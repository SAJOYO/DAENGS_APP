package com.daengs.app.map.provider.naver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.R
import com.daengs.app.miniroom.art.DogBreed
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocationAvatarTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun render(photo: Bitmap? = null, res: Int? = R.drawable.ic_location_paw) =
        locationAvatarBitmap(context, photo, res, 96, 5f)

    @Test
    fun `늦게 도착한 사진은 발바닥을 대체하고 삭제하면 발바닥으로 돌아간다`() {
        val fallback = requireNotNull(render())
        val photo = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val loaded = requireNotNull(render(photo))
        assertEquals(Color.RED, loaded.getPixel(48, 48))
        assertEquals(Color.WHITE, loaded.getPixel(48, 1))
        assertEquals(Color.TRANSPARENT, loaded.getPixel(0, 0))
        assertFalse(loaded.sameAs(fallback))
        assertTrue(requireNotNull(render()).sameAs(fallback))
    }

    @Test
    fun `잘못된 리소스와 해제된 사진도 로컬 발바닥으로 복구된다`() {
        val recycled = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { recycle() }
        val fallback = requireNotNull(render())
        assertTrue(requireNotNull(render(res = 0)).sameAs(fallback))
        assertTrue(requireNotNull(render(recycled, 0)).sameAs(fallback))
    }

    @Test
    fun `패키징된 모든 견종 얼굴이 실제로 디코딩된다`() {
        DogBreed.entries.forEach { breed ->
            val bitmap = circularAvatarBitmap(context, breed.portraitRes, 96, 5f)
            assertNotNull(breed.name, bitmap)
            assertEquals(breed.name, 255, Color.alpha(requireNotNull(bitmap).getPixel(48, 48)))
        }
    }

    @Test
    fun `얼굴을 요청하지 않는 지도는 SDK 기본 아이콘으로 돌아간다`() {
        assertNull(render(res = null))
    }
}
