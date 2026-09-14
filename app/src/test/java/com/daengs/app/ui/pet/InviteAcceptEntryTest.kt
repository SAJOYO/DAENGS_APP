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
 * 초대받기로 가는 두 자리.
 *
 * **신규 사용자와 기존 사용자 둘 다 길이 있어야 한다.** 초대받은 사람이 길을 못 찾아
 * 강아지를 새로 등록하면, 서버가 합쳐 주지 않아 같은 아이가 두 마리가 된다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InviteAcceptEntryTest {

    @get:Rule
    val compose = createComposeRule()

    private fun pet(id: String) = Pet(
        id = id, name = "네옹", breed = DogBreed.BEAGLE.id,
        sex = null, neutered = null, weightKg = null,
        birthDate = null, birthDateKind = null, isPrimary = true,
    )

    private fun my(pets: List<Pet>?, onAcceptInvite: (() -> Unit)?) {
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE,
                nickname = "네옹집사",
                pets = pets,
                canAddMore = true,
                onAddPet = {},
                onEditPet = {},
                onPickPrimary = {},
                onDeletePet = {},
                onAcceptInvite = onAcceptInvite,
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

    /** 아직 강아지가 없는 사람 — 등록하지 않고도 초대받기로 갈 수 있어야 한다. */
    @Test
    fun `강아지가 없어도 마이에서 초대받기로 갈 수 있다`() {
        var opened = false
        my(pets = emptyList(), onAcceptInvite = { opened = true })

        compose.onNodeWithTag("my-accept-invite").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("my-accept-invite").performScrollTo().performClick()

        assertTrue(opened)
    }

    /** 이미 등록한 사람도 같은 자리로 들어간다. */
    @Test
    fun `강아지가 있어도 마이에서 초대받기로 갈 수 있다`() {
        var opened = false
        my(pets = listOf(pet("mine")), onAcceptInvite = { opened = true })

        compose.onNodeWithTag("my-accept-invite").performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun `연결이 없으면 그 줄이 안 뜬다`() {
        my(pets = listOf(pet("mine")), onAcceptInvite = null)

        compose.onAllNodesWithTag("my-accept-invite").assertCountEquals(0)
    }

    /**
     * 첫 등록 화면의 보조 진입점. 초대받은 사람이 여기까지 왔을 때 **등록 대신** 빠져나갈
     * 길이 있어야 한다.
     */
    @Test
    fun `등록 화면에서 초대받기로 빠져나갈 수 있다`() {
        var opened = false
        compose.setContent {
            PetFormScreen(
                onSubmit = { _, _ -> },
                onCancel = {},
                onAcceptInvite = { opened = true },
                busy = false,
                error = null,
            )
        }

        compose.onNodeWithTag("form-accept-invite").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("form-accept-invite").performScrollTo().performClick()

        assertTrue(opened)
    }

    /** 고치기로 들어온 화면에는 할 말이 아니다 — 부르는 쪽이 null 을 넘긴다. */
    @Test
    fun `연결이 없으면 등록 화면에도 안 뜬다`() {
        compose.setContent {
            PetFormScreen(onSubmit = { _, _ -> }, onCancel = {}, busy = false, error = null)
        }

        compose.onAllNodesWithTag("form-accept-invite").assertCountEquals(0)
    }
}
