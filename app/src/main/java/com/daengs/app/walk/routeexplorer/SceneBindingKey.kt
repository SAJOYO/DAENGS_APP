package com.daengs.app.walk.routeexplorer

import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.WalkEntry
import com.daengs.app.walk.WalkMeasurementDetail
import com.daengs.app.walk.diary.DiaryScene
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Derived from one immutable read. Login generation is enforced by its owning source/flow. */
internal data class SceneBindingKey(val ownerId: String, val sessionId: String, val sceneId: String,
    val measurementId: String, val resultDigest: String, val eventRevision: String,
    val sceneRevision: String, val policyVersion: String = POLICY) {
    companion object {
        const val POLICY = "measurement-scene-binding-v2"
        fun of(measurement: WalkMeasurementDetail, scene: DiaryScene, entry: WalkEntry?): SceneBindingKey {
            val (event, sceneRevision) = revisions(scene, entry)
            return SceneBindingKey(measurement.ownerId, scene.sessionId, scene.id, measurement.id,
                measurement.resultDigest, event, sceneRevision)
        }
        fun revisions(scene: DiaryScene, entry: WalkEntry?): Pair<String, String> {
            val source = scene.source; val anchor = source?.observation; val content = scene.content
            val pin = entry?.pin; val photo = scene.photo
            val event = revision(scene.sessionId, scene.id, scene.atMillis, scene.point, scene.entryId,
                source?.id, source?.atMillis, anchor?.clientSeq, anchor?.chainIndex, anchor?.atMillis, anchor?.point,
                content?.point, content?.locationAtMillis, content?.locationMethod, content?.positionState,
                photo?.id, photo?.sessionId, photo?.capturedAtMillis, photo?.point,
                entry?.id, entry?.sessionId, entry?.recordedAtMillis, entry?.point, entry?.locationCapturedAtMillis,
                pin?.point, pin?.method, pin?.state, pin?.payload, scene.relational?.anchor?.toString())
            val sceneRevision = revision(scene.title, scene.body, scene.evidence, scene.needsReview,
                source?.fingerprint, source?.available, source?.hidden, content?.recordText, content?.recordKind,
                content?.locationLabel, content?.address, content?.order, entry?.note, entry?.petId)
            return event to sceneRevision
        }
    }
}

/** Length-prefixed, typed fields avoid delimiter collisions and JSONObject/JVM platform differences. */
private fun revision(vararg values: Any?): String {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
        fun field(value: Any?) {
            when (value) {
                null -> out.writeByte(0)
                is String -> { out.writeByte(1); val s = value.toByteArray(Charsets.UTF_8); out.writeInt(s.size); out.write(s) }
                is Int -> { out.writeByte(2); out.writeInt(value) }
                is Long -> { out.writeByte(3); out.writeLong(value) }
                is Boolean -> { out.writeByte(4); out.writeBoolean(value) }
                is GeoPoint -> { out.writeByte(5); out.writeLong(value.latitude.toRawBits()); out.writeLong(value.longitude.toRawBits()) }
                else -> error("Unsupported scene revision field")
            }
        }
        values.forEach(::field)
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
}
