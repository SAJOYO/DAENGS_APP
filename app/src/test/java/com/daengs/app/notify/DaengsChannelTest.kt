package com.daengs.app.notify

import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 채널 id 의 규격.
 *
 * `GaitAnalysisWorker.ensureChannel` 주석의 규칙을 그대로 잇는다 — *"한 채널에 묶으면
 * 사용자가 둘 중 하나만 끄지 못한다"*. 지금은 **성격이 같은 둘**(보행 분석 완료 · 산책
 * 일기 장면)이 한 채널에 있다: 기다리던 것이 준비됐다고 한 번 부르는 알림이다.
 */
class DaengsChannelTest {

    /**
     * **옛 `gait_analysis` 를 그대로 쓰지 않는다.**
     *
     * 산책 일기 장면이 같은 채널에 들어오면서 `gait_` 라는 이름이 하는 일과 어긋났다.
     * 지우는 쪽은 [ensureAnalysisChannel] 이 맡는다 — 출시 전이라 잃을 설정이 없다.
     */
    @Test
    fun `분석 결과 채널은 옛 보행 채널과 다른 id 다`() {
        assertNotEquals(LEGACY_GAIT_CHANNEL_ID, ANALYSIS_CHANNEL_ID)
    }
}
