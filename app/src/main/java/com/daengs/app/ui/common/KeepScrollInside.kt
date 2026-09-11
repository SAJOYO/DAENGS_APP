package com.daengs.app.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * 상자 안의 스크롤을 **밖으로 넘기지 않는다.**
 *
 * 기본 동작은 안쪽이 끝에 닿으면 남은 만큼을 부모가 받는다. 그러면 견종을 훑다가
 * 마지막 줄에서 손가락이 조금 더 가는 순간 **폼 전체가 따라 움직여서** 보고 있던
 * 견종이 화면 밖으로 나간다. 한 번의 드래그가 두 가지를 움직이면 지금 어느 쪽을
 * 만지는 중인지 알 수 없다.
 *
 * 남은 스크롤과 남은 관성을 여기서 다 먹는다. 폼은 상자 **밖**을 끌어서 움직인다.
 *
 * **이것은 [Wheel] 이 푸는 문제의 반대쪽이다.** 여기는 *안쪽이 끝에 닿아 바깥으로 새는 것*
 * 을 막고, 휠은 *바깥이 움직여야 할 제스처를 안쪽이 먼저 먹는 것* 을 막는다. 휠은 깨어
 * 있는 동안 이것도 같이 쓴다 — 해를 돌리다 1996년에 닿았다고 폼이 딸려 움직이면
 * 고르던 자리를 잃는다.
 */
internal val KeepScrollInside = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
