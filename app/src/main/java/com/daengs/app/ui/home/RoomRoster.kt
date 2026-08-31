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
fun roomRoster(pets: List<Pet>?): List<DogBreed> =
    pets?.map { it.roomBreed }?.takeIf { it.isNotEmpty() }
    // 못 받아 왔거나(null) 한 마리도 없으면 데모로 채운다.
    //
    // **빈 방을 보여 주지 않는다.** 홈은 목록보다 먼저 그려지므로, 비웠다가 채우면
    // 앱을 열 때마다 방이 한 번 깜빡인다. 출시 앱은 로그인이 필수고 강아지가 없으면
    // 온보딩으로 가므로, 빈 목록으로 홈에 오래 머무는 길은 사실상 없다.
        ?: DogBreed.demoRoster(RoomDefaults.DOG_COUNT)
