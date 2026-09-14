package com.daengs.app.pet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `is_group_owner` 는 `is_owner` 와 **다른 값**이다.
 *
 * 내가 등록한 아이라도 남의 아이와 연결되면 그룹 주보호자는 초대한 쪽이다. 공통 정보
 * 수정·삭제는 서버가 409 (`not_group_owner`) 로 막으므로, 앱이 `is_owner` 로 버튼을 열면
 * 사용자는 눌러 봐야 실패를 안다.
 */
class PetGroupOwnerTest {

    @Test
    fun `연결된 아이는 내 행의 대표여도 그룹 주보호자가 아닐 수 있다`() {
        val pet = Pet.parse(JSONObject(base(isOwner = true, isGroupOwner = false)))

        assertTrue("내 행의 대표는 맞다", pet.isOwner)
        assertFalse("그룹 주보호자는 아니다", pet.isGroupOwner)
    }

    /**
     * **구 서버에는 필드가 없다.** 그때 `false` 로 떨어지면 자기 강아지의 수정 버튼이
     * 통째로 사라진다 — 연결이 없으면 두 값이 같으므로 `is_owner` 를 기본값으로 쓴다.
     */
    @Test
    fun `필드가 없으면 is_owner 를 따라간다`() {
        val mine = Pet.parse(JSONObject("""{"id":"p1","name":"네옹","breed":"beagle","is_primary":true,"is_owner":true}"""))
        val cared = Pet.parse(JSONObject("""{"id":"p2","name":"롱이","breed":"beagle","is_primary":false,"is_owner":false}"""))

        assertTrue(mine.isGroupOwner)
        assertFalse("돌보미 아이에 그룹 관리 버튼을 열면 안 된다", cared.isGroupOwner)
    }

    /** 두 필드가 다 없는 아주 옛 응답도 있다 — 그때는 내 아이로 본다. */
    @Test
    fun `두 필드가 다 없으면 내 아이로 본다`() {
        val pet = Pet.parse(JSONObject("""{"id":"p1","name":"네옹","breed":"beagle","is_primary":true}"""))

        assertTrue(pet.isOwner)
        assertTrue(pet.isGroupOwner)
    }

    @Test
    fun `연결 없는 아이는 두 값이 같다`() {
        val pet = Pet.parse(JSONObject(base(isOwner = true, isGroupOwner = true)))

        assertEquals(pet.isOwner, pet.isGroupOwner)
    }

    /** 이름 바꾸기는 전체 PUT 이 아니라 이름 한 칸만 보낸다 — 공통 정보를 덮으면 안 된다. */
    @Test
    fun `이름 수정 본문은 이름 한 칸뿐이다`() {
        val body = PetApi.displayNameBody("롱롱씨")

        assertEquals("롱롱씨", body.getString("name"))
        assertEquals("한 칸뿐이다", 1, body.length())
        assertFalse("견종·몸무게가 같이 가면 공통 정보가 덮인다", body.has("breed"))
    }

    /**
     * 경로에는 **목록이 준 id(= 받는 사람의 표시 행)** 가 들어가고, 전체 PUT 경로(`/{id}`)가
     * 아니라 `/display` 로 끝난다.
     */
    @Test
    fun `이름 수정 경로는 표시 행 id 의 display 다`() {
        assertEquals("/b-row/display", PetApi.displayNamePath("b-row"))
    }

    private fun base(isOwner: Boolean, isGroupOwner: Boolean) = """
        {"id":"p1","name":"네옹","breed":"beagle","is_primary":true,
         "is_owner":$isOwner,"is_group_owner":$isGroupOwner}
    """.trimIndent()
}
