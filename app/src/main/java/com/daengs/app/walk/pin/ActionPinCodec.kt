package com.daengs.app.walk.pin

import com.daengs.app.location.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import java.util.UUID

/** Immutable wire snapshot. Also reads legacy-v1 projections without inventing raw references. */
data class ActionPin(val payload: String) {
    private val json get() = JSONObject(payload)
    val state: String get() = json.getString("state")
    val method: String get() = json.getString("method")
    val point: GeoPoint? get() = json.optJSONObject("point")?.let { GeoPoint(it.getDouble("lat"), it.getDouble("lng")) }
    val resolveByMillis: Long get() = json.time("resolve_by")
    val isLocalPolicy: Boolean get() = json.getString("policy_version") == PinPolicyV1.VERSION
    /** Null means an older projection without references; an empty list still carries a receipt. */
    internal fun sceneReferences(targetAtMillis: Long): List<ActionPinSourceRef>? {
        val j = json
        if (!j.has("source_refs")) return null
        require(j.time("target_at") == targetAtMillis)
        val refs = j.getJSONArray("source_refs")
        require(refs.length() <= 100)
        return (0 until refs.length()).map { refs.getJSONObject(it).let { r ->
            ActionPinSourceRef(r.getInt("client_seq"), r.getInt("chain_index"), r.time("at"))
        } }.also { require(it.distinct().size == it.size) }
    }
    val label: String get() = when {
        state == "provisional" -> "위치 추정 중"
        state == "unlocated" -> "위치 없이 남긴 행동"
        method == "last_known" -> "마지막 확인 위치"
        method == "estimated" -> "추정 위치"
        else -> "GPS 위치"
    }

    internal fun resolution(owner: String, session: String, chain: Int): ActionPinResolution {
        val j = json
        require(isLocalPolicy)
        val refs = j.getJSONArray("source_refs")
        return ActionPinResolution(
            ActionPinRequest(UUID.fromString(j.getString("resolution_id")), owner, session, chain, j.time("target_at")),
            ActionPinState.valueOf(state.uppercase(Locale.ROOT)),
            ActionPinMethod.valueOf(method.uppercase(Locale.ROOT)), point, j.time("computed_at"), resolveByMillis,
            (0 until refs.length()).map { refs.getJSONObject(it).let { r ->
                ActionPinSourceRef(r.getInt("client_seq"), r.getInt("chain_index"), r.time("at"))
            } },
            if (j.isNull("uncertainty_m")) null else j.getDouble("uncertainty_m"),
            ActionPinUncertaintyBasis.valueOf(j.getString("uncertainty_basis").uppercase(Locale.ROOT)),
            ActionPinReason.valueOf(j.getString("reason").uppercase(Locale.ROOT)),
        )
    }
}

fun ActionPinResolution.toPin(observationCutoffMillis: Long? = null): ActionPin = ActionPin(JSONObject().apply {
    put("resolution_id", request.resolutionId.toString())
    put("state", state.name.lowercase(Locale.ROOT)); put("method", method.name.lowercase(Locale.ROOT))
    put("target_at", request.targetAtMillis.iso()); put("computed_at", computedAtMillis.iso())
    put("resolve_by", resolveByMillis.iso())
    observationCutoffMillis?.let { put("observation_cutoff_at", it.iso()) }
    put("policy_version", policyVersion); put("algorithm_version", algorithmVersion)
    put("point", point?.let { JSONObject().put("lat", it.latitude).put("lng", it.longitude) } ?: JSONObject.NULL)
    put("source_refs", JSONArray().apply { sourceRefs.forEach {
        put(JSONObject().put("client_seq", it.clientSeq).put("chain_index", it.chainIndex).put("at", it.atMillis.iso()))
    } })
    // Raw upload serializes Float accuracy as its decimal text, not the widened binary Double.
    put("uncertainty_m", if (uncertaintyBasis == ActionPinUncertaintyBasis.PROVIDER_ACCURACY)
        uncertaintyMeters?.toFloat()?.toString()?.toDouble() ?: JSONObject.NULL else uncertaintyMeters ?: JSONObject.NULL)
    put("uncertainty_basis", uncertaintyBasis.name.lowercase(Locale.ROOT))
    put("reason", reason.name.lowercase(Locale.ROOT))
}.toString())

private fun Long.iso() = Instant.ofEpochMilli(this).toString()
private fun JSONObject.time(key: String) = Instant.parse(getString(key)).toEpochMilli()
