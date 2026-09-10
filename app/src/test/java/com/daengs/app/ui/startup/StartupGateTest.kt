package com.daengs.app.ui.startup

import com.daengs.app.pet.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저장된 토큰으로 켠 앱이 **로딩에서 어디로 나가는가.**
 *
 * 셋 다 눈으로는 잘 안 잡힌다 — 잘 되는 날에는 로딩이 한순간이라 지나가고, 어긋나는
 * 것은 서버가 느리거나 죽은 날에만 보인다.
 */
class StartupGateTest {

    private fun pet(id: String) = Pet(
        id = id, name = "몽이", breed = "dog_beagle", sex = null, neutered = null,
        weightKg = null, birthDate = null, birthDateKind = null, isPrimary = true,
        farewellOn = null,
    )

    /** 목록을 못 받은 사이에 홈을 띄우면 **방이 데모로 채워진다.** 그게 이 화면의 이유다. */
    @Test
    fun `아직 못 받았으면 기다린다`() {
        assertEquals(StartupTarget.Wait, startupTarget(pets = null, petsError = null))
    }

    @Test
    fun `강아지가 있으면 홈으로`() {
        assertEquals(StartupTarget.Home, startupTarget(listOf(pet("a")), null))
    }

    /**
     * **강아지가 없어도 홈이다.** 예전에는 여기서 등록 화면으로 보냈다 — 로그인하자마자
     * 남의 정보를 채우게 만드는 자리였고, 빠져나갈 수도 없었다. 이제 빈 방으로 들어가고
     * ([com.daengs.app.ui.home.roomRoster]) 기능을 누를 때 청한다.
     *
     * **그래도 빈 목록과 `null` 은 여전히 다르다** — 위 `아직 못 받았으면 기다린다` 가
     * 그것을 잡는다. 못 받아 온 사이에 빈 방을 보여 주면 그것도 거짓말이다.
     */
    @Test
    fun `강아지가 없어도 홈으로`() {
        assertEquals(StartupTarget.Home, startupTarget(emptyList(), null))
    }

    /**
     * **이 줄이 없으면 로딩에 갇힌다.** 서버가 죽어 있으면 목록은 영영 `null` 이다.
     * 방이 비어 보이는 것이 정지 화면보다 낫다.
     */
    @Test
    fun `못 받았고 이유가 있으면 홈으로 보낸다`() {
        assertEquals(StartupTarget.Home, startupTarget(pets = null, petsError = "서버에 못 닿았어요"))
    }

    /** 목록이 이미 왔으면 뒤늦은 실패 문구가 남아 있어도 목록이 이긴다. */
    @Test
    fun `목록이 있으면 지난 실패는 무시한다`() {
        assertEquals(StartupTarget.Home, startupTarget(listOf(pet("a")), "지난 실패"))
        assertEquals(StartupTarget.Home, startupTarget(emptyList(), "지난 실패"))
    }

    /**
     * 세션을 못 되살렸으면 **기다리지 않는다.**
     *
     * 갱신이 실패하면 강아지 목록을 부르는 자리가 토큰을 못 받아 조용히 끝나고,
     * `pets` 도 `petsError` 도 영영 안 채워진다. 그때 `Wait` 를 돌려주면 로딩
     * 화면에서 나갈 길이 없다 — 사용자에게는 앱이 죽은 것으로 보인다.
     * 2026-09-09 실기기에서 그대로 밟았다 (닿지 않는 서버, 40초 넘게 로딩).
     */
    @Test fun `세션 갱신이 실패하면 기다리지 않고 홈으로 나간다`() {
        assertEquals(
            StartupTarget.Home,
            startupTarget(pets = null, petsError = null, session = SessionRestore.Failed),
        )
    }

    /** 되살리는 중에는 기존대로 기다린다. 실패가 아직 아니다. */
    @Test fun `세션을 되살리는 중이면 기다린다`() {
        assertEquals(
            StartupTarget.Wait,
            startupTarget(pets = null, petsError = null, session = SessionRestore.Pending),
        )
    }
}

/**
 * 로딩 화면을 **얼마나 더 붙들고 있나.**
 *
 * 눈으로는 못 잡는다 — 빠른 날에는 지나가고, 어긋나면 "왜 켤 때마다 느리지" 로만
 * 보인다. 특히 **목록이 여러 번 갱신되는 날**이 그렇다.
 */
class LoadingHoldTest {

    @Test
    fun `막 떴으면 최소 시간을 다 기다린다`() {
        assertEquals(MIN_LOADING_MS, loadingHoldMs(startedAt = 1_000L, now = 1_000L))
    }

    @Test
    fun `지난 만큼 빼고 기다린다`() {
        // 300 지났으면 400 만 더. **목록이 여러 번 갱신돼도 총 700 이다** —
        // 다시 돌 때마다 700 을 새로 세면 몇 초씩 잡혀 있는다.
        assertEquals(400L, loadingHoldMs(startedAt = 1_000L, now = 1_300L))
    }

    @Test
    fun `이미 늦었으면 곧장 나간다`() {
        assertEquals(0L, loadingHoldMs(startedAt = 1_000L, now = 1_700L))
        assertEquals(0L, loadingHoldMs(startedAt = 1_000L, now = 6_000L))
    }

    @Test
    fun `시계가 뒤로 가도 최소 시간을 넘지 않는다`() {
        // elapsedRealtime 은 뒤로 안 가지만, 넘겨받는 값이라 여기서 막아 둔다.
        assertEquals(MIN_LOADING_MS, loadingHoldMs(startedAt = 5_000L, now = 1_000L))
    }

    @Test
    fun `사람이 화면을 알아볼 만큼은 된다`() {
        // 두어 프레임(약 33ms)이면 "끊긴 것" 으로 읽힌다. 그게 이 카드의 출발점이다.
        assertTrue("$MIN_LOADING_MS ms", MIN_LOADING_MS >= 500L)
        // 하루에 여러 번 켜는 앱이라 너무 길면 그것대로 답답하다.
        assertTrue("$MIN_LOADING_MS ms", MIN_LOADING_MS <= 1_000L)
    }

}
