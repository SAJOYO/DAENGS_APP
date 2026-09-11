package com.daengs.app.screening

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.ui.storage.RECEIPT_EDGE
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 영수증은 **피부 병변보다 크게 굽는다.** 진단 크롭은 원본보다 한참 작아도 되지만
 * 영수증은 항목명이 잔글씨라 1600 에서 뭉갠다.
 *
 * 잔글씨가 실제로 읽히는지는 실기기가 본다. 여기서 잡는 것은 **[Photo.prepare] 의
 * `edge` 인자가 정말 그 크기로 줄이는가** 와, 기존 호출부가 안 바뀌었다는 것이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoEdgeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `영수증은 피부보다 큰 변으로 굽는다`() {
        assertEquals(1600, Photo.MAX_EDGE)
        assertEquals(2400, RECEIPT_EDGE)
        assertTrue("영수증이 더 커야 한다", RECEIPT_EDGE > Photo.MAX_EDGE)
    }

    @Test
    fun `edge 를 주면 그 크기로 줄인다`() = runBlocking {
        val source = sourceImage(width = 3000, height = 4000)

        val prepared = Photo.prepare(context, source, edge = RECEIPT_EDGE).getOrThrow()
        val decoded = BitmapFactory.decodeByteArray(prepared.jpeg, 0, prepared.jpeg.size)

        assertEquals("긴 변이 요청한 크기다", RECEIPT_EDGE, maxOf(decoded.width, decoded.height))
        assertTrue("가로세로 비가 유지된다", decoded.width < decoded.height)
    }

    @Test
    fun `edge 를 안 주면 기존 호출부와 같은 1600 이다`() = runBlocking {
        val source = sourceImage(width = 3000, height = 4000)

        val prepared = Photo.prepare(context, source).getOrThrow()
        val decoded = BitmapFactory.decodeByteArray(prepared.jpeg, 0, prepared.jpeg.size)

        assertEquals(Photo.MAX_EDGE, maxOf(decoded.width, decoded.height))
    }

    @Test
    fun `원본이 이미 작으면 늘리지 않는다`() = runBlocking {
        val source = sourceImage(width = 800, height = 600)

        val prepared = Photo.prepare(context, source, edge = RECEIPT_EDGE).getOrThrow()
        val decoded = BitmapFactory.decodeByteArray(prepared.jpeg, 0, prepared.jpeg.size)

        assertEquals(800, decoded.width)
        assertEquals(600, decoded.height)
    }

    /** 임시 JPEG 하나를 캐시에 굽고 그 자리를 가리킨다. */
    private fun sourceImage(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("receipt", ".jpg", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
}
