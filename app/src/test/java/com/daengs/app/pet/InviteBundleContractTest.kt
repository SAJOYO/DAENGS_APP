package com.daengs.app.pet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 다중 초대 묶음의 응답 모양. 저쪽 `schemas/pet_member.py` 의 `InviteBundleOut` ·
 * `InviteBundleCreated` · `InvitePetBrief` 다.
 *
 * 단일 초대([PetInviteContractTest])와 **다른 계약**이다 — 묶음은 강아지 목록을 싣고,
 * 상한을 세는 단위가 강아지당이 아니라 주보호자당이다.
 */
class InviteBundleContractTest {

    private val token = "abc_DEF-123"

    @Test
    fun `묶음 목록을 서버가 준 순서 그대로 읽는다`() {
        val list = InviteBundleList.parse(
            JSONObject(
                """
                {"invites": [
                  {"id":"i1","pets":[{"pet_id":"p1","name":"네옹","breed":"beagle","has_photo":true}],
                   "created_at":"2026-09-12T00:00:00Z","expires_at":"2026-09-13T00:00:00Z","accepted_at":null},
                  {"id":"i2","pets":[{"pet_id":"p2","name":"롱이","breed":null,"has_photo":false},
                                     {"pet_id":"p3","name":"몽이","breed":"maltese","has_photo":false}],
                   "created_at":"2026-09-11T00:00:00Z","expires_at":"2026-09-12T00:00:00Z","accepted_at":"2026-09-11T09:00:00Z"}
                ]}
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("i1", "i2"), list.invites.map { it.id })
        assertEquals(listOf("네옹"), list.invites[0].pets.map { it.name })
        assertEquals("두 마리를 한 링크로 부른 묶음", listOf("롱이", "몽이"), list.invites[1].pets.map { it.name })
    }

    /** 서버가 견종을 못 줄 수 있다. `getString` 으로 읽으면 안드로이드에서 `"null"` 이 된다. */
    @Test
    fun `견종이 null 이어도 문자열 null 로 읽지 않는다`() {
        val brief = InvitePetBrief.parse(
            JSONObject("""{"pet_id":"p1","name":"네옹","breed":null,"has_photo":false}"""),
        )

        assertNull(brief.breed)
        assertFalse(brief.hasPhoto)
    }

    /** 단일 초대와 같은 순서다 — 수락된 묶음은 24시간이 지나도 "만료" 가 아니라 "사용됨". */
    @Test
    fun `상태는 사용됨 → 만료 → 활성 순서로 가른다`() {
        val used = bundle(expiresAtMs = 100L, acceptedAtMs = 50L)
        val expired = bundle(expiresAtMs = 100L, acceptedAtMs = null)
        val alive = bundle(expiresAtMs = 300L, acceptedAtMs = null)

        assertEquals(InviteStatus.ACCEPTED, used.status(nowMs = 200L))
        assertEquals(InviteStatus.EXPIRED, expired.status(nowMs = 200L))
        assertEquals(InviteStatus.ACTIVE, alive.status(nowMs = 200L))
    }

    /**
     * **담긴 마릿수가 아니라 묶음 수를 센다.** 세 마리를 한 링크로 부른 것은 활성 1개다 —
     * 마릿수로 세면 세 마리 한 번에 부르는 순간 상한을 넘겨 버린다.
     */
    @Test
    fun `활성 묶음만 상한에 세고 담긴 마릿수는 안 센다`() {
        val invites = listOf(
            bundle(expiresAtMs = 300L, acceptedAtMs = null, pets = 3),
            bundle(expiresAtMs = 300L, acceptedAtMs = null, pets = 1),
            bundle(expiresAtMs = 100L, acceptedAtMs = null, pets = 5),
            bundle(expiresAtMs = 300L, acceptedAtMs = 50L, pets = 2),
        )

        assertEquals(2, invites.activeBundleCount(nowMs = 200L))
    }

    @Test
    fun `생성 응답에서 담긴 아이들을 읽는다`() {
        val created = CreatedInviteBundle.parse(
            JSONObject(
                """{"id":"i1","pet_ids":["p1","p2"],"token":"$token","expires_at":"2026-09-13T00:00:00Z"}""",
            ),
        )

        assertEquals("i1", created.id)
        assertEquals(listOf("p1", "p2"), created.petIds)
        assertEquals(token, created.token)
    }

    /** 토큰은 초대를 가로챌 자격증명이라 로그에 찍히면 안 된다. */
    @Test
    fun `toString 에 토큰이 찍히지 않는다`() {
        val created = CreatedInviteBundle("i1", listOf("p1"), token, 0L)

        assertFalse(created.toString().contains(token))
        assertTrue("어느 초대인지는 보여야 한다", created.toString().contains("i1"))
    }

    /** 목록 응답에는 토큰 자리가 없다 — 서버가 해시만 들고 있어서다. */
    @Test
    fun `목록에서는 토큰을 복원하지 않는다`() {
        val list = InviteBundleList.parse(
            JSONObject(
                """
                {"invites":[{"id":"i1","pets":[{"pet_id":"p1","name":"네옹"}],
                 "created_at":"2026-09-12T00:00:00Z","expires_at":"2026-09-13T00:00:00Z","accepted_at":null}]}
                """.trimIndent(),
            ),
        )

        assertFalse(list.toString().contains(token))
    }

    @Test
    fun `한 묶음 상한과 활성 묶음 상한은 서버와 같은 수다`() {
        assertEquals(5, MAX_PETS_PER_INVITE)
        assertEquals(3, MAX_ACTIVE_INVITE_BUNDLES)
    }

    private fun bundle(expiresAtMs: Long, acceptedAtMs: Long?, pets: Int = 1) = InviteBundle(
        id = "i",
        pets = (1..pets).map { InvitePetBrief("p$it", "이름$it", null, false) },
        createdAtMs = 0L,
        expiresAtMs = expiresAtMs,
        acceptedAtMs = acceptedAtMs,
    )
}
