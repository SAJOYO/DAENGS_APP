package com.daengs.app.walk.diary

import com.daengs.app.walk.WalkPhoto
import com.daengs.app.walk.WalkSummary
import com.daengs.app.walk.diary.relational.*
import org.json.JSONObject
import java.util.Locale

/** Saved cards are the reading unit. No legacy scene generation or default prose runs here. */
internal fun relationalDiaryWalk(walk: WalkSummary, input: DiaryBoardInput,
    photos: List<WalkPhoto>, draftPayload: String?): DiaryWalk {
    val saved = input.source.relational
    val publication = saved?.published?.takeIf { it.sessionId == walk.sessionId }
    val bundle = publication?.bundle
    val status = saved?.latest?.status?.name?.lowercase(Locale.ROOT) ?: input.source.relationalStatus
    val notice = when (status) {
        "pending", "running" -> if (bundle != null) "새 일기를 정리하고 있어요. 저장된 일기를 먼저 보여드려요." else "산책을 정리하고 있어요."
        "failed" -> if (bundle != null) "새 일기를 만들지 못했어요. 저장된 일기는 그대로 볼 수 있어요." else "일기를 만들지 못했어요. 다시 생성할 수 있어요."
        "stale" -> "기록이 바뀌었어요. 일기를 다시 생성해 주세요."
        else -> if (bundle == null) "저장된 일기를 확인하지 못했어요. 다시 불러와 주세요." else ""
    }
    if (bundle == null) {
        val originals = relationalOriginalScenes(walk, input, photos, StoryboardDraft.parse(draftPayload))
        return DiaryWalk(walk, originals, notice,
            preparing = originals.isEmpty() && status in setOf("pending", "running"), sourceEntries = input.entries)
    }
    val draft = StoryboardDraft.parse(draftPayload)
    val edits = draft.edits.groupBy { it.id }.mapNotNull { (id, values) ->
        values.singleOrNull()?.let { id to it }
    }.toMap()
    val editedOriginalIds = draft.edits.map { it.id }.filter { it.startsWith("original:") }.toSet()
    val behaviorByCard = bundle.cards.map { relationalBehaviorReference(it, input.entries, walk.sessionId) }
    val behaviorCardCounts = behaviorByCard.mapNotNull { it?.entryId }.groupingBy { it }.eachCount()
    val originalSources = relationalOriginalScenes(walk, input, photos, StoryboardDraft()).mapNotNull { scene ->
        scene.source?.let { it.id to it }
    }.toMap()
    fun originalEditId(reference: StoryboardEntryReference?) = reference?.entryId
        ?.takeIf { behaviorCardCounts[it] == 1 }?.let { "original:entry:$it" }
    val absorbedOriginalEditIds = behaviorByCard.mapNotNull(::originalEditId).filter { it in edits }.toSet()

    fun applyDirectEdit(source: StoryboardScene, edit: SceneEdit): StoryboardScene =
        source.copy(title = edit.title, body = edit.body, needsReview = edit.sourceFingerprint != source.fingerprint,
            bodyScope = edit.bodyScope)

    fun carryOriginalEdit(source: StoryboardScene, edit: SceneEdit, original: StoryboardScene?): StoryboardScene {
        val sameOriginal = original != null && edit.sourceFingerprint == original.fingerprint
        return source.copy(
            title = if (sameOriginal && edit.title == original.title) source.title else edit.title,
            body = if (sameOriginal && edit.body == original.body) source.body else edit.body,
            needsReview = !sameOriginal,
            bodyScope = edit.bodyScope,
        )
    }

    val scenes = bundle.cards.mapIndexedNotNull { index, card ->
        val anchor = card.anchor
        val behavior = behaviorByCard[index]
        val originals = card.originals.filterNot { it.deleted ||
            "original:${if (it.ref.store == "walk_photo") "photo" else "entry"}:${it.ref.id}" in editedOriginalIds }
        val temperature = relationalTemperature(card)
        val content = DiarySceneContent("", if (behavior != null) "behavior" else "relational", point = anchor.point, locationLabel = "",
            address = card.header.administrativeAddress?.cardLabel() ?: card.header.dong, order = index,
            administrativeAddress = card.header.administrativeAddress,
            locationMethod = anchor.method.name.lowercase(Locale.ROOT),
            locationAtMillis = anchor.locationAt?.toEpochMilli(), positionState = anchor.positionState,
            temperatureC = temperature?.celsius, temperatureObservation = temperature)
        // This carrier supports the existing local edit/hide UI only; it is not a legacy bundle.
        val source = StoryboardScene("relational:${card.sceneId}", anchor.eventAt.toEpochMilli(),
            card.title ?: "산책 장면 ${index + 1}", card.body, "", relationalBodyFingerprint(card), diary = content,
            observation = anchor.sourceFixes.singleOrNull()?.takeIf {
                anchor.method in setOf(RelationalPositionMethod.OBSERVED, RelationalPositionMethod.LAST_KNOWN) &&
                    anchor.point != null && anchor.locationAt == it.at &&
                    it.clientSeq in 0..Int.MAX_VALUE.toLong() && it.chainIndex in 0..Int.MAX_VALUE.toLong()
            }?.let { StoryboardObservation(it.clientSeq.toInt(), it.chainIndex.toInt(), it.at.toEpochMilli(), requireNotNull(anchor.point)) },
            entryReference = behavior, bodyScope = SceneBodyScope.SCENE)
        val directEdit = edits[source.id]
        val inheritedId = originalEditId(behavior)
        val inheritedEdit = inheritedId?.let(edits::get)
        val effectiveEdit = directEdit ?: inheritedEdit
        if (effectiveEdit?.hidden == true) return@mapIndexedNotNull null
        val displayed = when {
            directEdit != null -> applyDirectEdit(source, directEdit)
            inheritedEdit != null -> carryOriginalEdit(source, inheritedEdit, inheritedId?.let(originalSources::get))
            else -> source
        }
        DiaryScene("${walk.sessionId}/${source.id}", walk.sessionId, displayed.atMillis, displayed.title,
            displayed.body, anchor.point, "", needsReview = displayed.needsReview,
            entryId = behavior?.entryId, content = content, source = displayed, relational = card,
            originalNotes = originals.mapNotNull { (it.content as? RelationalRecordContent.Note)?.text },
            originalPhotos = originals.filter { it.content is RelationalRecordContent.Photo }.map { original ->
                DiaryOriginalPhoto(original.ref.id, photos.singleOrNull {
                    it.sessionId == walk.sessionId && it.id == original.ref.id && it.id in input.photoIds
                })
            }, notice = relationalPartNotice(card))
    }
    val editedOriginals = relationalOriginalScenes(walk, input, photos, draft).filter {
        it.source?.id in editedOriginalIds && it.source?.id !in absorbedOriginalEditIds
    }
    return DiaryWalk(walk, (scenes + editedOriginals).sortedWith(compareBy<DiaryScene> { it.atMillis }
        .thenBy { it.content?.order ?: Int.MAX_VALUE }.thenBy { it.id }), notice, bundle.title,
        published = true, sourceEntries = input.entries)
}

/** Preserve the pre-title edit identity exactly. A new title cannot invalidate a body edit. */
internal fun relationalBodyFingerprint(card: RelationalDiaryCard): String = storyboardHash(
    "RelationalDiaryCard(sceneId=${card.sceneId}, anchor=${card.anchor}, header=${card.header}, " +
        "space=${card.space}, action=${card.action}, body=${card.body}, currentContext=${card.currentContext}, " +
        "comparisonSceneId=${card.comparisonSceneId}, originals=${card.originals})")

internal fun relationalPartNotice(card: RelationalDiaryCard): String {
    val spaceFailed = card.space.status == RelationalPartStatus.FAILED
    val actionFailed = card.action.status == RelationalPartStatus.FAILED
    return when {
        spaceFailed && actionFailed -> "이 장면의 문장을 만들지 못했어요. 남긴 기록은 그대로 볼 수 있어요."
        spaceFailed -> "공간 문장을 만들지 못했어요."
        actionFailed -> "행동 문장을 만들지 못했어요."
        else -> ""
    }
}

/** Reuse the existing historical-temperature validator and header renderer. */
internal fun relationalTemperature(card: RelationalDiaryCard): DiarySceneTemperature? {
    val observations = card.header.weather?.let { JSONObject(it.toString()).optJSONArray("observations") } ?: return null
    val values = (0 until observations.length()).mapNotNull { i ->
        val item = observations.optJSONObject(i) ?: return@mapNotNull null
        val facts = item.optJSONObject("facts") ?: return@mapNotNull null
        val temperature = JSONObject(facts.toString()).put("scene_at", card.anchor.eventAt.toString())
            .put("evidence_id", item.optString("source_id"))
        sceneTemperature(JSONObject().put("temperature", temperature), card.anchor.eventAt.toEpochMilli())
    }.distinctBy { listOf(it.celsius, it.observedAtMillis, it.gridX, it.gridY) }
    return values.singleOrNull()
}
