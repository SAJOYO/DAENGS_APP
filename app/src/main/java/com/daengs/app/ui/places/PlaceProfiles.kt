package com.daengs.app.ui.places

import com.daengs.app.pet.Pet
import com.daengs.app.place.PlaceDogSnapshot
import java.time.LocalDate

/** Search selection is independent of the primary pet and walk selection. */
data class PlaceProfiles(
    val ownerId: String? = null,
    val pets: List<Pet> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val ready: Boolean = false,
    val message: String? = "반려견을 불러와 주세요.",
) {
    fun receive(owner: String?, items: List<Pet>?, busy: Boolean, error: String?): PlaceProfiles {
        val sameOwner = owner == ownerId
        val selected = if (sameOwner) selectedIds else emptySet()
        if (owner == null) return PlaceProfiles(message = "로그인 후 반려견을 선택할 수 있어요.")
        if (busy || error != null || items == null) return copy(
            ownerId = owner, pets = if (sameOwner) pets else emptyList(), selectedIds = selected,
            ready = false, message = error ?: "반려견을 불러오는 중…",
        )
        val available = items.filter { it.farewellOn == null }
        val retained = selected.intersect(available.map { it.id }.toSet())
        return PlaceProfiles(owner, available, retained, true, when {
            retained != selected -> "함께 갈 수 있는 반려견 목록이 바뀌어 선택을 갱신했어요."
            available.isEmpty() -> "함께 갈 반려견을 프로필에 등록해 주세요."
            else -> null
        })
    }

    fun toggle(id: String): PlaceProfiles = if (!ready || pets.none { it.id == id }) this else
        copy(selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id)

    fun snapshots(today: LocalDate = LocalDate.now()): List<PlaceDogSnapshot> =
        if (!ready) emptyList() else pets.filter { it.id in selectedIds }.map { pet ->
            val values = pet.toPlaceDogContext(today)
            PlaceDogSnapshot(pet.id, pet.updatedAt, values?.size, values?.weightKg, values?.ageYears)
        }
}
