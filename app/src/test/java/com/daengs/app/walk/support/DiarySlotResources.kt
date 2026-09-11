package com.daengs.app.walk.support

import org.json.JSONObject

/** DEV v2 CLI result, wrapped in the saved-walk API envelope; synthetic records only. */
fun diarySlotFixture(): JSONObject = JSONObject(requireNotNull(
    object {}.javaClass.getResourceAsStream("/diary-slots-preview-v1.json"),
).bufferedReader(Charsets.UTF_8).use { it.readText() })
