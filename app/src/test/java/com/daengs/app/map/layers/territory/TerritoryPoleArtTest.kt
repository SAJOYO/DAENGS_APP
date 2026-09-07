package com.daengs.app.map.layers.territory

import android.app.Application
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TerritoryPoleArtTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `미인증과 인증은 같은 점령 그림을 쓰고 기본과 구분된다`() {
        val neutral = territoryMarkerIcon(context, TerritoryMarkerOccupancy.NEUTRAL)
        val unverified = territoryMarkerIcon(context, TerritoryMarkerOccupancy.UNVERIFIED)
        val verified = territoryMarkerIcon(context, TerritoryMarkerOccupancy.VERIFIED)
        assertTrue(unverified.sameAs(verified))
        assertFalse(neutral.sameAs(unverified))
        fun baseBrightness(bitmap: Bitmap): Double = (560 until 605).flatMap { y ->
            (100 until 160).map { x -> bitmap.getPixel(x, y) }
        }.map { .299 * Color.red(it) + .587 * Color.green(it) + .114 * Color.blue(it) }.average()
        assertTrue("축소 전부터 점령 밑동이 명확히 어두워야 한다", baseBrightness(neutral) - baseBrightness(unverified) > 35)
    }

    @Test fun `투명 배경과 밑동 접점이 두 상태에서 유지된다`() {
        TerritoryMarkerOccupancy.entries.forEach { state ->
            val bitmap = BitmapFactory.decodeResource(context.resources, TerritoryPoleArt.resource(state))
            assertEquals(TerritoryPoleArt.WIDTH, bitmap.width)
            assertEquals(TerritoryPoleArt.HEIGHT, bitmap.height)
            for (y in 0 until bitmap.height) {
                assertEquals(0, Color.alpha(bitmap.getPixel(0, y)))
                assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, y)))
            }
            assertEquals(0, Color.alpha(bitmap.getPixel(128, 630)))
            assertTrue(Color.alpha(bitmap.getPixel(128, 622)) > 200)
            assertTrue("흰 포스터는 배경으로 지워지면 안 된다", Color.alpha(bitmap.getPixel(75, 330)) > 200)
        }
    }

    @Test fun `그림만 작아지고 옅은 사선 그림자는 밑동 오른쪽에 붙는다`() {
        TerritoryMarkerOccupancy.entries.forEach { state ->
            val bitmap = territoryMarkerIcon(context, state)
            val inkRows = (0 until bitmap.height).filter { y ->
                (0 until bitmap.width).any { x -> Color.alpha(bitmap.getPixel(x, y)) > 128 }
            }
            assertTrue("본체 높이는 이전 600px의 약 65%", inkRows.size in 380..395)
            assertTrue("축소해도 발은 같은 지리 좌표", inkRows.last() in 622..624)
            assertEquals(0, Color.alpha(bitmap.getPixel(128, 200)))
            assertEquals(46, Color.alpha(bitmap.getPixel(198, 583)))
            assertEquals(0, Color.alpha(bitmap.getPixel(58, 583)))
            assertEquals(0, Color.alpha(bitmap.getPixel(220, 560)))
        }
        assertEquals(48 to 120, TerritoryPoleArt.size(false))
        assertEquals(60 to 150, TerritoryPoleArt.size(true))
    }

    @Test fun `선택 및 성공 확대에서도 세로 비율과 접점이 유지된다`() {
        for (selected in listOf(false, true)) for (step in 0..10) {
            val frame = territoryFeedbackFrame(TerritoryFeedbackKind.MARKED, step / 10f)
            val (width, height) = TerritoryPoleArt.size(selected, frame.markerScale)
            assertEquals(2.5, height.toDouble() / width, .02)
            assertTrue(width >= if (selected) 60 else 48)
            assertTrue(height <= 178)
        }
        assertEquals(.5f, TerritoryPoleArt.ANCHOR_X)
        assertEquals(.975f, TerritoryPoleArt.ANCHOR_Y)
    }

    @Test fun `지도 픽셀 크기로 밝고 어두운 배경에 그려 비교한다`() {
        val output = Bitmap.createBitmap(600, 430, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(248, 246, 239))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.rgb(58, 65, 68)
        canvas.drawRect(0f, 215f, 600f, 430f, paint)
        for ((row, footY) in listOf(190f, 405f).withIndex()) {
            for ((col, state) in TerritoryMarkerOccupancy.entries.withIndex()) {
                val bitmap = territoryMarkerIcon(context, state)
                for ((variant, selected) in listOf(false, true).withIndex()) {
                    val (width, height) = TerritoryPoleArt.size(selected)
                    val x = 55f + col * 200 + variant * 85
                    val top = footY - height * TerritoryPoleArt.ANCHOR_Y
                    canvas.drawBitmap(bitmap, null, RectF(x - width / 2f, top, x + width / 2f, top + height), paint)
                    paint.color = if (row == 0) Color.GRAY else Color.LTGRAY
                    canvas.drawLine(x - 4, footY, x + 4, footY, paint)
                }
            }
        }
        val file = File("build/reports/territory-pole/native-sizes.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
