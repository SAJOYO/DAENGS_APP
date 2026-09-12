package com.daengs.app.dogcard

import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.namePlateBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 이름판이 **아바타 원을 안 덮는지**를 잠근다.
 *
 * 이름칸은 저쪽 카드에 인쇄된 제목의 잉크 상자라, 제목 바가 아바타 원 뒤까지 뻗어
 * 있는 판에서는 칸의 왼쪽 끝이 **원 속**으로 들어간다. 스물다섯 중 스물넷이 그렇고,
 * 제일 깊은 당근은 7.7%p 다. 이름판은 카드 **위에** 그려지므로 칸을 그대로 쓰면
 * 그 폭만큼 강아지 얼굴을 덮는다 — 사용자가 "이름 자리가 얼굴과 뭉갠다" 고 한 고장이
 * 딱 이 모양으로 다시 난다.
 *
 * 손으로 고친 `kiwi-gentle` 한 장만 원래 안 물렸다. 그 한 장 때문에 "우리 값은
 * 괜찮다" 고 지나갔던 적이 있어서, 여기서는 **스물다섯 장을 다** 본다.
 */
class CardNamePlateTest {

    /** 아바타 원의 오른쪽 끝. 얼굴은 `CLIP_BLEED` 까지 그려지므로 그 값으로 잰다. */
    private fun avatarRight(id: String): Float =
        CARD_TEMPLATES.first { it.id == id }.let { it.avatar.cx + it.avatar.rx * 1.12f }

    @Test
    fun `이름판이 아바타 원을 안 덮는다`() {
        CARD_TEMPLATES.forEach { t ->
            val plate = namePlateBox(t.name!!, t.avatar, t.ratio)
            assertTrue(
                "${t.id} 이름판 왼쪽 ${plate.x0}% 가 아바타 오른쪽 ${avatarRight(t.id)}% 를 물었다",
                plate.x0 >= avatarRight(t.id),
            )
        }
    }

    /**
     * **고장을 실제로 잡는 시험인지 확인한다.** 아바타를 안 넘기면 칸이 그대로
     * 돌아오는데, 그 값은 스물넷에서 원을 문다 — 위 시험이 통과하는 것이 비켜 주기
     * 때문이지 값이 원래 괜찮아서가 아니라는 것을 여기서 보인다.
     */
    @Test
    fun `아바타를 안 비키면 스물넷이 원을 문다`() {
        val bitten = CARD_TEMPLATES.count { t ->
            namePlateBox(t.name!!, null, t.ratio).x0 < avatarRight(t.id)
        }
        assertEquals(24, bitten)
    }

    /** 판은 칸의 오른쪽을 안 넘는다 — 칩과 같은 이유다 (`CardChipTest`). */
    @Test
    fun `이름판이 이름칸 오른쪽을 안 넘는다`() {
        CARD_TEMPLATES.forEach { t ->
            val name = t.name!!
            val plate = namePlateBox(name, t.avatar, t.ratio)
            assertTrue(
                "${t.id} 이름판 오른쪽 ${plate.x1}% 가 칸 ${name.x1}% 를 넘었다",
                plate.x1 <= name.x1 + 1e-3f,
            )
        }
    }

    /** 비키고 나서도 글자가 앉을 폭이 남아야 한다. 다 비키면 판이 뒤집힌다. */
    @Test
    fun `비킨 뒤에도 판이 충분히 넓다`() {
        CARD_TEMPLATES.forEach { t ->
            val plate = namePlateBox(t.name!!, t.avatar, t.ratio)
            assertTrue("${t.id} 이름판이 뒤집혔다 (${plate.x0}~${plate.x1})", plate.x1 > plate.x0)
            assertTrue(
                "${t.id} 이름판이 너무 좁다 (${plate.x1 - plate.x0}%p)",
                plate.x1 - plate.x0 > 25f,
            )
        }
    }
}
