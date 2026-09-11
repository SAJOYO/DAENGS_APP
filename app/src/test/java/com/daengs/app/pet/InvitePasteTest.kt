package com.daengs.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 붙여넣은 글에서 초대 토큰을 찾는가.
 *
 * 사용자는 링크만 복사하지 않는다 — 카톡에서 길게 눌러 복사하면 공유 문구가 통째로 온다.
 */
class InvitePasteTest {

    private val token = "abc_DEF-123"
    private val link = "https://daengapi.weareithero.cloud/invite#$token"

    private fun found(result: InvitePaste.Result): String? =
        (result as? InvitePaste.Result.Found)?.token

    @Test
    fun `링크만 붙여넣으면 토큰을 꺼낸다`() {
        assertEquals(token, found(InvitePaste.parse(link)))
    }

    /** 실제로 사용자가 카톡에서 복사하는 모양. */
    @Test
    fun `공유 문구 전체에서 링크를 찾아낸다`() {
        val message = InviteShare.message("네옹", link)

        assertEquals(token, found(InvitePaste.parse(message)))
    }

    @Test
    fun `앞뒤 공백과 줄바꿈을 견딘다`() {
        assertEquals(token, found(InvitePaste.parse("  \n $link \n\n ")))
        assertEquals(token, found(InvitePaste.parse("\t$link\r\n")))
    }

    @Test
    fun `괄호나 따옴표에 싸여 있어도 찾는다`() {
        assertEquals(token, found(InvitePaste.parse("초대: <$link>")))
        assertEquals(token, found(InvitePaste.parse("\"$link\"")))
    }

    @Test
    fun `아직 안 붙여넣었으면 빈 상태다`() {
        assertEquals(InvitePaste.Result.Empty, InvitePaste.parse(null))
        assertEquals(InvitePaste.Result.Empty, InvitePaste.parse(""))
        assertEquals(InvitePaste.Result.Empty, InvitePaste.parse("   \n  "))
    }

    @Test
    fun `우리 링크가 없으면 못 찾았다고 한다`() {
        assertEquals(InvitePaste.Result.NoLink, InvitePaste.parse("그냥 인사말"))
        assertEquals(InvitePaste.Result.NoLink, InvitePaste.parse("https://evil.example.com/invite#$token"))
        assertEquals(InvitePaste.Result.NoLink, InvitePaste.parse("https://daengapi.weareithero.cloud/other#$token"))
        assertEquals(InvitePaste.Result.NoLink, InvitePaste.parse("http://daengapi.weareithero.cloud/invite#$token"))
        assertEquals(InvitePaste.Result.NoLink, InvitePaste.parse("daengs://invite?token=$token"))
    }

    /** 경로에 실린 토큰은 우리 계약이 아니다 — 받아 주면 계약이 둘이 된다. */
    @Test
    fun `경로에 실린 토큰은 받지 않는다`() {
        assertEquals(InvitePaste.Result.NoLink, InvitePaste.parse("https://daengapi.weareithero.cloud/invite/$token"))
    }

    /**
     * 프래그먼트 안의 공백은 시험하지 않는다 — URL 에 날 공백이 들어갈 수 없어서 붙여넣기로는
     * 만들 수 없는 입력이고, 공백으로 토막을 자르는 이 파서에서는 뒷부분이 그냥 딴 글이 된다.
     */
    @Test
    fun `토큰 자리가 비면 못 찾았다고 한다`() {
        assertEquals(InvitePaste.Result.NoLink, InvitePaste.parse("https://daengapi.weareithero.cloud/invite"))
        assertEquals(InvitePaste.Result.NoLink, InvitePaste.parse("https://daengapi.weareithero.cloud/invite#"))
        assertEquals(InvitePaste.Result.NoLink, InvitePaste.parse("https://daengapi.weareithero.cloud/invite#  "))
    }

    /**
     * **어느 것을 쓸지 앱이 고르지 않는다.** 잘못 고르면 엉뚱한 아이의 보호자가 되고,
     * 한 번 쓴 초대는 되돌릴 수 없다.
     */
    @Test
    fun `서로 다른 초대가 여럿이면 거부한다`() {
        val other = "https://daengapi.weareithero.cloud/invite#zzz_YYY-999"

        assertEquals(InvitePaste.Result.Ambiguous, InvitePaste.parse("$link\n$other"))
        assertEquals(InvitePaste.Result.Ambiguous, InvitePaste.parse("먼저 $link 그리고 $other"))
    }

    /** 같은 링크가 두 번 붙어 온 것은 모호하지 않다 — 가리키는 초대가 하나다. */
    @Test
    fun `같은 링크가 두 번이면 그대로 쓴다`() {
        assertEquals(token, found(InvitePaste.parse("$link\n$link")))
    }

    /** 붙여넣기 결과가 로그에 실려도 토큰이 안 새야 한다. */
    @Test
    fun `toString 에 토큰이 찍히지 않는다`() {
        val result = InvitePaste.parse(link)

        assertFalse(result.toString().contains(token))
        assertTrue(result.toString().contains("가림"))
    }
}
