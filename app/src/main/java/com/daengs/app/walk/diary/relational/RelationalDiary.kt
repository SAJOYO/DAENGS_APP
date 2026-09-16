package com.daengs.app.walk.diary.relational

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.diary.DiarySceneAddress
import kotlinx.serialization.json.JsonObject
import java.time.Instant

enum class RelationalStatus { PENDING, RUNNING, READY, FAILED, STALE }
enum class RelationalPartStatus { RETURNED, FAILED, NOT_REQUESTED }
enum class RelationalSemanticStatus { MODEL_REVIEWED, UNVERIFIED, NOT_PUBLISHED }
enum class RelationalFamily { ROAD, LAND_COVER, SURROUNDING_OBJECT, AREA_CONTEXT }
enum class RelationalCollectionStatus { COMPLETE, PARTIAL, EMPTY, FAILED, NOT_REQUESTED, UNKNOWN }
enum class RelationalPositionMethod { OBSERVED, ESTIMATED, LAST_KNOWN, NONE }

data class RelationalDiaryPart(
    val status: RelationalPartStatus,
    val text: String,
    val semanticStatus: RelationalSemanticStatus,
)

data class RelationalFixRef(val clientSeq: Long, val chainIndex: Long, val at: Instant)
data class RelationalAnchor(
    val eventAt: Instant,
    val timeBasis: String,
    val point: GeoPoint?,
    val locationAt: Instant?,
    val accuracyM: Double?,
    val positionState: String,
    val method: RelationalPositionMethod,
    val sourceFixes: List<RelationalFixRef>,
)

/** Header data is available to the existing location/weather display, never added to prose. */
data class RelationalHeader(val sceneId: String, val dong: String?, val weather: JsonObject?,
    val administrativeAddress: DiarySceneAddress? = null)
data class RelationalFactScope(val kind: String, val description: String, val coverageKey: String?)
data class RelationalSceneFact(
    val id: String,
    val family: RelationalFamily,
    val value: JsonObject,
    val scope: RelationalFactScope,
    val subjectKey: String?,
    val sourceRefs: List<String>,
    val observedAt: Instant?,
    val retrievedAt: Instant?,
    val referenceDate: String?,
    val timeMeaning: String,
)
data class RelationalSceneSnapshot(
    val sceneId: String,
    val walkId: String,
    val recordedAt: Instant,
    val point: GeoPoint?,
    val accuracyM: Double?,
    val positionBasis: RelationalPositionMethod,
    val facts: List<RelationalSceneFact>,
    val collection: Map<RelationalFamily, RelationalCollectionStatus>,
    val collectionReasons: Map<RelationalFamily, List<String>>,
)

data class RelationalRecordRef(
    val store: String, val id: String, val version: String, val versionKind: String, val pinRevision: Long?,
)
sealed interface RelationalRecordContent {
    data class Behavior(val code: String, val petId: String?) : RelationalRecordContent
    data class Note(val text: String) : RelationalRecordContent
    data class Photo(val mediaRef: String) : RelationalRecordContent
}
data class RelationalOriginal(
    val ref: RelationalRecordRef,
    val deleted: Boolean,
    val content: RelationalRecordContent?,
    val anchor: RelationalAnchor?,
    val pinPayload: JsonObject?,
)
data class RelationalDiaryCard(
    val sceneId: String,
    val anchor: RelationalAnchor,
    val header: RelationalHeader,
    val space: RelationalDiaryPart,
    val action: RelationalDiaryPart,
    /** Authoritative server body, including an intentional empty string. No client rewriting. */
    val body: String,
    val currentContext: RelationalSceneSnapshot,
    val comparisonSceneId: String?,
    val originals: List<RelationalOriginal>,
)
data class RelationalDiaryBundle(
    val clientSessionId: String,
    val title: String?,
    val titleStatus: RelationalPartStatus,
    val cards: List<RelationalDiaryCard>,
)
data class RelationalPhotoManifest(val publisherId: String, val revision: Long)
data class RelationalDiaryResponse(
    val sessionId: String,
    val generation: Long,
    val inputRevision: String,
    val status: RelationalStatus,
    val entryRevisions: Map<String, Long>,
    val photosStatus: String,
    val photoManifest: RelationalPhotoManifest?,
    val targetSceneCount: Int,
    val bundle: RelationalDiaryBundle?,
    val errorCode: String?,
    val executionLimits: JsonObject,
    /** Exact public response for durable storage; excludes the backend's private receipt. */
    val rawJson: String,
) {
    companion object {
        const val FORMAT = "walk-relational-diary-v1"
        const val RESPONSE = "walk-relational-diary-response-v1"
        fun parse(rawJson: String): RelationalDiaryResponse = RelationalDiaryParser.parse(rawJson)
    }
}
