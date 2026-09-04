package com.daengs.app.pet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 화면이 읽는 프로필 사진.
 *
 * **파일 경로를 화면에 넘기지 않는다.** 경로는 사진을 바꿔도 그대로라, 경로를 키로
 * 읽는 쪽(`ui/dex/CardArt.kt` 의 `rememberFileImage`)은 **바뀐 줄을 모른다.**
 * 여기에 읽어 둔 그림을 담아 두면 바꾸는 순간 쓰는 화면이 다 같이 다시 그려진다.
 *
 * 강아지는 많아야 서넛이라 목록이 올 때 한 번에 읽어도 부담이 없다.
 * 사진은 [PetPhotos.SIDE] 짜리 한 장이다.
 */
@Stable
class PetPhotoHolder(private val files: PetPhotos) {

    private val photos = mutableStateMapOf<String, ImageBitmap>()

    /** 이 아이의 사진. null 이면 안 올렸거나 못 읽은 것이고, 화면은 견종 그림을 쓴다. */
    operator fun get(petId: String?): ImageBitmap? = petId?.let { photos[it] }

    /** 목록이 올 때. 없어진 아이의 사진은 지도에서도 뺀다. */
    suspend fun load(petIds: Collection<String>) {
        val read = withContext(Dispatchers.IO) {
            petIds.mapNotNull { id -> decode(id)?.let { id to it } }
        }
        photos.keys.retainAll(petIds.toSet())
        read.forEach { (id, image) -> photos[id] = image }
        // 파일이 사라진 아이는 지도에서도 빠져야 견종 그림으로 되돌아간다.
        petIds.filter { id -> read.none { it.first == id } }.forEach { photos.remove(it) }
    }

    /** 사진을 바꾼다. 쓰는 화면이 다 같이 바뀐다. */
    suspend fun set(petId: String, photo: Bitmap): Boolean {
        if (!files.write(petId, photo)) return false
        photos[petId] = photo.asImageBitmap()
        return true
    }

    /** 기본 그림으로 되돌린다. */
    suspend fun clear(petId: String) {
        files.delete(petId)
        photos.remove(petId)
    }

    /** 탈퇴할 때. 파일과 화면 양쪽에서 지운다. */
    suspend fun forgetEverything() {
        files.deleteAll()
        photos.clear()
    }

    private fun decode(petId: String): ImageBitmap? {
        val file = files.photoFile(petId)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
    }
}

/** 앱에 하나만 둔다 — 사진은 화면이 아니라 이 기기에 딸린 것이다. */
@Composable
fun rememberPetPhotoHolder(): PetPhotoHolder {
    val context = LocalContext.current.applicationContext
    return remember { PetPhotoHolder(PetPhotos(context)) }
}
