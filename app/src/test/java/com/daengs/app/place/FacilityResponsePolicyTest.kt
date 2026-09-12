package com.daengs.app.place

import com.daengs.app.place.bookmarks.*
import com.daengs.app.place.support.conversationFixture
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FacilityResponsePolicyTest {
    @Test fun invalidProposalUsesFailureMessageEvenWithOldUnsafeOrMissingAnswer() {
        val before = selected
        val receipt = JsonObject(before.receipt + mapOf(
            "code" to JsonPrimitive("invalid_plan"), "execution" to JsonPrimitive("not_run"),
            "question" to JsonPrimitive("원하는 장소나 바꿀 조건을 짧게 알려주세요."),
        ))
        for (raw in listOf("원하는 장소나 바꿀 조건을 짧게 알려주세요.", "조건을 바꿨어요!", "receipt=secret", "", null)) {
            val result = before.copy(receipt = receipt, answer = raw)
            assertTrue(result.preservesDisplay)
            assertEquals(FacilityResponsePolicy.PROCESSING_FAILED, FacilityResponsePolicy.answer(result))
            assertEquals(before.selected, result.selected)
            assertEquals(before.revision, result.revision)
        }
        assertTrue(FacilityResponsePolicy.allowed(FacilityResponsePolicy.PROCESSING_FAILED))
    }

    @Test fun genuineMissingTargetKeepsItsQuestion() {
        val question = "어느 장소를 제외할까요?"
        val result = selected.copy(answer = question, receipt = JsonObject(selected.receipt + mapOf(
            "code" to JsonPrimitive("clarification_required"), "execution" to JsonPrimitive("not_run"),
            "question" to JsonPrimitive(question),
        )))
        assertEquals(question, FacilityResponsePolicy.answer(result))
    }

    @Test fun outsideUsesBoundedPuppyLineAndFailuresStillDescribeFailure() {
        val before = selected
        val receipt = JsonObject(before.receipt + mapOf("code" to JsonPrimitive("facility_out_of_scope")))
        val result = before.copy(receipt = receipt, answer = "시를 써드릴게요")
        assertTrue(result.preservesDisplay)
        assertEquals(FacilityResponsePolicy.OUT_OF_SCOPE, FacilityResponsePolicy.answer(result))
        assertTrue(FacilityResponsePolicy.allowed(FacilityResponsePolicy.OUT_OF_SCOPE))
        val failed = result.copy(receipt = JsonObject(receipt + ("execution" to JsonPrimitive("failed"))))
        assertEquals("다시 찾지 못했어요. 보던 목록은 그대로예요.", FacilityResponsePolicy.answer(failed))
    }
    private val selected get() = conversationFixture("picked", buildJsonObject {
        put("client_request_id", "22222222-2222-4222-8222-222222222222")
        put("revision", 2)
    }).toConversationResult()

    @Test fun internalWordsNeverReplaceASelectionWithAnInventedAction() {
        val before = selected
        val answer = FacilityResponsePolicy.answer(before.copy(answer = "revision=2 session_id=secret"))!!
        assertFalse(answer.contains("revision"))
        assertFalse(answer.contains("secret"))
        assertFalse(answer.contains("찜"))
        assertEquals(2, before.revision)
    }

    @Test fun failedReceiptWinsOverAnIncorrectSuccessMessage() {
        val before = selected
        val failed = before.copy(answer = "여기 골라뒀어요!", receipt = JsonObject(before.receipt +
            ("execution" to JsonPrimitive("failed"))))
        val message = FacilityResponsePolicy.answer(failed)!!
        assertTrue(message.contains("못했어요"))
        assertFalse(message.contains("골라뒀어요"))
    }

    @Test fun unknownBookmarkOutcomeCannotClaimThatItWasSaved() {
        val message = FacilityResponsePolicy.bookmark(BookmarkOutcome(BookmarkCompletion.UNKNOWN,
            "HTTP 504 receipt=secret"), saved = true)
        assertFalse(message.contains("HTTP"))
        assertFalse(message.contains("저장돼"))
        assertTrue(message.contains("확인하지 못했어요"))
        val confirmed = FacilityResponsePolicy.bookmark(BookmarkOutcome(BookmarkCompletion.CONFIRMED,
            "session_id=secret"), saved = false)
        assertEquals("현재 찜에서 해제돼 있어요.", confirmed)
        assertFalse(FacilityResponsePolicy.bookmark(BookmarkOutcome(BookmarkCompletion.UNKNOWN,
            "여기 찜해뒀어요!"), saved = true).contains("찜해뒀"))
    }

    @Test fun excessiveProseFallsBackWithoutTruncatingIntoAFalseCompletion() {
        val answer = FacilityResponsePolicy.answer(selected.copy(answer = "설명입니다. ".repeat(50)))!!
        assertTrue(answer.length <= 160)
        assertTrue(answer.split(Regex("(?<=[.!?])\\s+")).size <= 2)
    }

    @Test fun technicalErrorsAndKoreanAttachedInternalTermsHaveUserFacingFallbacks() {
        assertEquals(FacilityResponsePolicy.UNKNOWN, FacilityResponsePolicy.text("API를 호출해 receipt를 받았어요."))
        assertFalse(FacilityException(0).facilityMessage().contains("서버"))
        assertFalse(FacilityException(504).facilityMessage().contains("시간이 초과"))
        assertTrue(FacilityException(401).facilityMessage().contains("로그인"))
    }
}
