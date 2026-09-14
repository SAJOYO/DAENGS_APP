package com.daengs.app.pet

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 링크로 받은 초대가 **액티비티 재생성을 넘어** 남는가 — 그리고 웹 폴백의 `intent://`
 * 가 실제 안드로이드 파서를 지나 무엇이 되는가.
 *
 * 액티비티 필드에 두면 재생성에 사라진다(에뮬레이터에서 다크 모드 전환으로 재현).
 * 여기서는 그 자리를 [InviteEntryViewModel] 로 옮긴 것이 실제로 이어지는지 본다 —
 * `MainActivity` 는 카카오 SDK·Room 까지 끌고 와 Robolectric 에서 못 띄우므로, 같은
 * 저장소(`ViewModelProvider(activity)`)를 맨 액티비티 위에서 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InviteEntryRecreateTest {

    private val token = "abc_DEF-123"

    private fun ActivityScenario<ComponentActivity>.model(): InviteEntryViewModel {
        lateinit var model: InviteEntryViewModel
        onActivity { model = ViewModelProvider(it)[InviteEntryViewModel::class.java] }
        return model
    }

    /** 로그인을 기다리는 사이(아직 안 연 토큰) 재생성돼도 토큰이 남는다. */
    @Test
    fun `기다리는 토큰은 재생성을 넘어 남는다`() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val before = scenario.model()
            before.receive(token)
            var firstActivity: ComponentActivity? = null
            scenario.onActivity { firstActivity = it }

            scenario.recreate()

            var secondActivity: ComponentActivity? = null
            scenario.onActivity { secondActivity = it }
            assertNotSame("액티비티는 정말 다시 만들어졌다", firstActivity, secondActivity)
            val after = scenario.model()
            assertSame("같은 ViewModel 이 이어진다", before, after)
            assertEquals(token, after.pendingToken)
        }
    }

    /** 미리보기를 보는 중(이미 열린 화면) 재생성돼도 화면·토큰·선택이 남는다. */
    @Test
    fun `열린 초대 화면과 선택은 재생성을 넘어 남는다`() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val before = scenario.model()
            before.receive(token)
            before.openFromLink()
            before.holder.choose("pet-1", PetChoice.Join)

            scenario.recreate()

            val after = scenario.model()
            assertTrue(after.accepting)
            assertTrue(after.autoEntered)
            assertEquals(InvitePaste.Result.Found(token), after.holder.parsed)
            assertEquals(mapOf("pet-1" to PetChoice.Join), after.holder.choices)
        }
    }

    /**
     * 웹 안내 페이지의 「앱에서 초대 열기」가 만드는 `intent://` 문자열을 **실제 안드로이드
     * 파서**로 읽는다. 서버 `routers/invite_web.py` 가 만드는 모양 그대로다 — 갈라지면
     * 이 테스트와 저쪽 `test_invite_web.py` 가 같이 깨져야 한다.
     *
     * 확인하는 것: 데이터 URI 는 토큰 없는 우리 `/invite` 그대로(매니페스트 필터와 맞음),
     * 토큰은 extra 에만, 패키지는 못박혀 있고, 폴백 URL 에는 토큰이 없다.
     */
    @Test
    fun `웹 폴백 intent 는 토큰을 URL 이 아니라 extra 에 싣는다`() {
        val fallback = "https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dcom.daengs.app"
        val uri = "intent://daengapi.weareithero.cloud/invite#Intent;scheme=https;package=com.daengs.app;" +
            "S.${InviteLink.WEB_FALLBACK_EXTRA}=$token;S.browser_fallback_url=$fallback;end"

        val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)

        assertEquals("https://daengapi.weareithero.cloud/invite", intent.dataString)
        assertEquals("com.daengs.app", intent.`package`)
        assertEquals(token, intent.getStringExtra(InviteLink.WEB_FALLBACK_EXTRA))
        // 정식 계약(프래그먼트)으로는 안 잡히고, 보조 통로로만 잡힌다.
        assertNull(InviteLink.tokenOf(intent.dataString))
        assertEquals(
            token,
            InviteLink.tokenOfWebFallback(intent.dataString, intent.getStringExtra(InviteLink.WEB_FALLBACK_EXTRA)),
        )
        // 앱이 없을 때 브라우저가 진짜로 여는 주소에는 토큰이 없다.
        val browserFallback = intent.getStringExtra("browser_fallback_url")
        assertEquals("https://play.google.com/store/apps/details?id=com.daengs.app", browserFallback)
    }
}
