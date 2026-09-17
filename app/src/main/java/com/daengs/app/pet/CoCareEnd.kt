package com.daengs.app.pet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 공동 돌봄이 끝나 **내 강아지 정보로 돌아온 아이**를 알아채는 자리 (#440).
 *
 * ### 왜 필요한가
 *
 * 내 아이를 남의 아이와 연결해 두는 동안 카드는 **그룹 주보호자의 공통 정보**(견종·성별·
 * 몸무게…)를 보여 준다. 나가거나 내보내지면 서버는 아무것도 복사하지 않고 **내 행의 원래 값**
 * 으로 돌아간다. 그래서 "비글 · 남아 · 5.0kg" 이던 카드가 갑자기 "믹스" 가 되고, 사용자는
 * 정보가 지워졌다고 읽는다. 계약은 그대로 두고 **한 번 알려 주기만** 한다.
 *
 * ### 어떻게 알아채나
 *
 * 목록을 받을 때마다 "연결된 내 아이"(`isOwner && !isGroupOwner`) id 를 계정별로 적어 둔다.
 * 다음에 받은 목록에서 그 id 가 **혼자 돌보는 내 아이**(`isOwner && isGroupOwner`, 다른 보호자
 * 없음)로 와 있으면 끝난 것이다.
 *
 * - **기기에 적는다.** 내보내기는 주보호자가 다른 폰에서 하므로, 내보내진 사람은 앱을 다시
 *   켠 뒤에야 안다 — 메모리에만 두면 그 경우를 영영 못 잡는다.
 * - **계정별로 적는다.** 한 폰에서 계정을 바꾸면 앞사람의 연결 목록이 뒷사람에게 알림을 띄우면
 *   안 된다.
 * - **목록에서 사라진 아이는 안 알린다.** 삭제됐거나 권한이 없어진 것이라 돌아온 정보가 없다.
 * - 연결 없이 참여한 돌보미(`!isOwner`)는 자기 행이 없어 카드가 그냥 사라진다 — 대상이 아니다.
 */
object CoCareEnd {
    /** 지금 남의 아이와 연결된 **내 행**. 다음 목록과 견줄 기준이다. */
    fun linkedOwnIds(pets: List<Pet>): Set<String> =
        pets.filter { it.isOwner && !it.isGroupOwner }.mapTo(linkedSetOf()) { it.id }

    /**
     * [previouslyLinked] 가운데 [pets] 에서 혼자 돌보는 내 아이로 돌아온 id.
     *
     * **다른 보호자가 남아 있으면 끝난 것이 아니다.** 주보호자가 탈퇴하며 나에게 승계하면 나는
     * 그룹 주보호자가 되지만 함께 돌보는 사람은 그대로라, "공동 돌봄이 종료되어" 는 틀린 말이다.
     */
    fun ended(previouslyLinked: Set<String>, pets: List<Pet>): Set<String> {
        if (previouslyLinked.isEmpty()) return emptySet()
        return pets.filter {
            it.id in previouslyLinked && it.isOwner && it.isGroupOwner && !it.hasOtherCarers
        }.mapTo(linkedSetOf()) { it.id }
    }

    /**
     * 화면에 띄우는 문장. **기술 용어를 쓰지 않는다** — 행·연결 해제 같은 말은 사용자에게 뜻이 없다.
     *
     * 이름을 앞에 붙이지 않는다. 짧은 알림 자리라 두 줄을 넘기면 잘리고, 여러 마리일 때와 문장이
     * 갈리지 않아야 한다.
     */
    const val MESSAGE = "공동 돌봄이 종료되어 내 강아지 정보로 돌아왔어요. 비어 있는 정보를 확인해 주세요."
}

/**
 * 계정별로 "연결된 내 아이" id 를 적어 두는 곳. 홀더는 이것만 본다 — JVM 테스트가
 * [MemoryCoCareLinkLog] 로 갈아 끼운다 (`PhotoRevealLog` 와 같은 결).
 */
interface CoCareLinkLog {
    fun load(account: String): Set<String>
    fun save(account: String, ids: Set<String>)
    fun forget(account: String)
}

class MemoryCoCareLinkLog : CoCareLinkLog {
    private val byAccount = mutableMapOf<String, Set<String>>()
    override fun load(account: String): Set<String> = byAccount[account].orEmpty()
    override fun save(account: String, ids: Set<String>) {
        if (ids.isEmpty()) byAccount.remove(account) else byAccount[account] = ids
    }
    override fun forget(account: String) {
        byAccount.remove(account)
    }
}

/**
 * `SharedPreferences("co-care-links")` 에 `linked:<appUserId>` → 쉼표로 이은 id 로 둔다.
 *
 * **열쇠에 계정을 넣는다.** `PrefsRevealLog` 처럼 로그아웃할 때 비우면, 내보내진 사람이
 * 로그아웃했다 다시 들어온 경우를 못 잡는다. 비어 있으면 열쇠를 지워 쌓이지 않게 한다.
 */
class PrefsCoCareLinkLog(context: Context) : CoCareLinkLog {
    private val prefs = context.applicationContext.getSharedPreferences("co-care-links", Context.MODE_PRIVATE)

    override fun load(account: String): Set<String> =
        prefs.getString(key(account), null)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            .orEmpty()

    override fun save(account: String, ids: Set<String>) {
        prefs.edit().apply {
            if (ids.isEmpty()) remove(key(account)) else putString(key(account), ids.joinToString(","))
        }.apply()
    }

    override fun forget(account: String) {
        prefs.edit().remove(key(account)).apply()
    }

    private fun key(account: String) = "linked:$account"
}

/**
 * 받은 목록을 보고 알릴 것을 쌓아 두는 자리. [PetHolder] 가 목록을 받을 때마다 [observe] 를
 * 부르고, 화면은 [pending] 이 차면 한 번 띄운 뒤 [shown] 을 부른다.
 *
 * **한 번만 뜬다.** 끝난 것을 알아챈 그 자리에서 새 연결 목록을 적으므로, 다음 목록에서는
 * 같은 아이가 기준에 없어 다시 잡히지 않는다. 직접 나가기처럼 목록을 연달아 받아도 마찬가지다.
 */
class CoCareEndWatch(private val log: CoCareLinkLog) {
    /** 아직 안 띄운 알림. 어느 계정 것인지 같이 든다 — 계정이 바뀐 뒤 남의 알림이 뜨면 안 된다. */
    var pending: Pending? by mutableStateOf(null)
        private set

    data class Pending(val account: String, val petIds: Set<String>)

    fun observe(account: String, pets: List<Pet>) {
        val ended = CoCareEnd.ended(log.load(account), pets)
        log.save(account, CoCareEnd.linkedOwnIds(pets))
        if (ended.isEmpty()) return
        val current = pending?.takeIf { it.account == account }
        pending = Pending(account, current?.petIds.orEmpty() + ended)
    }

    /** 띄웠다. 같은 알림을 다시 띄우지 않는다. */
    fun shown() {
        pending = null
    }

    /** 로그아웃. 적어 둔 연결 목록은 **남긴다** — 다시 들어왔을 때 그 사이 끝난 것을 알아야 한다. */
    fun signOut() {
        pending = null
    }

    /** 탈퇴. 그 계정 것은 다시 쓸 일이 없다. */
    fun forgetAccount(account: String) {
        pending = null
        log.forget(account)
    }
}
