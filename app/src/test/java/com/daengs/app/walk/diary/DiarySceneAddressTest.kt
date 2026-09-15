package com.daengs.app.walk.diary

import com.daengs.app.walk.support.diaryBoardFixture
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiarySceneAddressTest {
    @Test fun `national administrative structures keep their supplied names`() {
        val examples = listOf(
            listOf("서울특별시", "중랑구", "신내2동", "서울 중랑구 신내2동"),
            listOf("경기도", "수원시 영통구", "매탄3동", "수원시 영통구 매탄3동"),
            listOf("경기도", "양평군", "양평읍", "양평군 양평읍"),
            listOf("세종특별자치시", "세종시", "조치원읍", "세종시 조치원읍"),
            listOf("제주특별자치도", "제주시", "애월읍", "제주시 애월읍"),
            listOf("대전광역시", "서구", "둔산2동", "대전 서구 둔산2동"),
            listOf("강원특별자치도", "평창군", "대관령면", "평창군 대관령면"),
        )
        examples.forEach { (sido, sigungu, dong, expected) ->
            assertEquals(expected, DiarySceneAddress(sido, sigungu, dong, "administrative_dong").cardLabel())
        }
    }

    @Test fun `missing and conflicting snapshots never invent an address`() {
        fun piece(facts: String) = JSONObject().put("kind", "place_reference").put("facts", JSONObject(facts))
        assertNull(sceneAddress(JSONArray()))
        assertNull(sceneAddress(JSONArray().put(piece("""{"dong":null,"sigungu":"null"}"""))))
        assertEquals("신내2동", sceneAddress(JSONArray().put(piece("""{"dong":" 신내2동 "}""")))!!.cardLabel())
        assertNull(sceneAddress(JSONArray().put(piece("""{"dong":"신내1동"}"""))
            .put(piece("""{"dong":"신내2동"}"""))))
    }

    @Test fun `saved board parsing retains address components without changing prose`() {
        val response = diaryBoardFixture()
        val scene = response.getJSONObject("bundle").getJSONArray("scenes").getJSONObject(0)
        val originalBody = scene.getString("body")
        scene.put("place_reference", JSONArray().put(JSONObject().put("kind", "place_reference")
            .put("facts", JSONObject().put("sido", "서울특별시").put("sigungu", "중랑구")
                .put("dong", "신내2동").put("address_type", "administrative_dong"))))
        val parsed = GeoStoryboardBundle.parse(response.toString()).scenes.first()
        assertEquals("서울 중랑구 신내2동", parsed.diary!!.address)
        assertEquals("서울특별시", parsed.diary!!.administrativeAddress!!.sido)
        assertEquals(originalBody, parsed.body)
        assertNull(parsed.diary!!.temperatureC)
    }
}
