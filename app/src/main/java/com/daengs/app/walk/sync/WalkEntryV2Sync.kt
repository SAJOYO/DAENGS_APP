package com.daengs.app.walk.sync

import com.daengs.app.walk.store.WalkDao
import com.daengs.app.walk.store.WalkEntryRow
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** Frozen outbox envelope; local edits and pin completion cannot mutate a sent request. */
internal data class PinPending(val json: JSONObject) {
    val kind get() = json.getString("kind")
    val body get() = json.getJSONObject("body")
    val mutation get() = body.getString("mutation_id")
    val snapshot get() = json.optString("content", "").ifEmpty { null }
    val pin get() = json.optString("pin", "").ifEmpty { null }
    val localVersion get() = json.getString("local_version")

    companion object {
        fun from(row: WalkEntryRow, cutoffSupported: Boolean = true, recordingEvidence: String? = null): PinPending {
            val kind = when { row.payload == null -> "delete"; row.revision == 0 -> "create";
                row.dirty -> "content"; else -> "pin" }
            val body = JSONObject().put("expected_revision", row.revision)
                .put("mutation_id", UUID.randomUUID().toString())
            if (kind == "create" || kind == "content") body.put("content", JSONObject(requireNotNull(row.payload)))
            if (kind == "create" || kind == "pin") body.put("pin", row.pinPayload?.let(::JSONObject) ?: JSONObject.NULL)
            if (kind == "pin") body.put("expected_pin_revision", row.pinRevision)
            if ((kind == "create" || kind == "pin") && recordingEvidence != null)
                body.put("recording_evidence_fingerprint", recordingEvidence)
            val pin = body.optJSONObject("pin")
            if (!cutoffSupported && pin != null && !pin.isNull("observation_cutoff_at")) {
                fun time(key: String) = java.time.Instant.parse(pin.getString(key))
                if (time("observation_cutoff_at") < minOf(time("computed_at"), time("resolve_by")))
                    throw IOException("행동은 기기에 저장됐어요. 서버의 관측 종료 시각 지원을 기다리고 있어요.")
                pin.remove("observation_cutoff_at")
            }
            return PinPending(JSONObject().put("kind", kind).put("body", body)
                .put("local_version", row.mutationId).put("content", row.payload ?: "")
                .put("pin", row.pinPayload ?: ""))
        }
    }
}

/** v1 remains usable for old servers/old records; v2 records never downgrade to v1. */
class WalkEntryV2Sync(private val dao: WalkDao, private val owner: () -> String,
    private val request: suspend (String, String, String, JSONObject?, Boolean) -> JSONObject = { token, path, method, body, v2 ->
        WalkApi.call(token, path, method, body, v2 = v2, parse = ::JSONObject).getOrThrow()
    }) {
    suspend fun sync(token: String, sessionId: String, walkId: String): Boolean {
        val account = owner()
        fun checkAccount() { check(owner() == account) { "계정이 변경됐어요." } }
        if (account.isEmpty() || dao.session(sessionId)?.ownerId != account) return true
        suspend fun call(path: String, method: String, body: JSONObject?, v2: Boolean = true): JSONObject {
            checkAccount()
            val result = request(token, path, method, body, v2)
            checkAccount()
            return result
        }
        val caps = try { call("/entry-capabilities", "GET", null, false) }
        catch (e: WalkHttpException) {
            if (e.statusCode != 404) throw e
            JSONObject()
        }
        fun supports(field: String, value: String): Boolean = caps.optJSONArray(field)?.let { a ->
            (0 until a.length()).any { a.optString(it) == value }
        } == true
        if (!supports("read_versions", "walk-entry-v2")) {
            if (dao.entries(sessionId).any { it.isV2 }) throw IOException("행동은 기기에 저장됐어요. 서버의 새 기록 지원을 기다리고 있어요.")
            return false
        }
        val canCreate = supports("write_versions", "walk-entry-v2") &&
            supports("active_policy_versions", "action-pin-policy-v1")
        val needsEvidence = dao.entries(sessionId).any {
            it.isV2 && it.payload != null && (it.syncError == null || it.syncError == LEGACY_PIN_SOURCE_ERROR) &&
                (it.dirty || it.pinDirty || it.pendingRequest != null)
        }
        val recordingEvidence = if (needsEvidence) {
            if (!supports("gps_recording_versions", WalkRecordingContract.VERSION))
                throw IOException("행동은 기기에 저장됐어요. 서버의 GPS 기록 구분 지원을 기다리고 있어요.")
            val fixes = dao.fixes(sessionId).map { p ->
                com.daengs.app.walk.RecordedFix(p.clientSeq, p.chainIndex, p.atMillis, p.lat, p.lng,
                    p.accuracyM, p.isMock, recordingEligible = p.recordingEligible)
            }
            WalkRecordingSync { _, path, method, body -> call(path, method, body, false) }
                .ensure(token, walkId, fixes)
        } else null
        if (recordingEvidence != null && dao.fixes(sessionId).any { it.recordingEligible == false })
            dao.retryLegacyPinSourceErrors(sessionId, account)
        for (initial in dao.entries(sessionId)) {
            // The per-entry outbox also serializes a local finalization behind a lost create ACK.
            repeat(4) {
                checkAccount()
                val row = dao.entry(initial.id) ?: return@repeat
                if ((!row.dirty && !row.pinDirty && row.pendingRequest == null) || row.syncError != null) return@repeat
                if (!row.isV2) {
                    val response = try { if (row.payload == null) call("/$walkId/entries/${row.id}?expected_revision=${row.revision}&mutation_id=${row.mutationId}", "DELETE", null, false)
                    else call("/$walkId/entries/${row.id}", "PUT", JSONObject().put("expected_revision", row.revision)
                        .put("mutation_id", row.mutationId).put("content", JSONObject(row.payload)), false)
                    } catch (e: WalkHttpException) {
                        if (e.statusCode !in listOf(409, 426)) throw e
                        val latest = call("/$walkId/entries", "GET", null).getJSONArray("entries")
                        val remote = (0 until latest.length()).map { latest.getJSONObject(it) }
                            .firstOrNull { it.getString("id") == row.id } ?: throw e
                        dao.rebaseLegacyEntry(row.id, remote.toString(), account, requiresV2 = e.statusCode == 426)
                        return@repeat
                    }
                    dao.acknowledgeEntry(row.id, response.getInt("revision"), row.mutationId)
                    return@repeat
                }
                if (row.revision == 0 && row.payload != null && row.pendingRequest == null && !canCreate) return@repeat
                val serialized = dao.preparePinRequest(row.id, account,
                    caps.optBoolean("pin_observation_cutoff_supported"), recordingEvidence) ?: return@repeat
                val pending = PinPending(JSONObject(serialized))
                val path = "/$walkId/entries/${row.id}" + when (pending.kind) {
                    "delete" -> "?expected_revision=${pending.body.getInt("expected_revision")}&mutation_id=${pending.mutation}"
                    "pin" -> "/pin"
                    else -> ""
                }
                try {
                    val response = call(path, if (pending.kind == "delete") "DELETE" else "PUT",
                        if (pending.kind == "delete") null else pending.body)
                    dao.ackPinRequest(row.id, serialized, response.toString(), account)
                } catch (e: WalkHttpException) {
                    if (e.statusCode == 409 && e.message.orEmpty().contains("walk_entry_v2_writes_disabled")) {
                        throw IOException("행동은 기기에 저장됐어요. 서버 전송이 일시 보류됐어요.", e)
                    }
                    if (e.statusCode in listOf(422, 426)) {
                        dao.rejectPinRequest(row.id, serialized, account,
                            if (e.statusCode == 426) "앱 업데이트가 필요한 기록이에요. 기기의 원본은 보관하고 있어요."
                            else if (recordingEvidence != null) "GPS 구분을 전달했지만 서버가 위치 근거를 확인하지 못했어요. 기기 원본은 보관하고 있어요."
                            else LEGACY_PIN_SOURCE_ERROR)
                        return@repeat
                    }
                    if (e.statusCode !in listOf(409, 410)) throw e
                    val latest = call("/$walkId/entries", "GET", null).getJSONArray("entries")
                    val remote = (0 until latest.length()).map { latest.getJSONObject(it) }
                        .firstOrNull { it.getString("id") == row.id } ?: throw e
                    dao.conflictPinRequest(row.id, serialized, remote.toString(), account)
                }
            }
        }
        val entries = call("/$walkId/entries", "GET", null).getJSONArray("entries")
        for (i in 0 until entries.length()) {
            checkAccount()
            dao.acceptPinRemote(sessionId, entries.getJSONObject(i).toString(), account)
        }
        if (dao.entries(sessionId).any { it.isV2 && it.revision == 0 && it.payload != null && it.dirty } && !canCreate) {
            throw IOException("행동은 기기에 저장됐어요. 서버의 새 기록 쓰기를 기다리고 있어요.")
        }
        return true
    }
}

internal const val LEGACY_PIN_SOURCE_ERROR = "서버가 기록의 위치 근거를 확인하지 못했어요. 기기의 원본은 보관하고 있어요."
