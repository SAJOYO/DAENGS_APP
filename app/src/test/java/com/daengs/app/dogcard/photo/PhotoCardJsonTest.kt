package com.daengs.app.dogcard.photo

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** 저쪽 `schemas/ai_card.py` 의 `AiCardResponse` 를 읽는다. */
class PhotoCardJsonTest {

    private val generating = """
        {"id":"8a1c","dog_id":null,"month":9,"card":"9","dog_name":"콩이","title":"CHUSEOK 콩이",
         "status":"generating","error_code":null,"likeness":null,"attempts":null,
         "width":null,"height":null,"created_at":"2026-09-15T01:02:03.456789Z","image_url":null}
    """.trimIndent()

    /** 종류 카드는 `month` 가 null 로 오고 `card` 만 카드를 가리킨다 (#593, D-085). */
    private val strawberry = """
        {"id":"7b2d","dog_id":"d-1","month":null,"card":"strawberry","dog_name":"콩이","title":"BERRY 콩이",
         "status":"ready","error_code":null,"likeness":4,"attempts":1,
         "width":994,"height":1582,"created_at":"2026-09-18T01:02:03.456789Z","image_url":null}
    """.trimIndent()

    @Test
    fun `만드는 중인 카드를 읽는다`() {
        val card = parsePhotoCard(JSONObject(generating))
        assertEquals("8a1c", card.id)
        assertNull(card.dogId)
        assertEquals(PhotoCardKey.of(9), card.key)
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
        assertEquals(2, list.cards.size)
    }

    /** #543 배포 전에는 `daily_remaining` 이 안 온다 — 그때는 앱이 막지 않는다 (docs §9.1). */
    @Test
    fun `남은 횟수를 읽는다`() {
        val withRemaining = parsePhotoCardList("""{"cards":[],"daily_limit":1,"daily_remaining":0}""")
        assertEquals(0, withRemaining.dailyRemaining)
        val absent = parsePhotoCardList("""{"cards":[]}""")
        assertNull(absent.dailyRemaining)
        val explicitNull = parsePhotoCardList("""{"cards":[],"daily_limit":null,"daily_remaining":null}""")
        assertNull(explicitNull.dailyRemaining)
    }

    /** 종류 카드 — `month` 가 null 이어도 `card` 로 어느 카드인지 안다 (#593, D-085). */
    @Test
    fun `종류 카드는 달이 없어도 읽는다`() {
        val card = parsePhotoCard(JSONObject(strawberry))
        assertEquals(PhotoCardKey.Strawberry, card.key)
        assertNull(card.key.month)
        assertTrue(card.key.isKind)
        assertEquals("딸기", card.key.label)
        assertEquals(PhotoCardStatus.Ready, card.status)
    }

    /**
     * **`card` 를 `month` 보다 먼저 본다.** 전환기 계약이 둘을 함께 싣는데, 종류 카드에서
     * `month` 를 먼저 보면 null 이라 읽을 수 없고, 달 카드에서 어긋난 값이 오면 앱이 서버와
     * 다른 카드를 가리키게 된다.
     */
    @Test
    fun `카드 키가 달보다 우선이다`() {
        val odd = generating.replace("\"card\":\"9\"", "\"card\":\"strawberry\"")
        assertEquals(PhotoCardKey.Strawberry, parsePhotoCard(JSONObject(odd)).key)
    }

    /** #593 배포 전 서버는 `card` 를 안 준다 — 그때는 `month` 로 만든다. */
    @Test
    fun `카드 키가 없으면 달로 만든다`() {
        val old = generating.replace(",\"card\":\"9\"", "")
        assertEquals(PhotoCardKey.of(9), parsePhotoCard(JSONObject(old)).key)
    }

    /** 둘 다 없는 행은 그릴 칸을 찾을 수가 없다 — 조용히 넘기면 빈 칸이 생긴다. */
    @Test
    fun `카드도 달도 없으면 튄다`() {
        val broken = generating.replace(",\"card\":\"9\"", "").replace("\"month\":9", "\"month\":null")
        assertThrows(JSONException::class.java) { parsePhotoCard(JSONObject(broken)) }
    }

    @Test
    fun `모르는 상태는 만드는 중으로 본다`() {
        assertEquals(PhotoCardStatus.Failed, PhotoCardStatus.of("failed"))
        assertEquals(PhotoCardStatus.Generating, PhotoCardStatus.of("queued"))
    }

    /** 한글 이름이 쿼리에서 깨지면 서버가 엉뚱한 이름으로 카드를 굽는다. */
    @Test
    fun `쿼리는 한글 이름을 인코딩하고 강아지가 없으면 뺀다`() {
        assertEquals("card=4&dog_name=%EC%BD%A9%EC%9D%B4", photoCardQuery(PhotoCardKey.of(4), "콩이", null))
        assertEquals("card=9&dog_name=a+b&dog_id=d-1", photoCardQuery(PhotoCardKey.of(9), "a b", "d-1"))
    }

    /**
     * **`month` 를 같이 보내지 않는다** (#593, D-085). 서버는 둘 다 받지만 어긋나면
     * 400 `card_conflict` 라, 어긋날 수 있는 길을 아예 안 만든다. 달 카드도 `card=` 로 간다.
     */
    @Test
    fun `쿼리는 카드 키만 보낸다`() {
        assertEquals(
            "card=strawberry&dog_name=%EC%BD%A9%EC%9D%B4&dog_id=d-1",
            photoCardQuery(PhotoCardKey.Strawberry, "콩이", "d-1"),
        )
        listOf(
            photoCardQuery(PhotoCardKey.of(4), "콩이", null),
            photoCardQuery(PhotoCardKey.Lettuce, "콩이", "d-1", "NEO"),
        ).forEach { assertFalse(it, it.contains("month=")) }
    }

    /** 제목 이름은 앞뒤 공백을 걷고, 걷은 뒤 비면 아예 안 보낸다 (§9.2). */
    @Test
    fun `쿼리는 제목 이름을 걷어서 보내고 비었으면 뺀다`() {
        assertEquals("card=9&dog_name=%EC%95%88%EB%85%95&dog_id=d-1&title_name=NEO", photoCardQuery(PhotoCardKey.of(9), "안녕", "d-1", " NEO "))
        assertEquals("card=9&dog_name=%EC%95%88%EB%85%95&dog_id=d-1", photoCardQuery(PhotoCardKey.of(9), "안녕", "d-1", "  "))
        assertEquals("card=9&dog_name=%EC%95%88%EB%85%95&dog_id=d-1", photoCardQuery(PhotoCardKey.of(9), "안녕", "d-1", null))
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
        assertEquals(
            "9월 카드를 만들지 못했어요 · 강아지가 잘 보이는 다른 사진으로 해 주세요",
            photoFailureText(PhotoCardKey.of(9), "no_image"),
        )
        assertTrue(photoFailureText(PhotoCardKey.of(4), "interrupted").endsWith("잠시 뒤 다시 만들어 주세요"))
        assertTrue(photoFailureText(PhotoCardKey.of(4), null).endsWith("잠시 뒤 다시 만들어 주세요"))
    }

    /** 종류 카드는 「딸기 카드를」 이다 — 「0월 카드를」 이 되면 안 된다 (D-085 가 콘솔에서 겪은 것). */
    @Test
    fun `종류 카드의 실패 문장은 이름으로 말한다`() {
        assertTrue(photoFailureText(PhotoCardKey.Strawberry, null).startsWith("딸기 카드를 만들지 못했어요"))
        assertTrue(photoFailureText(PhotoCardKey.Lettuce, "no_image").startsWith("상추 카드를 만들지 못했어요"))
    }

    /** 앱이 모르는 종류를 서버가 먼저 열 수 있다 — 빈칸보다 영문이라도 보여 주는 편이 낫다. */
    @Test
    fun `모르는 종류는 키를 그대로 보여 준다`() {
        assertEquals("tomato", PhotoCardKey("tomato").label)
        assertTrue(PhotoCardKey("tomato").isKind)
    }

    /** 1~12 밖의 숫자는 달이 아니다 — 「0월」·「13월」 칸이 서면 안 된다. */
    @Test
    fun `달이 아닌 숫자는 달로 읽지 않는다`() {
        assertNull(PhotoCardKey("0").month)
        assertNull(PhotoCardKey("13").month)
        assertEquals(12, PhotoCardKey("12").month)
    }

    /** 막힌 카드를 한 줄로 알릴 때 — 달이 앞, 종류가 뒤다 (§10.2). */
    @Test
    fun `막힌 카드 줄은 달부터 센다`() {
        val taken = setOf(PhotoCardKey.Lettuce, PhotoCardKey.of(9), PhotoCardKey.of(4))
        assertEquals("4월·9월·상추", taken.labelList())
    }
}
