package com.daengs.app.ui.places

import com.daengs.app.map.features.places.categoryLabel
import com.daengs.app.place.ConversationResult
import com.daengs.app.place.PlaceKind
import kotlinx.serialization.json.*

internal data class AppliedFilter(val id: String, val label: String)
internal data class AppliedFilterBranch(val id: String, val conditions: List<AppliedFilter>)
internal data class AppliedPlaceFilters(
    val all: List<AppliedFilter> = emptyList(),
    val any: List<AppliedFilterBranch> = emptyList(),
) {
    // One OR group is one removable constraint, regardless of its number of alternatives.
    val count get() = all.size + if (any.isEmpty()) 0 else 1
    val summary get() = (all.map { it.label } + if (any.isEmpty()) emptyList() else listOf("조합 조건 1개")).joinToString(" · ")
}

internal fun ConversationResult.appliedPlaceFilters(): AppliedPlaceFilters {
    fun JsonElement.condition(): AppliedFilter {
        val atom = jsonObject
        val value = atom.getValue("value")
        val label = when (atom.getValue("capability").jsonPrimitive.content) {
            "operations.parking" -> if (value.jsonPrimitive.boolean) "주차 가능한 곳만" else "주차 불가로 확인된 곳만"
            "pet_access.exclusive" -> if (value.jsonPrimitive.boolean) "반려동물 전용 시설만" else "반려동물 전용 시설 제외"
            "purpose.kind" -> {
                val kinds = value.jsonArray.map { categoryLabel(PlaceKind.fromWire(it.jsonPrimitive.content)) }
                if (atom.getValue("op").jsonPrimitive.content == "not_in") kinds.joinToString("·") + " 제외"
                else kinds.joinToString(" 또는 ")
            }
            else -> "확인할 수 없는 추가 조건"
        }
        return AppliedFilter(atom.getValue("id").jsonPrimitive.content, label)
    }
    val hard = filters.getValue("hard").jsonObject
    return AppliedPlaceFilters(
        hard.getValue("all").jsonArray.map { it.condition() },
        hard.getValue("any").jsonArray.map { branch -> branch.jsonObject.let {
            AppliedFilterBranch(it.getValue("id").jsonPrimitive.content,
                it.getValue("all").jsonArray.map { atom -> atom.condition() })
        } },
    )
}
