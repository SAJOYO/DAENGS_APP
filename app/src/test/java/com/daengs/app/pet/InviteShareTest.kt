package com.daengs.app.pet

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InviteShareTest {

    private val link = "https://daengapi.weareithero.cloud/invite#abc_DEF-123"

    @Test
    fun `공유 문구에 이름과 유효기간 안내와 링크가 들어간다`() {
        val message = InviteShare.message("네옹", link)

        assertTrue(message.contains("네옹의 공동 돌봄 초대장이 도착했어요!"))
        assertTrue(message.contains("이 초대장은 24시간 동안 사용할 수 있습니다."))
        assertTrue(message.contains(link))
    }

    /**
     * **누르라고 하면 안 된다.** App Links 가 없어 링크를 누르면 브라우저가 빈 페이지를
     * 연다. 받는 사람이 거기서 멈추면 초대가 그대로 죽는다.
     */
    @Test
    fun `링크를 누르라고 안내하지 않는다`() {
        val message = InviteShare.message("네옹", link)

        assertFalse(message.contains("링크를 눌러 "))
        assertFalse(message.contains("눌러 참여"))
        assertFalse(message.contains("클릭"))
        assertFalse(message.contains("접속"))
        assertTrue("대신 붙여넣기를 안내한다", message.contains("붙여넣"))
    }

    /** 받는 사람이 앱을 깔고 어디로 가야 하는지까지 문구가 데려다줘야 한다. */
    @Test
    fun `설치부터 붙여넣기까지 순서대로 안내한다`() {
        val message = InviteShare.message("네옹", link)

        val install = message.indexOf("설치")
        val login = message.indexOf("카카오로 로그인")
        val entry = message.indexOf("「공동 돌봄 초대받기」")
        val paste = message.indexOf("복사해 붙여넣어")

        assertTrue("설치 안내가 있어야 한다", install >= 0)
        assertTrue("카카오 로그인 안내가 있어야 한다", login >= 0)
        assertTrue("앱 안의 진입점 이름을 그대로 불러 줘야 한다", entry >= 0)
        assertTrue("붙여넣기 안내가 있어야 한다", paste >= 0)
        assertTrue("설치·로그인 → 진입점 → 붙여넣기 순서", install < entry && entry < paste)
        assertTrue("메시지 전체를 붙여넣어도 된다고 알려 준다", message.contains("메시지 전체"))
    }

    /** 줄바꿈이 뭉개지면 받는 사람이 링크를 문장의 일부로 읽는다. */
    @Test
    fun `안내와 링크 사이의 빈 줄이 유지된다`() {
        val message = InviteShare.message("네옹", link)

        assertTrue("링크 앞에 빈 줄이 있어야 한다", message.contains("사용할 수 있습니다.\n\n$link"))
        assertTrue("제목 뒤에도 빈 줄", message.contains("도착했어요!\n\n1."))
        assertTrue("링크가 맨 끝이어야 미리보기가 링크를 집는다", message.endsWith(link))
    }

    /** 두 번 적으면 붙여넣기가 "초대가 여러 개" 로 읽을 여지가 생긴다. */
    @Test
    fun `링크가 문구에 한 번만 들어간다`() {
        val message = InviteShare.message("네옹", link)

        assertEquals(1, message.split(link).size - 1)
    }

    @Test
    fun `이름이 없으면 대체 문구를 쓴다`() {
        assertTrue(InviteShare.message(null, link).contains("우리 아이의 공동 돌봄 초대장"))
        assertTrue(InviteShare.message("   ", link).contains("우리 아이의 공동 돌봄 초대장"))
    }

    @Test
    fun `공유 인텐트는 기본 공유창이 받는 모양이다`() {
        val message = InviteShare.message("네옹", link)
        val intent = InviteShare.intent(message)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(message, intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    /** 카카오 SDK 를 안 쓴다 — 특정 앱을 지목하지 않아야 사용자가 고를 수 있다. */
    @Test
    fun `공유 인텐트가 특정 앱을 지목하지 않는다`() {
        val intent = InviteShare.intent(InviteShare.message("네옹", link))

        assertEquals(null, intent.`package`)
        assertEquals(null, intent.component)
    }

    @Test
    fun `문구에 토큰만 따로 떨어져 나오지 않는다`() {
        val message = InviteShare.message("네옹", link)

        // 토큰은 링크 안에서만 등장해야 한다 — 따로 적어 두면 실수로 복사·로그된다.
        assertEquals(1, message.split("abc_DEF-123").size - 1)
        assertFalse(message.contains("token"))
    }

    // -- 클립보드 (민감정보) ----------------------------------------------------

    /**
     * 안드로이드 13 부터 복사하면 시스템이 미리보기를 띄운다. 표시를 안 하면 그 팝업에
     * **토큰이 든 링크가 그대로** 뜬다.
     */
    @Test
    fun `복사하는 클립을 민감정보로 표시한다`() {
        val clip = InviteShare.sensitiveClip(link)

        assertEquals(true, clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test
    fun `클립에는 링크만 담고 안내 문구는 안 담는다`() {
        val clip = InviteShare.sensitiveClip(link)

        assertEquals(link, clip.getItemAt(0).text.toString())
        assertFalse(clip.getItemAt(0).text.toString().contains("초대장이 도착했어요"))
    }

    /** 13 부터는 시스템이 "복사됨" 을 띄운다 — 앱이 또 띄우면 같은 말이 두 번 뜬다. */
    @Test
    fun `안드로이드 13 이상에서는 앱이 복사 안내를 띄우지 않는다`() {
        assertFalse(InviteShare.needsCopiedNotice(sdkInt = 33))
        assertFalse(InviteShare.needsCopiedNotice(sdkInt = 34))
        assertFalse(InviteShare.needsCopiedNotice(sdkInt = 35))
    }

    @Test
    fun `12L 이하에서는 앱이 복사 안내를 띄운다`() {
        assertTrue(InviteShare.needsCopiedNotice(sdkInt = 32))
        assertTrue(InviteShare.needsCopiedNotice(sdkInt = 30))
    }
}
