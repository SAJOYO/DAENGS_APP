package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
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
import com.daengs.app.pet.CreatedInviteBundle
import com.daengs.app.pet.InviteBundle
import com.daengs.app.pet.InvitePetBrief
import com.daengs.app.pet.InviteStatus
import com.daengs.app.pet.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InviteBundleScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val token = "abc_DEF-123"
    private val link = "https://daengapi.weareithero.cloud/invite#$token"

    private fun pet(id: String, name: String, groupOwner: Boolean = true, farewell: String? = null) = Pet(
        id = id, name = name, breed = "dog_beagle",
        sex = null, neutered = null, weightKg = null,
        birthDate = null, birthDateKind = null,
        isPrimary = false, isOwner = true, isGroupOwner = groupOwner,
        farewellOn = farewell?.let(java.time.LocalDate::parse),
    )

    private fun bundle(id: String, names: List<String>) = InviteBundle(
        id = id,
        pets = names.mapIndexed { i, n -> InvitePetBrief("b$i", n, null, false) },
        createdAtMs = 1_757_000_000_000L,
        expiresAtMs = 1_757_086_400_000L,
        acceptedAtMs = null,
    )

    private fun screen(
        pets: List<Pet> = listOf(pet("p1", "롱이"), pet("p2", "몽이")),
        selected: List<String> = emptyList(),
        invites: List<InviteBundle>? = emptyList(),
        justCreated: CreatedInviteBundle? = null,
        activeCount: Int = 0,
        canCreate: Boolean = false,
        busy: Boolean = false,
        error: String? = null,
        linkAvailable: Boolean = true,
        onToggle: (String) -> Unit = {},
        onCreate: () -> Unit = {},
        onCancel: (InviteBundle) -> Unit = {},
        onShare: (String) -> Unit = {},
        onCopy: (String) -> Unit = {},
    ) {
        compose.setContent {
            InviteBundleScreen(
                pets = pets,
                selected = selected,
                invites = invites,
                justCreated = justCreated,
                statusOf = { InviteStatus.ACTIVE },
                activeCount = activeCount,
                canCreate = canCreate,
                busy = busy,
                error = error,
                linkAvailable = linkAvailable,
                linkOf = { link },
                onToggle = onToggle,
                onCreate = onCreate,
                onCancel = onCancel,
                onShare = onShare,
                onCopy = onCopy,
            )
        }
    }

    // -- 고를 수 있는 아이 -------------------------------------------------------

    /** `isOwner` 로 거르면 연결된 공동 보호자의 아이가 뜨고, 골라 봐야 서버가 막는다. */
    @Test
    fun `그룹 주보호자인 아이만 고를 수 있다`() {
        screen(pets = listOf(pet("mine", "롱이"), pet("shared", "남의아이", groupOwner = false)))

        compose.onNodeWithTag("bundle-pick-mine").assertExists()
        compose.onAllNodesWithTag("bundle-pick-shared").assertCountEquals(0)
    }

    /** 배웅한 아이를 초대·후보에서 빼는 것이 서버의 최소 안전안이다. */
    @Test
    fun `배웅한 아이는 고를 수 없다`() {
        screen(pets = listOf(pet("alive", "롱이"), pet("gone", "별이", farewell = "2026-01-01")))

        compose.onNodeWithTag("bundle-pick-alive").assertExists()
        compose.onAllNodesWithTag("bundle-pick-gone").assertCountEquals(0)
    }

    @Test
    fun `왜 안 보이는지 알려 준다`() {
        screen(pets = listOf(pet("mine", "롱이"), pet("shared", "남의아이", groupOwner = false)))

        compose.onNodeWithTag("bundle-hidden-note").performScrollTo().assertIsDisplayed()
    }

    /** 고를 아이가 하나도 없으면 아래로 안 내려간다 — 요청도 안 나간다. */
    @Test
    fun `고를 아이가 없으면 생성 자리를 안 준다`() {
        screen(pets = listOf(pet("shared", "남의아이", groupOwner = false)))

        compose.onNodeWithTag("bundle-no-owned").assertIsDisplayed()
        compose.onAllNodesWithTag("bundle-create").assertCountEquals(0)
    }

    @Test
    fun `누르면 그 아이를 넘긴다`() {
        var toggled: String? = null
        screen(onToggle = { toggled = it })

        compose.onNodeWithTag("bundle-pick-p2").performScrollTo().performClick()

        assertEquals("p2", toggled)
    }

    // -- 만들기 ----------------------------------------------------------------

    @Test
    fun `한 마리도 여러 마리도 만들 수 있다`() {
        screen(selected = listOf("p1"), canCreate = true)
        compose.onNodeWithTag("bundle-create").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("1마리 초대 링크 만들기").assertExists()
    }

    @Test
    fun `고른 마릿수를 버튼에 적는다`() {
        screen(selected = listOf("p1", "p2"), canCreate = true)

        compose.onNodeWithText("2마리 초대 링크 만들기").assertExists()
    }

    @Test
    fun `아무것도 안 골랐으면 만들 수 없다`() {
        screen(selected = emptyList(), canCreate = false)

        compose.onNodeWithTag("bundle-create").performScrollTo().assertIsNotEnabled()
    }

    /** **묶음 수를 센다.** 세 마리를 한 링크로 부른 것은 한 자리다. */
    @Test
    fun `활성 초대를 묶음 단위로 센다`() {
        screen(invites = listOf(bundle("i1", listOf("롱이", "몽이", "콩이"))), activeCount = 1)

        compose.onNodeWithText("살아 있는 초대 1/3").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `상한이면 만들기를 막고 이유를 말한다`() {
        screen(selected = listOf("p1"), activeCount = 3, canCreate = false)

        compose.onNodeWithTag("bundle-create").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("bundle-at-limit").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `개발 환경에서는 생성을 막고 이유를 말한다`() {
        screen(selected = listOf("p1"), canCreate = true, linkAvailable = false)

        compose.onNodeWithTag("bundle-env-blocked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("bundle-create").performScrollTo().assertIsNotEnabled()
    }

    // -- 초대장 ----------------------------------------------------------------

    @Test
    fun `만든 직후 담긴 아이들 이름으로 초대장을 보여 준다`() {
        screen(justCreated = CreatedInviteBundle("i1", listOf("p1", "p2"), token, 0L))

        compose.onNodeWithTag("invite-ticket").assertIsDisplayed()
        compose.onNodeWithText("롱이·몽이의 공동 돌봄 초대장").assertExists()
    }

    @Test
    fun `초대장에서 공유와 복사를 내준다`() {
        var shared: String? = null
        var copied: String? = null
        screen(
            justCreated = CreatedInviteBundle("i1", listOf("p1"), token, 0L),
            onShare = { shared = it },
            onCopy = { copied = it },
        )

        compose.onNodeWithTag("ticket-share").performScrollTo().performClick()
        compose.onNodeWithText("링크 복사").performScrollTo().performClick()

        assertTrue("공유 문구에 링크가 들어간다", shared!!.contains(link))
        assertEquals("복사는 링크만 넘긴다", link, copied)
    }

    /** 화면에 토큰이 글자로 뜨면 어깨너머·스크린샷으로 샌다. */
    @Test
    fun `화면에 토큰이나 링크를 글자로 띄우지 않는다`() {
        screen(justCreated = CreatedInviteBundle("i1", listOf("p1"), token, 0L))

        compose.onAllNodesWithText(token, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(link, substring = true).assertCountEquals(0)
    }

    /** 목록에는 토큰이 없다 — 서버가 해시만 들고 있어서다. */
    @Test
    fun `목록에서는 링크를 복원하지 않는다`() {
        screen(invites = listOf(bundle("i1", listOf("롱이"))))

        compose.onAllNodesWithTag("ticket-share").assertCountEquals(0)
        compose.onNodeWithTag("bundle-link-once-notice").performScrollTo().assertExists()
    }

    // -- 목록 ------------------------------------------------------------------

    /** 어느 아이들을 부른 링크인지가 이 줄의 본래 쓸모다. */
    @Test
    fun `묶음 줄에 담긴 아이들을 적는다`() {
        screen(invites = listOf(bundle("i1", listOf("롱이", "몽이"))))

        compose.onNodeWithTag("bundle-row-names-i1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("롱이·몽이").assertExists()
    }

    @Test
    fun `취소를 누르면 그 묶음을 넘긴다`() {
        var cancelled: InviteBundle? = null
        val target = bundle("i1", listOf("롱이"))
        screen(invites = listOf(target), onCancel = { cancelled = it })

        compose.onNodeWithTag("bundle-cancel-i1").performScrollTo().performClick()

        assertEquals("i1", cancelled?.id)
    }

    @Test
    fun `빈 목록을 안내한다`() {
        screen(invites = emptyList())

        compose.onNodeWithTag("bundle-empty").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `아직 못 받았으면 불러오는 중을 알린다`() {
        screen(invites = null, busy = true)

        compose.onNodeWithTag("bundle-loading").performScrollTo().assertIsDisplayed()
    }

    /** "못 불러왔다" 와 "아직 없다" 는 다르다. */
    @Test
    fun `오류는 서버 문장 그대로 나오고 빈 목록으로 바뀌지 않는다`() {
        screen(invites = null, error = "초대를 불러오지 못했어요.")

        compose.onNodeWithTag("bundle-error").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("bundle-empty").assertCountEquals(0)
    }

    @Test
    fun `닫기가 있다`() {
        screen()

        compose.onNodeWithText("닫기").assertHasClickAction()
    }
}
