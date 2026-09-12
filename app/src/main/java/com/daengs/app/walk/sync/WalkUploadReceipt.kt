package com.daengs.app.walk.sync

import com.daengs.app.walk.RecordedFix
import java.util.UUID
import org.json.JSONObject

/** 업로드 2xx도 보낸 청크의 확인이어야 한다. 상세 GET/봉인 계약과 분리한다. */
internal object WalkUploadReceipt {
    const val VERSION = "walk-upload-receipt-v1"

    fun verify(
        response: JSONObject,
        clientSessionId: String,
        fixes: List<RecordedFix>,
        expectedWalkId: String? = null,
    ): String {
        val receipt = response.has("contract_version")
        if (receipt) {
            check(response.get("contract_version") == VERSION) { "지원하지 않는 산책 수신 확인 버전입니다." }
        } else {
            // 깨진 새 응답을 구형 응답으로 해석하지 않는다.
            check(!response.has("walk_id") && !response.has("chunk")) { "산책 수신 확인 버전이 없습니다." }
        }
        val walkId = response.uuid(if (receipt) "walk_id" else "id")
        check(response.uuid("client_session_id") == parseUuid(clientSessionId)) { "다른 산책 세션의 응답입니다." }
        check(expectedWalkId == null || walkId == parseUuid(expectedWalkId)) { "다른 서버 산책의 응답입니다." }

        val sequences = fixes.map { it.clientSeq }.sorted()
        check(sequences.all { it >= 0 } && sequences.distinct().size == fixes.size &&
            sequences.zipWithNext().all { (a, b) -> b.toLong() == a.toLong() + 1 }) {
            "보낼 좌표 순번이 연속되지 않습니다."
        }
        if (receipt) verifyChunk(response, sequences)
        else verifyLegacy(response, fixes)
        return walkId.toString()
    }

    private fun verifyChunk(response: JSONObject, sequences: List<Int>) {
        check(response.has("chunk")) { "산책 청크 수신 확인이 없습니다." }
        if (sequences.isEmpty()) {
            check(response.isNull("chunk")) { "빈 업로드에 좌표 수신 확인이 왔습니다." }
            return
        }
        val chunk = response.getJSONObject("chunk")
        check(chunk.integer("seq_from") == sequences.first().toLong() &&
            chunk.integer("seq_to") == sequences.last().toLong() &&
            chunk.integer("point_count") == sequences.size.toLong()) { "다른 좌표 묶음의 수신 확인입니다." }
        check(chunk.get("status") in setOf("stored", "replayed")) { "좌표 저장이 확인되지 않았습니다." }
    }

    private fun verifyLegacy(response: JSONObject, fixes: List<RecordedFix>) {
        // query를 무시하는 구형 서버의 누적 경로에서 이번 요청만 대조한다.
        val sequences = fixes.map { it.clientSeq }.toSet()
        val points = response.getJSONArray("points")
        for (index in 0 until points.length()) {
            val point = points.getJSONObject(index)
            val seq = point.integer("client_seq")
            check(seq in 0..Int.MAX_VALUE.toLong()) { "서버 좌표 순번이 잘못됐습니다." }
            if (seq.toInt() in sequences) {
                check(point.integer("chain_index") in 0..Int.MAX_VALUE.toLong())
                // 상세 표시용 parser의 숫자/문자열 강제 변환을 저장 확인에 쓰지 않는다.
                if (point.has("is_mock")) check(point.get("is_mock") is Boolean)
                if (!point.isNull("recording_eligible")) check(point.get("recording_eligible") is Boolean)
            }
        }
        val detail = RemoteWalkDetail.parse(response)
        val stored = detail.fixes.filter { it.clientSeq in sequences }
        check(stored.size == fixes.size && stored.map { it.clientSeq }.toSet() == sequences &&
            WalkRecordingContract.rawFingerprint(stored) == WalkRecordingContract.rawFingerprint(fixes)) {
            "서버의 좌표가 보낸 묶음과 다릅니다."
        }
        val bySequence = stored.associateBy { it.clientSeq }
        for (fix in fixes) {
            val known = bySequence.getValue(fix.clientSeq).recordingEligible
            // 구형 서버의 unknown은 뒤의 recording-evidence 단계에서 검증·복구한다.
            check(fix.recordingEligible == null || known == null || known == fix.recordingEligible) {
                "서버의 GPS 기록 구분이 보낸 값과 다릅니다."
            }
        }
    }

    private fun JSONObject.integer(key: String): Long {
        val value = get(key)
        check(value is Int || value is Long) { "수신 확인의 $key 값이 정수가 아닙니다." }
        return (value as Number).toLong()
    }

    private fun JSONObject.uuid(key: String): UUID {
        val value = get(key)
        check(value is String) { "산책 ID가 문자열이 아닙니다." }
        return parseUuid(value)
    }

    private fun parseUuid(value: String): UUID = UUID.fromString(value).also {
        check(it.toString().equals(value, ignoreCase = true)) { "산책 ID 형식이 잘못됐습니다." }
    }
}
