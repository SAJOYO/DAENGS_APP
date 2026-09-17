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
class PetHolder(
    /**
     * 이름만 바꾸는 요청. **맨 앞에 둔다** — `PetHolder { 목록 }` 처럼 뒤따르는 람다가
     * [listPets] 로 붙어야 기존 호출이 그대로 컴파일된다.
     */
    private val renameDisplay: suspend (String, String, String) -> Result<Pet> = PetApi::updateDisplayName,
    /** 지금 로그인한 계정. 목록을 받는 사이 바뀌었으면 [onListed] 를 부르지 않는다. */
    private val currentAccount: () -> String? = { null },
    /**
     * 목록을 받는 데 **성공할 때마다** 부른다 (공동 돌봄 종료 알림 #440 — [CoCareEndWatch]).
     *
     * 여기서 부르는 이유: 목록을 다시 받는 자리가 나가기·수락·내보내기·새로고침 등 여럿이라,
     * 부르는 쪽마다 붙이면 새 자리가 생길 때 빠뜨린다.
     */
    private val onListed: (account: String, pets: List<Pet>) -> Unit = { _, _ -> },
    /** 목록을 받는 요청. **맨 뒤에 둔다** — `PetHolder { 목록 }` 의 뒤따르는 람다가 여기로 붙는다. */
    private val listPets: suspend (String) -> Result<PetList> = PetApi::list,
) {
    private var refreshGeneration = 0L

    /**
     * 이름 바꾸기가 진행 중인가. **[busy] 와 따로 둔다** — 삭제 창과 이름 창이 같은 값을
     * 보면 한쪽의 진행 표시가 다른 창에 뜬다.
     */
    var renameBusy: Boolean by mutableStateOf(false)
        private set

    /** 이름 바꾸기가 실패한 이유. 창이 이것을 그대로 보여 준다. */
    var renameError: String? by mutableStateOf(null)
        private set
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
        refreshGeneration++
        busy = false
        pets = null
        maxPets = null
        error = null
    }

    /** 서버에서 목록을 다시 받아 온다. */
    suspend fun refresh(token: String): Boolean {
        val generation = ++refreshGeneration
        // **요청을 보낼 때의 계정을 잡아 둔다.** 늦게 온 응답을 다른 계정 이름으로 적으면 그 계정에
        // 남의 아이 id 가 기준으로 남는다.
        val account = currentAccount()
        busy = true
        error = null
        try {
            val result = listPets(token)
            result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            if (generation != refreshGeneration) return false
            result.onSuccess {
                pets = it.pets
                maxPets = it.maxPets
                if (account != null && account == currentAccount()) onListed(account, it.pets)
            }.onFailure { error = it.message ?: "반려견을 불러오지 못했어요." }
            return result.isSuccess
        } finally {
            if (generation == refreshGeneration) busy = false
        }
    }

    suspend fun add(token: String, draft: PetDraft): Boolean =
        guard { PetApi.create(token, draft) } andThen { refresh(token) }

    suspend fun edit(token: String, id: String, draft: PetDraft): Boolean =
        if (canManage(id)) guard { PetApi.update(token, id, draft) } andThen { refresh(token) } else false

    suspend fun remove(token: String, id: String): Boolean =
        if (canManage(id)) guard { PetApi.delete(token, id) } andThen { refresh(token) } else false

    /**
     * 아이를 배웅한다. [day] 가 null 이면 되돌린다.
     *
     * **가진 값을 통째로 다시 보낸다.** 서버가 PUT 이라 안 보낸 칸은 null 로 덮인다 —
     * 날짜 하나 바꾸자고 이름과 몸무게를 잃을 수는 없다.
     */
    suspend fun sendOff(token: String, pet: Pet, day: java.time.LocalDate?): Boolean =
        edit(token, pet.id, pet.toDraft().copy(farewellOn = day))

    suspend fun choosePrimary(token: String, id: String): Boolean =
        guard { PetApi.setPrimary(token, id) } andThen { refresh(token) }

    /**
     * **내 이름만** 바꾼다 (`PATCH /app/pets/{id}/display`).
     *
     * [id] 는 목록이 준 값 — 곧 그 사람의 표시 행(`display_pet_id`)이다. 연결된 아이에서
     * 공통 정보는 그룹 주보호자 것이지만 이름은 보호자마다 자기 값이라, 서버는 **행의
     * 대표**(`is_owner`)면 받는다. 그래서 [canManage] 를 지나지 않는다.
     *
     * - **전체 PUT([edit])으로 돌아가지 않는다.** 실패해도 마찬가지다 — 그 길은 연결된
     *   아이에서 409 이고, 뚫리면 주보호자의 견종·건강정보를 덮어쓴다.
     * - **진행 중이면 두 번째 요청을 보내지 않는다.**
     * - 실패하면 [renameError] 에 서버 문장을 남기고 목록을 건드리지 않는다. 입력값은
     *   창이 들고 있다.
     * - 성공하면 목록을 다시 받는다 — 카드와 대표 표시가 같은 목록에서 나온다.
     */
    suspend fun rename(token: String, id: String, name: String): Boolean {
        if (renameBusy) return false
        renameError = null
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            renameError = "이름을 입력해 주세요."
            return false
        }
        // 서버 `PetDisplayUpdate.name` 이 1~40자다. 넘겨 보내 놓고 422 를 받지 않는다.
        if (trimmed.length > PET_NAME_MAX) {
            renameError = "이름은 ${PET_NAME_MAX}자까지예요."
            return false
        }
        // 돌보미(행의 대표가 아님)는 서버가 404 다 — 눌러 봐야 실패할 요청을 안 보낸다.
        if (pets?.firstOrNull { it.id == id }?.isOwner != true) {
            renameError = "내가 등록한 아이의 이름만 바꿀 수 있어요."
            return false
        }
        renameBusy = true
        try {
            val result = renameDisplay(token, id, trimmed)
            result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            result.onFailure { renameError = it.message ?: "이름을 바꾸지 못했어요." }
            if (result.isFailure) return false
            refresh(token)
            return true
        } finally {
            renameBusy = false
        }
    }

    fun clearRenameError() {
        renameError = null
    }

    /** 화면 진입이 남아 있거나 늦은 탭이 도착해도 대표 전용 요청을 보내지 않는다. */
    /**
     * 공통 정보를 고치거나 지울 수 있나.
     *
     * **`isOwner` 가 아니라 [Pet.isGroupOwner] 로 본다.** 내가 등록한 아이라도 남의 아이와
     * 연결되면 그룹 주보호자는 초대한 쪽이고, 전체 PUT·삭제는 서버가 409
     * (`not_group_owner`) 로 막는다 — `isOwner` 로 재면 앱이 버튼을 열어 두고 사용자는
     * 눌러 봐야 실패를 안다. 연결이 없는 아이에서는 두 값이 같아 판정이 안 바뀐다.
     *
     * **이름 바꾸기는 여기를 지나지 않는다** — 그건 보호자마다 자기 값이라
     * [PetApi.updateDisplayName] 로 따로 간다.
     */
    private fun canManage(id: String): Boolean {
        if (pets?.firstOrNull { it.id == id }?.isGroupOwner != false) return true
        error = "대표 보호자만 강아지 정보를 바꿀 수 있어요."
        return false
    }

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

/** 강아지 이름 글자 수. 서버 `PetDisplayUpdate.name`·`PetCreate.name` 이 1~40자다. */
const val PET_NAME_MAX = 40

@Composable
fun rememberPetHolder(
    currentAccount: () -> String? = { null },
    onListed: (account: String, pets: List<Pet>) -> Unit = { _, _ -> },
): PetHolder = remember { PetHolder(currentAccount = currentAccount, onListed = onListed) }
