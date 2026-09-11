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

    @Test fun `공유 원본이어도 미인증과 인증의 합성 그림은 구분된다`() {
        val neutral = territoryMarkerIcon(context, TerritoryMarkerOccupancy.NEUTRAL)
        val unverified = territoryMarkerIcon(context, TerritoryMarkerOccupancy.UNVERIFIED)
        val verified = territoryMarkerIcon(context, TerritoryMarkerOccupancy.VERIFIED)
        assertEquals(TerritoryPoleArt.resource(TerritoryMarkerOccupancy.UNVERIFIED),
            TerritoryPoleArt.resource(TerritoryMarkerOccupancy.VERIFIED))
        assertFalse(unverified.sameAs(verified))
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
            assertEquals(76, Color.alpha(bitmap.getPixel(218, 575)))
            if (state == TerritoryMarkerOccupancy.NEUTRAL) {
                assertEquals(0, Color.alpha(bitmap.getPixel(58, 583)))
            }
            assertEquals(0, Color.alpha(bitmap.getPixel(220, 540)))
            val small = Bitmap.createScaledBitmap(bitmap, 48, 120, true)
            val visibleShadow = (100 until 119).sumOf { y ->
                (36 until 47).count { x -> Color.alpha(small.getPixel(x, y)) >= 40 }
            }
            assertTrue("실제 표시 크기에서도 밑동 밖 그림자가 남아야 한다", visibleShadow >= 8)
        }
        assertEquals(48 to 120, TerritoryPoleArt.size())
    }

    @Test fun `실제 지도 크기에서 본체가 상태색으로 빛나고 바닥 발광은 없다`() {
        val icons = TerritoryMarkerOccupancy.entries.associateWith { state ->
            val bitmap = territoryMarkerIcon(context, state)
            for (x in 0 until bitmap.width) {
                assertEquals(0, Color.alpha(bitmap.getPixel(x, bitmap.height - 1)))
            }
            for (y in 0 until bitmap.height) {
                assertEquals(0, Color.alpha(bitmap.getPixel(0, y)))
                assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, y)))
            }
            assertEquals("본체에서 떨어진 바닥에는 발광을 깔지 않는다", 0, Color.alpha(bitmap.getPixel(50, 602)))
            Bitmap.createScaledBitmap(bitmap, 48, 120, true)
        }
        fun count(state: TerritoryMarkerOccupancy, xs: IntRange, ys: IntRange, predicate: (Int) -> Boolean) =
            ys.sumOf { y -> xs.count { x -> predicate(icons.getValue(state).getPixel(x, y)) } }
        val orange: (Int) -> Boolean = { Color.alpha(it) > 40 && Color.red(it) > Color.green(it) + 40 && Color.green(it) > Color.blue(it) + 40 }
        val mint: (Int) -> Boolean = { Color.alpha(it) > 40 && Color.green(it) > Color.red(it) + 60 && Color.blue(it) > Color.red(it) + 40 }
        assertEquals(0, count(TerritoryMarkerOccupancy.NEUTRAL, 20..27, 65..95, orange))
        assertTrue("미인증 본체의 중간 높이가 주황색이어야 한다", count(TerritoryMarkerOccupancy.UNVERIFIED, 20..27, 65..95, orange) >= 30)
        assertTrue("인증 본체의 중간 높이가 민트색이어야 한다", count(TerritoryMarkerOccupancy.VERIFIED, 20..27, 65..95, mint) >= 30)
        val white: (Int) -> Boolean = { Color.alpha(it) > 200 && Color.red(it) > 230 && Color.green(it) > 230 && Color.blue(it) > 230 }
        assertTrue("전봇대 자체의 밝은 픽셀과 별개로 인증 체크의 흰 획이 남는다",
            count(TerritoryMarkerOccupancy.VERIFIED, 34..40, 50..56, white) >=
                count(TerritoryMarkerOccupancy.UNVERIFIED, 34..40, 50..56, white) + 5)
    }

    @Test fun `준비는 크기가 고정되고 성공 효과는 세로 비율과 접점을 유지한다`() {
        for (kind in TerritoryFeedbackKind.entries) for (step in 0..10) {
            val frame = territoryFeedbackFrame(kind, step / 10f)
            val (width, height) = TerritoryPoleArt.size(frame.markerScale)
            if (kind == TerritoryFeedbackKind.READY) assertEquals(48 to 120, width to height)
            assertEquals(2.5, height.toDouble() / width, .02)
            assertTrue(width >= 48)
            assertTrue(height <= 143)
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
                val (width, height) = TerritoryPoleArt.size()
                val x = 100f + col * 200
                val top = footY - height * TerritoryPoleArt.ANCHOR_Y
                canvas.drawBitmap(bitmap, null, RectF(x - width / 2f, top, x + width / 2f, top + height), paint)
                paint.color = if (row == 0) Color.GRAY else Color.LTGRAY
                canvas.drawLine(x - 4, footY, x + 4, footY, paint)
            }
        }
        val file = File("build/reports/territory-pole/native-sizes.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
