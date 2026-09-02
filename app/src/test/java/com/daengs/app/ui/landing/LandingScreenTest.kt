package com.daengs.app.ui.landing

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.ui.my.PRIVACY_POLICY_LABEL
import com.daengs.app.ui.my.PRIVACY_POLICY_URL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 릴리스는 로그인이 필수라 이 화면을 못 지나면 My 화면에 못 간다. 그래서 방침
 * 링크가 **로그인 전에** 있어야 하고, 누르면 정본 주소로 가야 한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LandingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `privacy policy link is visible before login and opens the deployed page`() {
        compose.setContent {
            LandingScreen(canLogin = true, busy = false, error = null, onKakaoLogin = {}, onSkip = {})
        }

        compose.onNodeWithText(PRIVACY_POLICY_LABEL).assertIsDisplayed().performClick()

        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(PRIVACY_POLICY_URL, started?.dataString)
    }

    @Test
    fun `privacy policy link stays reachable when login is unavailable`() {
        compose.setContent {
            LandingScreen(canLogin = false, busy = false, error = null, onKakaoLogin = {}, onSkip = {})
        }

        compose.onNodeWithText(PRIVACY_POLICY_LABEL).assertIsDisplayed()
    }
}
