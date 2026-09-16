package com.daengs.app.gait.work

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * 챗 재진입 때 되살릴 "보행 분석 중" 카드 고르기 ([pendingGaitRecords]).
 *
 * **여기서 잡는 것은 "빠뜨리지도 겹치지도 않는다" 다.** 남의 강아지 카드가 붙거나, 끝난
 * 분석이 "분석 중" 으로 되살아나거나, 이미 떠 있는 카드가 하나 더 붙으면 여기가 깨진다.
 */
class GaitPendingWatchesTest {

    private val pet = "pet-1"
    private val otherPet = "pet-2"

    private fun work(
        recordId: String?,
        petId: String?,
        state: WorkInfo.State = WorkInfo.State.ENQUEUED,
    ): WorkInfo {
        val tags = buildSet {
            add("com.daengs.app.gait.work.GaitAnalysisWorker")
            recordId?.let { add(GaitWatchTags.record(it)) }
            petId?.let { add(GaitWatchTags.pet(it)) }
        }
        return WorkInfo(UUID.randomUUID(), state, tags)
    }

    @Test
    fun `아직 안 끝난 작업의 기록을 고른다`() {
        val got = pendingGaitRecords(
            works = listOf(
                work("rec-enqueued", pet, WorkInfo.State.ENQUEUED),
                work("rec-running", pet, WorkInfo.State.RUNNING),
                work("rec-blocked", pet, WorkInfo.State.BLOCKED),
            ),
            petId = pet,
            shownRecordIds = emptySet(),
        )

        assertEquals(listOf("rec-enqueued", "rec-running", "rec-blocked"), got)
    }

    @Test
    fun `끝난 작업은 분석 중으로 되살리지 않는다`() {
        val got = pendingGaitRecords(
            works = listOf(
                work("rec-done", pet, WorkInfo.State.SUCCEEDED),
                work("rec-failed", pet, WorkInfo.State.FAILED),
                work("rec-cancelled", pet, WorkInfo.State.CANCELLED),
            ),
            petId = pet,
            shownRecordIds = emptySet(),
        )

        assertTrue("끝난 분석이 영영 도는 것처럼 보이면 안 된다 ($got)", got.isEmpty())
    }

    @Test
    fun `다른 강아지 작업은 붙이지 않는다`() {
        val got = pendingGaitRecords(
            works = listOf(work("rec-mine", pet), work("rec-other", otherPet)),
            petId = pet,
            shownRecordIds = emptySet(),
        )

        assertEquals(listOf("rec-mine"), got)
    }

    @Test
    fun `강아지를 모르면 아무것도 붙이지 않는다`() {
        val got = pendingGaitRecords(
            works = listOf(work("rec-1", pet)),
            petId = null,
            shownRecordIds = emptySet(),
        )

        assertTrue(got.isEmpty())
    }

    @Test
    fun `이미 대화에 있는 기록은 빼고 붙인다`() {
        val got = pendingGaitRecords(
            works = listOf(work("rec-shown", pet), work("rec-new", pet)),
            petId = pet,
            shownRecordIds = setOf("rec-shown"),
        )

        assertEquals("방금 접수한 카드나 완료 카드와 겹치면 안 된다", listOf("rec-new"), got)
    }

    @Test
    fun `같은 기록이 두 번 와도 한 번만 붙인다`() {
        val got = pendingGaitRecords(
            works = listOf(work("rec-1", pet), work("rec-1", pet, WorkInfo.State.RUNNING)),
            petId = pet,
            shownRecordIds = emptySet(),
        )

        assertEquals(listOf("rec-1"), got)
    }

    @Test
    fun `기록 tag 가 없는 작업은 건너뛴다`() {
        // 이 변경 전에 등록된 작업은 기록 tag 가 없다. 어느 기록인지 모르면 붙일 수 없다.
        val got = pendingGaitRecords(
            works = listOf(work(recordId = null, petId = pet), work("rec-1", pet)),
            petId = pet,
            shownRecordIds = emptySet(),
        )

        assertEquals(listOf("rec-1"), got)
    }

    @Test
    fun `tag 로 넣은 기록 id 를 그대로 꺼낸다`() {
        val id = "0b6e2a3c-9f1d-4d9a-8e8e-2f1a7c3b5d10"
        val tags = setOf(GaitWatchTags.pet(pet), GaitWatchTags.record(id), "other")

        assertEquals(id, GaitWatchTags.recordIdOf(tags))
        assertNull("강아지 tag 를 기록 id 로 읽으면 안 된다", GaitWatchTags.recordIdOf(setOf(GaitWatchTags.pet(pet))))
    }
}
