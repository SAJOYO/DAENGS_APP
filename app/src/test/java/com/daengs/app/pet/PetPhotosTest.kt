package com.daengs.app.pet

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 프로필 사진이 놓이는 자리.
 *
 * **id 는 서버가 준 문자열이다.** 우리가 정한 모양이 아니라서, 그대로 파일 이름에 쓰면
 * `/` 나 `..` 하나로 이 폴더 밖을 가리킬 수 있다 — 남의 파일을 덮어쓰거나 지운다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PetPhotosTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val photos = PetPhotos(context)

    @Test
    fun `사진은 pet-photos 폴더 안에만 놓인다`() {
        val dir = File(context.filesDir, PetPhotos.DIR).canonicalFile

        listOf(
            "3f9a12de-0000-4000-8000-000000000000",
            "../../shared_prefs/daengs_session",
            "/data/data/com.daengs.app/databases/cards.db",
            "..",
            "",
        ).forEach { id ->
            val file = photos.photoFile(id).canonicalFile
            assertEquals("$id -> ${file.path}", dir, file.parentFile)
            assertTrue("$id 은 jpg 로 놓인다", file.name.endsWith(".jpg"))
        }
    }

    @Test
    fun `id 가 다르면 파일도 다르다`() {
        // 씻으면서 서로 같은 이름이 되면 다른 아이의 사진을 덮어쓴다.
        assertTrue(PetPhotos.fileNameOf("dog-1") != PetPhotos.fileNameOf("dog-2"))
        assertEquals("dog-1.jpg", PetPhotos.fileNameOf("dog-1"))
    }

    @Test
    fun `쓸 수 없는 id 는 한 이름으로 모인다`() {
        // 이런 id 는 실제로 안 오지만, 와도 폴더 밖으로는 못 나간다.
        assertEquals("pet.jpg", PetPhotos.fileNameOf(".."))
        assertEquals("pet.jpg", PetPhotos.fileNameOf(""))
    }
}
