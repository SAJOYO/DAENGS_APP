package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntRect
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * **눈으로 보려고 찍는 것이다. 통과/실패를 따지지 않는다.**
 *
 * 실기기가 안 붙어 있을 때 카드가 지금 어떻게 나오는지 보려고 쓴다. 내보내기가 쓰는
 * [renderCard] 를 그대로 불러서 PNG 로 떨군다 — 공유 카드와 같은 그림이다.
 *
 * 얼굴은 **가짜다.** 진짜 누끼는 사진과 ML Kit 이 있어야 해서 여기서 못 만든다.
 * 구멍 테두리와 턱 밑 구멍만 보면 되므로 머리 모양만 흉내 낸다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardShotTest {
    @get:Rule val compose = createComposeRule()

    private val out = File(System.getProperty("daengs.shots") ?: "build/shots").apply { mkdirs() }

    @Test fun `카드를 찍어 둔다`() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val picked = listOf(TOMATO_CARD, KIWI_CARD, PEPPER_CARD, MUSHROOM_CARD)
        val shots = mutableMapOf<String, Bitmap>()

        compose.setContent {
            val measurer = rememberTextMeasurer()
            LaunchedEffect(Unit) {
                picked.forEach { template ->
                    val art = ctx.assets.open(template.art).use { BitmapFactory.decodeStream(it) }
                        .asImageBitmap()
                    // 판만 — 번호칩과 이름이 진짜 그대로 나온다.
                    shots["${template.id}-plate"] = renderCard(
                        art = art, template = template, face = null, measurer = measurer,
                        name = "나루", code = birthCode(8, 24), width = 1080,
                    )
                    // 가짜 얼굴을 끼운 것 — 구멍 테두리를 본다.
                    shots["${template.id}-face"] = renderCard(
                        art = art, template = template, face = fakeFace(), measurer = measurer,
                        name = "나루", code = birthCode(8, 24), width = 1080,
                    )
                }
            }
        }
        compose.waitForIdle()

        shots.forEach { (name, bmp) ->
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        println("찍은 장수: ${shots.size} → ${out.absolutePath}")
    }

    /**
     * 머리만 있고 **턱 아래가 없는** 누끼. 진짜 누끼가 꼭 그렇게 끝나고, 그 끝이
     * 구멍 안에 들어와서 카드에 구멍이 뚫렸다 (`GAP_PLUG` 가 막는 자리).
     */
    private fun fakeFace(): CardFace {
        val w = 512
        val h = 512
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val fur = android.graphics.Paint().apply { color = 0xFFD9B68A.toInt(); isAntiAlias = true }
        val dark = android.graphics.Paint().apply { color = 0xFF3A2B1E.toInt(); isAntiAlias = true }
        // 귀 둘
        c.drawOval(android.graphics.RectF(70f, 70f, 190f, 250f), fur)
        c.drawOval(android.graphics.RectF(322f, 70f, 442f, 250f), fur)
        // 머리 — 아래가 턱에서 끊긴다
        c.drawOval(android.graphics.RectF(96f, 96f, 416f, 430f), fur)
        // 눈·코
        c.drawOval(android.graphics.RectF(180f, 210f, 220f, 252f), dark)
        c.drawOval(android.graphics.RectF(292f, 210f, 332f, 252f), dark)
        c.drawOval(android.graphics.RectF(226f, 300f, 286f, 348f), dark)
        return CardFace(bmp.asImageBitmap(), IntRect(96, 96, 416, 430))
    }
}
