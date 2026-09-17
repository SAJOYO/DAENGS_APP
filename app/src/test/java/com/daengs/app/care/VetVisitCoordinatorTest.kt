package com.daengs.app.care

import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `저쪽이 우리 쪽 장애를 failed 로 주면 같은 초안으로 추출을 다시 부른다`() = runTest {
        // 저쪽은 Gemini 타임아웃·API 오류를 200 + status=failed 로 주고 extracted_at 을
        // 저장하지 않는다 — 다시 부르면 Gemini 가 다시 돌고 성공할 수 있다. 오류 객체가
        // 없다고 재시도를 막으면 저쪽 문서가 안내한 "다시 시도" 가 죽은 버튼이 된다.
        gateway.extractResult = Result.success(draft().copy(status = ExtractionStatus.FAILED))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        assertNull("오류가 아니다 — 200 이다", state(coordinator).receipt?.error)
        gateway.extractResult = Result.success(draft())
        assertTrue("재시도가 받아들여져야 한다", coordinator.retryReceipt(token))
        advanceUntilIdle()

        assertEquals("초안을 새로 만들지 않는다", listOf("id-1"), gateway.startedWith)
        assertEquals(listOf("draft-1", "draft-1"), gateway.extractedWith)
        assertEquals(ExtractionStatus.OK, state(coordinator).receipt?.draft?.status)
    }

    @Test
    fun `확정이 실패한 뒤의 재시도는 재추출이 아니다 — 고친 값과 항목을 날리지 않는다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        gateway.confirmResult = Result.failure(ChatApiError.unreachable("못 저장했어요", IOException()))
        coordinator.confirm(token, edits())
        advanceUntilIdle()

        assertFalse("여기는 confirm 을 다시 부르는 자리다", coordinator.retryReceipt(token))
        assertEquals("추출을 다시 부르지 않았다", listOf("draft-1"), gateway.extractedWith)
        assertEquals(ReceiptStep.READY, state(coordinator).receipt?.step)
    }

    @Test
    fun `재시작은 처음 찍은 그 사진을 다시 올린다`() = runTest {
        gateway.uploadResult = Result.failure(ChatApiError.unreachable("못 올렸어요", IOException()))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        gateway.uploadResult = Result.success(Unit)
        coordinator.retryReceipt(token)
        advanceUntilIdle()

        assertEquals(2, gateway.uploadedBytes.size)
        assertArrayEquals("같은 바이트여야 한다", jpeg, gateway.uploadedBytes[1])
    }

    @Test
    fun `확인 화면을 닫으면 사진을 놓는다 — 다시 열어도 옛 바이트가 안 올라간다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        coordinator.dismissReceipt()
        assertNull(state(coordinator).receipt)

        val other = byteArrayOf(9, 9)
        coordinator.beginReceipt(token, other)
        advanceUntilIdle()

        assertArrayEquals("새로 찍은 사진이어야 한다", other, gateway.uploadedBytes.last())
        assertEquals(listOf("id-1", "id-2"), gateway.startedWith)
    }

    @Test
    fun `추출이 도는 중에 닫으면 늦게 온 결과가 화면을 되살리지 않는다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        gateway.extractGate = gate
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        assertEquals(ReceiptStep.EXTRACTING, state(coordinator).receipt?.step)
        coordinator.dismissReceipt()
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull("닫힌 화면이 되살아나면 안 된다", state(coordinator).receipt)
    }

    @Test
    fun `추출이 도는 중에는 확정을 못 부른다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        gateway.extractGate = gate
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        assertFalse("유저가 본 적 없는 초안으로 확정하면 안 된다", coordinator.confirm(token, edits()))
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(gateway.confirmedWith.isEmpty())
    }

    @Test
    fun `처리 중인 영수증이 있으면 새로 찍은 것을 안 받는다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        gateway.extractGate = gate
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        assertFalse(coordinator.beginReceipt(token, byteArrayOf(9)))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("id-1"), gateway.startedWith)
    }

    @Test
    fun `목록을 못 읽으면 실패 상태로 남는다`() = runTest {
        gateway.listResult = Result.failure(ChatApiError.unreachable("못 읽었어요", IOException()))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        assertTrue(state(coordinator).visits is ChatLoadState.Failed)
    }

    @Test
    fun `삭제가 실패하면 목록은 그대로 두고 오류만 세운다`() = runTest {
        gateway.deleteResult = Result.failure(ChatApiError.unreachable("못 지웠어요", IOException()))
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        coordinator.delete(token, "v1")
        advanceUntilIdle()

        assertEquals(listOf("v1"), visits(coordinator).map { it.id })
        assertNotNull(state(coordinator).deleteError)
        coordinator.clearErrors()
        assertNull(state(coordinator).deleteError)
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

        gateway.confirmResult = Result.success(listOf(visit()))
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


    // -- 기간 (PR #416) -------------------------------------------------

    @Test
    fun `기본 기간은 최근 1년이고 그 창으로 부른다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        assertEquals(VetRange.RecentYear, state(coordinator).range)
        assertEquals(
            VetWindow(LocalDate.of(2025, 9, 16), LocalDate.of(2026, 9, 16)),
            gateway.listedWindow,
        )
    }

    @Test
    fun `기간을 바꾸면 그 창으로 다시 부르고 고른 기간이 상태에 남는다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        coordinator.load(token, VetRange.Year(2024))
        advanceUntilIdle()

        assertEquals(VetRange.Year(2024), state(coordinator).range)
        assertEquals(
            VetWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)),
            gateway.listedWindow,
        )
    }

    @Test
    fun `전체는 0001-01-01 부터 부른다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token, VetRange.All)
        advanceUntilIdle()

        assertEquals(LocalDate.of(1, 1, 1), gateway.listedWindow?.from)
    }

    /** 서버가 준 창과 오래된 기록 수는 **목록과 같은 칸에 함께** 들어와야 한다. */
    @Test
    fun `조회된 창과 오래된 기록 수가 목록과 같이 담긴다`() = runTest {
        gateway.listResult = Result.success(
            page(listOf(visit("v1")), olderCount = 3, start = LocalDate.of(2025, 9, 15)),
        )
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()

        val loaded = (state(coordinator).visits as ChatLoadState.Ready).value
        assertEquals(3, loaded.olderCount)
        assertEquals(LocalDate.of(2025, 9, 15), loaded.start)
    }

    /**
     * 아이를 바꾸면 **기간도 처음으로 돌아간다.** 앞의 아이에서 「2024년」을 보던 상태로
     * 다음 아이의 목록을 열면, 칩은 2024년인데 그 아이의 최근 기록이 없어 빈 화면이 된다.
     */
    @Test
    fun `강아지를 바꾸면 기간이 최근 1년으로 돌아간다`() = runTest {
        val coordinator = coordinator(this)
        coordinator.selectPet("pet")
        coordinator.load(token, VetRange.All)
        advanceUntilIdle()

        coordinator.selectPet("other")

        assertEquals(VetRange.RecentYear, state(coordinator).range)
    }

    // -- 배관 -----------------------------------------------------------


    /** **오늘을 고정한다.** 서버는 KST 로 오늘을 정하고, 테스트는 제 기기 시간대를 본다. */
    private val today = LocalDate.of(2026, 9, 16)

    private fun coordinator(scope: TestScope) =
        VetVisitCoordinator(scope, gateway, newId = sequenceIds(), today = { today })

    private fun page(
        visits: List<VetVisit>,
        olderCount: Int = 0,
        start: LocalDate? = null,
    ) = VetVisitPage(start = start, end = null, olderCount = olderCount, visits = visits)

    private fun sequenceIds(): () -> String {
        var n = 0
        return { "id-${++n}" }
    }

    private fun state(c: VetVisitCoordinator) = c.state.value

    private fun visits(c: VetVisitCoordinator): List<VetVisit> =
        (c.state.value.visits as ChatLoadState.Ready).value.visits

    @Test
    fun `나눠 확정하면 행마다 다른 키가 간다 — 같은 키면 기록이 한 벌만 남는다`() = runTest {
        val coordinator = coordinator(this)
        gateway.extractResult = Result.success(draft(patientCount = 2))
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        coordinator.confirm(
            token,
            edits(listOf(splitEdit(patientIndex = 0), splitEdit(patientIndex = 1))),
        )
        advanceUntilIdle()

        assertEquals(2, gateway.confirmedWith.size)
        assertEquals("행마다 제 키다", 2, gateway.confirmedWith.toSet().size)
        assertEquals("첫 행은 촬영에서 만든 키를 그대로 쓴다", "id-1", gateway.confirmedWith.first())
    }

    @Test
    fun `나눠 확정하다 실패해도 블록마다 같은 키로 다시 보낸다`() = runTest {
        val coordinator = coordinator(this)
        gateway.extractResult = Result.success(draft(patientCount = 2))
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()
        val rows = listOf(splitEdit(patientIndex = 0), splitEdit(patientIndex = 1))

        gateway.confirmResult = Result.failure(ChatApiError.unreachable("못 저장했어요", IOException()))
        coordinator.confirm(token, edits(rows))
        advanceUntilIdle()
        val first = gateway.confirmedWith.toList()

        gateway.confirmResult = Result.success(listOf(visit()))
        coordinator.confirm(token, edits(rows))
        advanceUntilIdle()

        assertEquals("새 uuid 를 만들면 기록이 두 벌 생긴다", first, gateway.confirmedWith.drop(2))
    }

    @Test
    fun `블록을 빼도 남은 블록의 키는 안 바뀐다`() = runTest {
        val coordinator = coordinator(this)
        gateway.extractResult = Result.success(draft(patientCount = 2))
        coordinator.selectPet("pet")
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        gateway.confirmResult = Result.failure(ChatApiError.unreachable("못 저장했어요", IOException()))
        coordinator.confirm(token, edits(listOf(splitEdit(patientIndex = 0), splitEdit(patientIndex = 1))))
        advanceUntilIdle()
        val secondBlockKey = gateway.confirmedWith[1]

        // 첫 블록을 빼고 둘째만 남겨 다시 보낸다. 자리로 키를 세면 여기서 키가 밀린다.
        gateway.confirmResult = Result.success(listOf(visit()))
        coordinator.confirm(token, edits(listOf(splitEdit(patientIndex = 1))))
        advanceUntilIdle()

        assertEquals(secondBlockKey, gateway.confirmedWith.last())
    }

    @Test
    fun `아이 수만큼 생긴 기록 중 이 아이 것만 목록에 붙는다`() = runTest {
        val coordinator = coordinator(this)
        gateway.extractResult = Result.success(draft(patientCount = 2))
        coordinator.selectPet("pet")
        coordinator.load(token)
        advanceUntilIdle()
        coordinator.beginReceipt(token, jpeg)
        advanceUntilIdle()

        // 형제의 기록도 같이 돌아온다. 이 목록은 이 아이의 목록이다.
        gateway.confirmResult = Result.success(
            listOf(visit(id = "v-mine"), visit(id = "v-sibling", petId = "other-pet")),
        )
        coordinator.confirm(token, edits(listOf(splitEdit(patientIndex = 0), splitEdit(patientIndex = 1))))
        advanceUntilIdle()

        assertEquals(listOf("v-mine", "v1"), visits(coordinator).map { it.id })
    }

    private fun edits(splits: List<SplitEdit> = listOf(splitEdit())) = ReceiptEdits(
        visitedOn = LocalDate.of(2026, 9, 10),
        totalKrw = 61_700,
        hospitalName = null,
        hospitalAddress = null,
        hospitalPhone = null,
        isEmergency = false,
        splits = splits,
    )

    private fun splitEdit(
        petId: String? = null,
        totalKrw: Int = 61_700,
        patientIndex: Int? = 0,
    ) = SplitEdit(
        petId = petId,
        reasonCode = "skin",
        reasonDetail = null,
        totalKrw = totalKrw,
        isOncology = false,
        patientIndex = patientIndex,
    )

    private fun draft(patientCount: Int = 1) = VetVisitDraft(
        draftId = "draft-1", petId = "pet", status = ExtractionStatus.OK,
        unreadableReason = null, visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = "압구정동물병원", hospitalAddress = null, hospitalPhone = "02-543-0075",
        items = emptyList(), patientCount = patientCount,
        suggestedReasonCode = "vaccination", isEmergency = false,
        possibleDuplicate = false,
        reasonOptions = listOf(VetReasonOption("skin", "피부"), VetReasonOption("ear", "귀")),
    )

    private fun visit(id: String = "v-new", petId: String = "pet") = VetVisit(
        id = id, petId = petId, visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
        hospitalName = null, hospitalAddress = null, hospitalPhone = null,
        reasonCode = "skin", reasonDetail = null, isEmergency = false, isOncology = false,
        clientEventId = "id-1",
    )

    private inner class FakeGateway : VetVisitGateway {
        val startedWith = mutableListOf<String>()
        val extractedWith = mutableListOf<String>()
        val confirmedWith = mutableListOf<String>()

        /** 실제로 올라간 바이트. 재시작이 같은 사진을 다시 올리는지 보려고 적어 둔다. */
        val uploadedBytes = mutableListOf<ByteArray>()

        var uploadResult: Result<Unit> = Result.success(Unit)
        var extractResult: Result<VetVisitDraft> = Result.success(draft())
        var confirmResult: Result<List<VetVisit>> = Result.success(listOf(visit()))
        var optionsResult: Result<List<VetReasonOption>> =
            Result.success(listOf(VetReasonOption("skin", "피부"), VetReasonOption("ear", "귀")))

        /** 채워 두면 추출이 여기서 멈춘다 — 도는 도중에 끼어드는 것을 볼 수 있다. */
        var extractGate: CompletableDeferred<Unit>? = null

        override suspend fun startDraft(accessToken: String, petId: String, clientEventId: String) =
            Result.success(
                VetVisitTicket("draft-1", "http://bridge/k", emptyMap(), created = true),
            ).also { startedWith += clientEventId }

        override suspend fun upload(ticket: VetVisitTicket, jpeg: ByteArray) =
            uploadResult.also { uploadedBytes += jpeg }

        override suspend fun extract(accessToken: String, draftId: String): Result<VetVisitDraft> {
            extractedWith += draftId
            extractGate?.await()
            return extractResult
        }

        override suspend fun confirm(
            accessToken: String,
            draftId: String,
            confirmation: VetVisitConfirmation,
        ) = confirmResult.also { confirmedWith += confirmation.splits.map { row -> row.clientEventId } }

        var listResult: Result<VetVisitPage> = Result.success(page(listOf(visit("v1"))))
        var deleteResult: Result<Unit> = Result.success(Unit)

        /** 마지막으로 요청된 창. **기간 프리셋이 실제로 나가는지** 를 여기서 본다. */
        var listedWindow: VetWindow? = null

        override suspend fun list(accessToken: String, petId: String, window: VetWindow) =
            listResult.also { listedWindow = window }

        override suspend fun reasonOptions(accessToken: String, petId: String) = optionsResult

        override suspend fun delete(accessToken: String, visitId: String) = deleteResult
    }
}
