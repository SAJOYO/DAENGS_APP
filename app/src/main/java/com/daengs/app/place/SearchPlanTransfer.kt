package com.daengs.app.place

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject

/** One read request from the saved view. The receiver must check the source before publishing. */
class SearchPlanTransfer(val filters: JsonObject, val ownerId: String, val pool: String = "all_places", val excluded: Set<PlaceKey> = emptySet(), val isCurrent: () -> Boolean) {
    val completion = CompletableDeferred<Unit>()
}
