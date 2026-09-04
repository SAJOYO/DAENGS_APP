package com.daengs.app.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 고른 사진을 어느 아이에게 거나.
 *
 * **새로 등록할 때가 위험하다.** id 를 서버가 만들기 때문에 폼에서는 알 수가 없고,
 * 목록을 다시 받아 늘어난 하나를 찾아야 한다. 틀리면 **남의 아이 얼굴에 사진이
 * 붙는데**, 화면에서는 "왜 다른 애 사진이지" 로만 보인다.
 */
class PetPhotoTargetTest {

    @Test
    fun `새로 등록하면 늘어난 아이에게 붙는다`() {
        val id = photoTargetId(
            editingId = null,
            before = setOf("p1", "p2"),
            after = listOf("p1", "p2", "p3"),
        )

        assertEquals("p3", id)
    }

    @Test
    fun `첫 아이도 찾는다`() {
        // 온보딩이 이 경우다 — 전에는 한 마리도 없다.
        assertEquals("p1", photoTargetId(null, emptySet(), listOf("p1")))
    }

    @Test
    fun `목록 순서가 달라도 늘어난 아이를 찾는다`() {
        // 서버가 대표를 앞으로 올리거나 이름순으로 줄 수 있다. 마지막 하나로
        // 짐작하면 그때 남의 아이에게 붙는다.
        val id = photoTargetId(null, setOf("p1", "p2"), listOf("p3", "p1", "p2"))

        assertEquals("p3", id)
    }

    @Test
    fun `고치기는 그 아이 그대로다`() {
        assertEquals("p2", photoTargetId("p2", setOf("p1", "p2"), listOf("p1", "p2")))
    }

    @Test
    fun `늘어난 것이 없으면 아무 데도 안 건다`() {
        // 등록이 실패했는데 사진만 걸리면 엉뚱한 아이가 바뀐다.
        assertNull(photoTargetId(null, setOf("p1"), listOf("p1")))
    }

    @Test
    fun `둘 이상 늘었으면 안 건다`() {
        // 다른 기기에서 같이 등록했을 때가 그렇다. **아무거나 고르면 남의 아이다.**
        assertNull(photoTargetId(null, setOf("p1"), listOf("p1", "p2", "p3")))
    }

    @Test
    fun `고치던 아이가 사라졌으면 안 건다`() {
        // 다른 기기에서 지웠을 때. 없는 id 로 파일을 쓰면 유령 파일이 남는다.
        assertNull(photoTargetId("p9", setOf("p9"), listOf("p1")))
    }
}
