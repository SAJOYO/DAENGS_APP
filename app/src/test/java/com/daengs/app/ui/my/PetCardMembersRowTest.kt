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
 * **프로필 수정과 다른 동작이어야 한다.** 공동 돌봄 아이는 카드 본체(수정)가 막혀 있는데,
 * 그 줄까지 같이 막히면 돌보미에게는 보호자 목록으로 갈 길이 아예 없어진다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetCardMembersRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun pet(id: String, isOwner: Boolean, isGroupOwner: Boolean = isOwner) = Pet(
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
    fun `프로필 수정이 막힌 공동 돌봄 아이에서도 줄이 눌린다`() {
        var opened: Pet? = null
        var edited: Pet? = null
        screen(
            listOf(pet("shared", isOwner = false)),
            onOpenMembers = { opened = it },
            onEditPet = { edited = it },
        )

        compose.onNodeWithText("함께 돌보는 사람").performClick()

        assertEquals("shared", opened?.id)
        assertNull("보호자 줄이 프로필 수정을 부르면 안 된다", edited)
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

    @Test
    fun `내가 그룹 주보호자인 아이에는 뱃지가 없다`() {
        screen(listOf(pet("mine", isOwner = true)), onOpenMembers = {})

        compose.onAllNodesWithText("공동 돌봄").assertCountEquals(0)
    }
}
