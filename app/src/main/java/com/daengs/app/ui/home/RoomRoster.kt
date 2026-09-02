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
