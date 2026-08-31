package com.daengs.app.map.layers.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * 마커 여덟 색이 **서로 구분되는가.**
 *
 * 색을 앱 화풍에 맞춰 낮추는 작업을 했는데, 낮추다 보면 여덟이 서로 비슷해진다.
 * 그러면 지도에서 **병원과 카페를 구분할 수 없다** — 색이 곧 카테고리 정보다.
 * 눈으로는 다음 사람이 못 지키므로 여기서 잡는다.
 *
 * 리소스를 안드로이드 없이 읽으려고 **XML 파일을 직접 읽는다.** 값을 코틀린에
 * 한 벌 더 적어 두면 두 군데가 되고 언젠가 갈라진다 — 원본은 `facility_colors.xml`
 * 하나다 (`ic_facility_*.xml` 의 `fillColor` 가 그 이름을 참조한다).
 */
class FacilityColorTest {

    private val file = File("src/main/res/values/facility_colors.xml")

    private val colors: Map<String, Int> by lazy {
        val text = file.readText()
        Regex("""<color name="(facility_\w+)">#([0-9A-Fa-f]{6})</color>""")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2].toInt(16) }
    }

    /**
     * 0~360. **거의 무채색이면 null** — 회색은 색상으로 구분하는 게 아니라
     * "색이 없다" 는 사실 자체로 구분된다 (기타 마커가 그렇다).
     */
    private fun hue(rgb: Int): Float? {
        val r = (rgb shr 16 and 0xFF) / 255f
        val g = (rgb shr 8 and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (max <= 0f || d / max < 0.2f) return null
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return (h + 360f) % 360f
    }

    /** 사람 눈이 느끼는 밝기(0~1). 초록이 파랑보다 밝게 보이는 걸 반영한다. */
    private fun luminance(rgb: Int): Float {
        val r = (rgb shr 16 and 0xFF) / 255f
        val g = (rgb shr 8 and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun hueGap(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return min(d, 360f - d)
    }

    @Test
    fun `여덟 색이 다 있다`() {
        assertTrue("파일이 없다: ${file.absolutePath}", file.exists())
        assertEquals(FacilityIconGroup.entries.size, colors.size)
        for (group in FacilityIconGroup.entries) {
            assertTrue("${group.wire} 색이 없다", colors.containsKey("facility_${group.wire}"))
        }
    }

    /**
     * 색상 간격 20°.
     *
     * **원본 팔레트가 16° 였다** — 용품(주황 24°)과 돌봄(금색 41°)이 그만큼밖에 안
     * 떨어져 있었다. 톤을 낮추면 그 둘이 더 붙으므로(11°까지 갔었다) 따뜻한 쪽을
     * 벌려서 22° 로 만들었다. 20° 는 **원본보다 나빠지지 않는다**는 선이다.
     *
     * 이 값을 올리려면 색을 더 벌려야 하는데, 우리 팔레트가 따뜻한 쪽으로 쏠려 있어
     * 그러면 앱 화풍에서 멀어진다. 여기가 두 요구가 만나는 자리다.
     */
    @Test
    fun `색상이 서로 20도 이상 벌어져 있다`() {
        val hues = colors.filterValues { hue(it) != null }.mapValues { hue(it.value)!! }
        val names = hues.keys.toList()
        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                val gap = hueGap(hues[names[i]]!!, hues[names[j]]!!)
                assertTrue(
                    "${names[i]} 와 ${names[j]} 의 색상 차이가 ${gap.toInt()}도 — 지도에서 같은 색으로 보인다",
                    gap >= 20f,
                )
            }
        }
    }

    /**
     * 밝기가 서로 비슷해야 한다.
     *
     * 하나만 밝으면 그 카테고리만 흐려 보여서 **덜 중요한 것처럼 읽힌다.** 안쪽 흰
     * 기호가 보이려면 여덟 다 어느 정도 어두워야 하는 이유도 있다.
     */
    @Test
    fun `밝기가 서로 비슷하다`() {
        val lums = colors.mapValues { luminance(it.value) }
        val lo = lums.minOf { it.value }
        val hi = lums.maxOf { it.value }
        assertTrue("가장 어두운 색이 %.2f — 흰 기호가 안 보인다".format(hi), hi <= 0.72f)
        assertTrue("밝기 폭이 %.2f — 어떤 마커만 흐려 보인다".format(hi - lo), hi - lo <= 0.22f)
    }
}
