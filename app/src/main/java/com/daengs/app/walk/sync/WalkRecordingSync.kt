package com.daengs.app.walk.sync

import com.daengs.app.walk.RecordedFix
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Versioned GPS eligibility, independent of location accuracy and motion policy. */
internal object WalkRecordingContract {
    const val VERSION = "gps-recording-v1"
    const val POLICY = "gps-recording-eligibility-v1"

    fun rawFingerprint(fixes: List<RecordedFix>): String {
        val rows = JSONArray()
        for (p in fixes.sortedBy { it.clientSeq }) rows.put(JSONArray().apply {
            put(p.clientSeq); put(p.chainIndex); put(p.atMillis)
            put(BigDecimal(p.lat.toString()).setScale(6, RoundingMode.HALF_EVEN).toPlainString())
            put(BigDecimal(p.lng.toString()).setScale(6, RoundingMode.HALF_EVEN).toPlainString())
            put(p.accuracyM?.let { BigDecimal(it.toString()).stripTrailingZeros().toPlainString() } ?: JSONObject.NULL)
            put(if (p.isMock) 1 else 0)
        })
        return digest("{\"v\":1,\"points\":$rows}")
    }

    fun evidenceFingerprint(fixes: List<RecordedFix>): String {
        val rows = JSONArray()
        for (p in fixes.sortedBy { it.clientSeq }) rows.put(JSONArray().put(p.clientSeq)
            .put(p.recordingEligible ?: JSONObject.NULL))
        return digest("{\"v\":1,\"policy\":\"$POLICY\",\"points\":$rows}")
    }

    private fun digest(value: String): String = "sha256:" + MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun List<RecordedFix>.toUploadPoints(): JSONArray = JSONArray().also { rows ->
    for (fix in this) rows.put(JSONObject().apply {
        put("client_seq", fix.clientSeq); put("chain_index", fix.chainIndex)
        put("at", fix.atMillis.toIso()); put("lat", fix.lat); put("lng", fix.lng)
        put("accuracy_m", fix.accuracyM ?: JSONObject.NULL); put("is_mock", fix.isMock)
        // Omission is historical unknown; never infer a true value from a missing field.
        fix.recordingEligible?.let { put("recording_eligible", it) }
    })
}

/** The server's stored receipt, not HTTP 200 alone, permits subsequent pin delivery. */
class WalkRecordingSync(
    private val request: suspend (String, String, String, JSONObject?) -> JSONObject = { token, path, method, body ->
        WalkApi.call(token, path, method, body, parse = ::JSONObject).getOrThrow()
    },
) {
    suspend fun requireSupport(token: String, checkOwner: () -> Unit = {}) {
        checkOwner()
        val caps = try { request(token, "/entry-capabilities", "GET", null) }
        catch (e: WalkHttpException) {
            if (e.statusCode != 404) throw e
            throw IOException("산책은 기기에 저장됐어요. 서버의 GPS 기록 구분 지원을 기다리고 있어요.", e)
        }
        checkOwner()
        val versions = caps.optJSONArray("gps_recording_versions")
        if (versions == null || (0 until versions.length()).none { versions.optString(it) == WalkRecordingContract.VERSION })
            throw IOException("산책은 기기에 저장됐어요. 서버의 GPS 기록 구분 지원을 기다리고 있어요.")
    }

    suspend fun ensure(token: String, walkId: String, fixes: List<RecordedFix>, checkOwner: () -> Unit = {}): String {
        suspend fun call(path: String, method: String, body: JSONObject?): JSONObject {
            checkOwner()
            return request(token, path, method, body).also { checkOwner() }
        }
        require(fixes.map { it.clientSeq } == fixes.indices.toList()) { "GPS 순번이 연속되지 않습니다." }
        val raw = WalkRecordingContract.rawFingerprint(fixes)
        val evidence = WalkRecordingContract.evidenceFingerprint(fixes)
        var detail = call("/$walkId", "GET", null)
        fun receipt(value: JSONObject): JSONObject = value.optJSONObject("recording_receipt")
            ?: throw IOException("서버가 GPS 기록 구분의 저장을 확인하지 못했어요.")
        fun validate(value: JSONObject) {
            check(value.optString("contract_version") == WalkRecordingContract.VERSION &&
                value.optString("policy_version") == WalkRecordingContract.POLICY &&
                value.optString("raw_input_fingerprint") == raw && value.optInt("point_count", -1) == fixes.size) {
                "서버와 기기의 GPS 원본이 달라요. 기기 기록은 보관하고 있어요."
            }
        }
        validate(receipt(detail))
        if (receipt(detail).optString("evidence_fingerprint") != evidence) {
            val remote = RemoteWalkDetail.parse(detail).fixes.associateBy { it.clientSeq }
            val missing = fixes.filter { local ->
                val stored = remote[local.clientSeq] ?: error("서버 GPS 순번이 누락됐어요.")
                check(stored.recordingEligible == null || stored.recordingEligible == local.recordingEligible) {
                    "서버와 기기의 GPS 구분이 달라요. 원본을 자동 변경하지 않았어요."
                }
                stored.recordingEligible == null && local.recordingEligible != null
            }
            for (chunk in missing.chunked(2_000)) {
                val reply = call("/$walkId/recording-evidence", "PUT", JSONObject()
                    .put("contract_version", WalkRecordingContract.VERSION)
                    .put("policy_version", WalkRecordingContract.POLICY)
                    .put("raw_input_fingerprint", raw).put("points", chunk.toUploadPoints()))
                validate(reply)
            }
            detail = call("/$walkId", "GET", null)
        }
        validate(receipt(detail))
        check(receipt(detail).optString("evidence_fingerprint") == evidence) {
            "서버가 GPS 기록 구분을 모두 저장하지 못했어요. 기기 기록은 보관하고 있어요."
        }
        return evidence
    }
}
