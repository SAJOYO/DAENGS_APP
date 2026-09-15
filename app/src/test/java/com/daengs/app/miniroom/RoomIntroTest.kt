package com.daengs.app.miniroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홈 첫 진입 연출의 **시간표.**
 *
 * 그림이 예쁜지는 못 잡는다 — 그건 실기기에서 본다. 여기서 잠그는 것은 세 컷의 **순서**와
 * **끝나면 평소 그림으로 돌아오는가**다. 카메라가 안 빠지거나, 문이 안 닫히거나, 어둠이
 * 남으면 홈이 영영 이상한 채로 남는다.
 */
class RoomIntroTest {

    @Test
    fun `첫 프레임은 문 안에 들어가 있고 문이 열려 있고 크림 막이 덮여 있다`() {
        val f = IntroTimeline.at(0L, night = false)
        assertEquals(1f, f.pull, 1e-4f)
        assertEquals(1f, f.doorOpen, 1e-4f)
        assertEquals(1f, f.veil, 1e-4f)
        assertFalse("첫 프레임부터 마중을 보내면 안 된다", f.greet)
        assertTrue(f.active)
    }

    @Test
    fun `끝나면 모든 값이 평소 그대로다`() {
        val f = IntroTimeline.at(IntroTimeline.TOTAL_MS, night = true)
        assertEquals(IntroFrame.DONE, f)
        assertFalse(f.active)
        assertEquals(0f, f.pull, 0f)
        assertEquals(0f, f.doorOpen, 0f)
        assertEquals(0f, f.dark, 0f)
        assertEquals(0f, f.veil, 0f)
    }

    @Test
    fun `카메라는 뒤로만 빠진다 - 다시 들어가지 않는다`() {
        var prev = Float.MAX_VALUE
        for (e in 0L until IntroTimeline.TOTAL_MS step 10L) {
            val pull = IntroTimeline.at(e, night = false).pull
            assertTrue("$e ms 에서 카메라가 다시 들어갔다 ($prev → $pull)", pull <= prev + 1e-6f)
            prev = pull
        }
        assertEquals(0f, IntroTimeline.at(IntroTimeline.CAMERA_END_MS, false).pull, 1e-4f)
    }

    @Test
    fun `문은 카메라가 자리를 잡은 뒤에 닫힌다`() {
        // 카메라가 한창 빠지는 중에는 문이 열려 있어야 문밖이 보인다
        val mid = IntroTimeline.at((IntroTimeline.CAMERA_START_MS + IntroTimeline.CAMERA_END_MS) / 2, false)
        assertEquals(1f, mid.doorOpen, 1e-4f)
        assertEquals(0f, IntroTimeline.at(IntroTimeline.DOOR_CLOSE_END_MS, false).doorOpen, 1e-4f)
    }

    @Test
    fun `밤에는 딸깍 - 어둠이 단번에 떨어진 뒤 서서히 걷힌다`() {
        val before = IntroTimeline.at(IntroTimeline.LIGHTS_CLICK_MS - 1, night = true).dark
        val after = IntroTimeline.at(IntroTimeline.LIGHTS_CLICK_MS, night = true).dark
        assertEquals(IntroTimeline.NIGHT_DARK, before, 1e-4f)
        assertEquals(IntroTimeline.NIGHT_AFTER_CLICK, after, 1e-4f)
        assertTrue("딸깍은 계단이어야 한다", before - after > 0.3f)
        assertEquals(0f, IntroTimeline.at(IntroTimeline.LIGHTS_END_MS, night = true).dark, 1e-4f)
    }

    @Test
    fun `낮에는 어둠 막이 없다`() {
        for (e in 0L until IntroTimeline.TOTAL_MS step 50L) {
            assertEquals("$e ms", 0f, IntroTimeline.at(e, night = false).dark, 0f)
        }
    }

    @Test
    fun `마중 신호는 불이 켜지는 순간부터다`() {
        assertFalse(IntroTimeline.at(IntroTimeline.GREET_AT_MS - 1, false).greet)
        assertTrue(IntroTimeline.at(IntroTimeline.GREET_AT_MS, false).greet)
    }

    @Test
    fun `무장하기 전에는 아무것도 안 한다`() {
        val intro = RoomIntro()
        assertFalse(intro.active)
        assertEquals(IntroFrame.DONE, intro.frameAt(5_000L, night = false))
        assertFalse("무장 안 했는데 마중을 보냈다", intro.takeGreet(IntroFrame.DONE))
    }

    /** [from] 부터 [to] 까지 16ms 프레임으로 시계를 돌리고 마지막 프레임을 준다. */
    private fun RoomIntro.play(from: Long, to: Long, night: Boolean = false): IntroFrame {
        var f = frameAt(from, night)
        var t = from
        while (t < to) {
            t = minOf(t + 16L, to)
            f = frameAt(t, night)
        }
        return f
    }

    @Test
    fun `시간은 처음 그려진 프레임부터 잰다 - 무장한 시점이 아니다`() {
        val intro = RoomIntro()
        intro.arm()
        // 무장 뒤 한참 있다가 첫 프레임이 와도 첫 컷이다
        val first = intro.frameAt(nowMs = 90_000L, night = false)
        assertEquals(1f, first.pull, 1e-4f)
        val later = intro.play(90_000L, 90_000L + IntroTimeline.CAMERA_END_MS)
        assertEquals(0f, later.pull, 1e-4f)
    }

    @Test
    fun `길이가 지나면 끝나고 그 뒤로는 계속 끝난 채다`() {
        val intro = RoomIntro()
        intro.arm()
        intro.frameAt(T0, false)
        assertTrue(intro.active)
        assertEquals(IntroFrame.DONE, intro.play(T0, T0 + IntroTimeline.TOTAL_MS))
        assertFalse(intro.active)
        assertEquals(IntroFrame.DONE, intro.frameAt(T0 + IntroTimeline.TOTAL_MS + 5L, false))
    }

    @Test
    fun `시계가 아직 0 이면 시작으로 안 치고 첫 컷을 그린다`() {
        // 프레임 시계는 첫 콜백 전까지 0 이다. 0 을 시작으로 잡으면 다음 프레임의 진짜
        // 시각이 통째로 "경과" 가 되어 연출이 한 프레임 만에 끝난다 — 실기기에서 겪었다.
        val intro = RoomIntro()
        intro.arm()
        val beforeClock = intro.frameAt(0L, false)
        assertEquals(1f, beforeClock.pull, 1e-4f)
        assertTrue(intro.active)
        val first = intro.frameAt(50_000_000L, false)
        assertEquals("진짜 첫 프레임이 시작이어야 한다", 1f, first.pull, 1e-4f)
        assertTrue(intro.active)
        val later = intro.play(50_000_000L, 50_000_000L + IntroTimeline.CAMERA_END_MS)
        assertEquals(0f, later.pull, 1e-4f)
    }

    @Test
    fun `프레임이 밀려도 한 프레임에 조금만 간다 - 카메라가 튀지 않는다`() {
        // 홈이 처음 그려질 때 한 프레임이 수백 ms 걸린다. 실제 시각을 그대로 쓰면 그 한
        // 프레임에 카메라가 제자리까지 튀어 버린다 — 실기기에서 첫 컷에 멈춰 있다가
        // 한 번에 튀는 걸로 보였다.
        val intro = RoomIntro()
        intro.arm()
        intro.frameAt(T0, false)
        val afterJank = intro.frameAt(T0 + 400L, false)
        assertEquals(
            "밀린 400ms 가 통째로 경과가 되면 안 된다",
            IntroTimeline.at(IntroTimeline.MAX_STEP_MS, false).pull,
            afterJank.pull,
            1e-4f,
        )
        assertTrue(intro.active)
        // 같은 프레임에서 두 번 읽어도(캔버스 + 크림 막) 시계는 한 번만 간다
        assertEquals(afterJank, intro.frameAt(T0 + 400L, false))
    }

    @Test
    fun `건너뛰면 즉시 끝나지만 마중 신호는 그대로 나간다`() {
        val intro = RoomIntro()
        intro.arm()
        intro.frameAt(T0, false)
        intro.skip()
        assertFalse(intro.active)
        val f = intro.frameAt(T0 + 50L, false)
        assertEquals(IntroFrame.DONE, f)
        assertTrue("건너뛰어도 강아지는 반겨야 한다", intro.takeGreet(f))
    }

    @Test
    fun `마중 신호는 한 번만 나간다`() {
        val intro = RoomIntro()
        intro.arm()
        val early = intro.play(T0, T0 + IntroTimeline.GREET_AT_MS - 16)
        assertFalse(intro.takeGreet(early))
        val due = intro.play(T0 + IntroTimeline.GREET_AT_MS - 16, T0 + IntroTimeline.GREET_AT_MS)
        assertTrue(intro.takeGreet(due))
        assertFalse("두 번째 프레임에서 또 나갔다", intro.takeGreet(due))
        assertFalse(intro.takeGreet(intro.play(T0 + IntroTimeline.GREET_AT_MS, T0 + IntroTimeline.TOTAL_MS)))
    }

    @Test
    fun `미리보기용은 시계를 무시하고 그 시각에 얼어붙는다`() {
        val intro = RoomIntro(previewElapsedMs = IntroTimeline.CAMERA_START_MS)
        val a = intro.frameAt(0L, false)
        val b = intro.frameAt(99_999L, false)
        assertEquals(a, b)
        assertTrue(a.active)
        assertFalse("미리보기가 강아지를 움직이면 안 된다", intro.takeGreet(intro.frameAt(TOTAL_PLUS, false)))
    }

    @Test
    fun `문 앞 자리는 바닥 안이고 왼쪽 모서리에서 한 걸음 안쪽이다`() {
        val g = RoomGeometry.of(1080f, 1080f / RoomSpec.ASPECT)
        val spot = g.doorstep()
        assertTrue("col ${spot.x}", spot.x in 0.6f..(RoomSpec.GRID - 0.6f))
        assertTrue("row ${spot.y}", spot.y in 0.6f..(RoomSpec.GRID - 0.6f))
        // 문은 왼쪽 벽이라 col 은 작고 row 는 크다
        assertTrue("문 앞이 아니다: $spot", spot.x < RoomSpec.GRID / 2f && spot.y > RoomSpec.GRID / 2f)
    }

    private companion object {
        const val TOTAL_PLUS = IntroTimeline.TOTAL_MS + 1

        /** 프레임 시계가 이미 도는 상태의 임의 시각. 0 은 "아직 안 돈 것" 이라 못 쓴다. */
        const val T0 = 12_345L
    }
}
