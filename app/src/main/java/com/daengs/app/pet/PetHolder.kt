package com.daengs.app.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 내 강아지 목록을 들고 있는 자리.
 *
 * **서버가 진짜다.** 여기 있는 것은 화면에 그리려고 들고 있는 사본이고, 무엇이든
 * 바꾼 뒤에는 목록을 다시 받아 온다 — 대표 승계처럼 **서버가 알아서 하는 일**이
 * 있어서, 응답 하나만 보고 앱이 상태를 짐작하면 어긋난다.
 *
 * 기기에 저장하지 않는다. 강아지는 계정에 딸린 데이터라 로그인해야 보이고,
 * 로그인하면 서버에서 받아 오면 된다. 기기에 캐시를 두면 "폰에는 있는데 서버에는
 * 없는" 상태를 다룰 자리가 하나 더 생긴다.
 */
class PetHolder {
    /** 마지막으로 받아 온 목록. 아직 한 번도 못 받았으면 null 이다 (빈 목록과 다르다). */
    var pets: List<Pet>? by mutableStateOf(null)
        private set

    /** 서버가 정한 마릿수 상한. 못 받아 왔으면 null 이라 `+` 를 감추지 않는다. */
    var maxPets: Int? by mutableStateOf(null)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /** 대표. 없으면 null — 마지막 한 마리를 지운 직후가 그렇다. */
    val primary: Pet? get() = pets?.firstOrNull { it.isPrimary }

    /** 아직 한 마리도 없나. **모름(null)과 다르다** — 라우팅이 이걸로 온보딩을 정한다. */
    val isEmpty: Boolean? get() = pets?.isEmpty()

    val canAddMore: Boolean
        get() = (pets?.size ?: 0) < (maxPets ?: Int.MAX_VALUE)

    fun clearError() {
        error = null
    }

    /** 로그아웃·탈퇴할 때. 다음 사람이 남의 강아지를 보면 안 된다. */
    fun forget() {
        pets = null
        maxPets = null
        error = null
    }

    /** 서버에서 목록을 다시 받아 온다. */
    suspend fun refresh(token: String): Boolean = guard {
        PetApi.list(token).onSuccess {
            pets = it.pets
            maxPets = it.maxPets
        }
    }

    suspend fun add(token: String, draft: PetDraft): Boolean =
        guard { PetApi.create(token, draft) } andThen { refresh(token) }

    suspend fun edit(token: String, id: String, draft: PetDraft): Boolean =
        guard { PetApi.update(token, id, draft) } andThen { refresh(token) }

    suspend fun remove(token: String, id: String): Boolean =
        guard { PetApi.delete(token, id) } andThen { refresh(token) }

    suspend fun choosePrimary(token: String, id: String): Boolean =
        guard { PetApi.setPrimary(token, id) } andThen { refresh(token) }

    /**
     * 한 번의 서버 왕복. 실패하면 서버가 준 문장을 [error] 에 남기고 false 를 준다.
     *
     * 저쪽이 사용자에게 보여 줄 말로 `detail` 을 써 놨다 ("강아지는 5마리까지…").
     * 그래서 여기서 문장을 새로 짓지 않고 그대로 쓴다.
     */
    private suspend fun guard(action: suspend () -> Result<*>): Boolean {
        busy = true
        error = null
        val result = action()
        busy = false
        result.exceptionOrNull()?.let { error = it.message ?: "잠시 뒤 다시 시도해 주세요." }
        return result.isSuccess
    }

    /**
     * 성공했을 때만 이어서 한다.
     *
     * 바꾸고 나면 **반드시 목록을 다시 받아 온다.** 서버가 말없이 바꾸는 것이 있어서다 —
     * 첫 아이를 대표로 세우고, 대표를 지우면 다음 아이로 승계한다. 그 규칙을 앱에서
     * 다시 계산하면 두 벌이 되고 언젠가 갈라진다.
     */
    private suspend infix fun Boolean.andThen(next: suspend () -> Boolean): Boolean =
        if (this) next() else false
}

@Composable
fun rememberPetHolder(): PetHolder = remember { PetHolder() }
