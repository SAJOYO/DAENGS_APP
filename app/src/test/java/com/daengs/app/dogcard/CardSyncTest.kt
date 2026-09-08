package com.daengs.app.dogcard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기기와 서버의 카드를 맞출 때 무엇을 하나.
 *
 * **틀리면 카드가 사라지거나 두 장이 된다.** 도감에서는 "왜 카드가 없어졌지" 로만
 * 보이고 원인이 안 보여서, 이 셈만 따로 시험한다 (`PetPhotoSyncTest` 와 같은 이유).
 */
class CardSyncTest {

    @Test
    fun `새 폰이면 서버 것을 전부 받아 온다`() {
        // ⚠️ 이 기능을 만든 이유가 이 줄이다. 기기가 비어 있는 것이 **새 폰**이다.
        val plan = planCardSync(localIds = emptyList(), remoteIds = listOf("a", "b", "c"))

        assertEquals(listOf("a", "b", "c"), plan.download.sorted())
        assertTrue(plan.upload.isEmpty())
    }

    @Test
    fun `서버가 비어 있으면 기기 것을 전부 올린다`() {
        // 첫 동기화가 이 경우다 — 서버가 생기기 전부터 이 폰에 쌓여 있던 카드들.
        val plan = planCardSync(localIds = listOf("a", "b"), remoteIds = emptyList())

        assertEquals(listOf("a", "b"), plan.upload.sorted())
        assertTrue(plan.download.isEmpty())
    }

    @Test
    fun `같으면 아무것도 안 한다`() {
        // 로그인할 때마다 전부 다시 올리면 느리고 데이터를 먹는다.
        val plan = planCardSync(listOf("a", "b"), listOf("b", "a"))

        assertTrue(plan.isEmpty)
    }

    @Test
    fun `양쪽이 다르면 각자 채운다`() {
        val plan = planCardSync(localIds = listOf("a", "b"), remoteIds = listOf("b", "c"))

        assertEquals(listOf("a"), plan.upload)
        assertEquals(listOf("c"), plan.download)
    }

    @Test
    fun `기기에 없는 서버 카드를 지우지 않는다`() {
        // ⚠️ 여기가 프로필 사진과 갈리는 자리다.
        //
        //    "기기에 없고 서버에 있다" 를 삭제로 보면 **새 폰에서 복원이 통째로 안 된다** —
        //    새 폰은 기기가 비어 있으니 서버 카드를 전부 밀어 버린다.
        //    사진은 파일 옆 도장으로 새 폰과 삭제를 구분할 수 있지만, 카드는 Room 이
        //    비어 있으면 그 구분이 없다. 그래서 삭제는 지운 그 자리에서 알린다.
        val plan = planCardSync(localIds = emptyList(), remoteIds = listOf("a"))

        assertEquals(listOf("a"), plan.download)
        // 삭제 목록이라는 것 자체가 없다.
        assertTrue(plan.upload.isEmpty())
    }
}
