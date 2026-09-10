package com.daengs.app.care

import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * 영수증 한 장의 생애. 여기서 잡는 것은 **재시도가 무엇을 다시 하느냐** 하나다 —
 * 업로드까지 못 간 실패는 새 초안이어야 하고, 추출·확정의 실패는 **같은 키로** 다시
 * 가야 한다 (저쪽이 멱등이라 Gemini 도 요금도 다시 안 든다).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VetVisitCoordinatorTest {

    private val gateway = FakeGateway()
    private val token = "acc"
    private val jpeg = byteArrayOf(1, 2, 3)

    @Test
    fun `촬영에서 만든 키가 초안과 확정에 같이 간다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        assertEquals(ReceiptStep.READY, state(coordinator).receipt?.step)
        coordinator.confirm(token, edits())
        advanceUntilIdle()

        assertEquals("id-1", gateway.startedWith.single())
        assertEquals("id-1", gateway.confirmedWith.single())
    }

    @Test
    fun `업로드가 실패하면 다시 누를 때 새 키로 새 초안을 만든다`() = runTest {
        gateway.uploadResult = Result.failure(ChatApiError.unreachable("못 올렸어요", IOException()))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        assertNotNull(state(coordinator).receipt?.error)
        gateway.uploadResult = Result.success(Unit)
        coordinator.retryReceipt(token)
        advanceUntilIdle()

        assertEquals(listOf("id-1", "id-2"), gateway.startedWith)
        assertEquals(ReceiptStep.READY, state(coordinator).receipt?.step)
    }

    @Test
    fun `추출이 연결 실패면 같은 초안으로 추출만 다시 부른다`() = runTest {
        gateway.extractResult = Result.failure(ChatApiError.unreachable("못 읽었어요", IOException()))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        gateway.extractResult = Result.success(draft())
        coordinator.retryReceipt(token)
        advanceUntilIdle()

        assertEquals("초안을 새로 만들지 않는다", listOf("id-1"), gateway.startedWith)
        assertEquals(listOf("draft-1", "draft-1"), gateway.extractedWith)
    }

    @Test
    fun `사진이 그 자리에 없다는 409 면 새 초안을 만든다`() = runTest {
        gateway.extractResult = Result.failure(
            ChatApiError(409, "photo_not_uploaded", "업로드된 영수증 사진을 찾을 수 없습니다."),
        )
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        gateway.extractResult = Result.success(draft())
        coordinator.retryReceipt(token)
        advanceUntilIdle()

        assertEquals(listOf("id-1", "id-2"), gateway.startedWith)
    }

    @Test
    fun `확정이 실패하면 같은 키로 확정만 다시 보낸다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        gateway.confirmResult = Result.failure(ChatApiError.unreachable("못 저장했어요", IOException()))
        coordinator.confirm(token, edits())
        advanceUntilIdle()
        assertEquals(ReceiptStep.READY, state(coordinator).receipt?.step)

        gateway.confirmResult = Result.success(visit())
        coordinator.confirm(token, edits())
        advanceUntilIdle()

        assertEquals(listOf("id-1", "id-1"), gateway.confirmedWith)
        assertEquals(listOf("id-1"), gateway.startedWith)
    }

    @Test
    fun `확정에 성공하면 목록 맨 앞에 붙고 흐름이 닫힌다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()
        coordinator.confirm(token, edits())
        advanceUntilIdle()

        assertNull("확인 화면이 닫힌다", state(coordinator).receipt)
        assertEquals(listOf("v-new", "v1"), visits(coordinator).map { it.id })
    }

    @Test
    fun `사유 표시명은 목록과 같이 받아 둔다 — 앱이 한글을 안 적는다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        assertEquals("피부", state(coordinator).reasonLabels["skin"])
        assertEquals(listOf("skin", "ear"), state(coordinator).reasonOptions.map { it.code })
    }

    @Test
    fun `사유 목록을 못 받아도 기록 목록은 보인다`() = runTest {
        gateway.optionsResult = Result.failure(ChatApiError.unreachable("못 받았어요", IOException()))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        assertTrue(state(coordinator).reasonLabels.isEmpty())
        assertEquals(listOf("v1"), visits(coordinator).map { it.id })
    }

    @Test
    fun `강아지를 바꾸면 처리 중이던 영수증이 사라진다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        coordinator.selectPet("other")

        assertNull(state(coordinator).receipt)
        assertEquals(ChatLoadState.Idle, state(coordinator).visits)
    }

    @Test
    fun `삭제는 목록에서 뺀다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        coordinator.delete(token, "v1")
        advanceUntilIdle()

        assertTrue(visits(coordinator).isEmpty())
        assertNull(state(coordinator).deleteError)
    }

    // -- 배관 -----------------------------------------------------------

    private fun coordinator(scope: TestScope) =
        VetVisitCoordinator(scope, gateway, newId = sequenceIds())

    private fun sequenceIds(): () -> String {
        var n = 0
        return { "id-${++n}" }
    }

    private fun state(c: VetVisitCoordinator) = c.state.value

    private fun visits(c: VetVisitCoordinator): List<VetVisit> =
        (c.state.value.visits as ChatLoadState.Ready).value

    private fun edits() = ReceiptEdits(
        reasonCode = "skin",
        reasonDetail = null,
        visitedOn = LocalDate.of(2026, 9, 10),
        totalKrw = 61_700,
        hospitalName = null,
        hospitalAddress = null,
        hospitalPhone = null,
        isEmergency = false,
        isOncology = false,
    )

    private fun draft() = VetVisitDraft(
        draftId = "draft-1", petId = "pet", status = ExtractionStatus.OK,
        unreadableReason = null, visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = "압구정동물병원", hospitalAddress = null, hospitalPhone = "02-543-0075",
        items = emptyList(), suggestedReasonCode = "vaccination", isEmergency = false,
        possibleDuplicate = false,
        reasonOptions = listOf(VetReasonOption("skin", "피부"), VetReasonOption("ear", "귀")),
    )

    private fun visit(id: String = "v-new") = VetVisit(
        id = id, petId = "pet", visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = null, hospitalAddress = null, hospitalPhone = null,
        reasonCode = "skin", reasonDetail = null, isEmergency = false, isOncology = false,
        clientEventId = "id-1",
    )

    private inner class FakeGateway : VetVisitGateway {
        val startedWith = mutableListOf<String>()
        val extractedWith = mutableListOf<String>()
        val confirmedWith = mutableListOf<String>()

        var uploadResult: Result<Unit> = Result.success(Unit)
        var extractResult: Result<VetVisitDraft> = Result.success(draft())
        var confirmResult: Result<VetVisit> = Result.success(visit())
        var optionsResult: Result<List<VetReasonOption>> =
            Result.success(listOf(VetReasonOption("skin", "피부"), VetReasonOption("ear", "귀")))

        override suspend fun startDraft(accessToken: String, petId: String, clientEventId: String) =
            Result.success(
                VetVisitTicket("draft-1", petId, "k", "http://bridge/k", emptyMap(), created = true),
            ).also { startedWith += clientEventId }

        override suspend fun upload(ticket: VetVisitTicket, jpeg: ByteArray) = uploadResult

        override suspend fun extract(accessToken: String, draftId: String) =
            extractResult.also { extractedWith += draftId }

        override suspend fun confirm(
            accessToken: String,
            draftId: String,
            confirmation: VetVisitConfirmation,
        ) = confirmResult.also { confirmedWith += confirmation.clientEventId }

        override suspend fun list(accessToken: String, petId: String) =
            Result.success(listOf(visit("v1")))

        override suspend fun reasonOptions(accessToken: String, petId: String) = optionsResult

        override suspend fun delete(accessToken: String, visitId: String) = Result.success(Unit)
    }
}
