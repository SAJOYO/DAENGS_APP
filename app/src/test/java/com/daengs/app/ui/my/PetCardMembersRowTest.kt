package com.daengs.app.ui.my

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 카드 안의 「함께 돌보는 사람」 줄.
 *
 * **카드 본체와 다른 곳으로 가야 한다.** 카드를 누르면 그 아이의 프로필이고, 이 줄은
 * 그 아이를 함께 돌보는 **사람들**이다. 둘이 같은 곳으로 가면 돌보미에게는 보호자
 * 목록으로 갈 길이 아예 없어진다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetCardMembersRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun pet(
        id: String,
        isOwner: Boolean,
        isGroupOwner: Boolean = isOwner,
        hasOtherCarers: Boolean = false,
    ) = Pet(
        id = id,
        name = if (isOwner) "네옹" else "몽이",
        breed = DogBreed.BEAGLE.id,
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = null,
        birthDateKind = null,
        isPrimary = isOwner,
        isOwner = isOwner,
        isGroupOwner = isGroupOwner,
        hasOtherCarers = hasOtherCarers,
    )

    private fun screen(pets: List<Pet>, onOpenMembers: (Pet) -> Unit, onEditPet: (Pet) -> Unit = {}) {
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE,
                nickname = "네옹집사",
                pets = pets,
                canAddMore = true,
                onAddPet = {},
                onEditPet = onEditPet,
                onPickPrimary = {},
                onDeletePet = {},
                onOpenMembers = onOpenMembers,
                deleteBusy = false,
                deleteError = null,
                onDismissDelete = {},
                signedIn = true,
                onSignIn = {},
                onSignOut = {},
                onWithdraw = {},
                withdrawBusy = false,
                withdrawError = null,
                onDismissWithdraw = {},
            )
        }
    }

    @Test
    fun `대표 보호자의 아이에서 줄을 보고 들어간다`() {
        var opened: Pet? = null
        screen(listOf(pet("mine", isOwner = true)), onOpenMembers = { opened = it })

        compose.onNodeWithText("함께 돌보는 사람").assertIsDisplayed()
        compose.onNodeWithText("함께 돌보는 사람").performClick()

        assertEquals("mine", opened?.id)
    }

    @Test
    fun `공동 돌봄 아이에서도 줄이 프로필이 아니라 보호자 목록으로 간다`() {
        var opened: Pet? = null
        var edited: Pet? = null
        screen(
            listOf(pet("shared", isOwner = false)),
            onOpenMembers = { opened = it },
            onEditPet = { edited = it },
        )

        compose.onNodeWithText("함께 돌보는 사람").performClick()

        assertEquals("shared", opened?.id)
        assertNull("보호자 줄이 프로필을 열면 안 된다", edited)
    }

    @Test
    fun `아이마다 한 줄씩 있다`() {
        screen(listOf(pet("mine", isOwner = true), pet("shared", isOwner = false)), onOpenMembers = {})

        compose.onAllNodesWithText("함께 돌보는 사람").assertCountEquals(2)
    }

    // -- 공동 돌봄 뱃지 --------------------------------------------------------------

    /**
     * 기존 강아지와 연결한 공동 보호자의 카드(`display_pet_id` = 자기 행). 자기 행의 대표라
     * `isOwner` 는 true 인데 그룹 주보호자는 남이다 — 행 기준으로 가르면 뱃지가 사라진다.
     */
    @Test
    fun `연결한 공동 보호자의 자기 행 카드에도 공동 돌봄 뱃지가 보인다`() {
        screen(listOf(pet("linked", isOwner = true, isGroupOwner = false)), onOpenMembers = {})

        compose.onNodeWithText("공동 돌봄").assertIsDisplayed()
    }

    @Test
    fun `연결 없이 돌보미로 참여한 아이에도 뱃지가 보인다`() {
        screen(listOf(pet("shared", isOwner = false)), onOpenMembers = {})

        compose.onNodeWithText("공동 돌봄").assertIsDisplayed()
    }

    /**
     * 공동 보호자를 둔 **그룹 주보호자 본인** 카드. `isOwner`·`isGroupOwner` 가 둘 다 true 라
     * 혼자 등록한 아이와 같은 값이다 — 서버 `has_other_carers` 로만 갈린다.
     */
    @Test
    fun `다른 보호자가 있는 그룹 주보호자 카드에도 뱃지가 보인다`() {
        screen(listOf(pet("co", isOwner = true, isGroupOwner = true, hasOtherCarers = true)), onOpenMembers = {})

        compose.onNodeWithText("공동 돌봄").assertIsDisplayed()
    }

    /** 뱃지 자리는 빈칸으로 남지만 **글자는 없어야** 한다 — 화면 읽기가 없는 뱃지를 읽으면 안 된다. */
    @Test
    fun `혼자 돌보는 아이에는 뱃지가 없다`() {
        screen(listOf(pet("mine", isOwner = true)), onOpenMembers = {})

        compose.onAllNodesWithText("공동 돌봄", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `뱃지 조건표`() {
        fun badge(isOwner: Boolean, isGroupOwner: Boolean, hasOtherCarers: Boolean) =
            showsCoCareBadge(pet("p", isOwner, isGroupOwner, hasOtherCarers))

        assertEquals("그룹 주보호자 + 다른 보호자", true, badge(true, true, true))
        assertEquals("혼자 돌봄", false, badge(true, true, false))
        assertEquals("연결한 공동 보호자 자기 행", true, badge(true, false, false))
        assertEquals("연결 없이 참여한 돌보미", true, badge(false, false, false))
    }
}
