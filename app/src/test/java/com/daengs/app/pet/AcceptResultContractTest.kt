package com.daengs.app.pet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 묶음 수락 응답. 저쪽 `InviteAcceptResponse` 다.
 *
 * **최상위 `pet_id`·`name` 은 구 앱 호환 앵커일 뿐이다.** 새 앱이 그것만 읽으면 여러 마리를
 * 받았을 때 나머지가 사라진다 — 항목별 `display_pet_id` 를 써야 한다.
 */
class AcceptResultContractTest {

    @Test
    fun `항목별 결과를 읽는다`() {
        val accepted = AcceptedInvite.parse(JSONObject(BUNDLE))

        assertEquals(2, accepted.pets.size)
        assertEquals(AcceptResult.LINKED, accepted.pets[0].result)
        assertEquals(AcceptResult.JOINED, accepted.pets[1].result)
    }

    /**
     * **연결했으면 이후 요청에 쓸 id 가 초대에 담겼던 아이가 아니다.** 하나로 뭉치면
     * 받는 사람이 초대한 사람의 행에 기록을 쓴다.
     */
    @Test
    fun `연결한 항목은 초대된 id 와 표시 id 가 다르다`() {
        val linked = AcceptedInvite.parse(JSONObject(BUNDLE)).pets[0]

        assertEquals("p1", linked.invitedPetId)
        assertEquals("m1", linked.displayPetId)
        assertNotEquals(linked.invitedPetId, linked.displayPetId)
    }

    @Test
    fun `연결 안 한 항목은 두 id 가 같다`() {
        val joined = AcceptedInvite.parse(JSONObject(BUNDLE)).pets[1]

        assertEquals("p2", joined.invitedPetId)
        assertEquals("p2", joined.displayPetId)
    }

    /** 서버가 값을 늘려도 앱이 죽거나 다른 결과로 뭉개지 않아야 한다. */
    @Test
    fun `모르는 result 는 UNKNOWN 으로 들고 있는다`() {
        assertEquals(AcceptResult.ALREADY_MEMBER, AcceptResult.of("already_member"))
        assertEquals(AcceptResult.ALREADY_OWNER, AcceptResult.of("already_owner"))
        assertEquals(AcceptResult.UNKNOWN, AcceptResult.of("something_new"))
        assertEquals(AcceptResult.UNKNOWN, AcceptResult.of(null))
    }

    /**
     * 옛 서버는 `pets` 를 안 준다. 그때도 화면이 한 줄은 그려야 한다 —
     * 빈 목록을 그대로 그리면 성공했는데 아무것도 안 보인다.
     */
    @Test
    fun `옛 응답에는 앵커로 한 줄을 세운다`() {
        val old = AcceptedInvite.parse(JSONObject("""{"pet_id":"p1","name":"네옹"}"""))

        assertEquals(emptyList<AcceptedPet>(), old.pets)
        assertEquals(1, old.rows.size)
        assertEquals("p1", old.rows[0].displayPetId)
        assertEquals("네옹", old.rows[0].name)
    }

    @Test
    fun `항목이 있으면 앵커로 줄을 만들지 않는다`() {
        val accepted = AcceptedInvite.parse(JSONObject(BUNDLE))

        assertEquals(accepted.pets, accepted.rows)
    }

    private companion object {
        const val BUNDLE = """
        {"pet_id":"m1","name":"롱롱씨",
         "pets":[{"invited_pet_id":"p1","display_pet_id":"m1","name":"롱롱씨","result":"linked"},
                 {"invited_pet_id":"p2","display_pet_id":"p2","name":"몽이","result":"joined"}]}
        """
    }
}
