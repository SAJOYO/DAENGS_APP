package com.daengs.app.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OCR 학습 이용 동의는 `PATCH /auth/app/me` 한 칸이다.
 *
 * 여기서 잡는 것은 **보낸 칸만 바뀐다**는 저쪽 규칙을 앱이 지키는가 하나다. 닉네임을
 * 고칠 때 `ocr_consent` 를 같이 보내면 그 값으로 덮이고, 반대로 토글만 보낼 때
 * 닉네임을 같이 보내도 덮인다. 판 번호는 서버가 정하므로 앱이 보낼 자리가 없다.
 */
class AuthApiOcrConsentTest {

    @Test
    fun `동의 토글은 ocr_consent 칸만 싣는다 — 닉네임을 같이 보내지 않는다`() {
        val body = AuthApi.ocrConsentBody(true)

        assertTrue(body.getBoolean("ocr_consent"))
        assertFalse("같이 보내면 그 값으로 덮인다", body.has("nickname"))
        assertFalse(body.has("room_name"))
        assertFalse("판 번호는 서버가 정한다 — 앱이 고를 수 있으면 근거가 안 선다", body.has("ocr_consent_version"))
        assertEquals("칸이 하나뿐이다", 1, body.length())
    }

    @Test
    fun `끄는 것도 같은 칸 하나다`() {
        val body = AuthApi.ocrConsentBody(false)

        assertFalse(body.getBoolean("ocr_consent"))
        assertEquals(1, body.length())
    }

    @Test
    fun `me 응답의 ocr_consent 는 불리언이다`() {
        assertTrue(AuthApi.parseMe(JSONObject(ME_CONSENTED)).ocrConsent)
        assertFalse(AuthApi.parseMe(JSONObject(ME_NOT_CONSENTED)).ocrConsent)
    }

    @Test
    fun `칸이 아예 없는 옛 서버에서도 미동의로 읽고 안 깨진다`() {
        val me = AuthApi.parseMe(JSONObject(ME_OLD_SERVER))

        assertFalse(me.ocrConsent)
        assertEquals("u1", me.appUserId)
        assertNull(me.nickname)
    }

    private companion object {
        const val ME_CONSENTED = """
            {"app_user_id": "u1", "room_name": "네옹이네", "nickname": "윤주",
             "ocr_consent": true, "ocr_consent_version": "2026-09-01"}
        """

        const val ME_NOT_CONSENTED = """
            {"app_user_id": "u1", "room_name": "네옹이네", "nickname": "윤주",
             "ocr_consent": false, "ocr_consent_version": null}
        """

        /** 이 칸을 모르는 서버. 로그인이 실패하면 안 된다. */
        const val ME_OLD_SERVER = """{"app_user_id": "u1", "room_name": null}"""
    }
}
