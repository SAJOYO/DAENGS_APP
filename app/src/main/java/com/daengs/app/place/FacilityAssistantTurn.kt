package com.daengs.app.place

import com.daengs.app.auth.Session
import com.daengs.app.place.bookmarks.BookmarkTurn
import kotlinx.serialization.json.JsonObject

/** Frozen before routing, including the account lifetime and the cards that references name. */
class FacilityAssistantTurn internal constructor(
    internal val generation: Long,
    internal val session: Session,
    internal val before: ConversationResult?,
    val context: JsonObject,
    val bookmarks: BookmarkTurn?,
)
