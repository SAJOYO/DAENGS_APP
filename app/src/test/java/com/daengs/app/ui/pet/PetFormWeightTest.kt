package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.daengs.app.pet.PetDraft
import com.daengs.app.pet.PetWeight
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 몸무게를 잘못 넣으면 **화면에서 막히는지.**
 *
 * [PetWeight] 가 판정을 잡고, 여기서는 그 판정이 화면에 실제로 걸려 있는지를 본다 —
 * 비공개 테스트에서 터진 것은 판정이 없어서가 아니라 화면이 아무것도 안 막아서였다.
 * 저장이 눌리면 서버가 422 를 주고, 그 본문이 그대로 화면에 찍혔다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetFormWeightTest {
    @get:Rule
    val compose = createComposeRule()

    private fun form(onSubmit: (PetDraft) -> Unit = {}) {
        compose.setContent {
            PetFormScreen(onSubmit = onSubmit, onCancel = null, busy = false, error = null)
        }
    }

    @Test
    fun `한계를 넘으면 저장이 안 눌린다`() {
        var sent: PetDraft? = null
        form { sent = it }

        compose.onNodeWithContentDescription("이름").performTextInput("네옹")
        compose.onNodeWithContentDescription("몸무게 (kg)").performTextInput("201")
        compose.waitForIdle()

        compose.onNodeWithText("등록하기").assertIsNotEnabled().performClick()
        compose.waitForIdle()

        assertNull("한계를 넘으면 서버까지 가면 안 된다", sent)
    }

    @Test
    fun `한계를 넘으면 그 칸 아래에서 이유를 말한다`() {
        form()

        compose.onNodeWithContentDescription("몸무게 (kg)").performTextInput("201")
        compose.waitForIdle()

        compose.onNodeWithText("${PetWeight.MAX_KG}kg 까지 넣을 수 있어요.").assertExists()
    }

    @Test
    fun `말이 안 되는 길이는 칸에 안 찍힌다`() {
        form()

        // 실제로 들어갔던 값. 칸이 받지 않으므로 자리표시자가 그대로 남는다.
        compose.onNodeWithContentDescription("몸무게 (kg)").performTextInput("800000000000000000")
        compose.waitForIdle()

        compose.onNodeWithText("4.2").assertExists()   // 자리표시자가 그대로 남아 있다
    }

    @Test
    fun `보통 몸무게면 저장이 눌린다`() {
        form()

        compose.onNodeWithContentDescription("이름").performTextInput("네옹")
        compose.onNodeWithContentDescription("몸무게 (kg)").performTextInput("4.2")
        compose.waitForIdle()

        compose.onNodeWithText("등록하기").assertIsEnabled()
    }

    @Test
    fun `몸무게를 비워 둬도 저장이 눌린다`() {
        // 이름 말고는 전부 비울 수 있는 것이 이 폼의 규칙이다.
        form()

        compose.onNodeWithContentDescription("이름").performTextInput("네옹")
        compose.waitForIdle()

        compose.onNodeWithText("등록하기").assertIsEnabled()
    }
}
