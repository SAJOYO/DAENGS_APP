package com.daengs.app.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 채널 id 의 규격.
 *
 * `GaitAnalysisWorker.ensureChannel` 주석의 규칙을 그대로 잇는다 — *"한 채널에 묶으면
 * 사용자가 둘 중 하나만 끄지 못한다"*. 지금 둘로 갈라져 있다: **기다리던 것이 준비됐다**
 * (보행 분석 · 산책 일기 · 포토 카드)와 **앱이 먼저 말을 건다**(산책 알림).
 */
class DaengsChannelTest {

    /**
     * **옛 id 를 다시 쓰지 않는다.** 이름이 두 번 좁았다 — `gait_analysis` 는 산책 일기가
     * 들어오며, `analysis_result` 는 포토 카드가 들어오며 좁아졌다. 지우는 쪽은
     * [ensureDaengsChannels] 가 맡는다.
     */
    @Test
    fun `쓰는 채널은 옛 채널 id 와 겹치지 않는다`() {
        assertFalse(RESULT_CHANNEL_ID in LEGACY_CHANNEL_IDS)
        assertFalse(WALK_REMINDER_CHANNEL_ID in LEGACY_CHANNEL_IDS)
    }

    @Test
    fun `기다리던 결과와 앱이 먼저 거는 말은 다른 채널이다`() {
        assertTrue(RESULT_CHANNEL_ID != WALK_REMINDER_CHANNEL_ID)
    }

    /** 옛 id 를 목록에서 빼면 지워지지 않고 남는다. 둘 다 기억해야 한다. */
    @Test
    fun `지울 옛 채널이 둘이다`() {
        assertTrue("gait_analysis" in LEGACY_CHANNEL_IDS)
        assertTrue("analysis_result" in LEGACY_CHANNEL_IDS)
    }
}
