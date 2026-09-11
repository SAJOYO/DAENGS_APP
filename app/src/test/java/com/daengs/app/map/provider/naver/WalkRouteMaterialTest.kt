package com.daengs.app.map.provider.naver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkRouteMaterialTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun render(size: Int, base: Int = Color.TRANSPARENT): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(base)
        requireNotNull(context.getDrawable(R.drawable.ic_walk_route_pattern)).apply {
            setBounds(0, 0, size, size)
            draw(canvas)
        }
        return bitmap
    }

    @Test
    fun `Android가 그라데이션 벡터를 투명도를 가진 패턴으로 그린다`() {
        // Naver's bytecode cannot load in this JVM runner; this covers Android resource rendering only.
        val bitmap = render(14)
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        assertTrue(Color.alpha(bitmap.getPixel(bitmap.width / 2, 0)) in 1..200)
        // Export the actual Android-rendered resource for the design board, not a second drawing.
        val dir = File("build/route-material-preview").apply { mkdirs() }
        File(dir, "android-tile.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "material-tile.png").outputStream().use { render(224).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `원통의 가운데가 양끝보다 밝고 바탕 속도색도 구별된다`() {
        val red = render(56, Color.rgb(189, 60, 121))
        val blue = render(56, Color.rgb(42, 105, 189))
        fun brightness(pixel: Int) = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
        val center = red.getPixel(22, 2)
        assertTrue(brightness(center) > brightness(red.getPixel(1, 2)))
        assertTrue(brightness(center) > brightness(red.getPixel(54, 2)))
        assertTrue(Color.red(center) > Color.blue(center))
        val blueCenter = blue.getPixel(22, 2)
        assertTrue(Color.blue(blueCenter) > Color.red(blueCenter))
    }
}
