package com.daengs.app.ui.dex

import com.daengs.app.dogcard.photo.PhotoCard
import com.daengs.app.dogcard.photo.PhotoCardKey
import com.daengs.app.dogcard.photo.PhotoCardStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PhotoMakeStageTest {
    private fun card(status: PhotoCardStatus) =
        PhotoCard("a", null, PhotoCardKey.of(9), "안녕", "CHUSEOK 안녕", status, null, null, 0L)

    @Test fun `아직 안 보냈으면 사진 고르기`() = assertEquals(PhotoMakeStage.Pick, photoMakeStage(null, null))
    @Test fun `그리는 중이면 그리는 중`() = assertEquals(PhotoMakeStage.Drawing, photoMakeStage(card(PhotoCardStatus.Generating), null))

    /** 완성이라도 그림을 받기 전에는 뒤집을 앞면이 없다. */
    @Test fun `완성이어도 그림이 없으면 그리는 중`() = assertEquals(PhotoMakeStage.Drawing, photoMakeStage(card(PhotoCardStatus.Ready), null))
    @Test fun `완성이고 그림이 있으면 뒤집기`() = assertEquals(PhotoMakeStage.Reveal, photoMakeStage(card(PhotoCardStatus.Ready), File("a.png")))
    @Test fun `실패면 실패`() = assertEquals(PhotoMakeStage.Failed, photoMakeStage(card(PhotoCardStatus.Failed), null))
}
