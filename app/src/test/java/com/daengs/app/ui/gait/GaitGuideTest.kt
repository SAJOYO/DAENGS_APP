package com.daengs.app.ui.gait

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 촬영 가이드의 치수 ([GaitGuide]).
 *
 * 그림이 시안처럼 보이는지는 여기서 못 잡는다 — 그건 에뮬레이터에서 본다. 여기서
 * 잡는 것은 **어느 화면에서도 틀 비율이 안 깨지고, 정한 자리(개선안 1 × [GaitGuide.SCALE])를
 * 넘지 않는다**는 것이다. 예전 구현은 프리뷰 폭과 높이를 따로 곱해서, 프리뷰가 길쭉할수록
 * 틀과 실루엣이 세로로 늘어났다. 그 방식으로 돌아가면 첫 테스트가 먼저 깨진다.
 */
class GaitGuideTest {

    /**
     * 에뮬레이터(1080×2400) 촬영 화면의 프리뷰 1016×1632px, 시안 프리뷰 503×756px,
     * 더 길쭉한 화면, 가로로 누운 화면, 아주 작은 화면.
     */
    private val previews = listOf(
        1016f to 1632f,
        503f to 756f,
        1056f to 1900f,
        1632f to 1016f,
        320f to 360f,
    )

    private val widthLimit = GaitGuide.WIDTH_SHARE * GaitGuide.SCALE
    private val heightLimit = GaitGuide.HEIGHT_SHARE * GaitGuide.SCALE

    @Test
    fun `어느 크기에서도 틀 비율은 개선안 2 그대로다`() {
        previews.forEach { (w, h) ->
            val frame = GaitGuide.frame(w, h)
            assertEquals("${w}x$h", GaitGuide.FRAME_ASPECT, frame.height / frame.width, 0.001f)
        }
    }

    @Test
    fun `틀은 프리뷰 한가운데에 있다`() {
        previews.forEach { (w, h) ->
            val frame = GaitGuide.frame(w, h)
            assertEquals("${w}x$h 가로", w / 2f, frame.center.x, 0.01f)
            assertEquals("${w}x$h 세로", h / 2f, frame.center.y, 0.01f)
        }
    }

    @Test
    fun `정한 자리를 넘지 않고 한쪽은 꼭 닿는다`() {
        previews.forEach { (w, h) ->
            val frame = GaitGuide.frame(w, h)
            val widthShare = frame.width / w
            val heightShare = frame.height / h
            assertTrue("${w}x$h 폭 $widthShare", widthShare <= widthLimit + 1e-4f)
            assertTrue("${w}x$h 높이 $heightShare", heightShare <= heightLimit + 1e-4f)
            // 둘 다 모자라면 쓸 수 있는 자리를 남긴 채 작게 그린 것이다.
            assertTrue(
                "${w}x$h 는 어느 쪽에도 안 닿았다",
                abs(widthShare - widthLimit) < 1e-3f || abs(heightShare - heightLimit) < 1e-3f,
            )
        }
    }

    @Test
    fun `틀은 프리뷰 밖으로 안 나간다`() {
        previews.forEach { (w, h) ->
            val frame = GaitGuide.frame(w, h)
            assertTrue("${w}x$h", frame.left >= 0f && frame.top >= 0f && frame.right <= w && frame.bottom <= h)
        }
    }

    @Test
    fun `세로 화면에서는 폭이 크기를 정한다`() {
        // 에뮬레이터 프리뷰. 폭 1016 × 0.467 × 1.1 = 522px, 높이는 그 1.774배 = 926px 로
        // 높이 한도(1632 × 0.590 × 1.1 = 1059px)보다 작다.
        val frame = GaitGuide.frame(1016f, 1632f)
        assertEquals(1016f * widthLimit, frame.width, 0.01f)
        assertTrue(frame.height < 1632f * heightLimit)
    }

    /**
     * 이 치수를 고른 이유를 잡는다. 시안 프리뷰 크기에 그리면 틀 폭과 강아지 키가
     * 개선안 1(틀 폭 235px · 강아지 분홍 채움 339px)의 정확히 [GaitGuide.SCALE] 배여야 한다.
     */
    @Test
    fun `시안 프리뷰에서 틀과 강아지가 개선안 1 의 SCALE 배다`() {
        val frame = GaitGuide.frame(503f, 756f)
        assertEquals("개선안 1 틀 폭 × SCALE", 235f * GaitGuide.SCALE, frame.width, 0.5f)

        // 강아지 상자는 윤곽 추적이라 흰 테두리까지 들어 있어 분홍 채움보다 조금 크다
        // (개선안 2 에서 319px → 321px). 같은 몫으로 환산해 비교한다.
        val dog = GaitGuide.dog(frame)
        assertEquals("개선안 1 강아지 키 × SCALE", 339f * 321f / 319f * GaitGuide.SCALE, dog.height, 3f)
    }

    @Test
    fun `강아지는 틀 안에 있고 코너 표시와 안 겹친다`() {
        val frame = GaitGuide.frame(1016f, 1632f)
        val dog = GaitGuide.dog(frame)
        assertTrue(dog.left >= frame.left && dog.right <= frame.right)
        assertTrue(dog.top >= frame.top && dog.bottom <= frame.bottom)

        val cornerReach = frame.width * (GaitGuide.CORNER_INSET + GaitGuide.CORNER_ARM)
        assertTrue("코너 표시가 강아지 옆구리를 덮는다", dog.left - frame.left > cornerReach)
        assertTrue("코너 표시가 꼬리 끝을 덮는다", dog.top - frame.top > cornerReach)
    }

    @Test
    fun `점선은 틀 한가운데를 지난다`() {
        previews.forEach { (w, h) ->
            val frame = GaitGuide.frame(w, h)
            val dog = GaitGuide.dog(frame)
            assertEquals("${w}x$h", frame.center.x, dog.left + dog.width * GaitGuide.SPINE_X, 0.05f)
        }
    }

    @Test
    fun `좌표는 모두 강아지 상자 0~1 안이다`() {
        assertEquals("윤곽 점 개수", 85, GaitGuide.OUTLINE.size / 2)
        (listOf(GaitGuide.OUTLINE) + GaitGuide.INNER_LINES).forEach { points ->
            assertEquals("x, y 가 짝이 안 맞는다", 0, points.size % 2)
            points.forEach { assertTrue("$it", it in 0f..1f) }
        }
        listOf(GaitGuide.LEFT_LEG_X, GaitGuide.RIGHT_LEG_X, GaitGuide.HIP_Y, GaitGuide.KNEE_Y, GaitGuide.PAW_Y)
            .forEach { assertTrue("$it", it in 0f..1f) }
    }

    @Test
    fun `관절점은 위에서부터 고관절 무릎 뒷발 순서다`() {
        assertTrue(GaitGuide.HIP_Y < GaitGuide.KNEE_Y && GaitGuide.KNEE_Y < GaitGuide.PAW_Y)
        assertTrue(GaitGuide.LEFT_LEG_X < GaitGuide.SPINE_X && GaitGuide.SPINE_X < GaitGuide.RIGHT_LEG_X)
    }
}
