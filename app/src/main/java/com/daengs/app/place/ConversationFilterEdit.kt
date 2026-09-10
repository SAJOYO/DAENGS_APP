package com.daengs.app.place

import kotlinx.serialization.json.*

/** Removal refers to the exact committed state displayed when the user clicked. */
data class ConversationFilterEdit(
    val sessionId: String,
    val revision: Int,
    val removeAll: List<String> = emptyList(),
    val removeAny: List<String> = emptyList(),
) {
    fun toJson() = buildJsonObject {
        put("remove_all", JsonArray(removeAll.map(::JsonPrimitive)))
        put("remove_any", JsonArray(removeAny.map(::JsonPrimitive)))
    }
}
