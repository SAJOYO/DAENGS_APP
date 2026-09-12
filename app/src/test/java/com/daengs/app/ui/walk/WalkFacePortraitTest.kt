package com.daengs.app.ui.walk

import com.daengs.app.R
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkFacePortraitTest {
    private val selected = Pet(
        id = "selected", name = "댕댕", breed = "mixed", sex = null, neutered = null,
        weightKg = null, birthDate = null, birthDateKind = null, isPrimary = false, farewellOn = null,
    )

    @Test
    fun `산책 얼굴을 아직 모르면 파란 점 대신 발바닥을 요청한다`() {
        assertEquals(R.drawable.ic_location_paw, walkFacePortraitRes(null, null))
    }

    @Test
    fun `선택한 믹스견에 대표 강아지 얼굴을 빌려오지 않는다`() {
        assertEquals(R.drawable.ic_location_paw, walkFacePortraitRes(selected, DogBreed.BEAGLE))
    }

    @Test
    fun `견종을 알면 선택한 아이 또는 기본 아이의 얼굴을 유지한다`() {
        assertEquals(DogBreed.BEAGLE.portraitRes, walkFacePortraitRes(selected.copy(breed = "dog_beagle"), null))
        assertEquals(DogBreed.BEAGLE.portraitRes, walkFacePortraitRes(null, DogBreed.BEAGLE))
    }
}
