package com.daengs.app.pet

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 우리 아이 프로필 사진이 놓이는 자리.
 *
 * **서버에는 아직 사진 자리가 없다.** [Pet] 이 받는 것은 이름·견종·성별·중성화·
 * 몸무게·생일뿐이다. 백엔드에 GCP 파일 저장소가 정해지면 그때 옮기기로 했다
 * (2026-09-04). 그래서 **파일을 다루는 것을 이 클래스 하나로 모아 둔다** — 그때
 * 여기 속만 업로드·내려받기로 갈아끼우면 화면은 안 건드려도 된다.
 *
 * 그때까지는 뽑은 카드·방 배치와 같은 처지다. **폰을 바꾸면 사진이 사라지고 견종
 * 그림으로 되돌아간다.**
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
        }.isSuccess
    }

    /** 기본 그림으로 되돌리기. 파일이 없어지면 화면이 견종 그림을 쓴다. */
    suspend fun delete(petId: String) = withContext(Dispatchers.IO) {
        runCatching { photoFile(petId).delete() }
        Unit
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

        /** 구운 사진의 한 변. 제일 크게 쓰이는 자리가 120dp(≈360px)라 이만하면 넉넉하다. */
        const val SIDE = 512
    }
}
