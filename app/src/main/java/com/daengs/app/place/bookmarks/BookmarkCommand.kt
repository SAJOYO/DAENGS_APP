package com.daengs.app.place.bookmarks

import com.daengs.app.place.PlaceKey

/** Captured at utterance time. The implementation owns account and per-place intent fences. */
fun interface BookmarkTurn {
    suspend fun execute(requestId: String, key: PlaceKey, saved: Boolean): BookmarkOutcome
    fun cancel() {}
}

enum class BookmarkCompletion { CONFIRMED, SUPERSEDED, UNKNOWN, FAILED }
data class BookmarkOutcome(val completion: BookmarkCompletion, val message: String)
