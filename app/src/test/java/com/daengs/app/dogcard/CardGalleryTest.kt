package com.daengs.app.dogcard

import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 카드를 사진첩에 넣는 값.
 *
 * 그림이 예쁜지는 못 잡는다. 여기서 잡는 것은 **어느 길로 가는가**(권한 없는 기기에서
 * 권한이 필요한 길로 가면 그냥 실패한다)와 **어느 앨범에 들어가는가** 다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardGalleryTest {

    @Test
    fun `안드로이드 10 부터 사진첩으로 바로 간다`() {
        // 그 아래에서 MediaStore 에 넣으려면 WRITE_EXTERNAL_STORAGE 가 필요한데
        // 이 앱은 그 권한을 안 받는다. **이 한 줄이 갈림길이다.**
        assertFalse("안드로이드 9", usesMediaStore(28))
        assertTrue("안드로이드 10", usesMediaStore(29))
        assertTrue("안드로이드 13", usesMediaStore(33))
    }

    @Test
    fun `사진첩에 댕스 앨범으로 들어간다`() {
        val values = galleryValues("daengs-cabbage-20260902-1830-3f9a12.png", 1_756_800_000_000L)

        assertEquals("Pictures/댕스", values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals("image/png", values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertEquals(
            "daengs-cabbage-20260902-1830-3f9a12.png",
            values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME),
        )
    }

    @Test
    fun `다 쓰기 전까지는 갤러리에 안 보인다`() {
        // IS_PENDING 을 안 걸면 반쯤 그려진 카드가 사진첩에 잠깐 뜬다.
        val values = galleryValues("card.png", 0L)

        assertEquals(1, values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
    }

    @Test
    fun `시각은 초 단위다`() {
        // 밀리초를 그대로 넣으면 갤러리가 카드를 2554년으로 보낸다.
        val millis = 1_756_800_000_000L
        val values = galleryValues("card.png", millis)

        assertEquals(millis / 1000, values.getAsLong(MediaStore.MediaColumns.DATE_ADDED))
        assertEquals(millis / 1000, values.getAsLong(MediaStore.MediaColumns.DATE_MODIFIED))
    }
}
