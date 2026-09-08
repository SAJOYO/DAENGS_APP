package com.daengs.app.pet

import java.time.LocalDate
import java.time.LocalTime

/**
 * 개발자 패널이 넣어 보는 **가짜 강아지들.** 디버그 소스셋이다.
 *
 * 짝이 되는 `app/src/release/.../DevPets.kt` 는 늘 빈 목록이다 —
 * `DevCards` · `DeveloperPanel` 이 쓰는 그 수법이고, 출시본에는 안 들어간다.
 *
 * ## 왜 필요한가
 *
 * 강아지에 딸린 화면이 여럿인데(마이의 강아지 카드, 삭제·배웅 확인창, 방에 서는 아이,
 * 방 구성 고르기) **전부 로그인해야만 보인다.** 둘러보기에는 아이가 한 마리도 없다.
 * 그래서 계정을 못 쓰는 기기에서는 그 화면들을 **한 번도 못 보고** 넘겨야 했다 —
 * 프로필 사진도 방 구성도 그렇게 못 본 채 나갔다.
 *
 * ## 이것으로 못 하는 것
 *
 * **서버에 없는 아이들이다.** 이름을 고치거나 지우거나 대표를 바꾸는 것은 서버로 가서
 * 실패한다 — 확인창이 어떻게 생겼는지는 볼 수 있고, 누른 뒤의 결과는 못 본다.
 * 방 구성처럼 **기기에만 사는 것**은 그대로 다 된다.
 */

/** 넣어 볼 수 있는 마릿수. `0` 은 원래대로(서버가 준 목록)다. */
val DEV_PET_COUNTS = listOf(0, 1, 2, 3)

/**
 * 가짜 강아지 [count] 마리.
 *
 * **셋째는 배웅한 아이다.** 방에서 배웅한 아이 곁에 뜨는 하트가 명부와 첨자로 이어져
 * 있어서(`departedInRoom`), 그게 어긋나는지는 배웅한 아이가 있어야 보인다.
 *
 * ⚠️ **하트는 그 아이가 쉬고 있을 때만 뜬다** (`MiniRoomDrawing` — 걷는 내내 따라
 * 다니면 몸에 붙은 장식이 된다). 확인하려면 그 아이가 멈출 때까지 봐야 한다.
 */
fun devPets(count: Int): List<Pet> = DEV_DOGS.take(count.coerceIn(0, DEV_DOGS.size))

private fun devPet(
    id: String,
    name: String,
    breed: String,
    primary: Boolean = false,
    farewell: LocalDate? = null,
    feedingStyle: Pet.FeedingStyle? = null,
    feedingTimes: List<LocalTime>? = null,
    healthConditions: String? = null,
    medications: String? = null,
) = Pet(
    id = id,
    name = name,
    breed = breed,
    sex = Pet.Sex.MALE,
    neutered = true,
    weightKg = 5.2f,
    birthDate = LocalDate.of(2021, 8, 24),
    birthDateKind = Pet.BirthDateKind.BIRTHDAY,
    farewellOn = farewell,
    isPrimary = primary,
    feedingStyle = feedingStyle,
    feedingTimes = feedingTimes,
    healthConditions = healthConditions,
    medications = medications,
)

private val DEV_DOGS = listOf(
    // 첫째는 돌봄 칸(#200)이 차 있다. 수정 화면에서 시각 칩과 병·약 칸이 찬 모습을 보는 자리.
    devPet(
        "dev-1", "몽이", "dog_beagle", primary = true,
        feedingStyle = Pet.FeedingStyle.SCHEDULED,
        feedingTimes = listOf(LocalTime.of(8, 0), LocalTime.of(19, 30)),
        healthConditions = "슬개골 탈구",
        medications = "관절 영양제",
    ),
    devPet("dev-2", "초코", "dog_welsh_corgi"),
    // 배웅한 아이. 하트가 남의 아이 곁으로 옮겨 가는지 보는 자리다.
    devPet("dev-3", "별이", "dog_maltese", farewell = LocalDate.of(2026, 3, 14)),
)
