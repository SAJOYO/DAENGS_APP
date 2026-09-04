package com.daengs.app.ui.pet

import com.daengs.app.ui.dogcard.FRAME_MAX_SCALE
import com.daengs.app.ui.dogcard.FaceFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * 사진이 **원을 늘 덮는가.**
 *
 * 카드 뽑기의 원형 틀은 자유로워도 된다 — 거기 들어가는 것은 누끼라 둘레가 원래
 * 비어 있다. **프로필은 사진 한 장이 통째로 들어가는 자리**라, 빠져나가면 그 자리에
 * 아무것도 없다. 화면에서는 "왜 여백이 생기지" 로만 보인다.
 */
class CoverFrameTest {

    /** 그 배율·자리에서 사진이 원(1x1)을 덮는가. */
    private fun covers(frame: FaceFrame, w: Int, h: Int): Boolean {
        val long = max(w, h).toFloat()
        val fw = w / long * frame.scale
        val fh = h / long * frame.scale
        val left = frame.cx - fw / 2f
        val top = frame.cy - fh / 2f
        return left <= 0.001f && left + fw >= 0.999f && top <= 0.001f && top + fh >= 0.999f
    }

    @Test
    fun `정사각형 사진은 그대로 원을 덮는다`() {
        val frame = coverFrame(FaceFrame.CENTER, 800, 800)

        assertEquals(1f, frame.scale, 0.001f)
        assertTrue(covers(frame, 800, 800))
    }

    @Test
    fun `세로로 긴 사진은 키워서 덮는다`() {
        // 3:4 사진을 배율 1 로 두면 좌우가 빈다.
        val frame = coverFrame(FaceFrame.CENTER, 600, 800)

        assertTrue("좌우가 비면 안 된다", covers(frame, 600, 800))
        assertEquals(800f / 600f, frame.scale, 0.001f)
    }

    @Test
    fun `너무 줄이려 해도 원은 채워진다`() {
        val frame = coverFrame(FaceFrame(scale = 0.35f, cx = 0.5f, cy = 0.5f), 900, 1200)

        assertTrue(covers(frame, 900, 1200))
    }

    @Test
    fun `너무 밀어도 가장자리가 안 빈다`() {
        listOf(
            FaceFrame(1.5f, cx = 1.4f, cy = 0.5f),
            FaceFrame(1.5f, cx = -0.4f, cy = 0.5f),
            FaceFrame(1.5f, cx = 0.5f, cy = 1.4f),
            FaceFrame(1.5f, cx = 0.5f, cy = -0.4f),
        ).forEach { pushed ->
            assertTrue("$pushed", covers(coverFrame(pushed, 1000, 1000), 1000, 1000))
        }
    }

    @Test
    fun `덮는 안에서는 사용자가 민 자리를 그대로 둔다`() {
        // 가둔다고 늘 가운데로 되돌리면 맞추는 것 자체가 안 된다.
        val moved = FaceFrame(scale = 2f, cx = 0.6f, cy = 0.45f)

        val frame = coverFrame(moved, 1000, 1000)

        assertEquals(0.6f, frame.cx, 0.001f)
        assertEquals(0.45f, frame.cy, 0.001f)
        assertEquals(2f, frame.scale, 0.001f)
    }

    @Test
    fun `아주 길쭉한 사진은 최대까지만 키운다`() {
        // 파노라마. 덮을 수가 없으므로 한계까지만 키우고 바탕색이 메운다
        // (`bakeProfile` 의 크림색). 여기서 무한정 키우면 화소가 뭉갠다.
        val frame = coverFrame(FaceFrame.CENTER, 4000, 400)

        assertEquals(FRAME_MAX_SCALE, frame.scale, 0.001f)
        // 못 덮는 쪽은 가운데에 세운다 — 한쪽에 몰리면 더 이상해 보인다.
        assertEquals(0.5f, frame.cy, 0.001f)
    }

    @Test
    fun `크기를 모르면 아무것도 안 한다`() {
        val untouched = FaceFrame(1.2f, 0.4f, 0.4f)

        assertEquals(untouched, coverFrame(untouched, 0, 0))
    }

    @Test
    fun `배율 한계를 넘지 않는다`() {
        val frame = coverFrame(FaceFrame(scale = 99f, cx = 0.5f, cy = 0.5f), 800, 800)

        assertTrue(frame.scale <= FRAME_MAX_SCALE)
        assertEquals(min(99f, FRAME_MAX_SCALE), frame.scale, 0.001f)
    }
}
