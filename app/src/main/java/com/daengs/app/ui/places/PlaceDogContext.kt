package com.daengs.app.ui.places

import com.daengs.app.pet.Pet
import com.daengs.app.place.DogSearchContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 프로필 identity를 넘기지 않고 Place 계약이 받는 값만 투영한다. */
fun Pet?.toPlaceDogContext(today: LocalDate = LocalDate.now()): DogSearchContext? {
    this ?: return null
    val weight = weightKg?.toDouble()?.takeIf { it > 0.0 && it <= 200.0 }
    val age = birthDate
        ?.takeIf { birthDateKind == Pet.BirthDateKind.BIRTHDAY && !it.isAfter(today) }
        ?.let { ChronoUnit.DAYS.between(it, today) / 365.2425 }
        ?.takeIf { it in 0.0..40.0 }
    if (weight == null && age == null) return null
    return DogSearchContext(weightKg = weight, ageYears = age)
}
