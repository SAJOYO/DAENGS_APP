package com.daengs.app.dogcard

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 카드 한 장을 남에게 보내는 Intent 와, 그 그림이 놓이는 자리.
 *
 * **읽기 권한 플래그가 빠지면 공유 시트는 뜨는데 받는 앱에서 그림이 안 열린다** —
 * 시트가 떴으니 된 줄 알기 쉬운 실패라 여기서 잡는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CardShareTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `그림 하나를 보내는 Intent 다`() {
        val uri = Uri.parse("content://com.daengs.app.fileprovider/cards/card.png")

        val intent = shareCardIntent(uri)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
    }

    @Test
    fun `받는 앱이 그림을 열 수 있다`() {
        val intent = shareCardIntent(Uri.parse("content://x/cards/card.png"))

        assertTrue(
            "읽기 권한이 없으면 시트는 뜨고 그림만 안 열린다",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    @Test
    fun `글을 같이 안 보낸다`() {
        // 카톡에서 그림과 별개의 말풍선이 하나 더 간다.
        val intent = shareCardIntent(Uri.parse("content://x/cards/card.png"))

        assertEquals(null, intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `공유 파일은 cards 폴더 밖으로 안 나간다`() {
        // FileProvider 가 여는 범위를 정하는 자리다. cardFileName 이 이미 걸러 주지만
        // 부르는 곳이 늘었을 때를 대비해 여기서도 막는다.
        val dir = File(context.cacheDir, "cards").canonicalFile

        listOf(
            "daengs-cabbage-20260902-1830-3f9a12.png",
            "../../shared_prefs/daengs_session.xml",
            "/data/data/com.daengs.app/databases/cards.db",
            "..",
            "",
        ).forEach { name ->
            val file = cardShareFile(context, name).canonicalFile
            assertEquals("$name -> ${file.path}", dir, file.parentFile)
            assertTrue("$name 은 png 로 나간다", file.name.endsWith(".png"))
        }
    }

    @Test
    fun `쓸 수 있는 이름만 남는다`() {
        assertEquals("daengs-cabbage-20260902-1830-3f9a12.png", safeShareName("daengs-cabbage-20260902-1830-3f9a12.png"))
        assertEquals("card.png", safeShareName(".."))
        assertEquals("session.png", safeShareName("../../shared_prefs/session.xml"))
    }
}
