package com.daengs.app.pet

import com.daengs.app.miniroom.art.DogBreed
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 저쪽 계약을 우리가 제대로 읽는가.
 *
 * 아래 JSON 은 `DAENGS_dev` 의 `schemas/pet.py` 가 만드는 모양 그대로다.
 * 저쪽이 필드를 바꾸면 여기가 먼저 깨져야 한다.
 */
class PetTest {

    private val full = """
        {
          "id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
          "name": "네옹",
          "breed": "dog_toy_poodle_light_brown",
          "sex": "female",
          "neutered": true,
          "weight_kg": "4.2",
          "birth_date": "2023-05-14",
          "birth_date_kind": "birthday",
          "is_primary": true
        }
    """.trimIndent()

    @Test
    fun `가득 찬 응답을 읽는다`() {
        val p = Pet.parse(JSONObject(full))
        assertEquals("네옹", p.name)
        assertEquals(Pet.Sex.FEMALE, p.sex)
        assertEquals(true, p.neutered)
        assertEquals(LocalDate.of(2023, 5, 14), p.birthDate)
        assertEquals(Pet.BirthDateKind.BIRTHDAY, p.birthDateKind)
        assertTrue(p.isPrimary)
        assertTrue("이전 서버 응답은 소유한 아이로 취급한다", p.isOwner)
    }

    @Test
    fun `공동 돌봄 아이를 is_owner로 구분한다`() {
        val p = Pet.parse(JSONObject(full).put("is_owner", false))
        assertFalse(p.isOwner)
    }

    /**
     * **몸무게는 문자열로 온다.**
     *
     * 저쪽이 `Decimal` 이라 pydantic 이 정밀도를 지키려고 `"4.2"` 처럼 따옴표를
     * 씌워 내보낸다. `optDouble` 로 읽으면 NaN 이고, 화면에는 몸무게가 없는 것처럼
     * 보인다 — 숫자가 0 으로 뭉개지는 게 아니라 **조용히 사라지는** 종류다.
     */
    @Test
    fun `몸무게가 문자열로 와도 숫자로 읽는다`() {
        assertEquals(4.2f, Pet.parse(JSONObject(full)).weightKg!!, 0.001f)
    }

    /** 모르는 것은 null 이다. **false 로 바뀌면 안 된다.** */
    @Test
    fun `모름은 null 로 남는다`() {
        val json = """
            {"id":"x","name":"모름이","breed":"mix","sex":null,"neutered":null,
             "weight_kg":null,"birth_date":null,"birth_date_kind":null,"is_primary":false}
        """.trimIndent()
        val p = Pet.parse(JSONObject(json))
        assertNull(p.sex)
        assertNull(p.neutered)
        assertNull(p.weightKg)
        assertNull(p.birthDate)
        assertNull(p.birthDateKind)
        assertFalse(p.isPrimary)
    }

    /**
     * 견종은 문자열이라 **모르는 값이 와도 안 깨진다.**
     *
     * 서버는 어휘를 검사하지 않는다. 믹스(`mix`)나 앱을 업데이트하기 전에 저쪽에서
     * 늘어난 견종이 올 수 있고, enum 으로 받으면 그때 파싱이 통째로 죽는다.
     */
    @Test
    fun `모르는 견종이 와도 파싱이 안 깨진다`() {
        val json = """{"id":"x","name":"믹스","breed":"mix","is_primary":false}"""
        val p = Pet.parse(JSONObject(json))
        assertEquals("mix", p.breed)
        assertNull("모르는 견종은 그릴 얼굴이 없다", p.breedArt)
    }

    /**
     * **견종 id 는 `dog_` 로 시작한다** (`DogBreed.id` = `"dog_toy_poodle_light_brown"`).
     *
     * 그림 리소스 이름을 그대로 id 로 쓰기 때문이다. 서버로 보낼 때도 이 값을 그대로
     * 보내야 다시 받아 얼굴로 이을 수 있다 — 접두어를 떼서 보내면 돌아온 값이
     * [DogBreed.byId] 에서 null 이 되어 대체 얼굴이 뜬다.
     */
    @Test
    fun `아는 견종은 얼굴로 이어진다`() {
        val p = Pet.parse(JSONObject(full))
        assertEquals(DogBreed.TOY_POODLE_LIGHT_BROWN, p.breedArt)
        assertEquals("dog_toy_poodle_light_brown", DogBreed.TOY_POODLE_LIGHT_BROWN.id)
    }

    /**
     * 날짜와 종류는 **같이 있거나 같이 없어야** 한다.
     *
     * 서버도 422 로 막지만, 보내기 전에 알면 화면에서 바로 말해 줄 수 있다.
     */
    @Test
    fun `날짜만 있으면 보낼 수 없다`() {
        val base = PetDraft(name = "네옹", breed = "beagle")
        assertTrue(base.valid)
        assertFalse(base.copy(birthDate = LocalDate.of(2024, 1, 1)).valid)
        assertFalse(base.copy(birthDateKind = Pet.BirthDateKind.BIRTHDAY).valid)
        assertTrue(
            base.copy(
                birthDate = LocalDate.of(2024, 1, 1),
                birthDateKind = Pet.BirthDateKind.FAMILY_DAY,
            ).valid,
        )
    }

    @Test
    fun `이름이 비면 보낼 수 없다`() {
        assertFalse(PetDraft(name = "   ", breed = "beagle").valid)
    }

    /** 안 채운 항목은 **JSON null 로 보낸다.** 빼먹으면 서버가 옛 값을 남길지 알 수 없다. */
    @Test
    fun `모르는 항목은 null 로 보낸다`() {
        val json = PetDraft(name = "네옹", breed = "beagle").toJson()
        assertTrue(json.isNull("sex"))
        assertTrue(json.isNull("neutered"))
        assertTrue(json.isNull("weight_kg"))
        assertTrue(json.isNull("birth_date"))
        assertTrue(json.isNull("birth_date_kind"))
        assertEquals("네옹", json.getString("name"))
    }

    @Test
    fun `보낼 때 이름 앞뒤 공백을 턴다`() {
        assertEquals("네옹", PetDraft(name = "  네옹 ", breed = "beagle").toJson().getString("name"))
    }

    /** 상한은 **서버가 정한다.** 앱에 숫자를 박으면 저쪽이 바꿔도 앱은 옛 숫자를 쓴다. */
    @Test
    fun `목록에서 마릿수 상한을 읽는다`() {
        val list = PetList.parse(JSONObject("""{"pets": [], "max_pets": 5}"""))
        assertEquals(5, list.maxPets)
        assertTrue(list.pets.isEmpty())
    }

    /**
     * 상한이 안 와도 **목록은 떠야 한다.**
     *
     * 예전엔 `getInt` 라 상한 한 필드 때문에 파싱이 통째로 실패했다. 그러면 사용자는
     * `+` 버튼이 아니라 **자기 강아지를 통째로 못 본다.** 모르면 모르는 채로 둔다.
     */
    @Test
    fun `상한이 안 와도 강아지는 읽는다`() {
        val body = """{"pets": [{"id": "p1", "name": "네옹", "breed": "beagle", "is_primary": true}]}"""
        val list = PetList.parse(JSONObject(body))
        assertNull(list.maxPets)
        assertEquals("네옹", list.pets.single().name)
    }

    // ── 돌봄 (#200 · 저쪽 #331) ─────────────────────────────────────────

    private val care = """
        {"id":"x","name":"네옹","breed":"mix","is_primary":false,
         "feeding_style":"scheduled","feeding_times":["08:00","19:30"],
         "health_conditions":"슬개골 탈구","medications":"관절약"}
    """.trimIndent()

    @Test
    fun `돌봄 칸을 읽는다`() {
        val p = Pet.parse(JSONObject(care))
        assertEquals(Pet.FeedingStyle.SCHEDULED, p.feedingStyle)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(19, 30)), p.feedingTimes)
        assertEquals("슬개골 탈구", p.healthConditions)
        assertEquals("관절약", p.medications)
    }

    /** 옛 서버(#331 전)는 이 칸을 아예 안 준다. **없어도 깨지면 안 되고 null 이다.** */
    @Test
    fun `돌봄 칸이 안 와도 null 로 읽는다`() {
        val p = Pet.parse(JSONObject(full))
        assertNull(p.feedingStyle)
        assertNull(p.feedingTimes)
        assertNull(p.healthConditions)
        assertNull(p.medications)
    }

    @Test
    fun `자율급식은 시각 없이 온다`() {
        val json = """{"id":"x","name":"네옹","breed":"mix","is_primary":false,
            "feeding_style":"free","feeding_times":null}"""
        val p = Pet.parse(JSONObject(json))
        assertEquals(Pet.FeedingStyle.FREE, p.feedingStyle)
        assertNull(p.feedingTimes)
    }

    /** 초안에도 실린다. 빠지면 몸무게 한 번 고칠 때 지병이 null 로 덮인다 (PUT). */
    @Test
    fun `초안이 돌봄 칸을 그대로 담는다`() {
        val d = Pet.parse(JSONObject(care)).toDraft()
        assertEquals(Pet.FeedingStyle.SCHEDULED, d.feedingStyle)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(19, 30)), d.feedingTimes)
        assertEquals("슬개골 탈구", d.healthConditions)
        assertEquals("관절약", d.medications)
    }

    @Test
    fun `돌봄 칸을 안 채우면 null 로 보낸다`() {
        val json = PetDraft(name = "네옹", breed = "beagle").toJson()
        assertTrue(json.isNull("feeding_style"))
        assertTrue(json.isNull("feeding_times"))
        assertTrue(json.isNull("health_conditions"))
        assertTrue(json.isNull("medications"))
    }

    @Test
    fun `시간제 급식은 시각을 HH_MM 배열로 보낸다`() {
        val json = PetDraft(
            name = "네옹", breed = "beagle",
            feedingStyle = Pet.FeedingStyle.SCHEDULED,
            feedingTimes = listOf(LocalTime.of(8, 0), LocalTime.of(19, 30)),
        ).toJson()
        assertEquals("scheduled", json.getString("feeding_style"))
        val arr = json.getJSONArray("feeding_times")
        assertEquals(listOf("08:00", "19:30"), (0 until arr.length()).map(arr::getString))
    }

    /**
     * 시간제인데 시각을 아직 안 넣었으면 **빈 배열이 아니라 null** 이다.
     * 저쪽이 `min_length=1` 이라 `[]` 는 422 다.
     */
    @Test
    fun `시각이 하나도 없으면 null 로 보낸다`() {
        val json = PetDraft(
            name = "네옹", breed = "beagle",
            feedingStyle = Pet.FeedingStyle.SCHEDULED, feedingTimes = emptyList(),
        ).toJson()
        assertEquals("scheduled", json.getString("feeding_style"))
        assertTrue(json.isNull("feeding_times"))
    }

    /** 공백만 적은 병·약은 **모름** 이다. 빈 문자열이 가면 저쪽이 "복약 중" 으로 읽는다. */
    @Test
    fun `공백만 있는 병과 약은 null 로 보낸다`() {
        val json = PetDraft(
            name = "네옹", breed = "beagle", healthConditions = "  ", medications = " 관절약 ",
        ).toJson()
        assertTrue(json.isNull("health_conditions"))
        assertEquals("관절약", json.getString("medications"))
    }

    /** 시각은 시간제일 때만 있을 수 있다. 서버도 막지만 여기서 알아야 화면이 말한다. */
    @Test
    fun `자율급식에 시각이 붙으면 보낼 수 없다`() {
        val base = PetDraft(name = "네옹", breed = "beagle", feedingTimes = listOf(LocalTime.of(8, 0)))
        assertFalse(base.valid)
        assertFalse(base.copy(feedingStyle = Pet.FeedingStyle.FREE).valid)
        assertTrue(base.copy(feedingStyle = Pet.FeedingStyle.SCHEDULED).valid)
    }

    /** 저쪽 상한(`CARE_TEXT_MAX` = 200). 넘기면 422 라 보내기 전에 안다. */
    @Test
    fun `병과 약은 200자를 넘기면 보낼 수 없다`() {
        val base = PetDraft(name = "네옹", breed = "beagle")
        assertTrue(base.copy(healthConditions = "가".repeat(PetDraft.CARE_TEXT_MAX)).valid)
        assertFalse(base.copy(healthConditions = "가".repeat(PetDraft.CARE_TEXT_MAX + 1)).valid)
        assertFalse(base.copy(medications = "가".repeat(PetDraft.CARE_TEXT_MAX + 1)).valid)
    }
}
