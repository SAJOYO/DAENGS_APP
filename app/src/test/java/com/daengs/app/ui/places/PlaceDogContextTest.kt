package com.daengs.app.ui.places

import com.daengs.app.pet.Pet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceDogContextTest {
    private val today = LocalDate.of(2026, 9, 3)

    @Test
    fun `birthday and valid weight become anonymous search conditions`() {
        val context = pet(
            weightKg = 7.5f,
            birthDate = LocalDate.of(2023, 9, 3),
            birthDateKind = Pet.BirthDateKind.BIRTHDAY,
        ).toPlaceDogContext(today)

        assertEquals(7.5, context?.weightKg)
        assertEquals(3.0, context?.ageYears!!, 0.01)
    }

    @Test
    fun `family day is not treated as the dog's age`() {
        val context = pet(
            weightKg = 5f,
            birthDate = LocalDate.of(2020, 1, 1),
            birthDateKind = Pet.BirthDateKind.FAMILY_DAY,
        ).toPlaceDogContext(today)

        assertEquals(5.0, context?.weightKg)
        assertNull(context?.ageYears)
    }

    @Test
    fun `invalid or future profile values are omitted instead of crashing the request`() {
        val context = pet(
            weightKg = 0f,
            birthDate = today.plusDays(1),
            birthDateKind = Pet.BirthDateKind.BIRTHDAY,
        ).toPlaceDogContext(today)

        assertNull(context)
    }

    private fun pet(
        weightKg: Float?,
        birthDate: LocalDate?,
        birthDateKind: Pet.BirthDateKind?,
    ) = Pet(
        id = "dog-1",
        name = "댕이",
        breed = "poodle",
        sex = null,
        neutered = null,
        weightKg = weightKg,
        birthDate = birthDate,
        birthDateKind = birthDateKind,
        isPrimary = true,
    )
}
