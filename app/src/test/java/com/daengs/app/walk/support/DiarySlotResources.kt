package com.daengs.app.walk.support

import org.json.JSONObject

/** DEV v2 CLI result, wrapped in the saved-walk API envelope; synthetic records only. */
fun diarySlotFixture(): JSONObject = JSONObject(requireNotNull(
    object {}.javaClass.getResourceAsStream("/diary-slots-preview-v1.json"),
).bufferedReader(Charsets.UTF_8).use { it.readText() })

/** DEV v3 rules with synthetic grid observations and fixed writer responses; no external calls. */
fun diarySlotTemperatureFixture(): JSONObject = JSONObject(requireNotNull(
    object {}.javaClass.getResourceAsStream("/diary-slots-preview-v3.json"),
).bufferedReader(Charsets.UTF_8).use { it.readText() })
