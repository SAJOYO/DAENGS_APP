package com.daengs.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 인벤토리 슬롯이 **자기가 필요한 높이를 받는가.**
 *
 * 여기가 깨져 있었다. 챗봇 카드와 인벤토리 패널이 같은 칸(104dp)을 쓰는데, 패딩과
 * 탭 줄을 뺀 **55.6dp** 만 슬롯에 남았다. 슬롯이 요구하는 것은 88dp 였다. 썸네일
 * (53dp)까지만 들어가고 **이름과 개수가 배치조차 안 됐다** — Pixel 3 XL 에서
 * `uiautomator dump` 를 떠 보니 그 글자 노드가 계층에 아예 없었다.
 *
 * **눈으로는 "썸네일만 있는 깔끔한 줄" 로 보여서 아무도 버그라고 생각하지 않았다.**
 * 그래서 숫자로 잡는다.
 */
class InventoryMetricsTest {

    @Test
    fun `패널 높이는 슬롯과 탭 줄을 다 담는다`() {
        val need = InventoryMetrics.PanelPadding * 2 +
            InventoryMetrics.TabRow +
            InventoryMetrics.Gap +
            InventoryMetrics.Slot
        assertTrue(
            "패널 ${InventoryMetrics.Panel} 에 필요한 것 $need 이 안 들어간다",
            InventoryMetrics.Panel >= need,
        )
    }

    @Test
    fun `슬롯 높이는 썸네일과 이름 한 줄을 담는다`() {
        val need = InventoryMetrics.SlotPadding * 2 +
            InventoryMetrics.Thumb +
            InventoryMetrics.Label
        assertEquals(need, InventoryMetrics.Slot)
    }

    /** 픽셀 아트라 썸네일이 작아지면 러그와 방석을 구분할 수 없다. */
    @Test
    fun `썸네일은 40dp 보다 작아지지 않는다`() {
        assertTrue("${InventoryMetrics.Thumb} 는 너무 작다", InventoryMetrics.Thumb >= 40.dp)
    }

    /**
     * 챗봇 칸보다 크다 — 그래서 `+` 를 누를 때 방이 움직이고, 그 움직임을
     * `animateDpAsState` 로 잇는다. 예전에는 둘을 같은 높이로 **강제**해서 방이 안
     * 움직이게 했고, 그 대가가 위의 잘린 이름표였다.
     */
    @Test
    fun `인벤토리가 챗봇 칸보다 크다`() {
        assertTrue(InventoryMetrics.Panel > CardSlotHeight)
    }

    /**
     * 방이 움직이는 양.
     *
     * 이름·개수를 두 줄로 두면 패널이 136dp 가 되어 방이 48dp 움직인다. 한 줄로
     * 합쳐 26dp 로 줄였다 — 사용자가 정했다.
     */
    @Test
    fun `방이 움직이는 양은 30dp 를 넘지 않는다`() {
        val moves = InventoryMetrics.Panel - CardSlotHeight
        assertTrue("방이 $moves 움직인다", moves <= 30.dp)
    }

    @Test
    fun `챗봇 칸은 88dp 다`() {
        assertEquals(88.dp, CardSlotHeight)
    }
}
