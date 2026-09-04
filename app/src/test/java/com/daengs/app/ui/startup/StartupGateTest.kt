package com.daengs.app.ui.startup

import com.daengs.app.pet.Pet
import org.junit.Assert.assertEquals
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
     * **빈 목록과 `null` 은 다르다.** 둘을 같이 다루면 목록을 기다리는 사이에
     * 온보딩이 떠서, 이미 강아지가 있는 사람에게 등록 화면이 스친다.
     */
    @Test
    fun `강아지가 없으면 온보딩으로`() {
        assertEquals(StartupTarget.Onboarding, startupTarget(emptyList(), null))
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
        assertEquals(StartupTarget.Onboarding, startupTarget(emptyList(), "지난 실패"))
    }
}
