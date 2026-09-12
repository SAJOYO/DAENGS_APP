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
import com.daengs.app.ui.my.MyScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 초대를 **보내는** 자리로 가는 길. 받는 쪽은 [InviteAcceptEntryTest] 다.
 *
 * 강아지 카드 안쪽(보호자 목록 → 초대 관리)은 아이 하나짜리 초대라, 여러 마리를 한
 * 링크로 부르는 길은 계정 단위인 마이에 둔다.
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

    private fun my(onInvitePeople: (() -> Unit)?) {
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE,
                nickname = "네옹집사",
                pets = listOf(pet("mine")),
                canAddMore = true,
                onAddPet = {},
                onEditPet = {},
                onPickPrimary = {},
                onDeletePet = {},
                onAcceptInvite = {},
                onInvitePeople = onInvitePeople,
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
    fun `마이에서 초대하기로 갈 수 있다`() {
        var opened = false
        my(onInvitePeople = { opened = true })

        compose.onNodeWithTag("my-invite-people").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("my-invite-people").performScrollTo().performClick()

        assertTrue(opened)
    }

    /** 보내는 자리와 받는 자리가 **둘 다** 있어야 한다 — 하나만 있으면 반쪽 흐름이다. */
    @Test
    fun `초대받기와 초대하기가 같이 뜬다`() {
        my(onInvitePeople = {})

        compose.onNodeWithTag("my-accept-invite").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("my-invite-people").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `연결이 없으면 그 줄이 안 뜬다`() {
        my(onInvitePeople = null)

        compose.onAllNodesWithTag("my-invite-people").assertCountEquals(0)
    }
}
