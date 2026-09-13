package com.daengs.app.walk.diary

import org.json.JSONObject

/** Provenance of the immutable server publication; local title/body edits remain independent. */
data class PublishedCardPart(val text: String, val origin: String, val actionId: String?, val actorId: String?)

data class PublishedCardWriting(
    val contentRevision: String,
    val titleOrigin: String,
    val space: PublishedCardPart,
    val actions: List<PublishedCardPart>,
    val originalText: String?,
)

internal fun publishedCardWriting(card: JSONObject): PublishedCardWriting? {
    if (card.isNull("writing")) return null // Historical boards keep their existing parser/reader.
    val value = card.getJSONObject("writing")
    require(value.getString("format") == "diary-card-narrative-v1")
    val revision = value.digestText("content_revision")
    require(value.digestText("title_based_on_content_revision") == revision)
    val titleOrigin = value.getString("title_origin").also { require(it in setOf("generated", "fallback")) }
    fun part(obj: JSONObject): PublishedCardPart {
        val text = obj.getString("text").also { require(it.length <= 220) }
        val origin = obj.getString("origin").also { require(it in setOf("generated", "fallback")) }
        return PublishedCardPart(text, origin,
            if (obj.isNull("action_id")) null else obj.requiredText("action_id", 200),
            if (obj.isNull("actor_id")) null else obj.requiredText("actor_id", 200))
    }
    val space = part(value.getJSONObject("space"))
    require(space.actionId == null && space.actorId == null && space.text.isNotBlank())
    val rawActions = value.getJSONArray("actions")
    require(rawActions.length() <= 1)
    val actions = (0 until rawActions.length()).map { part(rawActions.getJSONObject(it)) }
    val record = card.optJSONObject("user_record")
    val behavior = record?.optString("kind") == "behavior"
    require(actions.isNotEmpty() == behavior)
    actions.forEach {
        require(!it.actionId.isNullOrBlank() && it.text.isNotBlank())
        require(it.actorId == record?.let { r -> if (r.isNull("pet_id")) null else r.getString("pet_id") })
        require(it.actionId == "action:" + storyboardHash(canonicalJson(card.getJSONObject("core"))))
    }
    val original = if (value.isNull("original_text")) null else value.getString("original_text")
    if (record?.optString("kind") == "note") require(original == record.getString("text"))
    if (record?.optString("kind") !in setOf("note", "photo")) require(original == null)
    val body = (listOf(space.text) + actions.map { it.text } + listOfNotNull(original))
        .filter { it.isNotEmpty() }.joinToString("\n")
    require(card.getString("body") == body)
    return PublishedCardWriting(revision, titleOrigin, space, actions, original)
}
