package com.daengs.app.pet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 사진이 **누구 것인지 안 섞이는가**, 그리고 **껐다 켜도 남는가.**
 *
 * 로그인해야 볼 수 있는 자리라 화면에서 확인하기가 어렵다. 파일과 보관함은 화면
 * 없이도 그대로 돌릴 수 있어서 여기서 잡는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetPhotoHolderTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val files = PetPhotos(context)

    @Before
    fun clean() {
        File(context.filesDir, PetPhotos.DIR).listFiles()?.forEach { it.delete() }
    }

    /** 크기로 서로를 가른다 — 어느 아이 사진인지 눈이 아니라 값으로 본다. */
    private fun photo(side: Int, color: Int) =
        Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun `앱을 껐다 켜도 사진이 남는다`() = runBlocking {
        PetPhotoHolder(files).set("p1", photo(16, Color.RED))

        // **앱을 다시 켠 자리.** 보관함도 파일 객체도 새로 만든다 — 남아 있는 것은
        // 디스크뿐이다.
        val reopened = PetPhotoHolder(PetPhotos(context))
        assertNull("읽기 전에는 비어 있다", reopened["p1"])
        reopened.load(listOf("p1"))

        assertNotNull("파일에서 다시 읽혀야 한다", reopened["p1"])
        assertEquals(16, reopened["p1"]?.width)
    }

    @Test
    fun `아이가 여럿이어도 안 섞인다`() = runBlocking {
        val holder = PetPhotoHolder(files)
        holder.set("p1", photo(8, Color.RED))
        holder.set("p2", photo(24, Color.GREEN))

        val reopened = PetPhotoHolder(PetPhotos(context))
        reopened.load(listOf("p1", "p2", "p3"))

        assertEquals(8, reopened["p1"]?.width)
        assertEquals(24, reopened["p2"]?.width)
        assertNull("사진을 안 올린 아이는 견종 그림이다", reopened["p3"])
    }

    @Test
    fun `한 아이만 지워도 나머지는 남는다`() = runBlocking {
        val holder = PetPhotoHolder(files)
        holder.set("p1", photo(8, Color.RED))
        holder.set("p2", photo(24, Color.GREEN))

        holder.clear("p1")

        assertNull(holder["p1"])
        assertNotNull(holder["p2"])
        // 파일도 같이 지워져야 다시 켤 때 되살아나지 않는다.
        val reopened = PetPhotoHolder(PetPhotos(context))
        reopened.load(listOf("p1", "p2"))
        assertNull("지운 사진이 되살아나면 안 된다", reopened["p1"])
        assertNotNull(reopened["p2"])
    }

    @Test
    fun `없어진 아이의 사진은 화면에서도 빠진다`() = runBlocking {
        val holder = PetPhotoHolder(files)
        holder.set("p1", photo(8, Color.RED))
        assertNotNull(holder["p1"])

        // 다른 기기에서 지워서 목록에 없어진 경우.
        holder.load(listOf("p2"))

        assertNull(holder["p1"])
    }

    @Test
    fun `탈퇴하면 사진이 하나도 안 남는다`() = runBlocking {
        val holder = PetPhotoHolder(files)
        holder.set("p1", photo(8, Color.RED))
        holder.set("p2", photo(24, Color.GREEN))

        holder.forgetEverything()

        assertNull(holder["p1"])
        assertNull(holder["p2"])
        // **파일이 진짜로 없어져야 한다.** 다음에 이 폰으로 로그인한 사람이
        // 남의 강아지 사진을 물려받으면 안 된다.
        val left = File(context.filesDir, PetPhotos.DIR).listFiles().orEmpty()
        assertTrue("남은 파일: ${left.map { it.name }}", left.isEmpty())
    }
}
