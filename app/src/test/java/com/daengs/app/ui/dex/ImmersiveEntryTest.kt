package com.daengs.app.ui.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 이머시브 진입에서 **이름이 한 벌만 보이는지.**
 *
 * 이름은 두 군데가 그린다 — 녹고 있는 카드와 그 아래 창틀이다. 그리고 **두 이름칸은
 * 일부러 다른 사각형**이다: 창틀 값은 원화에서 직접 쟀고 카드 값과 왼쪽 끝이 3.8%
 * 어긋나 있다 ([ImmersiveScene.frameName]). 그래서 둘이 동시에 보이는 순간이 있으면
 * 이름이 **크기가 다른 두 벌**로 읽힌다.
 *
 * 실기기 녹화로 재 보니 진입 시작 `t≈1167ms` 부터 `t≈1367ms` 까지 약 200ms 동안
 * 그랬다. 눈으로만 보면 "글씨가 살짝 어긋난 것 같다" 정도로 지나가는 자리라, 겹치는
 * 구간이 0 이라는 것을 여기서 못 박아 둔다.
 *
 * **알파를 같이 올리는 크로스페이드로 고치면 이 테스트가 깨진다.** 중간에서 둘 다
 * 반쯤 보이는 것은 똑같기 때문이고, 그게 이 테스트의 쓸모다.
 */
class ImmersiveEntryTest {

    /** 진입 연출을 1ms 씩 훑는다. `ENTER_MS` 와 같은 길이다. */
    private val steps = 0..2100

    @Test
    fun `카드와 창틀 글자가 동시에 보이는 순간이 없다`() {
        val overlapped = steps.filter { ms ->
            val melt = meltAt(ms / 2100f)
            cardShowsAt(melt) && frameTextShowsAt(melt)
        }

        assertTrue("겹치는 구간이 있다: ${overlapped.firstOrNull()}ms 부터", overlapped.isEmpty())
    }

    @Test
    fun `이름이 아무도 안 그리는 순간도 없다`() {
        // 겹침을 없애려고 창틀 글자를 늦추다 보면 반대로 빈 구간이 생긴다.
        // 카드가 그려지는 마지막 순간과 창틀 글자가 나오는 첫 순간이 붙어 있어야 한다.
        val blank = steps.filter { ms ->
            val melt = meltAt(ms / 2100f)
            !cardShowsAt(melt) && !frameTextShowsAt(melt)
        }

        assertTrue("이름이 빈 구간이 있다: ${blank.firstOrNull()}ms 부터", blank.isEmpty())
    }

    @Test
    fun `카드가 다 녹은 뒤에 창틀 글자가 나온다`() {
        assertTrue("다 녹으면 창틀이 이름을 맡는다", frameTextShowsAt(1f))
        assertFalse("다 녹으면 카드는 안 그린다", cardShowsAt(1f))
        // 아주 조금이라도 남아 있으면 아직 카드 차례다.
        assertTrue(cardShowsAt(0.999f))
        assertFalse(frameTextShowsAt(0.999f))
    }

    @Test
    fun `녹는 구간은 진입의 30퍼센트에서 62퍼센트다`() {
        // 저쪽 `plate-in` 키프레임의 비율이다. 여기가 움직이면 위 두 테스트의
        // 전제(겹침 구간의 위치)도 같이 움직인다.
        assertEquals(0f, meltAt(0.30f), 0.001f)
        assertEquals(1f, meltAt(0.62f), 0.001f)
        assertEquals(0.5f, meltAt(0.46f), 0.01f)
        // 구간 밖은 잘려 있다.
        assertEquals(0f, meltAt(0f), 0.001f)
        assertEquals(1f, meltAt(1f), 0.001f)
    }
}
