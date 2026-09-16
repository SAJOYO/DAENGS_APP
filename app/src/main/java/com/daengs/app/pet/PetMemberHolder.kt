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
    /**
     * 구성원 한 명을 빼는 요청. **맨 앞에 둔다** — `PetMemberHolder { 목록 }` 처럼 뒤따르는
     * 람다가 [listMembers] 로 붙어야 기존 호출이 그대로 컴파일된다 ([PetHolder] 와 같은 이유).
     */
    private val removeMember: suspend (String, String, String) -> Result<Unit> =
        PetMemberApi()::remove,
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

    /**
     * 내보내기·나가기가 도는 중인가. **[busy] 와 따로 둔다** — 조회 진행 표시와 같은 값을
     * 보면 목록을 다시 받는 동안에도 확인 창이 진행 중처럼 보인다 ([PetHolder.renameBusy] 와 같은 결).
     */
    var actionBusy: Boolean by mutableStateOf(false)
        private set

    /**
     * 내보내기·나가기가 실패한 이유. **[error] 와 따로 둔다** — 목록은 멀쩡히 떠 있는데
     * 목록 오류 자리에 적으면 화면이 "목록을 못 불러왔다" 로 바뀐다.
     */
    var actionError: String? by mutableStateOf(null)
        private set

    fun clearActionError() {
        actionError = null
    }

    /** 로그아웃·탈퇴할 때. 다음 사람이 남의 집 보호자를 보면 안 된다. */
    fun forget() {
        generation++
        petId = null
        members = null
        busy = false
        error = null
        actionBusy = false
        actionError = null
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
        // **지난 실패 문구도 같이 지운다.** 내보내기가 한 번 실패한 뒤 화면을 닫았다가
        // 다시 들어오면, 새로 받은 목록 밑에 그때 문장이 그대로 붙어 있었다.
        actionError = null
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

    /**
     * 다른 보호자를 내보낸다 (주보호자만).
     *
     * **목록에서 줄부터 지우지 않는다.** 서버가 막으면(403·409) 지워 놓은 줄을 되살려야
     * 하는데, 그 사이 다른 기기에서 명단이 바뀌었을 수 있어 되살린 것이 진짜인지 알 수
     * 없다. 성공한 뒤 **서버에서 다시 받는 것**이 언제나 맞다.
     *
     * 실패하면 들고 있던 목록을 그대로 두고 [actionError] 만 남긴다 — 사용자는 같은
     * 버튼을 다시 누르면 된다.
     */
    suspend fun remove(token: String, petId: String, targetAppUserId: String): Boolean =
        delete(token, petId, targetAppUserId, "보호자를 내보내지 못했어요.") { load(token, petId) }

    /**
     * 내가 이 아이의 공동 돌봄에서 나간다.
     *
     * 내보내기와 **같은 요청**이다 (저쪽 권한이 「대표 또는 본인」이다). 다만 나가고 나면
     * 그 아이에 대한 권한이 없어서 **목록을 다시 받지 않는다** — 다시 받으면 서버가 404 를
     * 주고, 방금 성공한 일이 화면에서 실패로 보인다. 대신 들고 있던 것을 버린다.
     */
    suspend fun leave(token: String, petId: String, myAppUserId: String): Boolean =
        delete(token, petId, myAppUserId, "공동 돌봄에서 나가지 못했어요.") { forget() }

    /**
     * 한 번의 삭제 왕복. 성공했을 때만 [after] 를 잇는다.
     *
     * **진행 중이면 두 번째 요청을 보내지 않는다** — 확인 창을 닫고 버튼을 다시 눌러도
     * 같은 사람을 두 번 빼는 요청이 나가지 않는다.
     */
    private suspend fun delete(
        token: String,
        petId: String,
        targetAppUserId: String,
        fallbackMessage: String,
        after: suspend () -> Unit,
    ): Boolean {
        if (actionBusy) return false
        actionBusy = true
        actionError = null
        try {
            val result = removeMember(token, petId, targetAppUserId)
            result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            result.onFailure { actionError = it.message ?: fallbackMessage }
            if (result.isFailure) return false
            after()
            return true
        } finally {
            actionBusy = false
        }
    }
}

@Composable
fun rememberPetMemberHolder(): PetMemberHolder = remember { PetMemberHolder() }
