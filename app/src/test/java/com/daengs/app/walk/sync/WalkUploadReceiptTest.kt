package com.daengs.app.walk.sync

import com.daengs.app.walk.RecordedFix
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WalkUploadReceiptTest {
    private val points = listOf(uploadFix(2_000), uploadFix(2_001))

    @Test fun `stored와 replayed는 해당 청크의 확인이며 누적 개수가 아니다`() {
        for (status in listOf("stored", "replayed")) {
            assertEquals(UPLOAD_WALK, verify(uploadReceipt(points, status)))
        }
        assertEquals(UPLOAD_WALK, WalkUploadReceipt.verify(uploadReceipt(points), UPLOAD_SESSION, points.reversed(), UPLOAD_WALK))
    }

    @Test fun `빈 생성은 chunk null이어야 하며 누락이나 빈 객체는 확인이 아니다`() {
        assertEquals(UPLOAD_WALK, WalkUploadReceipt.verify(uploadReceipt(emptyList()), UPLOAD_SESSION, emptyList()))
        for (response in listOf(uploadReceipt(emptyList()).apply { remove("chunk") },
            uploadReceipt(emptyList()).put("chunk", JSONObject()), uploadReceipt(points))) {
            assertTrue(runCatching { WalkUploadReceipt.verify(response, UPLOAD_SESSION, emptyList()) }.isFailure)
        }
        rejects(uploadReceipt(points).put("chunk", JSONObject.NULL))
    }

    @Test fun `버전 없음 null 새 버전은 구형 응답으로 떨어지지 않는다`() {
        rejects(uploadReceipt(points).apply { remove("contract_version") })
        for (version in listOf(JSONObject.NULL, "walk-upload-receipt-v2", 1)) {
            rejects(uploadReceipt(points).put("contract_version", version))
            rejects(legacyUpload(points).put("contract_version", version))
        }
        rejects(legacyUpload(points).put("chunk", JSONObject.NULL))
        rejects(legacyUpload(points).put("walk_id", UPLOAD_WALK))
    }

    @Test fun `다른 세션 서버 ID 및 잘못된 UUID는 완료로 인정하지 않는다`() {
        for (value in listOf(UPLOAD_OTHER, "", "walk", "1-1-1-1-1", 42, JSONObject.NULL)) {
            rejects(uploadReceipt(points).put("client_session_id", value))
            rejects(uploadReceipt(points).put("walk_id", value))
        }
        assertTrue(runCatching {
            WalkUploadReceipt.verify(uploadReceipt(points).put("walk_id", "1-1-1-1-1"), UPLOAD_SESSION, points)
        }.isFailure)
        assertEquals(UPLOAD_WALK, WalkUploadReceipt.verify(uploadReceipt(points)
            .put("walk_id", UPLOAD_WALK.uppercase()), UPLOAD_SESSION.uppercase(), points, UPLOAD_WALK))
    }

    @Test fun `순번 개수 상태 불일치와 숫자 강제 변환을 거부한다`() {
        for ((key, values) in mapOf(
            "seq_from" to listOf(0, 2_001, "2000", 2000.5, JSONObject.NULL),
            "seq_to" to listOf(2_000, 4_000, "2001", 2001.5, JSONObject.NULL),
            "point_count" to listOf(0, 2_002, "2", 2.5, JSONObject.NULL),
            "status" to listOf("pending", "STORED", "", true, JSONObject.NULL),
        )) {
            for (value in values) rejects(uploadReceipt(points).apply { getJSONObject("chunk").put(key, value) })
            rejects(uploadReceipt(points).apply { getJSONObject("chunk").remove(key) })
        }
    }

    @Test fun `로컬 중복이나 청크 내부 누락도 잘못된 확인으로 통과하지 않는다`() {
        for (fixes in listOf(listOf(uploadFix(-1)), listOf(uploadFix(0), uploadFix(0)), listOf(uploadFix(0), uploadFix(2)))) {
            assertTrue(runCatching { WalkUploadReceipt.verify(uploadReceipt(fixes), UPLOAD_SESSION, fixes) }.isFailure)
        }
    }

    @Test fun `구형 누적 경로는 해당 청크의 좌표만 대조하고 순서와 저장 정밀도를 허용한다`() {
        val stored = listOf(uploadFix(0)) + points.map { it.copy(lat = 37.123457, lng = 127.765432) }
        val response = legacyUpload(stored.reversed())
        assertEquals(UPLOAD_WALK, verify(response))
        assertEquals(UPLOAD_WALK, WalkUploadReceipt.verify(response, UPLOAD_SESSION, emptyList()))
    }

    @Test fun `구형 응답의 좌표 누락 중복 다른 내용은 성공이 아니다`() {
        rejects(legacyUpload(listOf(points.first())))
        rejects(legacyUpload(points + points.first()))
        val first = points.first()
        for (changed in listOf(first.copy(chainIndex = 1), first.copy(atMillis = first.atMillis + 1),
            first.copy(lat = 38.0), first.copy(lng = 128.0), first.copy(accuracyM = 10f), first.copy(isMock = true))) {
            rejects(legacyUpload(listOf(changed, points.last())))
        }
    }

    @Test fun `구형 응답의 필수 상세 필드와 두 ID도 확인한다`() {
        for (key in listOf("id", "client_session_id", "points", "started_at", "ended_at")) {
            rejects(legacyUpload(points).apply { remove(key) })
        }
        rejects(legacyUpload(points).put("id", UPLOAD_OTHER))
        rejects(legacyUpload(points).put("client_session_id", UPLOAD_OTHER))
        rejects(JSONObject().put("id", UPLOAD_WALK))
    }

    @Test fun `구형 좌표의 순번 잘림과 불리언 문자열을 저장 확인으로 해석하지 않는다`() {
        for ((key, value) in listOf("client_seq" to 2000.5, "client_seq" to "2000",
            "client_seq" to 4_294_969_296L, "chain_index" to 0.5,
            "is_mock" to "false", "recording_eligible" to "false")) {
            rejects(legacyUpload(points).apply { getJSONArray("points").getJSONObject(0).put(key, value) })
        }
    }

    @Test fun `구형 unknown은 recording 복구에 맡기고 알려진 GPS 충돌은 거부한다`() {
        val known = points.map { it.copy(recordingEligible = false) }
        assertEquals(UPLOAD_WALK, WalkUploadReceipt.verify(legacyUpload(points), UPLOAD_SESSION, known))
        assertEquals(UPLOAD_WALK, verify(legacyUpload(known)))
        assertTrue(runCatching { WalkUploadReceipt.verify(legacyUpload(known.map { it.copy(recordingEligible = true) }),
            UPLOAD_SESSION, known) }.isFailure)
    }

    private fun verify(response: JSONObject) = WalkUploadReceipt.verify(response, UPLOAD_SESSION, points, UPLOAD_WALK)
    private fun rejects(response: JSONObject) {
        assertTrue("잘못된 확인이 성공했습니다: $response", runCatching { verify(response) }.isFailure)
    }
}

internal const val UPLOAD_SESSION = "cd1e0000-0000-0000-0000-000000000001"
internal const val UPLOAD_WALK = "ab2f0000-0000-0000-0000-000000000002"
internal const val UPLOAD_OTHER = "00000000-0000-0000-0000-000000000003"
internal fun uploadFix(seq: Int) = RecordedFix(seq, 0, 1_000L + seq,
    37.1234567, 127.7654321, 5f, false)

/** DEV#478 docs/walk/upload-receipts.md의 wire 형식. point_count는 요청 청크 개수. */
internal fun uploadReceipt(points: List<RecordedFix>, status: String = "stored") = JSONObject()
    .put("contract_version", "walk-upload-receipt-v1").put("walk_id", UPLOAD_WALK)
    .put("client_session_id", UPLOAD_SESSION).put("chunk", if (points.isEmpty()) JSONObject.NULL else JSONObject()
        .put("seq_from", points.minOf { it.clientSeq }).put("seq_to", points.maxOf { it.clientSeq })
        .put("point_count", points.size).put("status", status))

internal fun legacyUpload(points: List<RecordedFix>) = JSONObject()
    .put("id", UPLOAD_WALK).put("client_session_id", UPLOAD_SESSION)
    .put("started_at", 1_000L.toIso()).put("ended_at", 10_000L.toIso())
    .put("points", points.toUploadPoints().apply {
        // Pydantic Decimal 좌표는 응답에서 문자열이다.
        for (index in 0 until length()) getJSONObject(index).apply {
            put("lat", points[index].lat.toString())
            put("lng", points[index].lng.toString())
        }
    })
