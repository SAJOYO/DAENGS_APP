package com.daengs.app.dogcard

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 카드 그림이 놓이는 자리.
 *
 * **DB 에 경로를 안 적고 id 에서 계산한다.** `DexCard.art` 가 id 에서 assets 경로를
 * 계산하는 것과 같은 결이다. 절대경로를 표에 넣으면 앱을 옮기거나 사용자 프로필이
 * 바뀌어 `filesDir` 이 달라지는 순간 **전부 죽은 링크**가 된다.
 *
 * **원본 사진은 저장하지 않는다.** 누끼는 배경이 통째로 날아가서 뒤에 찍힌 집도
 * 사람도 같이 지워진다 — 원본보다 안전하다.
 */
class CardFiles(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "cards").apply { mkdirs() }

    /** 구멍에 끼울 얼굴. 알파가 필요해서 PNG 다 (JPEG 은 못 쓴다). */
    fun faceFile(id: String): File = File(dir, "$id.png")

    suspend fun writeFace(id: String, face: Bitmap): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            faceFile(id).outputStream().use { face.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
    }

    suspend fun deleteFace(id: String) = withContext(Dispatchers.IO) {
        runCatching { faceFile(id).delete() }
        Unit
    }

    /** 탈퇴할 때. 표를 비우는 것과 짝이다. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        Unit
    }
}
