package com.daengs.app.dogcard.photo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** 저쪽 `schemas/ai_card.py` 의 `AiCardResponse` 를 읽는다. */
class PhotoCardJsonTest {

    private val generating = """
        {"id":"8a1c","dog_id":null,"month":9,"dog_name":"콩이","title":"CHUSEOK 콩이",
         "status":"generating","error_code":null,"likeness":null,"attempts":null,
         "width":null,"height":null,"created_at":"2026-09-15T01:02:03.456789Z","image_url":null}
    """.trimIndent()

    @Test
    fun `만드는 중인 카드를 읽는다`() {
        val card = parsePhotoCard(JSONObject(generating))
        assertEquals("8a1c", card.id)
        assertNull(card.dogId)
        assertEquals(9, card.month)
        assertEquals("콩이", card.dogName)
        assertEquals(PhotoCardStatus.Generating, card.status)
        assertNull(card.likeness)
        assertEquals(Instant.parse("2026-09-15T01:02:03.456Z").toEpochMilli(), card.createdAtMillis)
    }

    /** 파이썬 쪽이 `+00:00` 으로 줄 수도 있다. */
    @Test
    fun `오프셋 표기 시각도 읽는다`() {
        val card = parsePhotoCard(JSONObject(generating.replace("Z\"", "+09:00\"")))
        assertEquals(Instant.parse("2026-09-14T16:02:03.456Z").toEpochMilli(), card.createdAtMillis)
    }

    @Test
    fun `완성 단건은 그림 주소를 준다`() {
        val body = generating.replace("\"generating\"", "\"ready\"")
            .replace("\"likeness\":null", "\"likeness\":5")
            .replace("\"image_url\":null", "\"image_url\":\"https://storage.example/a.png?sig=1\"")
        val detail = parsePhotoCardDetail(body)
        assertEquals(PhotoCardStatus.Ready, detail.card.status)
        assertEquals(5, detail.card.likeness)
        assertEquals("https://storage.example/a.png?sig=1", detail.imageUrl)
    }

    @Test
    fun `목록을 읽는다`() {
        val list = parsePhotoCardList("""{"cards":[$generating,$generating]}""")
        assertEquals(2, list.size)
    }

    @Test
    fun `모르는 상태는 만드는 중으로 본다`() {
        assertEquals(PhotoCardStatus.Failed, PhotoCardStatus.of("failed"))
        assertEquals(PhotoCardStatus.Generating, PhotoCardStatus.of("queued"))
    }

    /** 한글 이름이 쿼리에서 깨지면 서버가 엉뚱한 이름으로 카드를 굽는다. */
    @Test
    fun `쿼리는 한글 이름을 인코딩하고 강아지가 없으면 뺀다`() {
        assertEquals("month=4&dog_name=%EC%BD%A9%EC%9D%B4", photoCardQuery(4, "콩이", null))
        assertEquals("month=9&dog_name=a+b&dog_id=d-1", photoCardQuery(9, "a b", "d-1"))
    }

    /** 서버 `message` 는 앱이 그대로 띄우는 문장이다. */
    @Test
    fun `오류 본문의 문장을 통과시킨다`() {
        val body = """{"detail":{"code":"limit_reached","message":"오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요."}}"""
        assertEquals("오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요.", photoCardErrorMessage(429, body))
        assertEquals("서버 오류 (500)", photoCardErrorMessage(500, "<html>"))
        assertEquals("서버 오류 (502)", photoCardErrorMessage(502, null))
    }

    @Test
    fun `실패 코드마다 다시 할 일을 말한다`() {
        assertEquals("9월 카드를 만들지 못했어요 · 강아지가 잘 보이는 다른 사진으로 해 주세요", photoFailureText(9, "no_image"))
        assertTrue(photoFailureText(4, "interrupted").endsWith("잠시 뒤 다시 만들어 주세요"))
        assertTrue(photoFailureText(4, null).endsWith("잠시 뒤 다시 만들어 주세요"))
    }
}
