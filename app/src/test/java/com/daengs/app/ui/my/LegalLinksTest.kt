package com.daengs.app.ui.my

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegalLinksTest {
    @Test
    fun `privacy row opens the deployed public policy page`() {
        val intent = privacyPolicyIntent()

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(PRIVACY_POLICY_URL, intent.dataString)
        assertEquals(
            "https://sajoyo.github.io/daengs-legal/privacy.html",
            intent.dataString,
        )
    }
}
