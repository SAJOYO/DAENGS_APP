package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.pet.PetInviteBundleApi
import com.daengs.app.pet.PetInviteBundleHolder
import com.daengs.app.ui.my.MyScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 초대를 **보내는** 자리로 가는 길. 받는 쪽은 [InviteAcceptEntryTest] 다.
 *
 * **마이에는 보내는 자리를 두지 않는다.** 「누구를 부를까」는 그 아이의 맥락에서 시작하는
 * 일이라 강아지 카드 안(「함께 돌보는 사람」 → 「새 돌보미 초대」)에서 들어간다. 마이에
 * 나란히 두면 「초대받기/초대하기」가 한 글자만 달라 서로 헷갈린다 — 실제로 헷갈렸다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InviteBundleEntryTest {

    @get:Rule
    val compose = createComposeRule()

    private fun pet(id: String) = Pet(
        id = id, name = "네옹", breed = DogBreed.BEAGLE.id,
        sex = null, neutered = null, weightKg = null,
        birthDate = null, birthDateKind = null, isPrimary = true,
    )

    /** 마이에는 **받는 쪽만** 남는다. */
    @Test
    fun `마이에는 보내는 자리를 두지 않는다`() {
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE,
                nickname = "네옹집사",
                pets = listOf(pet("mine")),
                canAddMore = true,
                onAddPet = {}, onEditPet = {}, onPickPrimary = {}, onDeletePet = {},
                onAcceptInvite = {},
                deleteBusy = false, deleteError = null, onDismissDelete = {},
                signedIn = true, onSignIn = {}, onSignOut = {}, onWithdraw = {},
                withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
            )
        }

        compose.onNodeWithTag("my-accept-invite").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("my-invite-people").assertCountEquals(0)
    }

    /** 보내는 자리는 그 아이의 보호자 목록 안에 있다. */
    @Test
    fun `보호자 목록에서 초대 관리로 간다`() {
        var opened = false
        compose.setContent {
            PetMembersScreen(
                members = emptyList(),
                petName = "네옹",
                currentUserId = "me",
                isGroupOwner = true,
                onOpenInvites = { opened = true },
            )
        }

        compose.onNodeWithTag("open-invites").performScrollTo().performClick()

        assertTrue(opened)
    }

    /**
     * 강아지 카드에서 들어오면 **그 아이가 이미 골라져 있다.**
     *
     * 「이 아이를 함께 돌볼 사람을 부른다」로 들어왔는데 아무것도 안 골라져 있으면,
     * 사용자가 방금 지나온 맥락을 화면에서 다시 찾아 눌러야 한다.
     */
    @Test
    fun `들어온 아이가 미리 골라져 있다`() {
        val holder = PetInviteBundleHolder(PetInviteBundleApi { "http://127.0.0.1:1" })

        holder.startWith("p2")

        assertEquals(listOf("p2"), holder.selected)
    }

    /** 미리 골라 준 뒤에도 다른 아이를 **더** 고를 수 있어야 한다. */
    @Test
    fun `미리 고른 뒤에도 다른 아이를 더 고를 수 있다`() {
        val holder = PetInviteBundleHolder(PetInviteBundleApi { "http://127.0.0.1:1" })
        holder.startWith("p2")

        holder.toggle("p3")

        assertEquals(listOf("p2", "p3"), holder.selected)
    }

    /**
     * 화면에 다시 들어올 때마다 초기화하면, 두 마리째를 고르다 뒤로 갔다 온 사용자가
     * 처음으로 돌아간다.
     */
    @Test
    fun `이미 고르는 중이면 다시 들어와도 안 건드린다`() {
        val holder = PetInviteBundleHolder(PetInviteBundleApi { "http://127.0.0.1:1" })
        holder.startWith("p2")
        holder.toggle("p3")

        holder.startWith("p2")

        assertEquals(listOf("p2", "p3"), holder.selected)
    }
}
