package com.daengs.app.ui.storage

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daengs.app.care.CareActor
import com.daengs.app.care.CareDaySummary
import com.daengs.app.care.CareEvent
import com.daengs.app.care.CareKind
import com.daengs.app.care.CareLogState
import com.daengs.app.chat.ChatApiError
import com.daengs.app.chat.ChatLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CareLogSectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `오늘 요약은 건수 넷과 기록 줄을 보여 준다`() {
        compose.setContent {
            CareLogSection(state = CareLogState("pet", today = ChatLoadState.Ready(summary(listOf(event())))), zone = SEOUL)
        }

        compose.onNodeWithText("밥 2 · 약 1 · 간식 3 · 산책 1").assertIsDisplayed()
        compose.onNodeWithText("약 · 13:30").assertIsDisplayed()
        compose.onAllNodesWithText("삭제").assertCountEquals(1)
    }

    @Test
    fun `밥을 누르면 밥 기록을 요청한다`() {
        var recorded: CareKind? = null
        compose.setContent {
            CareLogSection(state = CareLogState("pet", today = ChatLoadState.Ready(summary())), onRecord = { recorded = it })
        }
        compose.onNodeWithText("밥").performClick()
        assertEquals(CareKind.MEAL, recorded)
    }

    @Test
    fun `보내는 중에는 다른 버튼도 안 눌린다`() {
        var recorded: CareKind? = null
        compose.setContent {
            CareLogSection(
                state = CareLogState("pet", today = ChatLoadState.Ready(summary()), recording = CareKind.MEAL),
                onRecord = { recorded = it },
            )
        }
        compose.onNodeWithText("간식").performClick()
        assertNull(recorded)
    }

    @Test
    fun `기록이 없으면 빈 화면 대신 한 줄을 둔다`() {
        compose.setContent { CareLogSection(state = CareLogState("pet", today = ChatLoadState.Ready(summary()))) }
        compose.onNodeWithText("아직 오늘 챙긴 기록이 없어요").assertIsDisplayed()
    }

    @Test
    fun `읽기 실패는 문장과 다시 시도다`() {
        var retried = false
        compose.setContent {
            CareLogSection(
                state = CareLogState("pet", today = ChatLoadState.Failed(ChatApiError(0, null, "서버에 닿지 못했어요."))),
                onRetryLoad = { retried = true },
            )
        }
        compose.onNodeWithText("서버에 닿지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `기록 실패는 문장과 다시 시도이고 다시 시도는 같은 종류를 다시 보낸다`() {
        var recorded: CareKind? = null
        compose.setContent {
            CareLogSection(
                state = CareLogState(
                    "pet",
                    today = ChatLoadState.Ready(summary()),
                    recordError = ChatApiError(0, null, "서버에 닿지 못했어요."),
                    lastRecordKind = CareKind.SNACK,
                ),
                onRecord = { recorded = it },
            )
        }
        compose.onNodeWithText("서버에 닿지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(CareKind.SNACK, recorded)
    }

    @Test
    fun `삭제를 누르면 확인을 묻고 확인해야 지운다`() {
        var deleted: CareEvent? = null
        compose.setContent {
            CareLogSection(
                state = CareLogState("pet", today = ChatLoadState.Ready(summary(listOf(event())))),
                onConfirmDelete = { deleted = it },
                zone = SEOUL,
            )
        }
        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithText("이 기록을 지울까요?").assertIsDisplayed()
        assertNull(deleted)
        compose.onNodeWithText("지우기").performClick()
        assertEquals("event-1", deleted?.id)
    }

    @Test
    fun `돌보미는 자신이 쓴 기록만 지울 수 있다`() {
        val mine = event().copy(actor = CareActor("me", "나"))
        val others = event().copy(id = "event-2", actor = CareActor("other", "키키"))
        // 작성자를 모르는 기록(옛 기록·탈퇴자)을 "모르니까 내 것" 으로 치면 안 된다.
        val nameless = event().copy(id = "event-3", actor = CareActor(null, null))
        val notMine = { _: String -> false }
        val mineRow = { _: String -> true }

        assertEquals(true, canDeleteCareEvent(mine, currentUserId = "me", ownsPetRow = notMine))
        assertEquals(false, canDeleteCareEvent(others, currentUserId = "me", ownsPetRow = notMine))
        assertEquals(false, canDeleteCareEvent(nameless, currentUserId = "me", ownsPetRow = notMine))
        assertEquals(true, canDeleteCareEvent(others, currentUserId = "me", ownsPetRow = mineRow))
        assertEquals(true, canDeleteCareEvent(nameless, currentUserId = "me", ownsPetRow = mineRow))

        compose.setContent {
            CareLogSection(
                state = CareLogState("pet", today = ChatLoadState.Ready(summary(listOf(mine, others)))),
                canDelete = { canDeleteCareEvent(it, "me", ownsPetRow = notMine) },
                zone = SEOUL,
            )
        }
        compose.onNodeWithText("나").assertIsDisplayed()
        compose.onNodeWithText("키키").assertIsDisplayed()
        compose.onAllNodesWithText("삭제").assertCountEquals(1)
    }

    // -- 그룹 공동 조회에서의 삭제 노출 -----------------------------------------
    //
    // 서버 규칙은 **「내가 쓴 것」 이거나 「그 기록이 달린 행이 내 것」** 이다
    // (`repositories/care_event.py` — `actor_app_user_id = 나 OR pets.app_user_id = 나`).
    // 하루 요약이 그룹 전체를 합쳐 주므로 남의 행에 달린 기록이 같은 목록에 섞여 온다.

    /** 계정의 대표 강아지를 소유했다는 것만으로 열면 안 된다 — 그것이 옛 판정의 결함이었다. */
    @Test
    fun `내 대표 강아지를 소유해도 남의 행 기록은 안 열린다`() {
        val theirs = event().copy(id = "e-theirs", petId = "their-row", actor = CareActor("other", "키키"))
        // 내가 소유한 것은 내 행 하나뿐이다. 기록은 남의 행에 달려 있다.
        val ownsOnlyMyRow = { petId: String -> petId == "my-row" }

        assertEquals(false, canDeleteCareEvent(theirs, currentUserId = "me", ownsPetRow = ownsOnlyMyRow))
    }

    @Test
    fun `내가 쓴 기록은 남의 행에 있어도 지울 수 있다`() {
        val mineOnTheirRow = event().copy(petId = "their-row", actor = CareActor("me", "나"))

        assertEquals(
            true,
            canDeleteCareEvent(mineOnTheirRow, currentUserId = "me", ownsPetRow = { it == "my-row" }),
        )
    }

    @Test
    fun `내가 소유한 행의 기록은 남이 썼어도 지울 수 있다`() {
        val theirsOnMyRow = event().copy(petId = "my-row", actor = CareActor("other", "키키"))

        assertEquals(
            true,
            canDeleteCareEvent(theirsOnMyRow, currentUserId = "me", ownsPetRow = { it == "my-row" }),
        )
    }

    /**
     * **연결된 그룹의 표시용 id 와 기록의 원본 id 를 헷갈리면 안 된다.**
     *
     * 내 목록에 보이는 카드의 id 는 `display-row`(내 행)인데, 그룹 주보호자가 적은
     * 기록은 `anchor-row`(그쪽 행)에 달려 온다. 표시용 id 로 물으면 참이 되어 삭제가
     * 뜨고, 누르면 서버가 404 를 낸다.
     */
    @Test
    fun `표시용 행과 기록의 원본 행을 혼동하지 않는다`() {
        val onAnchorRow = event().copy(id = "e-anchor", petId = "anchor-row", actor = CareActor("owner", "대표"))
        val ownsDisplayRowOnly = { petId: String -> petId == "display-row" }

        assertEquals(false, canDeleteCareEvent(onAnchorRow, currentUserId = "me", ownsPetRow = ownsDisplayRowOnly))
        // 판정이 실제로 기록의 pet_id 를 묻는지 확인한다 — 표시용 id 를 물었다면 참이 됐다.
        assertEquals(true, ownsDisplayRowOnly("display-row"))
    }

    /** 그룹 주보호자라도 남의 행에 달린 남의 기록은 못 지운다 — 서버가 넓히지 않은 경계다. */
    @Test
    fun `그룹 주보호자도 남의 행 기록은 못 지운다`() {
        val onCarerRow = event().copy(id = "e-carer", petId = "carer-row", actor = CareActor("carer", "돌보미"))

        assertEquals(
            false,
            canDeleteCareEvent(onCarerRow, currentUserId = "group-owner", ownsPetRow = { it == "anchor-row" }),
        )
    }

    /** 섞여 온 목록에서 지울 수 있는 줄에만 삭제가 뜬다. */
    @Test
    fun `그룹으로 섞여 온 목록에서 내 것에만 삭제가 뜬다`() {
        val mineRow = event().copy(id = "e1", petId = "my-row", actor = CareActor("other", "키키"))
        val theirRow = event().copy(id = "e2", petId = "their-row", actor = CareActor("other", "키키"))

        compose.setContent {
            CareLogSection(
                state = CareLogState("pet", today = ChatLoadState.Ready(summary(listOf(mineRow, theirRow)))),
                canDelete = { canDeleteCareEvent(it, "me", ownsPetRow = { id -> id == "my-row" }) },
                zone = SEOUL,
            )
        }

        compose.onAllNodesWithText("삭제").assertCountEquals(1)
    }

    // -- 목록에서 「내 행」을 고르는 규칙 -------------------------------------
    //
    // 목록은 논리 그룹당 카드 하나로 접히지만 **내가 가진 행은 언제나 그 카드로 남는다.**
    // 서버 셋이 사슬로 보장한다 — `list_accessible` 의 `member_condition`(내 행은 첫
    // 조건에서 들어옴) · `collapse` 가 「내가 대표인 행」을 먼저 고름 · 부분 UNIQUE
    // `pets_identity_one_per_user` 가 한 그룹에 내 행 둘을 막음. 그 전제가 깨지면 이
    // 판정이 조용히 틀리므로 여기서 잡는다.

    @Test
    fun `내가 가진 행이면 목록에서 찾는다`() {
        val pets = listOf(pet("my-row", owner = true), pet("their-row", owner = false))

        assertEquals(true, ownsPetRow(pets, "my-row"))
    }

    /** 공동 돌봄으로 참여한 아이도 목록에 있다 — 있다는 것만으로 열면 안 된다. */
    @Test
    fun `목록에 있어도 내 행이 아니면 아니다`() {
        val pets = listOf(pet("their-row", owner = false))

        assertEquals(false, ownsPetRow(pets, "their-row"))
    }

    /** 연결된 그룹에서 내 카드의 id 는 **내 행**이다 — 앵커 행은 내 목록에 안 온다. */
    @Test
    fun `연결된 그룹에서는 내 행만 내 것이다`() {
        // 내 카드 하나(내 행). 대표가 적은 기록은 anchor-row 에 달려 온다.
        val pets = listOf(pet("my-row", owner = true))

        assertEquals(true, ownsPetRow(pets, "my-row"))
        assertEquals(false, ownsPetRow(pets, "anchor-row"))
    }

    /** 목록을 아직 못 받았으면 못 지우는 쪽으로 기운다. */
    @Test
    fun `목록이 비면 아무것도 내 것이 아니다`() {
        assertEquals(false, ownsPetRow(emptyList(), "my-row"))
    }

    // -- 둘 다 모를 때 -----------------------------------------------------------

    /**
     * **로그인 정보와 작성자가 둘 다 없으면 열면 안 된다.** `null == null` 로 기울면
     * 옛 기록·탈퇴자의 기록이 아무에게나 열린다.
     */
    @Test
    fun `현재 사용자와 작성자가 둘 다 없으면 안 열린다`() {
        val nameless = event().copy(actor = CareActor(null, null))
        val noActor = event().copy(id = "e-no-actor", actor = null)
        val notMine = { _: String -> false }

        assertEquals(false, canDeleteCareEvent(nameless, currentUserId = null, ownsPetRow = notMine))
        assertEquals(false, canDeleteCareEvent(noActor, currentUserId = null, ownsPetRow = notMine))
        // 작성자를 알아도 내가 누군지 모르면 안 된다.
        assertEquals(
            false,
            canDeleteCareEvent(event().copy(actor = CareActor("someone", "누구")), null, notMine),
        )
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        fun event() = CareEvent(
            id = "event-1",
            petId = "pet",
            kind = CareKind.MEDICATION,
            occurredAtMs = 1_756_701_000_000L, // 2025-09-01T13:30+09:00
            note = null,
            clientEventId = "client-1",
        )

        fun pet(id: String, owner: Boolean) = com.daengs.app.pet.Pet(
            id = id, name = "이름", breed = "dog_beagle",
            sex = null, neutered = null, weightKg = null,
            birthDate = null, birthDateKind = null,
            isPrimary = false, isOwner = owner,
        )

        fun summary(events: List<CareEvent> = emptyList()) =
            CareDaySummary("pet", "2025-09-01", "Asia/Seoul", meal = 2, medication = 1, snack = 3, walk = 1, events = events)
    }
}
