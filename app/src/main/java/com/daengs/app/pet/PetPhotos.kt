package com.daengs.app.pet

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 우리 아이 프로필 사진이 놓이는 **기기 쪽 자리.**
 *
 * **이제 원본은 서버다.** 여기 있는 것은 캐시다 — 서버에 올려 두고, 그리려고 받아
 * 둔 사본이다. 그래서 폰을 바꿔도 사진이 따라온다.
 *
 * ⚠️ **캐시를 없애고 매번 받아오면 안 된다.** 세 가지가 깨진다 —
 *    오프라인(지하철에서 빈칸), 속도(목록마다 네트워크), 그리고 서버 저장소가
 *    아직 안 켜졌으면 **503** 이다. 그때도 화면은 살아야 한다.
 *
 * 서버와 주고받는 것은 [PetPhotoApi] 가, 무엇을 올리고 받을지는
 * [PetPhotoSync] 가 정한다. 여기는 **파일만** 다룬다.
 *
 * **경로를 어디에도 안 적고 id 에서 계산한다** — `dogcard/CardFiles.kt` 가 같은 문제를
 * 이미 풀어 뒀다. 절대경로를 적어 두면 앱을 옮기거나 사용자 프로필이 바뀌어
 * `filesDir` 이 달라지는 순간 전부 죽은 링크가 된다.
 */
class PetPhotos(private val context: Context) {

    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    /** 이 아이의 사진. 없을 수도 있다 — 사진은 선택이다. */
    fun photoFile(petId: String): File = File(dir, fileNameOf(petId))

    suspend fun write(petId: String, photo: Bitmap): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            photoFile(petId).outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            // 사용자가 새로 고른 사진은 **아직 서버 것이 아니다.** 도장을 지워서
            // 다음 동기화가 이것을 "올려야 할 사진" 으로 보게 한다.
            clearStamp(petId)
        }.isSuccess
    }

    /**
     * 서버에서 받아 온 바이트를 그대로 둔다.
     *
     * [write] 와 달리 다시 굽지 않는다 — 서버에 있는 것이 이미 우리가 구워 올린
     * 그 바이트라, 여기서 또 압축하면 볼 때마다 조금씩 나빠진다.
     */
    suspend fun writeBytes(petId: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching { photoFile(petId).writeBytes(bytes) }.isSuccess
    }

    /** 올릴 때 읽는다. 없으면 null. */
    suspend fun readBytes(petId: String): ByteArray? = withContext(Dispatchers.IO) {
        photoFile(petId).takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    /** 사진이 기기에 있나. 바이트를 읽지 않는다. */
    fun exists(petId: String): Boolean = photoFile(petId).exists()

    /** 기본 그림으로 되돌리기. 파일이 없어지면 화면이 견종 그림을 쓴다. */
    suspend fun delete(petId: String) = withContext(Dispatchers.IO) {
        runCatching { photoFile(petId).delete() }
        runCatching { stampFile(petId).delete() }
        Unit
    }

    // -- 캐시 도장 --------------------------------------------------------
    //
    // 받아 둔 사진 옆에 **서버의 photo_updated_at 을 그대로** 적어 둔다. 목록이 올 때
    // 그 값과 비교해서 같으면 안 받는다.
    //
    // **도장이 없다 = 서버에서 온 것이 아니다.** 사용자가 방금 고른 사진이거나, 서버가
    // 생기기 전부터 이 폰에 있던 사진이다. 동기화는 그것을 "올려야 할 것" 으로 본다 —
    // 그래서 옛 사용자의 사진이 첫 동기화에 저절로 올라간다.

    private fun stampFile(petId: String): File = File(dir, "${stampNameOf(petId)}")

    /** 이 사진이 서버의 어느 판인가. null 이면 아직 서버 것이 아니다. */
    suspend fun stamp(petId: String): String? = withContext(Dispatchers.IO) {
        stampFile(petId).takeIf { it.exists() }?.let {
            runCatching { it.readText().trim().ifEmpty { null } }.getOrNull()
        }
    }

    /** 서버에서 받아 왔거나 올리고 확정했을 때. */
    suspend fun setStamp(petId: String, updatedAt: String) = withContext(Dispatchers.IO) {
        runCatching { stampFile(petId).writeText(updatedAt) }
        Unit
    }

    private fun clearStamp(petId: String) {
        runCatching { stampFile(petId).delete() }
    }

    /**
     * 탈퇴할 때.
     *
     * **이건 우리 아이의 진짜 사진이다.** 안 지우면 다음에 이 폰으로 로그인한 사람이
     * 남의 강아지 사진을 물려받는다 — 방·카드·산책 좌표를 지우는 것과 같은 이유다.
     */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        Unit
    }

    companion object {
        const val DIR = "pet-photos"

        /**
         * id 를 파일 이름으로.
         *
         * **씻어서 쓴다.** id 는 서버가 준 문자열이라 우리가 정한 모양이 아니다.
         * `/` 나 `..` 가 섞이면 이 폴더 밖을 가리키게 된다 — `CardShare.safeShareName`
         * 과 같은 이유로, 여기서 막지 않으면 남의 파일을 덮어쓰거나 지운다.
         *
         * 알파가 필요 없어서 JPEG 이다. 512x512 한 장이 60KB 쯤인데 PNG 로 두면 400KB 다.
         */
        fun fileNameOf(petId: String): String {
            val kept = petId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            return "${kept.ifEmpty { "pet" }}.jpg"
        }

        /** 도장 파일 이름. 사진과 **같은 씻김**을 거쳐야 한 아이를 가리킨다. */
        fun stampNameOf(petId: String): String = fileNameOf(petId).removeSuffix(".jpg") + ".stamp"

        /** 구운 사진의 한 변. 제일 크게 쓰이는 자리가 120dp(≈360px)라 이만하면 넉넉하다. */
        const val SIDE = 512
    }
}
