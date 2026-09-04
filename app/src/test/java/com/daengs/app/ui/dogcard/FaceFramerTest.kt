package com.daengs.app.ui.dogcard

import android.graphics.Rect as AndroidRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 원 안에 얼굴을 맞추는 셈.
 *
 * **[nudged] 는 이전 값 위에 쌓는 것이다.** 부르는 쪽이 매번 같은 시작 값을 넘기면
 * 손가락을 움직여도 제자리인데, 화면에서는 "그림이 원래 안 움직이는 것" 과 구분이
 * 안 된다. 그래서 쌓이는 성질을 여기서 못 박아 둔다 (`FaceFrameStep` 이 그걸 어겼다).
 *
 * `initialFrame` 이 `android.graphics.Rect` 를 재므로 Robolectric 위에서 돈다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaceFramerTest {

    @Test
    fun `손짓을 이어서 주면 배율이 쌓인다`() {
        // 한 번에 1.1배씩 여덟 번. 1.1^8 = 2.14
        var frame = FaceFrame(1f, 0.5f, 0.5f)
        repeat(8) { frame = frame.nudged(dScale = 1.1f, dx = 0f, dy = 0f) }

        assertEquals(2.14f, frame.scale, 0.01f)
    }

    @Test
    fun `같은 시작 값에 계속 얹으면 한 번 준 것과 같다`() {
        // 박제된 값을 넘기던 버그의 모양. 여덟 번 줘도 1.1 에 머문다.
        val start = FaceFrame(1f, 0.5f, 0.5f)
        var last = start
        repeat(8) { last = start.nudged(dScale = 1.1f, dx = 0f, dy = 0f) }

        assertEquals(1.1f, last.scale, 0.001f)
    }

    @Test
    fun `끌면 자리도 쌓인다`() {
        var frame = FaceFrame(1f, 0.5f, 0.5f)
        repeat(5) { frame = frame.nudged(dScale = 1f, dx = 0.02f, dy = -0.03f) }

        assertEquals(0.60f, frame.cx, 0.001f)
        assertEquals(0.35f, frame.cy, 0.001f)
    }

    @Test
    fun `배율은 한계 안에 머문다`() {
        var big = FaceFrame(1f, 0.5f, 0.5f)
        repeat(40) { big = big.nudged(dScale = 1.2f, dx = 0f, dy = 0f) }
        assertEquals(FRAME_MAX_SCALE, big.scale, 0.001f)

        var small = FaceFrame(1f, 0.5f, 0.5f)
        repeat(40) { small = small.nudged(dScale = 0.8f, dx = 0f, dy = 0f) }
        assertEquals(FRAME_MIN_SCALE, small.scale, 0.001f)
    }

    @Test
    fun `원 밖으로 아주 나가지는 않는다`() {
        var frame = FaceFrame(1f, 0.5f, 0.5f)
        repeat(60) { frame = frame.nudged(dScale = 1f, dx = 0.1f, dy = -0.1f) }

        assertEquals(1.5f, frame.cx, 0.001f)
        assertEquals(-0.5f, frame.cy, 0.001f)
    }

    @Test
    fun `첫 틀은 또렷한 얼굴의 짧은 쪽을 원에 맞춘다`() {
        // 600x600 그림 한가운데에 200x300 얼굴. 짧은 쪽(200)이 원을 덮어야 한다.
        val core = AndroidRect(200, 150, 400, 450)
        val frame = initialFrame(600, 600, core)

        assertEquals(3f, frame.scale, 0.001f)
    }

    @Test
    fun `첫 틀은 얼굴 한가운데를 원 복판으로 민다`() {
        // 얼굴이 왼쪽 위로 치우쳐 있으면 오른쪽 아래로 밀어야 한다.
        val core = AndroidRect(50, 50, 250, 250)
        val frame = initialFrame(600, 600, core)

        assertTrue("치우친 얼굴을 오른쪽으로 밀어야 한다", frame.cx > 0.5f)
        assertTrue("치우친 얼굴을 아래로 밀어야 한다", frame.cy > 0.5f)
    }

    @Test
    fun `그림 크기가 없으면 가운데로 떨어진다`() {
        val frame = initialFrame(0, 0, AndroidRect(0, 0, 10, 10))

        assertEquals(FaceFrame.CENTER, frame)
    }
}
