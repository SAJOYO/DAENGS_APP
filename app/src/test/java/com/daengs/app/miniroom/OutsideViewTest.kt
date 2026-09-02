package com.daengs.app.miniroom

import com.daengs.app.miniroom.art.WindowSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 창밖·문밖 여섯 벌이 **제 그림을 가리키는가**, 그리고 창유리 자리가 그대로인가.
 *
 * 그림 열두 장은 `tools/make_outside.py` 가 찍고 `import_room_assets.py` 가 이름을
 * 짓는다. 사람이 손대는 곳이 없어 보이지만, 한 장을 다시 뽑다가 이름이 어긋나면
 * **엉뚱한 날씨가 조용히 걸린다** — 밤인데 낮 그림이 걸려도 앱은 안 죽는다.
 * 여기서 여섯 갈래가 서로 다른 그림을 물고 있는지만 못 박는다.
 */
class OutsideViewTest {

    @Test
    fun `시간 x 날씨가 빠짐없이 있다`() {
        val expected = OutsideTime.entries.size * OutsideWeather.entries.size
        assertEquals(expected, OutsideView.entries.size)
        val combos = OutsideView.entries.map { it.time to it.weather }.toSet()
        assertEquals("한 칸도 비면 안 된다", expected, combos.size)
    }

    /**
     * **흐림만 그림을 빌려 쓴다.** 맑음 그림에 회색 막을 씌워 만들기 때문이다
     * ([OutsideWeather.CLOUDY]). 나머지는 자기 그림이 있어야 한다.
     */
    @Test
    fun `흐림만 맑음 그림을 빌려 쓴다`() {
        OutsideView.entries.forEach {
            assertEquals("${it.name} 의 막 여부", it.weather == OutsideWeather.CLOUDY, it.veil)
        }
        val cloudy = OutsideView.entries.filter { it.weather == OutsideWeather.CLOUDY }
        cloudy.forEach { c ->
            val clear = OutsideView.of(c.time, OutsideWeather.CLEAR)
            assertEquals("${c.name} 은 같은 시간 맑음 그림을 쓴다", clear.window, c.window)
            assertEquals("${c.name} 은 같은 시간 맑음 문 그림을 쓴다", clear.door, c.door)
        }
    }

    @Test
    fun `막을 안 쓰는 벌은 저마다 다른 그림이다`() {
        val own = OutsideView.entries.filterNot { it.veil }
        val windows = own.map { it.window }.toSet()
        val doors = own.map { it.door }.toSet()
        assertEquals("창 그림이 서로 달라야 한다", own.size, windows.size)
        assertEquals("문 그림이 서로 달라야 한다", own.size, doors.size)
        OutsideView.entries.forEach {
            assertTrue("${it.name} 의 창·문 그림이 같은 리소스다", it.window != it.door)
        }
    }

    @Test
    fun `of 는 시간과 날씨로 그 벌을 찾는다`() {
        OutsideView.entries.forEach {
            assertEquals(it, OutsideView.of(it.time, it.weather))
        }
    }

    /**
     * 유리 사각형은 방 그림에서 **테마 여섯 장이 일치하는 화소**로 뽑은 값이다
     * (`tools/make_outside.py` 의 `glass_mask`). 손으로 고칠 값이 아니라서,
     * 바뀌었다면 그림이 바뀐 것이다 — 그때는 그림 열두 장도 같이 다시 뽑아야 한다.
     */
    @Test
    fun `창유리 자리가 방 그림 안에 있다`() {
        assertEquals(30.66f, WindowSpec.glass.left, 0.01f)
        assertEquals(15.55f, WindowSpec.glass.top, 0.01f)
        assertEquals(51.69f, WindowSpec.glass.right, 0.01f)
        assertEquals(51.07f, WindowSpec.glass.bottom, 0.01f)

        val g = RoomGeometry.of(900f)
        val dst = WindowSpec.rectOf(g)
        assertTrue("창유리가 방 그림 왼쪽으로 넘어갔다", dst.left >= g.stage.left)
        assertTrue("창유리가 방 그림 오른쪽으로 넘어갔다", dst.right <= g.stage.right)
        assertTrue("창유리가 방 그림 위로 넘어갔다", dst.top >= g.stage.top)
        // 유리는 벽에 있다. 바닥(무대 아래 절반)까지 내려오면 자리가 틀린 것이다.
        assertTrue("창유리가 바닥까지 내려왔다", dst.bottom < g.stage.top + g.stage.height * 0.6f)
    }
}
