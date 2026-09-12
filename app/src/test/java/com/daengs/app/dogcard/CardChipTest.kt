package com.daengs.app.dogcard

import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.Slot
import com.daengs.app.ui.dogcard.chipBox
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 번호칩이 **판 밖으로 안 나가는지**를 잠근다.
 *
 * 칩은 칸보다 넓게 그린다 — 인쇄된 `NEO-0824` 의 획 끝을 덮어야 해서다. 그 여유를
 * 칸 **크기** 대비 6% 로 잡았더니, 납작한 번호칸에서 가로만 세로의 다섯 배가 되어
 * 칩 오른쪽 끝이 카드 폭의 95.1% 까지 나갔다. 판 안쪽 선은 92~95% 사이라
 * **스물네 장에서 칩이 은테를 밟고 있었다.**
 *
 * 화면으로는 잘 안 보이는 고장이다 — 도감 칸 크기(130dp)에서 넘치는 폭이 두세
 * 화소고, 칩도 은테도 카드의 일부처럼 생겼다. 여기서 잡는다.
 */
class CardChipTest {

    /** 카드 하나를 그릴 때 칩이 실제로 덮는 자리. */
    private fun chipOf(id: String): Pair<Slot, Slot> {
        val t = CARD_TEMPLATES.first { it.id == id }
        return t.code!! to chipBox(t.code!!, t.ratio)
    }

    /**
     * **칩은 번호칸의 오른쪽을 못 넘는다.**
     *
     * 칸 오른쪽 끝이 곧 판 안쪽 선이라, 한 톨이라도 넘으면 은테 위다. 왼쪽·위·아래로는
     * 넓혀도 판 안이라 괜찮다 — 그래서 한쪽만 잠근다.
     */
    @Test
    fun `칩이 번호칸 오른쪽을 안 넘는다`() {
        CARD_TEMPLATES.forEach { t ->
            val code = t.code!!
            val chip = chipBox(code, t.ratio)
            assertTrue(
                "${t.id} 칩 오른쪽 ${chip.x1}% 가 칸 ${code.x1}% 를 넘었다",
                chip.x1 <= code.x1 + 1e-3f,
            )
        }
    }

    /**
     * 칩이 칸보다 **넓기는 해야 한다.** 여유를 0 으로 만들면 인쇄된 번호의 획 끝이
     * 칩 밖으로 삐져나온다 — 젠틀 키위에서 실기기로 본 고장이다.
     */
    @Test
    fun `칩이 칸보다 넓다`() {
        CARD_TEMPLATES.forEach { t ->
            val code = t.code!!
            val chip = chipBox(code, t.ratio)
            assertTrue("${t.id} 칩 왼쪽", chip.x0 < code.x0)
            assertTrue("${t.id} 칩 위", chip.y0 < code.y0)
            assertTrue("${t.id} 칩 아래", chip.y1 > code.y1)
        }
    }

    /**
     * 칩이 카드 밖으로 안 나간다. 위로 넓히다가 카드 위 끝을 넘으면 잘려서 한쪽만
     * 각진 상자가 된다.
     */
    @Test
    fun `칩이 카드 안에 있다`() {
        CARD_TEMPLATES.forEach { t ->
            val chip = chipBox(t.code!!, t.ratio)
            assertTrue("${t.id} 칩 왼쪽 ${chip.x0}", chip.x0 >= 0f)
            assertTrue("${t.id} 칩 오른쪽 ${chip.x1}", chip.x1 <= 100f)
            assertTrue("${t.id} 칩 위 ${chip.y0}", chip.y0 >= 0f)
            assertTrue("${t.id} 칩 아래 ${chip.y1}", chip.y1 <= 100f)
        }
    }

    /**
     * **번호칸이 은테까지 가지 않는다.**
     *
     * 스물다섯 장의 판을 화소 단위로 확대해 재 보니 홀로그램 속살이 제일 오른쪽까지
     * 가는 카드가 바나나(95.3%) 이고, 그 오른쪽은 전부 은테다. 칸이 여기를 넘으면
     * 칩이 따라 넘는다 — `int(w * 0.945)` 로 박아 둔 값이 딱 이 고장이었다.
     */
    @Test
    fun `번호칸이 은테 앞에서 끝난다`() {
        CARD_TEMPLATES.forEach { t ->
            val code = t.code!!
            assertTrue("${t.id} 번호칸 오른쪽 ${code.x1}%", code.x1 <= 95.05f)
        }
    }

    /**
     * 같은 1080x1440 판에서 온 일곱 장은 틀이 같으니 번호칸 오른쪽도 같아야 한다.
     * 한 장만 고치고 나머지를 잊으면 도감에서 그 한 장만 달라 보인다 (젠틀 키위를
     * 고칠 때 실제로 그렇게 됐다).
     */
    @Test
    fun `한 판에서 온 야채 일곱 장은 번호칸 오른쪽이 같다`() {
        val ids = listOf(
            "cabbage", "sweet-potato", "mushroom", "broccoli",
            "cucumber", "spinach", "tomato",
        )
        val rights = ids.map { id -> CARD_TEMPLATES.first { it.id == id }.code!!.x1 }.distinct()
        assertTrue("일곱 장이 제각각이다: $rights", rights.size == 1)
        assertTrue("은테를 밟는다: ${rights[0]}", rights[0] <= 93.4f)
    }

    /**
     * 칩이 **이름칸을 안 물어야 한다.** 칩을 왼쪽으로 넓히는 쪽이라, 번호칸이 이름바
     * 바로 옆에 붙어 있는 버섯형에서 이름 끝 글자가 칩 밑으로 들어갈 수 있다.
     */
    @Test
    fun `칩이 이름칸을 안 문다`() {
        CARD_TEMPLATES.forEach { t ->
            val name = t.name!!
            val chip = chipBox(t.code!!, t.ratio)
            val apart = name.x1 <= chip.x0 || chip.x1 <= name.x0 ||
                name.y1 <= chip.y0 || chip.y1 <= name.y0
            assertTrue("${t.id} 칩이 이름칸을 문다 (이름 ${name.x1} / 칩 ${chip.x0})", apart)
        }
    }

    /**
     * 칩을 안 까는 넷은 번호판이 **카드 아래 왼쪽**에 따로 있다. 이 넷에는 칩을 안
     * 그리므로 위 규칙이 흔들면 안 되는데, 값 자체는 그대로 남아 있어야 한다.
     */
    @Test
    fun `아래 왼쪽 번호판 넷은 그대로다`() {
        listOf("pepper", "eggplant", "carrot", "danhobak").forEach { id ->
            val (code, _) = chipOf(id)
            assertTrue("$id 는 칩을 안 깐다", !CARD_TEMPLATES.first { it.id == id }.codeChip)
            assertTrue("$id 번호판이 왼쪽에 있다 (${code.x0}~${code.x1})", code.x1 < 30f)
            assertTrue("$id 번호판이 아래에 있다 (${code.y0})", code.y0 > 70f)
        }
    }
}
