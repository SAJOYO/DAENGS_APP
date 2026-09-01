package com.daengs.app.gait

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 고른 영상에서 화면에 필요한 것만 뽑아 놓은 것. 원본은 [uri] 로만 들고 있다. */
class PreparedVideo(val uri: Uri, val seconds: Int, val thumbnail: Bitmap?)

/**
 * 갤러리·카메라에서 온 영상을 읽는다. [Photo][com.daengs.app.screening.Photo] 의 영상판이다.
 *
 * **영상은 사진처럼 통째로 안 읽는다.** 10초짜리도 수십 MB 이고 프레임을 다 올릴
 * 이유가 없다. 필요한 건 둘뿐이다 — 길이와 표지 한 장.
 *
 * 길이는 카드가 "10초 이상 권장" 을 지켰는지 말할 때 쓰고, 표지는 목록에서 어느
 * 날 영상인지 알아보는 데 쓴다. **표지가 없어도 화면은 돈다** — 코덱이 프레임을
 * 못 주는 경우가 있어서(일부 기기의 HEVC), 없으면 발바닥 자리표시로 물러선다.
 */
object GaitVideo {

    /** 표지 한 장의 긴 변. 목록 썸네일이 60dp 안쪽이라 이걸로 넉넉하다. */
    private const val THUMB_EDGE = 480

    /** 표지를 뽑을 시각. 0 은 검은 첫 프레임이 잡히는 일이 잦다. */
    private const val THUMB_AT_US = 1_000_000L

    suspend fun prepare(context: Context, uri: Uri): Result<PreparedVideo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val millis = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                    check(millis != null && millis > 0) { "영상 길이를 읽을 수 없습니다." }

                    // 반올림한다. 9.6초를 9초로 깎으면 "10초 이상" 안내를 지킨
                    // 영상이 안 지킨 것으로 표시된다.
                    val seconds = ((millis + 500) / 1000).toInt()

                    // 영상보다 짧은 지점에서 뽑는다. 5초짜리에 1초는 늘 안전하지만,
                    // 그보다 짧은 영상이면 중간으로 물러선다.
                    val at = if (millis * 1000 > THUMB_AT_US) THUMB_AT_US else millis * 500
                    val frame = runCatching {
                        retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }.getOrNull()

                    PreparedVideo(uri, seconds, frame?.scaledToFit(THUMB_EDGE))
                } finally {
                    // **반드시 놓아 준다.** 안 놓으면 코덱 핸들이 남아서, 영상을
                    // 몇 편 고르고 나면 다음 setDataSource 가 조용히 실패한다.
                    runCatching { retriever.release() }
                }
            }
        }

    /**
     * 앱 안 카메라(CameraX)가 찍어 넣을 자리. `content://` 가 아니라 파일이다.
     *
     * 사진이 쓰는 `camera/` 폴더를 **그대로 쓴다.** 파일 이름만 다르다 —
     * `res/xml/file_paths.xml` 이 그 한 폴더만 열어 두고 있어서, 다른 폴더를
     * 쓰려면 FileProvider 범위를 넓혀야 한다. 넓힐 이유가 없다.
     */
    fun cameraFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "gait.mp4")
    }

    private fun Bitmap.scaledToFit(edge: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= edge) return this
        val ratio = edge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
        if (scaled !== this) recycle()
        return scaled
    }
}
