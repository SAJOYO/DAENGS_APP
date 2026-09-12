package com.daengs.app.pet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저쪽 `schemas/pet_member.py` 의 `InviteOut`·`InviteCreated` 를 그대로 읽는가.
 *
 * 시각은 전부 **고정값**이다 — 실제 현재 시각에 기대면 자정·CI 시간대에 따라 결과가 바뀐다.
 */
class PetInviteContractTest {

    private val now = 1_757_000_000_000L // 고정된 "지금"

    private fun invite(
        id: String = "i1",
        createdAt: String = "2026-09-11T00:00:00Z",
        expiresAt: String = "2026-09-12T00:00:00Z",
        acceptedAt: String? = null,
    ) = PetInvite.parse(
        JSONObject()
            .put("id", id)
            .put("created_at", createdAt)
            .put("expires_at", expiresAt)
            .put("accepted_at", acceptedAt ?: JSONObject.NULL),
    )

    @Test
    fun `목록 응답을 서버가 준 순서 그대로 읽는다`() {
        val list = PetInviteList.parse(
            JSONObject(
                """{
                    "pet_id": "p1",
                    "invites": [
                        {"id":"first","created_at":"2026-09-11T00:00:00Z","expires_at":"2026-09-12T00:00:00Z","accepted_at":null},
                        {"id":"second","created_at":"2026-09-11T01:00:00Z","expires_at":"2026-09-12T01:00:00Z","accepted_at":"2026-09-11T02:00:00Z"}
                    ]
                }""",
            ),
        )

        assertEquals("p1", list.petId)
        assertEquals(listOf("first", "second"), list.invites.map { it.id })
        assertNull(list.invites[0].acceptedAtMs)
        assertEquals(true, list.invites[1].acceptedAtMs != null)
    }

    /** 수락된 초대는 24시간이 지나도 "만료" 가 아니라 "사용됨" 이다. 순서가 뒤집히면 안 된다. */
    @Test
    fun `상태는 사용됨 → 만료 → 활성 순서로 가른다`() {
        val accepted = invite(acceptedAt = "2026-09-11T02:00:00Z", expiresAt = "2026-09-12T00:00:00Z")
        assertEquals(InviteStatus.ACCEPTED, accepted.status(nowMs = accepted.expiresAtMs + 1))
        assertEquals(InviteStatus.ACCEPTED, accepted.status(nowMs = accepted.expiresAtMs - 1))

        val plain = invite()
        assertEquals(InviteStatus.EXPIRED, plain.status(nowMs = plain.expiresAtMs))
        assertEquals(InviteStatus.EXPIRED, plain.status(nowMs = plain.expiresAtMs + 1))
        assertEquals(InviteStatus.ACTIVE, plain.status(nowMs = plain.expiresAtMs - 1))
    }

    /** 상한은 **살아 있는 것만** 센다. 서버 `count_valid_invites` 와 같은 셈이다. */
    @Test
    fun `활성 초대만 상한에 센다`() {
        val list = listOf(
            invite(id = "a", expiresAt = "2099-01-01T00:00:00Z"),
            invite(id = "b", expiresAt = "2099-01-01T00:00:00Z", acceptedAt = "2026-09-11T02:00:00Z"),
            invite(id = "c", expiresAt = "2020-01-01T00:00:00Z"),
        )

        assertEquals(1, list.activeCount(now))
    }

    @Test
    fun `생성 응답을 읽는다`() {
        val created = CreatedInvite.parse(
            JSONObject("""{"id":"i1","pet_id":"p1","token":"abc_DEF-123","expires_at":"2026-09-12T00:00:00Z"}"""),
        )

        assertEquals("i1", created.id)
        assertEquals("p1", created.petId)
        assertTrue(created.expiresAtMs > 0)
    }

    /**
     * **토큰이 로그에 새지 않아야 한다.** 자료 클래스의 기본 `toString` 은 모든 칸을 찍는데,
     * 홀더나 오류 경로에서 객체가 통째로 찍히면 그대로 남는다.
     */
    @Test
    fun `toString 에 토큰이 찍히지 않는다`() {
        val created = CreatedInvite("i1", "p1", "SUPER-SECRET-TOKEN", 0L)

        assertFalse(created.toString().contains("SUPER-SECRET-TOKEN"))
        assertTrue(created.toString().contains("가림"))
    }

    @Test
    fun `대표 판정은 내 줄의 isOwner 로 한다`() {
        val members = listOf(
            PetMember("owner", "아빠", isOwner = true),
            PetMember("me", "나연", isOwner = false),
        )

        // 목록에 대표가 있다는 사실만 보면 돌보미도 대표가 되어 버린다.
        assertFalse(members.isOwnedBy("me"))
        assertTrue(members.isOwnedBy("owner"))
        assertFalse(members.isOwnedBy(null))
        assertFalse(members.isOwnedBy("stranger"))
    }
}
