package com.daengs.app.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * 상자 안의 스크롤을 **밖으로 넘기지 않는다.**
 *
 * 기본 동작은 안쪽이 끝에 닿으면 남은 만큼을 부모가 받는다. 그러면 목록을 훑다가
 * 마지막 줄에서 손가락이 조금 더 가는 순간 **폼 전체가 따라 움직여서** 보고 있던
 * 것이 화면 밖으로 나간다. 한 번의 드래그가 두 가지를 움직이면 지금 어느 쪽을
 * 만지는 중인지 알 수 없다.
 *
 * 남은 스크롤과 남은 관성을 여기서 다 먹는다. 폼은 상자 **밖**을 끌어서 움직인다.
 *
 * 스크롤 안에 스크롤을 두는 것은 원래 이 저장소가 피하는 것이다 (`CareLogSection` ·
 * `ChatSummaryRoute` 의 규칙). 그런데 **고를 것이 많은 목록**은 예외가 된다 — 견종
 * 28개나 진료 사유 17개를 폼 안에 전부 펼치면 그 아래 칸들이 화면 밖으로 밀린다.
 * 그때는 자리를 정해 두고 그 안에서 스크롤하되, 안쪽이 끝에 닿았을 때 바깥 폼이
 * 따라 움직이지 않게 여기서 남은 스크롤을 먹는다.
 *
 * 안 붙이면 안쪽을 넘기다가 폼 전체가 같이 튀고, 테스트에서는 `performScrollTo` 가
 * 어느 스크롤을 움직여야 할지 몰라 클릭이 엉뚱한 자리에 떨어진다 (실제로 그렇게 한 번
 * 깨졌다 — `ReceiptConfirmScreenTest` 의 사유 고르기).
 *
 * `PetFormScreen` 의 `BreedGrid` 가 먼저 쓰던 것을 두 번째 쓰임(진료 사유 칩)이 생기면서
 * 여기로 올렸다.
 *
 * **[Wheel] 은 이것의 반대쪽 문제를 푼다** (APP#277). 여기는 *안쪽이 끝에 닿아 바깥으로
 * 새는 것* 을 막고, 휠은 *바깥이 움직여야 할 제스처를 안쪽이 먼저 먹는 것* 을 막는다.
 * 방향은 반대지만 깨어 있는 휠은 이것도 같이 쓴다 — 해를 돌리다 1996년에 닿았다고 폼이
 * 딸려 움직이면 고르던 자리를 잃는다.
 */
val KeepScrollInside = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
