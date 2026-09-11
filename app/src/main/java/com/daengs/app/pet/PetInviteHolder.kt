package com.daengs.app.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 한 아이의 초대를 들고 있는 자리. [PetMemberHolder] 와 같은 모양이다.
 *
 * **방금 만든 토큰은 목록과 따로 둔다** ([justCreated]). 목록에는 토큰이 없어서, 생성 직후
 * 목록을 다시 받아 같은 자리에 담으면 그 순간 토큰이 사라진다 — 사용자는 아직 공유도 못
 * 했는데 링크가 없어진다.
 */
class PetInviteHolder(
    private val api: PetInviteApi = PetInviteApi(),
    /** 지금. **기기 시계를 화면·홀더가 직접 읽지 않는다** — 테스트가 실제 시각에 안 기댄다. */
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var generation = 0L

    var petId: String? by mutableStateOf(null)
        private set

    /** 받아 온 초대. **null 은 아직 못 받은 것이고 빈 목록과 다르다.** */
    var invites: List<PetInvite>? by mutableStateOf(null)
        private set

    /**
     * 방금 만들어 아직 공유하지 않은 초대. **목록 갱신이 이 값을 덮지 않는다.**
     *
     * 사용자가 공유를 마치거나 화면을 닫으면 [clearCreated] 로 비운다. 잃어버리면 그
     * 초대는 취소하고 새로 만드는 수밖에 없다 — 서버가 해시만 들고 있어서다.
     */
    var justCreated: CreatedInvite? by mutableStateOf(null)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /** 지금 살아 있는 초대 수. 서버 상한(3)과 같은 셈이지만 **최종 판정은 서버 409 다.** */
    val activeCount: Int get() = invites?.activeCount(now()) ?: 0

    fun clearError() {
        error = null
    }

    fun clearCreated() {
        justCreated = null
    }

    fun forget() {
        generation++
        petId = null
        invites = null
        justCreated = null
        busy = false
        error = null
    }

    /** 이 초대가 지금 어떤 상태인가. 화면이 같은 시계를 쓰도록 홀더를 거친다. */
    fun statusOf(invite: PetInvite): InviteStatus = invite.status(now())

    suspend fun load(token: String, petId: String): Boolean {
        val generation = ++this.generation
        if (this.petId != petId) {
            this.petId = petId
            invites = null
            // 다른 아이의 토큰은 들고 있을 이유가 없다. **그 아이 것이면 남긴다** —
            // 첫 생성은 목록을 한 번도 안 받은 상태에서 일어나므로, 여기서 무조건
            // 지우면 방금 만든 링크가 공유되기도 전에 사라진다.
            if (justCreated?.petId != petId) justCreated = null
        }
        busy = true
        error = null
        try {
            val result = api.list(token, petId)
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
     * 초대를 만든다. 성공하면 [justCreated] 에 담고 목록을 다시 받는다.
     *
     * **상한은 서버가 정한다.** 앱의 [activeCount] 는 버튼을 미리 가리는 데만 쓰고,
     * 넘쳤을 때의 문장은 서버 409 의 것을 그대로 보여 준다.
     */
    suspend fun create(token: String, petId: String): Boolean {
        busy = true
        error = null
        val result = api.create(token, petId)
        result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        busy = false
        result
            .onSuccess { justCreated = it }
            .onFailure { error = it.message ?: "초대를 만들지 못했어요." }
        if (result.isSuccess) load(token, petId)
        return result.isSuccess
    }

    /**
     * 초대를 취소한다. 성공하면 목록을 다시 받는다.
     *
     * 방금 만든 초대를 취소하면 들고 있던 토큰도 같이 버린다 — 안 버리면 이미 죽은 링크를
     * 공유할 수 있다.
     */
    suspend fun cancel(token: String, petId: String, inviteId: String): Boolean {
        busy = true
        error = null
        val result = api.cancel(token, petId, inviteId)
        result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        busy = false
        result.onFailure { error = it.message ?: "초대를 취소하지 못했어요." }
        if (result.isSuccess) {
            if (justCreated?.id == inviteId) justCreated = null
            load(token, petId)
        }
        return result.isSuccess
    }
}

@Composable
fun rememberPetInviteHolder(): PetInviteHolder = remember { PetInviteHolder() }
