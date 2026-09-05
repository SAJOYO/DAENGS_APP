package com.daengs.app.ui.walk

import com.daengs.app.pet.Pet

/**
 * **산책에 데리고 나갈 아이를 어떻게 고르는가.**
 *
 * ## 무엇이 잘못됐었나
 *
 * 처음에는 **등록한 아이가 전부 골라진 채로** 시작했다. 그런데 화면은
 * "누구와 나갈까요?" 라고 **묻고** 있었고, 골라진 표시는 연분홍 바탕과 흰 바탕의
 * 차이뿐이라 밝은 곳에서 거의 안 보였다.
 *
 * 그래서 비공개 테스트에서 이런 일이 났다 — **데리고 갈 아이를 "고르려고" 눌렀는데
 * 그게 빼는 동작이었고, 산책이 나머지 아이들과 다녀온 것으로 기록됐다.**
 * 걷는 내내 아무 표시도 없었고, 끝나고 기록을 볼 때야 알았다.
 *
 * ## 어떻게 고쳤나
 *
 * **마릿수로 가른다** ([defaultWalkDogs]). 사고는 두 마리 이상일 때만 나기 때문이다 —
 * 한 마리면 고를 것이 없어서 실수할 여지도 없다.
 *
 * 그리고 **아무도 안 고르면 시작을 막는다** ([canStartWalk]). 예전에는 "사람이 걸은
 * 것은 걸은 것이다" 라며 열어 두었는데, 그 근거였던 *등록한 아이가 없는 사람*은
 * 이제 산책 화면에 아예 못 들어온다 — `ui/home/PetGate.kt` 의 문이 먼저 막는다.
 * 남은 것은 "아이가 있는데 다 뺀 경우" 뿐이고, 그건 의도보다 실수다.
 *
 * ⚠️ **둘러보기는 예외다.** 로그인 전에는 문이 안 서고 등록한 아이도 없으므로,
 * 목록이 비었으면 그대로 시작하게 둔다. 거기까지 막으면 둘러보기가 아무것도 못 하는
 * 화면이 된다.
 */

/**
 * 산책 화면을 열었을 때 **미리 골라져 있을 아이들.**
 *
 * - **한 마리면 그 아이.** 고를 것이 없으니 탭을 하나 더 시키지 않는다
 * - **두 마리 이상이면 아무도 안 고른다.** 반드시 의식해서 고르게 한다 —
 *   이 카드가 없애려는 사고가 정확히 이 경우에서만 났다
 * - 목록이 비었으면 빈 값 (둘러보기)
 */
fun defaultWalkDogs(pets: List<Pet>): Set<String> =
    if (pets.size == 1) setOf(pets.single().id) else emptySet()

/**
 * 지금 산책을 시작해도 되나. **강아지 앱이니 아이 없이는 안 나간다.**
 *
 * @param pets 등록한 아이들. **비어 있으면 막지 않는다** — 둘러보기다
 * @param selected 지금 골라 둔 아이들
 */
fun canStartWalk(pets: List<Pet>, selected: Set<String>): Boolean =
    pets.isEmpty() || selected.isNotEmpty()

/**
 * 고르는 자리 위에 뜰 말. **상태를 말하지 질문하지 않는다.**
 *
 * 예전 문구는 "누구와 나갈까요?" 하나였다. 전부 골라진 채로 그렇게 물으니, 읽는
 * 사람은 아직 아무도 안 골라진 줄 알았다. 지금은 골라진 수에 따라 다르게 말한다.
 *
 * @return 비어 있을 수 없다 — 자리는 늘 한 줄을 차지한다
 */
fun walkDogPickLabel(pets: List<Pet>, selected: Set<String>): String {
    val picked = pets.count { it.id in selected }
    return when {
        picked == 0 -> "누구와 나갈까요?"
        picked == pets.size && pets.size > 1 -> "모두 함께 나가요"
        else -> "${picked}마리와 나가요"
    }
}

/**
 * 시작을 막았을 때 그 **이유**. 막지 않았으면 `null`.
 *
 * **버튼만 흐리게 두지 않는다** — 왜 안 눌리는지 모르면 고장으로 읽힌다.
 */
fun walkStartBlockedReason(pets: List<Pet>, selected: Set<String>): String? =
    if (canStartWalk(pets, selected)) null else "함께 나갈 아이를 한 마리는 골라 주세요."
