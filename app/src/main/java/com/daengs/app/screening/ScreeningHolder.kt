package com.daengs.app.screening

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 피부 변화 기록을 들고 있는 자리. `PetHolder` · `GaitHolder` 와 같은 결이다 —
 * `ViewModel` 을 안 쓰고 `mutableStateOf` 홀더를 `remember` 로 잡는다 (이 저장소의 패턴).
 *
 * **서버가 진짜다.** 여기 있는 것은 그리려고 든 사본이라, 기기에 저장하지 않는다.
 * 기록은 계정에 딸린 것이라 로그인해야 보이고, 로그인하면 받아 오면 된다.
 *
 * 진행 중인 진단은 여기 안 둔다. 그건 **대화의 한 줄**이라 말풍선 옆자리에 붙어 있어야
 * 하고, `ChatScreen` 이 `ChatEntry` 로 들고 있다 (보행이 하는 것과 같다).
 */
@Stable
class ScreeningHolder(private val accessToken: suspend () -> String?) {

    /** 최근 것이 앞이다. 아직 한 번도 못 받았으면 null 이다 (빈 목록과 다르다). */
    var records: List<ScreeningRecord>? by mutableStateOf(null)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    /**
     * 마지막으로 실패한 이유. **저장소가 안 켜졌을 때는 안 채운다** — 그건 사용자가
     * 할 수 있는 게 없는 상태라, 화면이 "아직 준비 중" 으로 조용히 비어 있는 편이 낫다.
     */
    var error: String? by mutableStateOf(null)
        private set

    /**
     * 기록마다 받아 둔 사진. **목록에는 주소가 안 와서** 하나를 열 때 받는다
     * (저쪽이 N 장마다 저장소를 두드리지 않으려고 그렇게 뒀다).
     */
    private val photos = mutableStateMapOf<String, ImageBitmap>()

    operator fun get(recordId: String): ImageBitmap? = photos[recordId]

    fun clearError() {
        error = null
    }

    /** 로그아웃·탈퇴할 때. 다음 사람이 남의 피부 사진을 보면 안 된다. */
    fun forget() {
        records = null
        photos.clear()
        error = null
    }

    /** 목록을 받아 온다. [petId] 를 주면 그 아이 것만. */
    suspend fun refresh(petId: String? = null) {
        val token = accessToken() ?: return
        busy = true
        ScreeningRecordApi.list(token, petId)
            .onSuccess {
                records = it
                error = null
            }
            .onFailure { error = it.message }
        busy = false
    }

    /**
     * 하나를 연다. 사진 주소는 **단건 조회에서만** 오므로 여기서 받아 그림까지 만든다.
     *
     * 이미 받아 둔 사진은 다시 안 받는다 — 목록을 오르내릴 때마다 받으면 느리고
     * 데이터를 먹는다.
     */
    suspend fun open(recordId: String): ScreeningRecord? {
        val token = accessToken() ?: return null
        val record = ScreeningRecordApi.get(token, recordId).getOrElse {
            error = it.message
            return null
        }
        if (photos[recordId] == null) {
            record.photoUrl?.let { url ->
                ScreeningRecordApi.photo(url)
                    .mapCatching { decode(it) }
                    .onSuccess { image -> if (image != null) photos[recordId] = image }
            }
        }
        return record
    }

    /** 기록 하나를 지운다. 서버에서 사진까지 지워진다. */
    suspend fun delete(recordId: String): Boolean {
        if (records?.firstOrNull { it.recordId == recordId }?.canDelete == false) {
            error = "이 피부 기록을 지울 권한이 없어요."
            return false
        }
        val token = accessToken() ?: return false
        return ScreeningRecordApi.delete(token, recordId)
            .onSuccess {
                records = records?.filterNot { it.recordId == recordId }
                photos.remove(recordId)
            }
            .onFailure { error = it.message }
            .isSuccess
    }

    private fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** 앱에 하나만 둔다 — 기록은 화면이 아니라 계정에 딸린 것이다. */
@Composable
fun rememberScreeningHolder(accessToken: suspend () -> String?): ScreeningHolder =
    remember(accessToken) { ScreeningHolder(accessToken) }
