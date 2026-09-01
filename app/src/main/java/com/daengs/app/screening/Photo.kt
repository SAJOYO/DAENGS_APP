package com.daengs.app.screening

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 화면에 띄울 그림과 서버로 보낼 바이트. 한 번 읽어 둘 다 만든다.
 *
 * 말풍선에 남는 것은 [thumbnail] 이다. 보낼 크기([Photo.MAX_EDGE])의 비트맵을
 * 그대로 들고 있으면 한 장에 7MB 라, 대화에 몇 장만 쌓여도 메모리가 없다.
 */
class PreparedPhoto(val thumbnail: Bitmap, val jpeg: ByteArray)

/**
 * 갤러리·카메라에서 온 사진을 **줄여서** 들고 온다.
 *
 * 요즘 폰 사진은 4000x3000 에 5MB 를 넘는다. 그대로 두면 두 군데서 터진다.
 * 하나는 서버 한도 12MB([serve.py] 의 `MAX_BYTES`), 다른 하나는 우리 쪽 메모리다 —
 * 원본 비트맵 하나가 48MB 라 대화에 몇 장만 쌓여도 OOM 이 난다.
 *
 * 모델은 어차피 훨씬 작게 잘라 쓰므로 [MAX_EDGE] 로 줄여도 판정에 손해가 없다.
 */
object Photo {

    /** 서버로 보낼 사진의 긴 변. 진단 크롭은 이보다 한참 작다. */
    const val MAX_EDGE = 1600

    /** 말풍선에 남길 그림의 긴 변. 말풍선이 화면 폭의 절반쯤이라 이걸로 충분하다. */
    const val THUMB_EDGE = 512

    private const val JPEG_QUALITY = 90

    suspend fun prepare(context: Context, uri: Uri): Result<PreparedPhoto> =
        withContext(Dispatchers.IO) {
            runCatching {
                // 1) 먼저 크기만 읽는다. 픽셀은 아직 안 올린다.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                check(longest > 0) { "사진을 읽을 수 없습니다." }

                // 2) 2의 거듭제곱으로 미리 줄여서 올린다. 원본을 통째로 올린 뒤
                //    줄이면 그 순간 메모리를 다 쓴다.
                val options = BitmapFactory.Options().apply {
                    inSampleSize = generateSequence(1) { it * 2 }
                        .first { longest / it <= MAX_EDGE * 2 }
                }
                val decoded = context.contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: error("사진을 읽을 수 없습니다.")

                // 3) EXIF 회전을 실제로 돌려 놓는다.
                //    카메라는 센서를 그대로 저장하고 "돌려서 보라"는 표시만 남긴다.
                //    화면은 그 표시를 보지만 **서버는 못 본다** — 안 돌리면 모델이
                //    옆으로 누운 사진을 받는다.
                val upright = context.contentResolver.openInputStream(uri).use { stream ->
                    val orientation = runCatching {
                        ExifInterface(stream!!).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                    decoded.rotated(orientation)
                }

                val scaled = upright.scaledToFit(MAX_EDGE)
                if (scaled !== upright) upright.recycle()
                val jpeg = ByteArrayOutputStream().also {
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
                }.toByteArray()
                // 압축이 끝나면 큰 비트맵은 필요 없다. 썸네일만 남긴다.
                val thumbnail = scaled.scaledToFit(THUMB_EDGE)
                if (thumbnail !== scaled) scaled.recycle()
                PreparedPhoto(thumbnail, jpeg)
            }
        }

    /**
     * 카메라 앱에 넘길 자리. `content://` 여야 한다 — API 24 부터 `file://` 을 넘기면
     * 카메라 앱 쪽에서 FileUriExposedException 이 난다.
     *
     * 캐시에 쓰고 이름을 고정한다. 찍을 때마다 새 파일을 만들면 캐시가 계속 늘고,
     * 사진 한 장을 보내고 나면 다시 쓸 일이 없다.
     */
    fun cameraTarget(context: Context): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile(context))

    /** 같은 자리를 **파일로**. 앱 안 카메라는 `content://` 가 아니라 파일에 쓴다. */
    fun cameraFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture.jpg")
    }

    private fun Bitmap.rotated(orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
            .also { if (it !== this) recycle() }
    }

    private fun Bitmap.scaledToFit(edge: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= edge) return this
        val ratio = edge.toFloat() / longest
        // **여기서 recycle 하지 않는다.** 원본을 두 번 줄여 쓰는 자리가 있어서,
        // 안에서 지우면 두 번째 호출이 이미 지워진 비트맵을 받는다. 호출자가 지운다.
        return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }
}
