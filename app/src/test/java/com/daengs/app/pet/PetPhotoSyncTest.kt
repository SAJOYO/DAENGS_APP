package com.daengs.app.pet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 사진을 어느 쪽으로 옮기나.
 *
 * **틀리면 사진이 사라지거나 방금 고른 것이 되돌아간다.** 화면에서는 "왜 없어졌지" 로만
 * 보이고 원인이 안 보이는 종류라, 네트워크·파일과 떼어 놓고 이 셈만 시험한다
 * (`PetPhotoTargetTest` 와 같은 이유).
 */
class PetPhotoSyncTest {

    @Test
    fun `새 폰이면 서버 것을 받아 온다`() {
        // 이 기능을 만든 이유가 이 줄이다 — 폰을 바꿔도 사진이 따라와야 한다.
        assertEquals(
            PhotoAction.DOWNLOAD,
            photoActionFor(
                serverHasPhoto = true,
                serverUpdatedAt = "2026-09-05T10:00:00Z",
                localExists = false,
                localStamp = null,
            ),
        )
    }

    @Test
    fun `양쪽 다 없으면 아무것도 안 한다`() {
        assertEquals(
            PhotoAction.NOTHING,
            photoActionFor(false, null, localExists = false, localStamp = null),
        )
    }

    @Test
    fun `서버가 생기기 전부터 있던 사진은 올린다`() {
        // 첫 동기화가 이 경우다. 도장이 없다 = 서버에서 온 것이 아니다.
        assertEquals(
            PhotoAction.UPLOAD,
            photoActionFor(false, null, localExists = true, localStamp = null),
        )
    }

    @Test
    fun `방금 고른 사진은 서버에 뭐가 있어도 올린다`() {
        // ⚠️ 여기서 DOWNLOAD 를 고르면 **사용자가 방금 고른 사진이 눈앞에서 되돌아간다.**
        //    도장이 없다는 건 이 폰에서 방금 고른 것이라는 뜻이고, 그게 제일 새 뜻이다.
        assertEquals(
            PhotoAction.UPLOAD,
            photoActionFor(
                serverHasPhoto = true,
                serverUpdatedAt = "2026-09-05T10:00:00Z",
                localExists = true,
                localStamp = null,
            ),
        )
    }

    @Test
    fun `같은 판이면 다시 안 받는다`() {
        // 목록을 열 때마다 사진을 받으면 느리고 데이터를 먹는다.
        val stamp = "2026-09-05T10:00:00Z"
        assertEquals(
            PhotoAction.NOTHING,
            photoActionFor(true, stamp, localExists = true, localStamp = stamp),
        )
    }

    @Test
    fun `다른 기기에서 바꿨으면 받아 온다`() {
        assertEquals(
            PhotoAction.DOWNLOAD,
            photoActionFor(
                serverHasPhoto = true,
                serverUpdatedAt = "2026-09-05T11:00:00Z",
                localExists = true,
                localStamp = "2026-09-05T10:00:00Z",
            ),
        )
    }

    @Test
    fun `다른 기기에서 지웠으면 여기서도 지운다`() {
        // ⚠️ 여기서 UPLOAD 를 고르면 **사용자가 지운 사진이 되살아난다.**
        //    도장이 있다 = 올렸던 것이다. 그런데 서버에 없다 = 누가 지웠다.
        assertEquals(
            PhotoAction.DELETE_LOCAL,
            photoActionFor(
                serverHasPhoto = false,
                serverUpdatedAt = null,
                localExists = true,
                localStamp = "2026-09-05T10:00:00Z",
            ),
        )
    }

    @Test
    fun `서버에 있다는데 시각이 없으면 받아 온다`() {
        // 옛 서버나 이상한 응답. 받아 오는 쪽이 안전하다 — 안 받으면 빈칸이 남는다.
        assertEquals(
            PhotoAction.DOWNLOAD,
            photoActionFor(
                serverHasPhoto = true,
                serverUpdatedAt = null,
                localExists = true,
                localStamp = "2026-09-05T10:00:00Z",
            ),
        )
    }

    @Test
    fun `공동 돌봄 아이의 기기 사진은 서버로 올리지 않는다`() {
        assertEquals(
            PhotoAction.DOWNLOAD,
            photoActionFor(
                serverHasPhoto = true,
                serverUpdatedAt = "2026-09-10T10:00:00Z",
                localExists = true,
                localStamp = null,
                canUpload = false,
            ),
        )
    }

    @Test
    fun `공동 돌봄 아이의 서버 사진이 없어지면 기기에서도 지운다`() {
        assertEquals(
            PhotoAction.DELETE_LOCAL,
            photoActionFor(
                serverHasPhoto = false,
                serverUpdatedAt = null,
                localExists = true,
                localStamp = null,
                canUpload = false,
            ),
        )
    }
}
