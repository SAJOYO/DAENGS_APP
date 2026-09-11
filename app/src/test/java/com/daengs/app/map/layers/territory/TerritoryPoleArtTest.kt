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

    @Test fun `미점유 본체 크기와 옅은 사선 그림자는 유지된다`() {
        listOf(TerritoryMarkerOccupancy.NEUTRAL).forEach { state ->
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

    @Test fun `회원 소유와 인증 조합마다 형광 색이 구별되고 질감과 체크가 유지된다`() {
        val icons = TerritoryPoleStyle.entries.associateWith { style ->
            assertEquals(style, TerritoryPoleStyle.of(style.occupancy, style.isMine))
            val bitmap = territoryMarkerIcon(context, style.occupancy, style.isMine)
            for (x in 0 until bitmap.width) assertEquals(0, Color.alpha(bitmap.getPixel(x, bitmap.height - 1)))
            for (y in 0 until bitmap.height) {
                assertEquals(0, Color.alpha(bitmap.getPixel(0, y)))
                assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, y)))
            }
            assertTrue(Color.alpha(bitmap.getPixel(50, 602)) <= 16)
            Bitmap.createScaledBitmap(bitmap, 48, 120, true)
        }
        assertEquals(TerritoryPoleStyle.NEUTRAL, TerritoryPoleStyle.of(TerritoryMarkerOccupancy.NEUTRAL, true))
        val hues = mapOf<TerritoryPoleStyle, (Int) -> Boolean>(
            TerritoryPoleStyle.MINE_UNVERIFIED to { Color.blue(it) > Color.red(it) + 60 && Color.blue(it) > Color.green(it) + 35 },
            TerritoryPoleStyle.MINE_VERIFIED to { Color.green(it) > Color.red(it) + 60 && Color.green(it) > Color.blue(it) + 50 },
            TerritoryPoleStyle.OTHER_UNVERIFIED to { Color.red(it) > Color.green(it) + 40 && Color.green(it) > Color.blue(it) + 40 },
            TerritoryPoleStyle.OTHER_VERIFIED to { Color.red(it) > Color.green(it) + 80 && Color.red(it) > Color.blue(it) + 80 },
        )
        for ((style, hue) in hues) {
            val pixels = (65..95).sumOf { y -> (0 until 48).count { x ->
                val color = icons.getValue(style).getPixel(x, y)
                Color.alpha(icons.getValue(TerritoryPoleStyle.NEUTRAL).getPixel(x, y)) < 16 && Color.alpha(color) > 40 && hue(color)
            } }
            assertTrue("$style 윤곽 밖 상태색 픽셀: $pixels", pixels >= 30)
        }
        val reference = icons.getValue(TerritoryPoleStyle.OTHER_UNVERIFIED).getPixel(24, 80)
        for (style in hues.keys) for (channel in listOf<(Int) -> Int>(Color::red, Color::green, Color::blue)) {
            assertTrue("본체를 페인트로 덮지 않는다", kotlin.math.abs(channel(reference) - channel(icons.getValue(style).getPixel(24, 80))) < 65)
        }
        fun whitePixels(style: TerritoryPoleStyle) = (50..56).sumOf { y -> (34..40).count { x ->
            val c = icons.getValue(style).getPixel(x, y)
            Color.alpha(c) > 200 && Color.red(c) > 230 && Color.green(c) > 230 && Color.blue(c) > 230
        } }
        for (mine in listOf(true, false)) {
            assertTrue("내 것과 상대 것 모두 인증에는 체크를 붙인다",
                whitePixels(TerritoryPoleStyle.of(TerritoryMarkerOccupancy.VERIFIED, mine)) >=
                    whitePixels(TerritoryPoleStyle.of(TerritoryMarkerOccupancy.UNVERIFIED, mine)) + 5)
        }
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
            for ((col, style) in TerritoryPoleStyle.entries.withIndex()) {
                val bitmap = territoryMarkerIcon(context, style.occupancy, style.isMine)
                val (width, height) = TerritoryPoleArt.size()
                val x = 60f + col * 120
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
