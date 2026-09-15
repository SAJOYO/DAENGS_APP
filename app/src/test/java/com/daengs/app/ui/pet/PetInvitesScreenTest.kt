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
import com.daengs.app.pet.CreatedInvite
import com.daengs.app.pet.InviteStatus
import com.daengs.app.pet.PetInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetInvitesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_757_000_000_000L
    private val hour = 3_600_000L
    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
    private val fakeLink = "https://daengapi.weareithero.cloud/invite#fake-token"

    private fun invite(id: String, expiresAt: Long, acceptedAt: Long? = null) =
        PetInvite(id = id, createdAtMs = now - hour, expiresAtMs = expiresAt, acceptedAtMs = acceptedAt)

    private val active = invite("live", now + 20 * hour)
    private val used = invite("used", now + 20 * hour, acceptedAt = now - hour)
    private val expired = invite("old", now - hour)

    private fun screen(
        isOwner: Boolean = true,
        invites: List<PetInvite>? = emptyList(),
        justCreated: CreatedInvite? = null,
        activeCount: Int = 0,
        busy: Boolean = false,
        error: String? = null,
        linkAvailable: Boolean = true,
        onCreate: () -> Unit = {},
        onCancel: (PetInvite) -> Unit = {},
        onShare: (String) -> Unit = {},
        onDismissCreated: () -> Unit = {},
    ) {
        compose.setContent {
            PetInvitesScreen(
                petName = "네옹",
                isOwner = isOwner,
                invites = invites,
                justCreated = justCreated,
                statusOf = { it.status(now) },
                activeCount = activeCount,
                busy = busy,
                error = error,
                linkAvailable = linkAvailable,
                linkOf = { if (linkAvailable) fakeLink else null },
                zone = seoul,
                onCreate = onCreate,
                onCancel = onCancel,
                onShare = onShare,
                onDismissCreated = onDismissCreated,
            )
        }
    }

    // -- 권한 -----------------------------------------------------------------

    /** 돌보미가 어떻게든 이 화면에 닿아도 목록·생성 자리를 안 준다. */
    @Test
    fun `돌보미에게는 초대 자리를 주지 않는다`() {
        screen(isOwner = false, invites = listOf(active), activeCount = 1)

        compose.onNodeWithTag("invites-not-owner").assertIsDisplayed()
        compose.onAllNodesWithText("초대 만들기").assertCountEquals(0)
        compose.onAllNodesWithText("대기 중").assertCountEquals(0)
    }

    // -- 목록 상태 -------------------------------------------------------------

    @Test
    fun `활성 사용됨 만료를 구분해 그린다`() {
        screen(invites = listOf(active, used, expired), activeCount = 1)

        compose.onNodeWithText("대기 중").assertIsDisplayed()
        compose.onNodeWithText("사용됨").assertIsDisplayed()
        compose.onNodeWithText("만료").assertIsDisplayed()
    }

    /** 수락된 초대를 지우면 재시도 복구용 영수증만 사라진다 — 취소할 자리가 아니다. */
    @Test
    fun `활성 초대에만 취소가 붙는다`() {
        screen(invites = listOf(active, used, expired), activeCount = 1)

        compose.onAllNodesWithText("취소").assertCountEquals(1)
    }

    @Test
    fun `취소를 누르면 그 초대를 넘긴다`() {
        var cancelled: PetInvite? = null
        screen(invites = listOf(active, used), activeCount = 1, onCancel = { cancelled = it })

        compose.onNodeWithText("취소").performClick()

        assertEquals("live", cancelled?.id)
    }

    @Test
    fun `빈 목록을 안내한다`() {
        screen(invites = emptyList())

        compose.onNodeWithTag("invites-empty").assertIsDisplayed()
    }

    @Test
    fun `아직 못 받았으면 불러오는 중을 알린다`() {
        screen(invites = null, busy = true)

        compose.onNodeWithTag("invites-loading").assertIsDisplayed()
        compose.onAllNodesWithText("아직 보낸 초대가 없어요.").assertCountEquals(0)
    }

    /** 실패를 빈 목록으로 바꾸면 "초대가 없다" 고 단언하게 된다. */
    @Test
    fun `오류는 서버 문장 그대로 나오고 빈 목록으로 바뀌지 않는다`() {
        screen(invites = null, error = "살아 있는 초대는 3개까지입니다.")

        compose.onNodeWithText("살아 있는 초대는 3개까지입니다.").assertIsDisplayed()
        compose.onAllNodesWithText("아직 보낸 초대가 없어요.").assertCountEquals(0)
    }

    // -- 상한 ------------------------------------------------------------------

    @Test
    fun `활성 초대가 셋이면 생성을 막는다`() {
        screen(invites = List(3) { invite("i$it", now + 20 * hour) }, activeCount = 3)

        compose.onNodeWithTag("invites-create").assertIsNotEnabled()
        compose.onNodeWithText("살아 있는 초대 3/3").assertIsDisplayed()
    }

    @Test
    fun `자리가 남으면 생성할 수 있다`() {
        screen(invites = listOf(active), activeCount = 1)

        compose.onNodeWithTag("invites-create").assertIsEnabled()
        compose.onNodeWithText("살아 있는 초대 1/3").assertIsDisplayed()
    }

    // -- 초대장 ----------------------------------------------------------------

    @Test
    fun `생성 직후 초대장을 보여준다`() {
        screen(
            invites = listOf(active),
            justCreated = CreatedInvite("live", "p1", "fake-token", now + 24 * hour),
            activeCount = 1,
        )

        compose.onNodeWithTag("invite-ticket").assertIsDisplayed()
        compose.onNodeWithText("초대 링크 공유하기").assertIsDisplayed()
        compose.onNodeWithText("24시간 동안 사용할 수 있어요").assertIsDisplayed()
    }

    /** 목록만 있을 때는 초대장이 없다 — 링크는 생성 응답에만 있었다. */
    @Test
    fun `목록만 있으면 초대장이 없다`() {
        screen(invites = listOf(active), activeCount = 1)

        compose.onAllNodesWithTag("invite-ticket").assertCountEquals(0)
        compose.onAllNodesWithText("초대 링크 공유하기").assertCountEquals(0)
    }

    @Test
    fun `공유는 이름과 링크가 든 문구를 넘긴다`() {
        var shared: String? = null
        screen(
            justCreated = CreatedInvite("live", "p1", "fake-token", now + 24 * hour),
            onShare = { shared = it },
        )

        compose.onNodeWithTag("ticket-share").performClick()

        assertTrue(shared!!.contains("네옹의 공동 돌봄 초대장이 도착했어요!"))
        assertTrue(shared!!.contains("24시간 동안 사용할 수 있습니다."))
        assertTrue(shared!!.endsWith(fakeLink))
    }

    /** 한 초대장은 한 사람만 수락한다 — 복사 버튼이 따로 있으면 공용 링크로 읽힌다. */
    @Test
    fun `초대장에는 복사 버튼 없이 한 사람만 수락한다고 알린다`() {
        screen(justCreated = CreatedInvite("live", "p1", "fake-token", now + 24 * hour))

        compose.onNodeWithText("초대 링크 공유하기").assertIsDisplayed()
        compose.onAllNodesWithText("링크 복사").assertCountEquals(0)
        compose.onNodeWithTag("ticket-one-recipient").assertIsDisplayed()
    }

    @Test
    fun `초대장을 닫으면 알린다`() {
        var dismissed = false
        screen(
            justCreated = CreatedInvite("live", "p1", "fake-token", now + 24 * hour),
            onDismissCreated = { dismissed = true },
        )

        compose.onAllNodesWithText("닫기")[1].performClick()

        assertTrue(dismissed)
    }

    /** 목록만 있는 상태에서는 링크를 되살릴 수 있는 것처럼 보이면 안 된다. */
    @Test
    fun `목록에서는 링크를 복원하지 않는다`() {
        screen(invites = listOf(active, used, expired), activeCount = 1)

        compose.onAllNodesWithText("초대 링크 공유하기").assertCountEquals(0)
        compose.onAllNodesWithText("링크 복사").assertCountEquals(0)
        compose.onNodeWithTag("invites-link-once-notice").assertExists()
    }

    // -- 환경 방어 -------------------------------------------------------------

    @Test
    fun `개발 환경에서는 생성을 막고 이유를 말한다`() {
        var created = false
        screen(linkAvailable = false, onCreate = { created = true })

        compose.onNodeWithTag("invites-env-blocked").assertIsDisplayed()
        compose.onNodeWithTag("invites-create").assertIsNotEnabled()
        compose.onNodeWithTag("invites-create").performClick()

        assertTrue("눌러도 생성 요청이 나가면 안 된다", !created)
    }

    @Test
    fun `개발 환경에서는 공유와 복사를 내주지 않는다`() {
        screen(
            linkAvailable = false,
            justCreated = CreatedInvite("live", "p1", "fake-token", now + 24 * hour),
        )

        compose.onNodeWithTag("ticket-no-link").assertIsDisplayed()
        compose.onAllNodesWithText("초대 링크 공유하기").assertCountEquals(0)
        compose.onAllNodesWithText("링크 복사").assertCountEquals(0)
    }

    /** 토큰은 링크 안에서만 다룬다 — 화면에 글자로 띄우면 어깨너머·스크린샷으로 샌다. */
    @Test
    fun `화면에 토큰이나 링크를 글자로 띄우지 않는다`() {
        screen(
            invites = listOf(active),
            justCreated = CreatedInvite("live", "p1", "fake-token", now + 24 * hour),
            activeCount = 1,
        )

        compose.onAllNodesWithText("fake-token", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(fakeLink, substring = true).assertCountEquals(0)
    }

    @Test
    fun `생성 버튼을 누르면 알린다`() {
        var created = false
        screen(invites = emptyList(), onCreate = { created = true })

        compose.onNodeWithTag("invites-create").performClick()

        assertTrue(created)
        assertNull(null)
    }
}
