package com.daengs.app.miniroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 방을 **가로로만** 늘리는 값의 성질.
 *
 * 이 값이 "각 폰에서 방을 최대한 크게" 의 손잡이다. 방 그림 폭의 한계는
 * `화면 폭 × INSET = 386.7dp` 로 **모든 폰에서 같다** (`DaengsTheme` 이 합성 폭을 늘
 * 411dp 로 맞춘다). 길쭉한 폰은 이미 그 한계에 닿아 있고(Pixel 7 은 1.14 에서 한계),
 * 정사각에 가까운 폰만 모자라다 — Pixel 3 XL 은 한계까지 1.53 이 필요하다.
 *
 * ⚠️ [RoomSpec.H_STRETCH] 의 **옛 주석은 대가를 잘못 말했다.** "여러 칸을 차지하는
 * 소품(러그 5x5)은 자기 칸을 다 덮지 못한다" 고 적혀 있었는데, 나중에 추가된
 * [RoomGeometry.scaleX] 가 소품에만 가로 배율을 줘서 그 문제는 이미 해결됐다.
 * 여기서 그 계약을 잡는다 — **소품은 같이 늘어나고 강아지는 안 늘어난다.**
 */
class RoomStretchTest {

    /**
     * 합성 안에서 폭은 늘 411dp 다. `1dp = 3.504px` 는 Pixel 3 XL 에서 실측한 값이고,
     * 여기서는 픽셀로 넣기만 하면 되므로 어떤 자를 써도 결론이 같다.
     */
    private val w = 411.4f * 3.504f

    /** 1·2 번을 반영한 방 상자(335.3dp). 이 상자에서는 세로가 먼저 걸린다. */
    private val h = 335.3f * 3.504f

    @Test
    fun `기본값은 1_18 이다`() {
        assertEquals(1.18f, RoomSpec.H_STRETCH, 1e-6f)
    }

    @Test
    fun `늘리면 방이 넓어진다`() {
        val a = RoomGeometry.of(w, h, hStretch = 1.18f)
        val b = RoomGeometry.of(w, h, hStretch = 1.40f)
        assertTrue("1.40 이 1.18 보다 안 넓다", b.stage.width > a.stage.width)
    }

    /** 가로만 늘린다 — 세로가 같이 커지면 상자를 넘고 방이 잘린다. */
    @Test
    fun `늘려도 세로는 그대로다`() {
        val a = RoomGeometry.of(w, h, hStretch = 1.18f)
        val b = RoomGeometry.of(w, h, hStretch = 1.40f)
        assertEquals(a.stage.height, b.stage.height, 0.01f)
    }

    /** 넘치게 잡아도 상자 폭에서 잘린다. 슬라이더를 끝까지 밀어도 화면 밖으로 안 나간다. */
    @Test
    fun `넘치게 잡아도 상자 폭을 넘지 않는다`() {
        val g = RoomGeometry.of(w, h, hStretch = 9f)
        assertTrue("방이 ${g.stage.width} 로 상자 $w 를 넘었다", g.stage.width <= w + 0.01f)
    }

    /**
     * 늘림은 **소품 배율([RoomGeometry.scaleX])에만** 걸린다. 강아지가 쓰는
     * [RoomGeometry.scale] 은 그대로다 — 개까지 늘리면 뚱뚱해진다.
     */
    @Test
    fun `늘림은 균일 배율을 바꾸지 않는다`() {
        val a = RoomGeometry.of(w, h, hStretch = 1.18f)
        val b = RoomGeometry.of(w, h, hStretch = 1.50f)
        assertEquals(a.scale, b.scale, 1e-6f)
        assertTrue("소품 배율이 안 늘어났다", b.scaleX > a.scaleX)
    }

    @Test
    fun `늘림이 1이면 원본 비율이다`() {
        val g = RoomGeometry.of(w, h, hStretch = 1f)
        assertEquals(g.scale, g.scaleX, 1e-6f)
    }

    /**
     * **길쭉한 폰은 이미 한계다.** Pixel 7 의 방 상자(452dp)에서는 기본값 1.18 만으로도
     * 폭이 한계에 닿아 있어서, 값을 올려도 더 안 넓어진다. 그래서 늘림을 올리는 것이
     * 잘 나오는 폰을 건드리지 않는다.
     */
    @Test
    fun `길쭉한 폰은 기본값에서 이미 한계에 닿는다`() {
        val tall = 452f * 3.504f
        val a = RoomGeometry.of(w, tall, hStretch = 1.18f)
        val b = RoomGeometry.of(w, tall, hStretch = 1.53f)
        assertEquals(a.stage.width, b.stage.width, 0.01f)
    }
}
