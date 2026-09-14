package com.daengs.app.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 묶음 초대를 들고 있는 자리. [PetInviteHolder] 와 같은 모양이지만 **스코프가 다르다** —
 * 저쪽은 강아지 하나이고 이쪽은 계정 전체다. 상한도 강아지당 3개가 아니라
 * **주보호자당 3묶음**이다 ([MAX_ACTIVE_INVITE_BUNDLES]).
 *
 * **방금 만든 토큰은 목록과 따로 둔다** ([justCreated]). 목록에는 토큰이 없어서, 생성 직후
 * 목록을 다시 받아 같은 자리에 담으면 그 순간 토큰이 사라진다.
 */
class PetInviteBundleHolder(
    private val api: PetInviteBundleApi = PetInviteBundleApi(),
    /** 지금. **기기 시계를 화면·홀더가 직접 읽지 않는다** — 테스트가 실제 시각에 안 기댄다. */
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var generation = 0L

    /** 받아 온 묶음. **null 은 아직 못 받은 것이고 빈 목록과 다르다.** */
    var invites: List<InviteBundle>? by mutableStateOf(null)
        private set

    /**
     * 지금 고른 아이들. **순서를 지킨다** — 사용자가 고른 차례대로 초대장에 적힌다.
     *
     * 화면이 아니라 여기 두는 이유는 화면을 나갔다 와도 고르던 것이 남아야 해서다.
     */
    var selected: List<String> by mutableStateOf(emptyList())
        private set

    /**
     * 방금 만들어 아직 공유하지 않은 묶음. **목록 갱신이 이 값을 덮지 않는다.**
     *
     * 잃어버리면 그 초대는 취소하고 새로 만드는 수밖에 없다 — 서버가 해시만 들고 있어서다.
     */
    var justCreated: CreatedInviteBundle? by mutableStateOf(null)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /** 지금 살아 있는 묶음 수. **최종 판정은 서버 409 다.** */
    val activeCount: Int get() = invites?.activeBundleCount(now()) ?: 0

    /** 지금 초대를 만들 수 있나. 상한은 미리 가리기만 하고 판정은 서버가 한다. */
    val canCreate: Boolean
        get() = selected.isNotEmpty() &&
            selected.size <= MAX_PETS_PER_INVITE &&
            activeCount < MAX_ACTIVE_INVITE_BUNDLES &&
            !busy

    fun clearError() {
        error = null
    }

    fun clearCreated() {
        justCreated = null
    }

    /**
     * 고르기를 뒤집는다. **상한을 넘기면 더 담지 않는다** — 담아 봐야 서버가 422 로 막고,
     * 그때 사용자는 무엇을 빼야 하는지 모른다.
     *
     * @return 상한 때문에 못 담았으면 false.
     */
    fun toggle(petId: String): Boolean {
        if (petId in selected) {
            selected = selected - petId
            return true
        }
        if (selected.size >= MAX_PETS_PER_INVITE) {
            error = "한 번에 ${MAX_PETS_PER_INVITE}마리까지 초대할 수 있어요."
            return false
        }
        selected = selected + petId
        error = null
        return true
    }

    fun clearSelection() {
        selected = emptyList()
    }

    /**
     * 그 아이를 고른 채로 화면을 연다. 강아지 카드에서 들어온 경우다.
     *
     * **이미 고르는 중이면 건드리지 않는다** — 화면에 다시 들어올 때마다 초기화하면
     * 사용자가 두 마리째를 고르다가 뒤로 갔다 오는 순간 처음으로 돌아간다.
     * 다른 아이를 **더** 고르는 것은 그대로 된다.
     */
    fun startWith(petId: String) {
        if (selected.isEmpty()) selected = listOf(petId)
    }

    fun forget() {
        generation++
        invites = null
        selected = emptyList()
        justCreated = null
        busy = false
        error = null
    }

    /** 이 묶음이 지금 어떤 상태인가. 화면이 같은 시계를 쓰도록 홀더를 거친다. */
    fun statusOf(invite: InviteBundle): InviteStatus = invite.status(now())

    suspend fun load(token: String): Boolean {
        val generation = ++this.generation
        busy = true
        error = null
        try {
            val result = api.list(token)
            result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            if (generation != this.generation) return false
            result
                .onSuccess { invites = it.invites }
                .onFailure { error = it.message ?: "초대를 불러오지 못했어요." }
            return result.isSuccess
        } finally {
            if (generation == this.generation) busy = false
        }
    }

    /**
     * 고른 아이들로 묶음을 만든다. 성공하면 [justCreated] 에 담고 목록을 다시 받는다.
     *
     * **성공하면 고르기를 비운다** — 안 비우면 같은 아이들로 두 번째 초대를 만들기 쉽고,
     * 그러면 상한 세 자리가 같은 초대로 찬다.
     */
    suspend fun create(token: String): Boolean {
        if (selected.isEmpty()) return false
        busy = true
        error = null
        val result = api.create(token, selected)
        result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        busy = false
        result
            .onSuccess {
                justCreated = it
                selected = emptyList()
            }
            .onFailure { error = it.message ?: "초대를 만들지 못했어요." }
        if (result.isSuccess) load(token)
        return result.isSuccess
    }

    /**
     * 묶음을 취소한다. **담긴 아이 전부가 함께 취소된다** — 골라 뺄 수 없다.
     *
     * 방금 만든 묶음을 취소하면 들고 있던 토큰도 같이 버린다 — 안 버리면 이미 죽은 링크를
     * 공유할 수 있다.
     */
    suspend fun cancel(token: String, inviteId: String): Boolean {
        busy = true
        error = null
        val result = api.cancel(token, inviteId)
        result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        busy = false
        result.onFailure { error = it.message ?: "초대를 취소하지 못했어요." }
        if (result.isSuccess) {
            if (justCreated?.id == inviteId) justCreated = null
            load(token)
        }
        return result.isSuccess
    }
}

@Composable
fun rememberPetInviteBundleHolder(): PetInviteBundleHolder = remember { PetInviteBundleHolder() }
