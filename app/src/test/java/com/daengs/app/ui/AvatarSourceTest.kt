package com.daengs.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.daengs.app.miniroom.art.DogBreed
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 어느 얼굴을 그리나.
 *
 * **이 순서 하나가 "내 개가 아닌 얼굴이 뜨는" 사고를 막는다.** 발자국은 "우리가 이
 * 아이 얼굴을 모른다" 는 뜻인데, 사용자가 직접 올렸으면 우리는 아는 것이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvatarSourceTest {

    // Robolectric 은 Compose 의 `ImageBitmap(w, h)` 가 부르는 오버로드를 안 흉내 낸다.
    // 안드로이드 비트맵을 만들어 감싸면 그 자리를 지난다.
    private val photo = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImageBitmap()

    @Test
    fun `올린 사진이 견종 그림보다 앞선다`() {
        val source = avatarSource(photo, DogBreed.BEAGLE)

        assertEquals(AvatarSource.Photo(photo), source)
    }

    @Test
    fun `견종을 몰라도 사진이 있으면 발자국이 아니다`() {
        // 믹스가 이 경우다. 사진을 올렸는데 발자국이 뜨면 올린 보람이 없다.
        val source = avatarSource(photo, null)

        assertEquals(AvatarSource.Photo(photo), source)
    }

    @Test
    fun `사진이 없으면 견종 그림이다`() {
        assertEquals(AvatarSource.Breed(DogBreed.BEAGLE), avatarSource(null, DogBreed.BEAGLE))
    }

    @Test
    fun `둘 다 없으면 발자국이다`() {
        // **아무 견종 얼굴이나 갖다 쓰지 않는다** — 남의 개가 내 프로필에 앉는다.
        assertEquals(AvatarSource.Paw, avatarSource(null, null))
    }
}
