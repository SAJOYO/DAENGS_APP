package com.daengs.app.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 한 아이의 보호자 목록을 들고 있는 자리. [PetHolder] 와 같은 모양이다.
 *
 * **서버 순서를 그대로 쓴다.** 저쪽 `list_members` 가 대표를 맨 앞에 놓고 그다음 돌보미를
 * 참여 순으로 준다. 앱이 다시 정렬하면 두 벌이 되고 언젠가 갈라진다.
 *
 * **실패를 빈 목록으로 바꾸지 않는다.** [members] 가 null 이면 "아직 못 받았다" 이고 빈
 * 목록은 "받아 봤더니 아무도 없다" 라 뜻이 다르다 — 실패한 조회를 빈 목록으로 그리면
 * 화면이 "보호자가 없어요" 라고 단언하게 된다.
 */
class PetMemberHolder(
    private val listMembers: suspend (String, String) -> Result<PetMemberList> =
        PetMemberApi()::listMembers,
) {
    /**
     * 몇 번째 조회인가. **늦게 온 응답을 버리는 데 쓴다** — 계정을 바꾸거나 다른 아이로
     * 넘어간 뒤 이전 요청이 도착하면, 지금 화면과 상관없는 사람 목록이 앉는다.
     */
    private var generation = 0L

    /** 지금 들고 있는 목록이 어느 아이 것인가. */
    var petId: String? by mutableStateOf(null)
        private set

    /** 받아 온 구성원. **null 은 아직 못 받은 것이고 빈 목록과 다르다.** */
    var members: List<PetMember>? by mutableStateOf(null)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /** 로그아웃·탈퇴할 때. 다음 사람이 남의 집 보호자를 보면 안 된다. */
    fun forget() {
        generation++
        petId = null
        members = null
        busy = false
        error = null
    }

    /**
     * 서버에서 받아 온다.
     *
     * **다른 아이로 넘어가면 들고 있던 목록을 먼저 비운다.** 안 비우면 새 목록이 올 때까지
     * 앞 아이의 보호자가 이 아이 것처럼 떠 있다.
     */
    suspend fun load(token: String, petId: String): Boolean {
        val generation = ++this.generation
        if (this.petId != petId) {
            this.petId = petId
            members = null
        }
        busy = true
        error = null
        try {
            val result = listMembers(token, petId)
            result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            if (generation != this.generation) return false
            result
                .onSuccess { members = it.members }
                .onFailure { error = it.message ?: "보호자 목록을 불러오지 못했어요." }
            return result.isSuccess
        } finally {
            if (generation == this.generation) busy = false
        }
    }
}

@Composable
fun rememberPetMemberHolder(): PetMemberHolder = remember { PetMemberHolder() }
