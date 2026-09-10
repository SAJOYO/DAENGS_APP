package com.daengs.app.screening

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreeningRecordContractTest {

    @Test
    fun `기록 작성자와 현재 사용자의 권한을 읽는다`() {
        val record = ScreeningRecord.parse(
            JSONObject(
                """{"record_id":"r1","pet_id":"p1","status":"DONE","created_at":"2026-09-10T10:00:00Z",
                    "created_by":{"app_user_id":"u1","nickname":"키키"},
                    "can_confirm":false,"can_delete":true}""",
            ),
        )

        assertEquals("u1", record.createdBy?.appUserId)
        assertEquals("키키", record.createdBy?.displayName)
        assertEquals(false, record.canConfirm)
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
