package com.daengs.app.legal

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 링크가 **바뀌지 않았는지**를 잡는다. Play Console 에 박힌 주소와 같아야 해서, 누가
 * 리팩터링하다 문자열을 건드리면 여기서 걸린다.
 *
 * `sdk = [34]` 는 다른 Robolectric 테스트와 같은 이유다 — Robolectric 이 targetSdk 37 을
 * 아직 모른다 (`WalkDaoTest` 참고).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LegalLinksTest {
    @Test
    fun `privacy policy opens the public daengs-legal page in a browser`() {
        val app = RuntimeEnvironment.getApplication()

        openPrivacyPolicy(app)

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://sajoyo.github.io/daengs-legal/privacy.html", started.dataString)
    }
}
