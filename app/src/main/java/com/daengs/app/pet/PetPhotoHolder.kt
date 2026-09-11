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

    /**
     * 사진을 바꾼다. 쓰는 화면이 다 같이 바뀐다.
     *
     * **기기에 먼저 쓰고 서버는 나중이다.** 서버를 기다렸다가 화면을 바꾸면 느린 망에서
     * 몇 초 동안 옛 사진이 남는다 — 사용자는 방금 고른 것을 바로 봐야 한다.
     * 못 올리면 도장이 없는 채로 남고, **다음 동기화가 다시 올린다.**
     */
    suspend fun set(petId: String, photo: Bitmap, token: String? = null): Boolean {
        if (!files.write(petId, photo)) return false
        photos[petId] = photo.asImageBitmap()
        if (token != null) upload(petId, token)
        return true
    }

    /**
     * 기본 그림으로 되돌린다.
     *
     * 서버에서도 지운다. **못 지워도 기기에서는 지운다** — 사용자가 지우라고 한 것을
     * 망 사정 때문에 안 지우면 안 된다. 서버에 남은 것은 다음 동기화가 도장 없는
     * 상태를 보고 정리한다.
     */
    suspend fun clear(petId: String, token: String? = null) {
        if (token != null) PetPhotoApi.delete(token, petId)
        files.delete(petId)
        photos.remove(petId)
    }

    /**
     * 목록을 받은 뒤 서버와 맞춘다.
     *
     * ⚠️ **실패해도 조용하다.** 사진은 있으면 좋은 것이지 없으면 화면이 못 뜨는 것이
     *    아니다. 저장소가 아직 안 켜졌으면 저쪽이 503 을 주는데(D-052), 그걸 에러로
     *    띄우면 사진을 한 번도 안 쓴 사람에게 매번 팝업이 뜬다.
     *
     * 무엇을 할지는 [photoActionFor] 가 정한다 — 그 셈만 따로 시험한다.
     */
    suspend fun sync(pets: List<Pet>, token: String) {
        for (pet in pets) {
            val action = photoActionFor(
                serverHasPhoto = pet.hasPhoto,
                serverUpdatedAt = pet.photoUpdatedAt,
                localExists = files.exists(pet.id),
                localStamp = files.stamp(pet.id),
                canUpload = pet.isOwner,
            )
            when (action) {
                PhotoAction.NOTHING -> Unit
                PhotoAction.UPLOAD -> upload(pet.id, token)
                PhotoAction.DOWNLOAD -> download(pet.id, pet.photoUpdatedAt, token)
                PhotoAction.DELETE_LOCAL -> {
                    files.delete(pet.id)
                    photos.remove(pet.id)
                }
            }
        }
    }

    private suspend fun upload(petId: String, token: String) {
        val bytes = files.readBytes(petId) ?: return
        PetPhotoApi.ticket(token, petId)
            .mapCatching { ticket -> PetPhotoApi.upload(ticket, bytes).getOrThrow() }
            .mapCatching { PetPhotoApi.confirm(token, petId).getOrThrow() }
            .onSuccess { pet ->
                // 확정된 뒤에야 도장을 찍는다. 먼저 찍으면 confirm 이 실패했을 때
                // "서버에 있는 줄 아는" 사진이 되어 다시 안 올린다.
                pet.photoUpdatedAt?.let { files.setStamp(petId, it) }
            }
    }

    private suspend fun download(petId: String, updatedAt: String?, token: String) {
        PetPhotoApi.download(token, petId).onSuccess { bytes ->
            if (!files.writeBytes(petId, bytes)) return@onSuccess
            updatedAt?.let { files.setStamp(petId, it) }
            decode(petId)?.let { photos[petId] = it }
        }
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
