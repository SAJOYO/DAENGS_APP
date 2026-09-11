package com.daengs.app.place.bookmarks

import com.daengs.app.place.PlaceKey
import kotlinx.serialization.json.JsonObject

/** Captured at utterance time. The implementation owns account and per-place intent fences. */
fun interface BookmarkTurn {
    suspend fun execute(requestId: String, key: PlaceKey, saved: Boolean): BookmarkOutcome
    fun cancel() {}
    val supportsSearch: Boolean get() = false
    suspend fun search(filters: JsonObject, isCurrent: () -> Boolean): String =
        "찜 탭에서 검색해 주세요."
}

enum class BookmarkCompletion { CONFIRMED, SUPERSEDED, UNKNOWN, FAILED }
data class BookmarkOutcome(val completion: BookmarkCompletion, val message: String)
