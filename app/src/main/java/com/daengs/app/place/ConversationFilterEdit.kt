package com.daengs.app.place

import kotlinx.serialization.json.*

/** Removal refers to the exact committed state displayed when the user clicked. */
data class ConversationFilterEdit(
    val sessionId: String,
    val revision: Int,
    val removeAll: List<String> = emptyList(),
    val removeAny: List<String> = emptyList(),
    val searchPool: String = "keep",
) {
    fun toJson() = buildJsonObject {
        require(searchPool in setOf("keep", "all_places", "unbookmarked", "new_candidates"))
        if (searchPool != "keep") put("search_pool", searchPool)
        put("remove_all", JsonArray(removeAll.map(::JsonPrimitive)))
        put("remove_any", JsonArray(removeAny.map(::JsonPrimitive)))
    }
}
