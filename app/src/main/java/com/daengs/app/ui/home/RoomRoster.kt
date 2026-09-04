package com.daengs.app.ui.home

import com.daengs.app.miniroom.RoomDefaults
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet

/**
 * 방에 세울 명부. **등록한 강아지가 그대로 방에 선다.**
 *
 * 예전에는 [DogBreed.ROOM_BREEDS] 세 종을 돌려 쓰는 장식이라, 비글을 등록한 사람의
 * 방에도 래브라도가 돌았다. 상단바 얼굴은 대표를 따르는데 방만 안 따라서 **같은
 * 화면이 두 말을 하고 있었다.**
 *
 * 이 자리가 앱과 미니룸이 만나는 유일한 곳이다 — `miniroom` 은 강아지 등록을 모르고,
 * `pet` 은 방을 모른다.
 *
 * @param pets null 이면 **아직 못 받아 온 것**이다 (빈 목록과 다르다)
 */
fun roomRoster(pets: List<Pet>?): List<DogBreed> = when {
    // ① 아직 못 받아 왔다 → **아무도 안 세운다.**
    //
    // 예전에는 여기서도 데모 네 마리를 세웠다. "빈 방을 깜빡이지 않으려고" 였는데,
    // 실기기에서 보니 **앱을 열 때마다 남의 강아지 네 마리가 있다가 목록이 도착하면
    // 사라지고 내 아이만 남았다.** 빈 방보다 나쁘다 — 없는 개를 보여 준 뒤 뺏는
    // 셈이라 그 순간 화면이 거짓말을 한다.
    //
    // 비워 두고 [HomeScreen] 이 불러오는 중이라고 말한다. 모르는 것은 모른다고 하는
    // 편이 낫다 (날씨 카드가 폴백을 단정하지 않는 것과 같은 이유).
    pets == null -> emptyList()

    // ② 받아 왔는데 한 마리도 없다 → 견본을 세운다.
    //
    // null 과 **다른 경우**다. 둘러보기 중이거나 온보딩으로 넘어가기 직전이고,
    // 그 짧은 사이에 방이 비면 앱이 고장 난 것처럼 보인다. 이건 로딩을 감추는
    // 거짓이 아니라 빈 상태의 견본이다.
    pets.isEmpty() -> DogBreed.demoRoster(RoomDefaults.DOG_COUNT)

    else -> pets.map { it.roomBreed }
}

/**
 * 방에 선 아이들 중 **배웅한 아이의 자리**.
 *
 * [roomRoster] 와 같은 차례이므로 여기서 나온 번호가 곧 그 명부의 첨자다. 견종 목록에
 * 배웅 여부를 같이 담지 않는 이유는 `miniroom` 이 [Pet] 을 모르기 때문이다 —
 * 견종은 그리는 데 필요한 값이고 배웅은 앱의 사실이라, 만나는 자리는 여기 하나면 된다.
 *
 * 못 받아 왔거나(null) 견본을 세운 경우에는 아무도 없다. **견본에 무지개를 달면 안
 * 된다** — 남의 아이도 아닌, 있지도 않은 아이를 배웅한 것으로 만든다.
 */
fun departedInRoom(pets: List<Pet>?): Set<Int> {
    if (pets.isNullOrEmpty()) return emptySet()
    return pets.indices.filter { pets[it].farewellOn != null }.toSet()
}

/**
 * 방에 세울 아이들. **뺀 아이는 빠지고, 다 빼도 한 마리는 남는다.**
 *
 * ⚠️ **[roomRoster] 와 [departedInRoom] 이 이 결과를 같이 봐야 한다.** 앞의 것이 낸
 * 번호가 뒤의 것의 첨자라, 거르는 자리를 둘로 나누면 반드시 어긋난다 — 무지개가 남의
 * 아이 머리 위에 뜬다.
 *
 * **다 빼지는 못한다.** 빈 방은 고장 난 것으로 읽히고, 사용자가 그걸 원해서 다 뺀
 * 것도 아니다(하나씩 빼다 보면 마지막이 남는다). 화면이 마지막 한 마리를 못 빼게
 * 막지만, 아이를 지우거나 다른 기기에서 고치면 여기로 흘러들 수 있어서 여기서도 막는다.
 * 그때 남기는 것은 **대표**다 — 상단바 얼굴과 같은 아이라야 화면이 한 말을 한다.
 *
 * @param hidden 방에서 뺀 아이의 id (`RoomStore.loadHiddenPetIds`)
 */
fun roomPets(pets: List<Pet>?, hidden: Set<String>): List<Pet>? {
    if (pets == null) return null
    if (pets.isEmpty() || hidden.isEmpty()) return pets
    val kept = pets.filterNot { it.id in hidden }
    if (kept.isNotEmpty()) return kept
    val fallback = pets.firstOrNull { it.isPrimary } ?: pets.first()
    return listOf(fallback)
}

/** 이 아이를 방에서 뺄 수 있나. **마지막 한 마리는 못 뺀다.** */
fun canHideFromRoom(pets: List<Pet>?, hidden: Set<String>, petId: String): Boolean {
    if (petId in hidden) return true          // 이미 빠져 있으면 되돌리는 쪽이다
    return (roomPets(pets, hidden)?.size ?: 0) > 1
}
