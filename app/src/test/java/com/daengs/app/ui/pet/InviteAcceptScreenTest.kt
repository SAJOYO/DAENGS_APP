package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.daengs.app.pet.AcceptOutcome
import com.daengs.app.pet.AcceptedInvite
import com.daengs.app.pet.InvitePaste
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InviteAcceptScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val token = "abc_DEF-123"
    private val link = "https://daengapi.weareithero.cloud/invite#$token"

    private fun screen(
        pasted: String = "",
        parsed: InvitePaste.Result = InvitePaste.Result.Empty,
        busy: Boolean = false,
        outcome: AcceptOutcome? = null,
        canAccept: Boolean = false,
        autoEntered: Boolean = false,
        onPaste: (String) -> Unit = {},
        onAccept: () -> Unit = {},
        onDone: () -> Unit = {},
    ) {
        compose.setContent {
            InviteAcceptScreen(
                pasted = pasted,
                parsed = parsed,
                busy = busy,
                outcome = outcome,
                canAccept = canAccept,
                autoEntered = autoEntered,
                onPaste = onPaste,
                onAccept = onAccept,
                onDone = onDone,
            )
        }
    }

    // -- 입력 -----------------------------------------------------------------

    @Test
    fun `붙여넣은 글을 그대로 넘긴다`() {
        var pasted: String? = null
        screen(onPaste = { pasted = it })

        compose.onNodeWithTag("accept-input").performTextInput(link)

        assertEquals(link, pasted)
    }

    @Test
    fun `링크를 찾으면 알려 준다`() {
        screen(pasted = link, parsed = InvitePaste.Result.Found(token), canAccept = true)

        compose.onNodeWithTag("accept-link-ok").assertIsDisplayed()
        compose.onNodeWithTag("accept-submit").assertIsEnabled()
    }

    @Test
    fun `링크를 못 찾으면 이유를 말하고 수락을 막는다`() {
        screen(pasted = "안녕하세요", parsed = InvitePaste.Result.NoLink)

        compose.onNodeWithTag("accept-no-link").assertIsDisplayed()
        compose.onNodeWithTag("accept-submit").assertIsNotEnabled()
    }

    /** 앱이 하나를 골라 버리면 엉뚱한 아이의 보호자가 된다. */
    @Test
    fun `초대가 여러 개면 수락을 막는다`() {
        screen(pasted = "둘", parsed = InvitePaste.Result.Ambiguous)

        compose.onNodeWithTag("accept-ambiguous").assertIsDisplayed()
        compose.onNodeWithTag("accept-submit").assertIsNotEnabled()
    }

    // -- App Links 자동 진입 -----------------------------------------------------

    /** 링크를 눌러서 왔으면 붙여넣기 칸과 "찾았어요" 안내를 다시 보여줄 이유가 없다. */
    @Test
    fun `자동 진입에서는 붙여넣기 칸과 찾았다는 안내를 숨긴다`() {
        screen(pasted = link, parsed = InvitePaste.Result.Found(token), canAccept = true, autoEntered = true)

        compose.onAllNodesWithTag("accept-input").assertCountEquals(0)
        compose.onAllNodesWithTag("accept-link-ok").assertCountEquals(0)
        // 그래도 수락 버튼은 그대로 있고 눌린다 — 링크만으로 자동 수락되는 것은 아니다.
        compose.onNodeWithTag("accept-submit").assertIsEnabled()
    }

    /** 수동 붙여넣기 경로는 그대로다 — autoEntered 가 기본값(false)이면 예전과 같다. */
    @Test
    fun `수동 경로는 자동 진입 화면을 숨기지 않는다`() {
        screen(pasted = link, parsed = InvitePaste.Result.Found(token), canAccept = true)

        compose.onNodeWithTag("accept-input").assertIsDisplayed()
        compose.onNodeWithTag("accept-link-ok").assertIsDisplayed()
    }

    // -- 확인 -----------------------------------------------------------------

    /** 붙여넣자마자 등록하지 않는다 — 한 번 확인받는다. */
    @Test
    fun `수락 버튼을 눌러야 요청이 시작된다`() {
        var accepted = 0
        screen(pasted = link, parsed = InvitePaste.Result.Found(token), canAccept = true, onAccept = { accepted++ })

        assertEquals(0, accepted)
        compose.onNodeWithTag("accept-submit").performClick()

        assertEquals(1, accepted)
    }

    @Test
    fun `수락 중에는 다시 누를 수 없다`() {
        var accepted = 0
        screen(
            pasted = link,
            parsed = InvitePaste.Result.Found(token),
            busy = true,
            canAccept = false,
            onAccept = { accepted++ },
        )

        compose.onNodeWithTag("accept-submit").performClick()

        assertEquals(0, accepted)
    }

    /** 각자 등록한 아이는 합쳐지지 않는다 — 모르고 수락하면 두 마리로 보인다. */
    @Test
    fun `중복 강아지 안내를 보여 준다`() {
        screen()

        compose.onNodeWithTag("accept-guidance").assertIsDisplayed()
        compose.onNodeWithText("• 이미 직접 등록한 강아지가 있어도 자동으로 합쳐지지 않아요. 같은 아이라면 목록에 두 마리로 보일 수 있어요.")
            .assertIsDisplayed()
    }

    /** 수락 전 미리보기 API 가 없으므로 아이 이름·대표를 지어내지 않는다. */
    @Test
    fun `수락 전에는 아이 정보를 꾸며내지 않는다`() {
        screen(pasted = link, parsed = InvitePaste.Result.Found(token), canAccept = true)

        compose.onAllNodesWithTag("accept-joined").assertCountEquals(0)
        // 대표 승인 대기 같은 상태를 만들지 않는다.
        compose.onAllNodesWithText("승인", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("대기", substring = true).assertCountEquals(0)
    }

    // -- 결과 -----------------------------------------------------------------

    @Test
    fun `성공하면 참여한 아이를 알려 준다`() {
        var done = false
        screen(outcome = AcceptOutcome.Joined(AcceptedInvite("p1", "네옹")), onDone = { done = true })

        compose.onNodeWithTag("accept-joined").assertIsDisplayed()
        compose.onNodeWithText("네옹의 공동 보호자가 되었어요").assertIsDisplayed()
        compose.onNodeWithTag("accept-done").performClick()

        assertTrue(done)
    }

    /** 성공 뒤에는 입력칸을 남겨 두지 않는다 — 쓴 토큰을 다시 보낼 자리가 없어야 한다. */
    @Test
    fun `성공 화면에는 입력칸과 수락 버튼이 없다`() {
        screen(outcome = AcceptOutcome.Joined(AcceptedInvite("p1", "네옹")))

        compose.onAllNodesWithTag("accept-input").assertCountEquals(0)
        compose.onAllNodesWithTag("accept-submit").assertCountEquals(0)
    }

    /** 404 와 410 을 한 문장으로 묶지 않는다 — 뒤쪽은 새 초대를 받으면 된다. */
    @Test
    fun `없는 초대와 만료를 다르게 말한다`() {
        screen(pasted = link, parsed = InvitePaste.Result.Found(token), outcome = AcceptOutcome.NotFound)
        compose.onNodeWithText("사용할 수 없는 초대예요. 링크가 잘못됐거나 다른 분이 이미 사용했어요.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `만료는 새 초대를 요청하라고 말한다`() {
        screen(pasted = link, parsed = InvitePaste.Result.Found(token), outcome = AcceptOutcome.Expired)
        compose.onNodeWithText("만료된 초대예요. 대표 보호자에게 새 초대를 요청해 주세요.").performScrollTo().assertIsDisplayed()
    }

    /** 409 는 세 종류라 앱이 다시 가르지 않고 서버 문장을 그대로 쓴다. */
    @Test
    fun `409 는 서버 문장을 그대로 보여 준다`() {
        screen(
            pasted = link,
            parsed = InvitePaste.Result.Found(token),
            outcome = AcceptOutcome.Conflict("한 아이의 보호자는 5명까지입니다."),
        )

        compose.onNodeWithText("한 아이의 보호자는 5명까지입니다.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `망 실패는 다시 시도할 수 있게 둔다`() {
        screen(
            pasted = link,
            parsed = InvitePaste.Result.Found(token),
            outcome = AcceptOutcome.Failed("서버에 닿지 못했어요. 잠시 뒤 다시 시도해 주세요."),
            canAccept = true,
        )

        compose.onNodeWithTag("accept-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("accept-submit").assertIsEnabled()
    }

    /** 화면에 토큰이 글자로 뜨면 어깨너머·스크린샷으로 샌다. 입력칸 값은 사용자가 붙여넣은 것 그대로다. */
    @Test
    fun `결과 화면에는 토큰이 남지 않는다`() {
        screen(outcome = AcceptOutcome.Joined(AcceptedInvite("p1", "네옹")))

        compose.onAllNodesWithText(token, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(link, substring = true).assertCountEquals(0)
    }
}
