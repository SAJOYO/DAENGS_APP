package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.daengs.app.pet.Pet
import com.daengs.app.pet.PetDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * 돌봄 칸(#200)이 **화면에서 초안으로 이어지는지.**
 *
 * 급식 시각은 시간제일 때만 받아야 한다 — 자율급식에 시각이 붙으면 서버가 422 다.
 * 그리고 고치기로 들어오면 서버가 준 값이 그대로 보여야 한다. 안 보이면 저장할 때
 * null 로 덮인다 (PUT).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetFormCareTest {
    @get:Rule
    val compose = createComposeRule()

    private fun form(initial: Pet? = null, onSubmit: (PetDraft) -> Unit = {}) {
        compose.setContent {
            PetFormScreen(
                onSubmit = { draft, _ -> onSubmit(draft) },
                onCancel = null,
                busy = false,
                error = null,
                initial = initial,
            )
        }
    }

    private val existing = Pet(
        id = "p1",
        name = "네옹",
        breed = MIX_BREED,
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = null,
        birthDateKind = null,
        isPrimary = true,
        feedingStyle = Pet.FeedingStyle.SCHEDULED,
        feedingTimes = listOf(LocalTime.of(8, 0), LocalTime.of(19, 30)),
        healthConditions = "슬개골 탈구",
        medications = "관절약",
    )

    @Test
    fun `시간제를 고르기 전에는 시각 추가가 없다`() {
        form()
        compose.onNodeWithText(ADD_FEEDING_TIME_LABEL).assertDoesNotExist()

        compose.onNodeWithText("자율급식").performScrollTo().performClick()
        compose.onNodeWithText(ADD_FEEDING_TIME_LABEL).assertDoesNotExist()
    }

    @Test
    fun `시간제를 고르면 시각 추가가 뜬다`() {
        form()
        compose.onNodeWithText("시간제").performScrollTo().performClick()
        compose.onNodeWithText(ADD_FEEDING_TIME_LABEL).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `고른 급식 방식과 적은 병·약이 초안에 실린다`() {
        var sent: PetDraft? = null
        form { sent = it }

        compose.onNodeWithContentDescription("이름").performTextInput("네옹")
        compose.onNodeWithText("자율급식").performScrollTo().performClick()
        compose.onNodeWithContentDescription("앓는 병").performScrollTo().performTextInput("슬개골 탈구")
        compose.onNodeWithContentDescription("먹는 약").performScrollTo().performTextInput("관절약")
        compose.onNodeWithText("등록하기").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(Pet.FeedingStyle.FREE, sent?.feedingStyle)
        assertNull(sent?.feedingTimes)
        assertEquals("슬개골 탈구", sent?.healthConditions)
        assertEquals("관절약", sent?.medications)
    }

    /** 같은 칩을 다시 누르면 모름이다 — 성별·중성화와 같은 규칙. */
    @Test
    fun `급식 방식을 다시 누르면 모름으로 돌아간다`() {
        var sent: PetDraft? = null
        form { sent = it }

        compose.onNodeWithContentDescription("이름").performTextInput("네옹")
        compose.onNodeWithText("자율급식").performScrollTo().performClick()
        compose.onNodeWithText("자율급식").performScrollTo().performClick()
        compose.onNodeWithText("등록하기").performScrollTo().performClick()
        compose.waitForIdle()

        assertNull(sent?.feedingStyle)
    }

    /**
     * 시간제에서 자율급식으로 바꾸면 **시각을 안 보낸다.** 그대로 보내면 서버가
     * "시각은 시간제일 때만" 이라며 422 를 준다.
     */
    @Test
    fun `시간제에서 자율급식으로 바꾸면 시각이 안 실린다`() {
        var sent: PetDraft? = null
        form(existing) { sent = it }

        compose.onNodeWithText("자율급식").performScrollTo().performClick()
        compose.onNodeWithText("저장하기").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(Pet.FeedingStyle.FREE, sent?.feedingStyle)
        assertNull(sent?.feedingTimes)
    }

    @Test
    fun `고칠 때 서버가 준 돌봄 값이 그대로 실린다`() {
        var sent: PetDraft? = null
        form(existing) { sent = it }

        compose.onNodeWithText("08:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("19:30").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("저장하기").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(Pet.FeedingStyle.SCHEDULED, sent?.feedingStyle)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(19, 30)), sent?.feedingTimes)
        assertEquals("슬개골 탈구", sent?.healthConditions)
        assertEquals("관절약", sent?.medications)
    }

    @Test
    fun `시각 옆 지우기를 누르면 그 시각이 빠진다`() {
        var sent: PetDraft? = null
        form(existing) { sent = it }

        compose.onNodeWithContentDescription("08:00 지우기").performScrollTo().performClick()
        compose.onNodeWithText("저장하기").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf(LocalTime.of(19, 30)), sent?.feedingTimes)
    }

    @Test
    fun `시각 추가를 누르면 다이얼이 나오고 추가하면 목록에 든다`() {
        var sent: PetDraft? = null
        form { sent = it }

        compose.onNodeWithContentDescription("이름").performTextInput("네옹")
        compose.onNodeWithText("시간제").performScrollTo().performClick()
        compose.onNodeWithText(ADD_FEEDING_TIME_LABEL).performScrollTo().performClick()
        // 다이얼 기본값이 그대로 들어간다. 몇 시인지는 다이얼의 몫이다.
        compose.onNodeWithText(CONFIRM_FEEDING_TIME_LABEL).performScrollTo().performClick()
        compose.onNodeWithText("등록하기").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(Pet.FeedingStyle.SCHEDULED, sent?.feedingStyle)
        assertEquals(1, sent?.feedingTimes?.size)
    }
}
