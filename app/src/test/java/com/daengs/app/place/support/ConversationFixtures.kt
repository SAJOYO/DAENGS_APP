package com.daengs.app.place.support

import kotlinx.serialization.json.*

/** Fixtures are serialized by the real Place workflow with synthetic shopping records. */
fun conversationFixture(name: String, request: JsonObject? = null): JsonObject {
    val body = object {}.javaClass.getResourceAsStream("/conversation_$name.json")!!
        .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    if (request == null) return body
    val revision = request["revision"] ?: JsonPrimitive((request["expected_revision"]?.jsonPrimitive?.int ?: 0) + 1)
    return JsonObject(body + mapOf(
        "client_request_id" to request.getValue("client_request_id"),
        "revision" to revision,
        "answer_status" to JsonPrimitive(if (name == "manual") "none" else if (request.containsKey("mode")) "pending" else "ready"),
        "answer" to if (name == "manual" || request.containsKey("mode")) JsonNull else
            JsonObject(body.getValue("answer").jsonObject + ("revision" to revision)),
    ))
}

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
