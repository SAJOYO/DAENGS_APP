package com.daengs.app.screening

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 저쪽 `schemas/screening.py` 가 만드는 모양 그대로 읽는가.
 *
 * **`created_by` 는 문자열이다** (`created_by: str | None`). 객체로 읽으면
 * `optJSONObject` 가 문자열 앞에서 null 을 주어 작성자가 조용히 사라진다.
 */
class ScreeningRecordContractTest {

    @Test
    fun `기록 작성자 이름표와 현재 사용자의 권한을 읽는다`() {
        val record = ScreeningRecord.parse(
            JSONObject(
                """{"record_id":"r1","pet_id":"p1","status":"DONE","created_at":"2026-09-10T10:00:00Z",
                    "created_by":"키키","can_confirm":false,"can_delete":true}""",
            ),
        )

        assertEquals("키키", record.createdBy)
        assertEquals(false, record.canConfirm)
        assertEquals(true, record.canDelete)
    }

    /** 아이를 지정하지 않은 개인 기록은 "구성원" 개념이 없어 이름표가 항상 null 이다. */
    @Test
    fun `아이 없는 개인 기록은 작성자 이름표가 없다`() {
        val record = ScreeningRecord.parse(
            JSONObject(
                """{"record_id":"r1","status":"DONE","created_at":"2026-09-10T10:00:00Z",
                    "created_by":null,"can_confirm":false,"can_delete":true}""",
            ),
        )

        assertNull(record.createdBy)
        assertEquals(true, record.canDelete)
    }

    @Test
    fun `옛 피부 기록 응답에는 권한 정보가 없어도 읽는다`() {
        val record = ScreeningRecord.parse(
            JSONObject("""{"record_id":"r1","status":"FAILED","created_at":"2026-09-10T10:00:00Z"}"""),
        )

        assertNull(record.createdBy)
        assertNull(record.canConfirm)
        assertNull(record.canDelete)
    }
}
