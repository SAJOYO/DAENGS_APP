package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.pet.PetDraft
import com.daengs.app.pet.PetHolder
import com.daengs.app.pet.PetList
import com.daengs.app.ui.my.MyScreen
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 연결한 공동 보호자(`isOwner = true`, `isGroupOwner = false`)의 **내 이름·사진 바꾸기**.
 *
 * 예전에는 카드 앞면에 「이름 변경」이라는 작은 글씨를 따로 두고 거기서만 바꿨다. 그
 * 사람은 카드를 눌러도 아무 일이 없었고(공통 정보 폼이 막혀 있었다), 결과적으로 **함께
 * 돌보는 아이의 생일·먹는 약을 볼 길이 아예 없었다.** 지금은 카드를 누르면 누구나
 * 프로필이 열리고, 무엇을 고칠 수 있는지는 그 화면이 가른다 ([PetProfilePermissionTest]).
 *
 * 여기서 보는 것은 **그 길이 이어져 있는가**다 — 카드에서 프로필로 들어가고, 이름을
 * 고쳐 저장하면 그 값이 나가고, 그 저장이 전체 PUT 이 아니라
 * `PATCH /app/pets/{id}/display` 로 간다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkedPetRenameTest {

    @get:Rule
    val compose = createComposeRule()

    private fun pet(
        id: String,
        name: String,
        isOwner: Boolean,
        isGroupOwner: Boolean,
        primary: Boolean = false,
    ) = Pet(
        id = id, name = name, breed = DogBreed.BEAGLE.id,
        sex = null, neutered = null, weightKg = null,
        birthDate = null, birthDateKind = null,
        isPrimary = primary, isOwner = isOwner, isGroupOwner = isGroupOwner,
    )

    private val linked = pet("b-row", "테스트연결", isOwner = true, isGroupOwner = false)

    private fun my(pets: List<Pet>, onEditPet: (Pet) -> Unit = {}) {
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE,
                nickname = "댕댕이",
                pets = pets,
                canAddMore = true,
                onAddPet = {}, onEditPet = onEditPet, onPickPrimary = {}, onDeletePet = {},
                deleteBusy = false, deleteError = null, onDismissDelete = {},
                signedIn = true, onSignIn = {}, onSignOut = {}, onWithdraw = {},
                withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
            )
        }
    }

    /** 마이의 카드가 여는 화면. `MainActivity` 가 넘기는 깃발과 같은 값으로 띄운다. */
    private fun profile(pet: Pet, onSubmit: (PetDraft) -> Unit = {}) {
        compose.setContent {
            PetFormScreen(
                initial = pet,
                onSubmit = { draft, _ -> onSubmit(draft) },
                onCancel = {},
                busy = false,
                error = null,
                canEditIdentity = pet.isOwner,
                canEditCommon = pet.isGroupOwner,
            )
        }
    }

    // -- ① 카드 앞면 --------------------------------------------------------------

    /** 앞면의 작은 글씨를 없앴다. 이름은 프로필 안에서 다른 값들과 같은 자리에 있다. */
    @Test
    fun `카드 앞면에 이름 변경이 없다`() {
        my(listOf(linked))

        compose.onAllNodesWithText("이름 변경").assertCountEquals(0)
    }

    @Test
    fun `연결한 공동 보호자도 카드를 누르면 프로필로 간다`() {
        var edited: Pet? = null
        my(listOf(linked), onEditPet = { edited = it })

        compose.onNodeWithText("테스트연결").performScrollTo().performClick()

        assertEquals(linked, edited)
    }

    // -- ② 그 프로필에서 할 수 있는 것 -----------------------------------------------

    @Test
    fun `그 프로필에서 이름과 사진을 바꿀 수 있다`() {
        profile(linked)

        compose.onNodeWithContentDescription("이름").assertIsDisplayed()
        compose.onNodeWithText("사진 올리기").performScrollTo().assertIsDisplayed()
    }

    /** 공통 정보는 그룹 주보호자의 값이라 **입력 칸 자체가 없다.** */
    @Test
    fun `그 프로필에는 공통 정보 입력 칸이 없다`() {
        profile(linked)

        compose.onAllNodesWithContentDescription("몸무게 (kg)").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("앓는 병").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("먹는 약").assertCountEquals(0)
    }

    @Test
    fun `이름을 고쳐 저장하면 새 이름이 넘어온다`() {
        var sent: PetDraft? = null
        profile(linked, onSubmit = { sent = it })

        compose.onNodeWithContentDescription("이름").performTextReplacement("새이름")
        compose.onNodeWithText("저장하기").performScrollTo().performClick()

        assertEquals("새이름", sent?.name)
    }

    // -- ③ 그 저장이 어디로 나가나 ---------------------------------------------------

    /**
     * **전체 PUT 으로 돌아가지 않는다.** 연결된 아이에서 그 길은 서버가 409
     * (`not_group_owner`) 로 막고, 뚫리더라도 주보호자의 견종·건강정보를 덮어쓴다.
     */
    @Test
    fun `홀더는 그 저장을 PATCH display 로 보내고 전체 PUT 은 막는다`() = runTest {
        val renames = mutableListOf<Triple<String, String, String>>()
        val holder = PetHolder(
            renameDisplay = { token, id, name ->
                renames += Triple(token, id, name)
                Result.success(linked.copy(name = name))
            },
            listPets = { Result.success(PetList(listOf(linked), 5)) },
        )
        holder.refresh("token")

        assertTrue(holder.rename("token", linked.id, "새이름"))
        assertEquals(listOf(Triple("token", "b-row", "새이름")), renames)

        // 같은 아이의 공통 정보는 여전히 막혀 있다.
        assertFalse(holder.edit("token", linked.id, linked.toDraft()))
        assertEquals("대표 보호자만 강아지 정보를 바꿀 수 있어요.", holder.error)
    }
}
