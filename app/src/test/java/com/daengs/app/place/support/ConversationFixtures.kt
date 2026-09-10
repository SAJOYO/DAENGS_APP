package com.daengs.app.place.support

import com.daengs.app.place.conversationFixture
import kotlinx.serialization.json.*

/** Canonical shopping response plus an explicit AND / OR filter state. */
fun filteredConversationFixture(request: JsonObject? = null): JsonObject {
    val body = conversationFixture("manual", request)
    val hard = Json.parseToJsonElement("""
        {"all":[{"id":"parking","capability":"operations.parking","op":"eq","value":true}],
         "any":[
           {"id":"shop","all":[{"id":"shop-kind","capability":"purpose.kind","op":"in","value":["shopping"]}]},
           {"id":"pet","all":[{"id":"pet-kind","capability":"purpose.kind","op":"in","value":["pet_shop"]}]}
         ]}
    """)
    return JsonObject(body + ("filters" to JsonObject(body.getValue("filters").jsonObject + ("hard" to hard))))
}
