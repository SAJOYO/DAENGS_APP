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

/**
 * 지도의 내 위치에 **기본 얼굴 대신 세울 아이.** 그대로 둘 때는 `null`.
 *
 * ## 왜 필요한가
 *
 * 지도 얼굴은 늘 **대표 아이**였다 (`WalkRoute` 의 `avatarPhoto` 주석). 앱의 다른
 * 얼굴이 다 그렇기 때문이고, 그건 맞다 — 상단바도 챗봇도 대표를 따른다.
 *
 * 그런데 산책은 **이번에 누구와 나가는지**를 고르고 시작한다. 대표가 `네옹` 인데
 * 오늘 `댕댕` 이랑만 나가면, 기록에는 댕댕이 남는데 **걷는 내내 지도에는 네옹 얼굴**이
 * 떠 있었다. 화면과 기록이 다른 말을 하는 자리다.
 *
 * ## 대표가 끼어 있으면 안 바꾼다
 *
 * `null` 을 돌려주어 **부르는 쪽이 원래 쓰던 값을 그대로 쓰게** 한다. 그래야 지금
 * 동작이 하나도 안 바뀌고, 개발자 패널의 견종 고르기(`devBreed`)처럼 바깥에서
 * 정해 넘기는 것들도 계속 이긴다. 바꾸는 것은 **대표를 빼고 나간 경우 하나**다.
 *
 * 여럿을 골랐고 그중 대표가 없으면 **고른 아이 중 첫 번째**다. 셋을 데리고 나갈 때
 * 누구 얼굴이어야 하는지에 정답은 없고, 적어도 **데려간 아이 중 하나**이기는 하다.
 *
 * @param pets 등록한 아이들. 차례가 곧 "첫 번째" 의 기준이다
 * @param selected 이번 산책에 고른 아이들
 */
fun walkFaceOverride(pets: List<Pet>, selected: Set<String>): Pet? {
    val picked = pets.filter { it.id in selected }
    if (picked.isEmpty()) return null
    // 대표를 데리고 나간다 — 지금 쓰던 얼굴이 이미 맞다.
    if (picked.any { it.isPrimary }) return null
    return picked.first()
}

/** Unknown breed is a paw, never another dog's portrait or an accidental SDK blue dot. */
internal fun walkFacePortraitRes(selectedPet: Pet?, defaultBreed: com.daengs.app.miniroom.art.DogBreed?): Int =
    (if (selectedPet == null) defaultBreed else selectedPet.breedArt)?.portraitRes
        ?: com.daengs.app.R.drawable.ic_location_paw
