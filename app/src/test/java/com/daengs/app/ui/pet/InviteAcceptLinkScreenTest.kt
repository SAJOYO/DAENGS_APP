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
import com.daengs.app.pet.AcceptOutcome
import com.daengs.app.pet.AcceptResult
import com.daengs.app.pet.AcceptedInvite
import com.daengs.app.pet.AcceptedPet
import com.daengs.app.pet.InvitePaste
import com.daengs.app.pet.InvitePetBrief
import com.daengs.app.pet.InvitePreview
import com.daengs.app.pet.InvitePreviewPet
import com.daengs.app.pet.PetChoice
import com.daengs.app.pet.PreviewOutcome
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 다중 초대의 수락 화면 — 미리보기와 연결 선택.
 *
 * 붙여넣기·오류 문구 같은 단일 초대 시절의 규칙은 [InviteAcceptScreenTest] 가 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InviteAcceptLinkScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val token = "abc_DEF-123"
    private val link = "https://daengapi.weareithero.cloud/invite#$token"

    private fun brief(id: String, name: String) = InvitePetBrief(id, name, null, false)

    private fun preview(
        pets: List<InvitePreviewPet> = listOf(
            InvitePreviewPet(brief("p1", "롱이"), false),
            InvitePreviewPet(brief("p2", "몽이"), false),
        ),
        candidates: List<InvitePetBrief> = listOf(brief("m1", "롱롱씨")),
        who: String? = "네옹집사",
    ) = PreviewOutcome.Ready(
        InvitePreview(invitedByNickname = who, expiresAtMs = 0L, pets = pets, linkCandidates = candidates),
    )

    private fun screen(
        outcome: AcceptOutcome? = null,
        preview: PreviewOutcome? = null,
        choices: Map<String, PetChoice> = emptyMap(),
        canAccept: Boolean = false,
        busy: Boolean = false,
        takenBy: (String) -> Set<String> = { emptySet() },
        onChoose: (String, PetChoice) -> Unit = { _, _ -> },
        onAccept: () -> Unit = {},
    ) {
        compose.setContent {
            InviteAcceptScreen(
                pasted = link,
                parsed = InvitePaste.Result.Found(token),
                busy = busy,
                outcome = outcome,
                canAccept = canAccept,
                preview = preview,
                choices = choices,
                takenBy = takenBy,
                onChoose = onChoose,
                onAccept = onAccept,
            )
        }
    }

    // -- 미리보기 ---------------------------------------------------------------

    @Test
    fun `초대된 아이를 각각 카드로 보여 준다`() {
        screen(preview = preview())

        compose.onNodeWithTag("accept-pet-p1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("accept-pet-p2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `초대한 사람을 알려 준다`() {
        screen(preview = preview())

        compose.onNodeWithTag("accept-invited-by").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("네옹집사님이 보낸 초대예요.").assertExists()
    }

    /** 서버가 이름을 못 줄 수 있다 — 그때 화면이 이름을 지어내면 안 된다. */
    @Test
    fun `초대한 사람 이름이 없어도 지어내지 않는다`() {
        screen(preview = preview(who = null))

        compose.onNodeWithText("받은 초대예요.").assertExists()
    }

    /** 미리보기가 없으면(옛 서버) 고르는 자리를 아예 안 낸다. */
    @Test
    fun `미리보기가 없으면 고르는 자리가 없다`() {
        screen(preview = null)

        compose.onAllNodesWithTag("accept-pet-p1").assertCountEquals(0)
        compose.onNodeWithText("초대 수락하기").assertExists()
    }

    @Test
    fun `경로가 없는 옛 서버에서도 옛 흐름을 막지 않는다`() {
        screen(preview = PreviewOutcome.Unsupported, canAccept = true)

        compose.onAllNodesWithTag("accept-pet-p1").assertCountEquals(0)
        compose.onNodeWithTag("accept-submit").performScrollTo().assertIsEnabled()
        compose.onAllNodesWithTag("accept-error").assertCountEquals(0)
    }

    @Test
    fun `미리보기 실패도 수락 실패와 같은 말로 그린다`() {
        screen(preview = PreviewOutcome.Expired)

        compose.onNodeWithTag("accept-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("만료된 초대예요. 대표 보호자에게 새 초대를 요청해 주세요.").assertExists()
    }

    // -- 고르기 ----------------------------------------------------------------

    @Test
    fun `아이마다 새로 참여와 연결을 고를 수 있다`() {
        val picked = mutableListOf<Pair<String, PetChoice>>()
        screen(preview = preview(), onChoose = { id, c -> picked += id to c })

        compose.onNodeWithTag("accept-join-p1").performScrollTo().performClick()
        compose.onNodeWithTag("accept-link-p2-m1").performScrollTo().performClick()

        assertEquals(listOf("p1" to PetChoice.Join, "p2" to PetChoice.Link("m1")), picked)
    }

    @Test
    fun `아직 안 고른 아이에게 고르라고 말한다`() {
        screen(preview = preview(), choices = mapOf("p1" to PetChoice.Join))

        compose.onAllNodesWithTag("accept-need-choice-p1").assertCountEquals(0)
        compose.onNodeWithTag("accept-need-choice-p2").performScrollTo().assertIsDisplayed()
    }

    /** 서버가 422 로 막는데, 그때는 어느 줄을 고쳐야 하는지 알 수 없다. */
    @Test
    fun `다른 줄이 가져간 후보는 못 고른다`() {
        screen(preview = preview(), choices = mapOf("p1" to PetChoice.Link("m1")), takenBy = { pet ->
            if (pet == "p2") setOf("m1") else emptySet()
        })

        compose.onNodeWithTag("accept-link-p2-m1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("accept-link-p1-m1").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `연결할 후보가 없으면 그렇게 말한다`() {
        screen(preview = preview(candidates = emptyList()))

        compose.onNodeWithTag("accept-no-candidates-p1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("accept-join-p1").performScrollTo().assertIsEnabled()
    }

    /** 오류가 아니다 — 서버가 그냥 지나간다. 고를 것이 없으니 자리를 안 낸다. */
    @Test
    fun `이미 구성원인 아이는 고르라고 하지 않는다`() {
        screen(
            preview = preview(
                pets = listOf(
                    InvitePreviewPet(brief("p1", "롱이"), false),
                    InvitePreviewPet(brief("p2", "몽이"), true),
                ),
            ),
        )

        compose.onNodeWithTag("accept-already-p2").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("accept-join-p2").assertCountEquals(0)
    }

    /** 연결은 과거까지 연다 — 고르기 전에 알려 줘야 되돌릴 수 없는 선택이 되지 않는다. */
    @Test
    fun `연결을 고르면 과거 기록도 공유된다고 알린다`() {
        screen(preview = preview(), choices = mapOf("p1" to PetChoice.Link("m1")))

        compose.onNodeWithTag("accept-link-warning").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `연결을 안 고르면 그 안내는 안 뜬다`() {
        screen(preview = preview(), choices = mapOf("p1" to PetChoice.Join))

        compose.onAllNodesWithTag("accept-link-warning").assertCountEquals(0)
    }

    // -- 수락 ------------------------------------------------------------------

    @Test
    fun `다 고르기 전에는 수락을 막는다`() {
        screen(preview = preview(), choices = mapOf("p1" to PetChoice.Join), canAccept = false)

        compose.onNodeWithTag("accept-submit").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `묶음이면 몇 마리인지 버튼이 말한다`() {
        screen(
            preview = preview(),
            choices = mapOf("p1" to PetChoice.Join, "p2" to PetChoice.Join),
            canAccept = true,
        )

        compose.onNodeWithText("2마리 모두 공동 돌봄 시작하기").assertExists()
    }

    @Test
    fun `한 마리면 예전 문구 그대로다`() {
        screen(preview = preview(pets = listOf(InvitePreviewPet(brief("p1", "롱이"), false))), canAccept = true)

        compose.onNodeWithText("초대 수락하기").assertExists()
    }

    // -- 결과 ------------------------------------------------------------------

    /** 앵커 하나만 그리면 여러 마리를 받았을 때 나머지가 사라진다. */
    @Test
    fun `결과를 항목마다 한 줄로 보여 준다`() {
        screen(
            outcome = AcceptOutcome.Joined(
                AcceptedInvite(
                    "m1", "롱롱씨",
                    listOf(
                        AcceptedPet("p1", "m1", "롱롱씨", AcceptResult.LINKED),
                        AcceptedPet("p2", "p2", "몽이", AcceptResult.JOINED),
                    ),
                ),
            ),
        )

        compose.onNodeWithText("2마리의 공동 보호자가 되었어요").assertExists()
        compose.onNodeWithTag("accept-row-m1").assertIsDisplayed()
        compose.onNodeWithTag("accept-row-p2").assertIsDisplayed()
    }

    /** 연결과 새 참여가 섞여 있으면 어느 쪽인지 알려 줘야 한다. */
    @Test
    fun `연결과 새 참여를 구분해 적는다`() {
        screen(
            outcome = AcceptOutcome.Joined(
                AcceptedInvite(
                    "m1", "롱롱씨",
                    listOf(
                        AcceptedPet("p1", "m1", "롱롱씨", AcceptResult.LINKED),
                        AcceptedPet("p2", "p2", "몽이", AcceptResult.JOINED),
                    ),
                ),
            ),
        )

        compose.onNodeWithText("· 롱롱씨 — 내 강아지와 연결했어요").assertExists()
        compose.onNodeWithText("· 몽이 — 새로 참여했어요").assertExists()
    }

    /** 옛 서버는 항목을 안 준다. 그때도 한 줄은 그려야 한다. */
    @Test
    fun `옛 응답도 한 줄로 그린다`() {
        screen(outcome = AcceptOutcome.Joined(AcceptedInvite("p1", "롱이")))

        compose.onNodeWithText("롱이의 공동 보호자가 되었어요").assertExists()
        compose.onNodeWithTag("accept-row-p1").assertIsDisplayed()
    }

    /** 422 는 상한 409 와 뜻이 다르다 — 다시 눌러도 같고 불러오기부터 해야 한다. */
    @Test
    fun `422 도 사용자에게 문장으로 보인다`() {
        screen(outcome = AcceptOutcome.Invalid("초대 정보가 바뀌었어요. 다시 불러와 주세요."))

        compose.onNodeWithTag("accept-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("초대 정보가 바뀌었어요. 다시 불러와 주세요.").assertExists()
    }

    /**
     * 화면에 토큰이 글자로 뜨면 어깨너머·스크린샷으로 샌다.
     *
     * **입력칸은 예외다** — 사용자가 붙여넣은 글 그대로라 거기 있는 것이 맞다. 선택
     * 카드나 안내 문구에까지 번지면 안 된다는 것을 본다.
     */
    @Test
    fun `선택 화면에서 토큰은 입력칸에만 있다`() {
        screen(preview = preview(), choices = mapOf("p1" to PetChoice.Link("m1")))

        compose.onAllNodesWithText(token, substring = true).assertCountEquals(1)
        compose.onNodeWithTag("accept-input").assertExists()
    }
}
