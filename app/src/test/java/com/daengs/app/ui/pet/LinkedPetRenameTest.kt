package com.daengs.app.ui.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.ui.my.MyScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 마이의 「이름 변경」 — **연결된 보호자에게만** 뜨는 이름 전용 창.
 *
 * 연결된 보호자(`isOwner = true`, `isGroupOwner = false`)는 공통 정보 수정이 막혀 있어서
 * 예전에는 자기 아이 이름조차 바꿀 길이 없었다. 계약(`docs/co-care.md` §6 권한표)은 그 사람의
 * 이름을 `PATCH /app/pets/{id}/display` 로 열어 두었다. 그 창을 열되, **공통 정보 폼으로
 * 새지 않는지**를 여기서 본다.
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

    private fun my(
        pets: List<Pet>,
        onRenamePet: ((Pet, String) -> Unit)? = { _, _ -> },
        onEditPet: (Pet) -> Unit = {},
        busy: Boolean = false,
        error: String? = null,
    ) {
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE,
                nickname = "댕댕이",
                pets = pets,
                canAddMore = true,
                onAddPet = {}, onEditPet = onEditPet, onPickPrimary = {}, onDeletePet = {},
                onRenamePet = onRenamePet,
                renamePetBusy = busy,
                renamePetError = error,
                deleteBusy = false, deleteError = null, onDismissDelete = {},
                signedIn = true, onSignIn = {}, onSignOut = {}, onWithdraw = {},
                withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
            )
        }
    }

    // -- ① 누구에게 버튼이 뜨나 ---------------------------------------------------

    @Test
    fun `연결된 보호자에게 이름 변경이 뜬다`() {
        my(listOf(linked))

        compose.onNodeWithText("이름 변경").performScrollTo().assertIsDisplayed()
    }

    /** 그룹 주보호자는 카드를 눌러 전체 편집으로 간다 — 따로 버튼을 두면 길이 둘이 된다. */
    @Test
    fun `그룹 주보호자에게는 이름 변경이 따로 안 뜨고 전체 편집이 그대로다`() {
        var edited: Pet? = null
        val mine = pet("mine", "롱이", isOwner = true, isGroupOwner = true)
        my(listOf(mine), onEditPet = { edited = it })

        compose.onAllNodesWithText("이름 변경").assertCountEquals(0)
        compose.onNodeWithText("롱이").performScrollTo().performClick()
        assertEquals(mine, edited)
    }

    /** 돌보미는 행의 대표가 아니라 서버가 404 다 — 눌러 봐야 실패할 버튼을 열지 않는다. */
    @Test
    fun `돌보미에게는 이름 변경이 안 뜬다`() {
        my(listOf(pet("cared", "롱롱씨", isOwner = false, isGroupOwner = false)))

        compose.onAllNodesWithText("이름 변경").assertCountEquals(0)
    }

    @Test
    fun `배선이 없으면 버튼이 안 뜬다`() {
        my(listOf(linked), onRenamePet = null)

        compose.onAllNodesWithText("이름 변경").assertCountEquals(0)
    }

    // -- ② 공통 정보는 계속 막힌다 --------------------------------------------------

    @Test
    fun `이름 변경이 생겨도 연결된 아이의 전체 편집과 삭제는 막혀 있다`() {
        var edited: Pet? = null
        my(listOf(linked), onEditPet = { edited = it })

        compose.onNodeWithText("테스트연결").performScrollTo().performClick()
        assertNull("카드를 눌러도 공통 정보 폼으로 안 간다", edited)
        compose.onAllNodesWithText("삭제").assertCountEquals(0)
    }

    // -- ③ 창 ---------------------------------------------------------------------

    @Test
    fun `창은 지금 이름으로 열리고 저장하면 그 아이와 새 이름을 넘긴다`() {
        var sent: Pair<Pet, String>? = null
        my(listOf(linked), onRenamePet = { p, n -> sent = p to n })

        compose.onNodeWithText("이름 변경").performScrollTo().performClick()
        compose.onNodeWithTag("rename-pet-input").assertTextEquals("테스트연결")
        compose.onNodeWithTag("rename-pet-input").performTextReplacement("새이름")
        compose.onNodeWithTag("rename-pet-save").performClick()

        assertEquals(linked to "새이름", sent)
    }

    @Test
    fun `빈 이름으로는 저장이 안 눌린다`() {
        var sent: Pair<Pet, String>? = null
        my(listOf(linked), onRenamePet = { p, n -> sent = p to n })

        compose.onNodeWithText("이름 변경").performScrollTo().performClick()
        compose.onNodeWithTag("rename-pet-input").performTextReplacement("   ")
        compose.onNodeWithTag("rename-pet-save").performClick()

        assertNull(sent)
    }

    /** 요청 중에는 저장 자리가 진행 표시로 바뀐다 — 두 번 눌러 두 번 보내지 않는다. */
    @Test
    fun `진행 중에는 저장을 다시 누를 수 없다`() {
        var busy by mutableStateOf(false)
        var sends = 0
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE, nickname = "댕댕이", pets = listOf(linked), canAddMore = true,
                onAddPet = {}, onEditPet = {}, onPickPrimary = {}, onDeletePet = {},
                onRenamePet = { _, _ -> sends++; busy = true },
                renamePetBusy = busy,
                deleteBusy = false, deleteError = null, onDismissDelete = {},
                signedIn = true, onSignIn = {}, onSignOut = {}, onWithdraw = {},
                withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
            )
        }

        compose.onNodeWithText("이름 변경").performScrollTo().performClick()
        compose.onNodeWithTag("rename-pet-input").performTextReplacement("새이름")
        compose.onNodeWithTag("rename-pet-save").performClick()

        compose.onAllNodesWithTag("rename-pet-save").assertCountEquals(0)
        assertEquals(1, sends)
    }

    /** 실패하면 창이 닫히지 않고, 친 글자가 남고, 이유가 보인다. */
    @Test
    fun `실패하면 입력이 남고 오류가 보인다`() {
        var busy by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE, nickname = "댕댕이", pets = listOf(linked), canAddMore = true,
                onAddPet = {}, onEditPet = {}, onPickPrimary = {}, onDeletePet = {},
                onRenamePet = { _, _ -> busy = true },
                renamePetBusy = busy,
                renamePetError = error,
                deleteBusy = false, deleteError = null, onDismissDelete = {},
                signedIn = true, onSignIn = {}, onSignOut = {}, onWithdraw = {},
                withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
            )
        }

        compose.onNodeWithText("이름 변경").performScrollTo().performClick()
        compose.onNodeWithTag("rename-pet-input").performTextReplacement("새이름")
        compose.onNodeWithTag("rename-pet-save").performClick()
        compose.waitForIdle()
        // 서버가 실패로 답했다.
        error = "서버 오류 (502)"
        busy = false
        compose.waitForIdle()

        compose.onNodeWithTag("rename-pet-dialog").assertIsDisplayed()
        compose.onNodeWithTag("rename-pet-input").assertTextEquals("새이름")
        compose.onNodeWithText("서버 오류 (502)").assertIsDisplayed()
    }

    /** 성공하면(진행이 끝나고 오류가 없으면) 창이 닫힌다. */
    @Test
    fun `성공하면 창이 닫힌다`() {
        var busy by mutableStateOf(false)
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE, nickname = "댕댕이", pets = listOf(linked), canAddMore = true,
                onAddPet = {}, onEditPet = {}, onPickPrimary = {}, onDeletePet = {},
                onRenamePet = { _, _ -> busy = true },
                renamePetBusy = busy,
                deleteBusy = false, deleteError = null, onDismissDelete = {},
                signedIn = true, onSignIn = {}, onSignOut = {}, onWithdraw = {},
                withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
            )
        }

        compose.onNodeWithText("이름 변경").performScrollTo().performClick()
        compose.onNodeWithTag("rename-pet-input").performTextReplacement("새이름")
        compose.onNodeWithTag("rename-pet-save").performClick()
        compose.waitForIdle()
        busy = false
        compose.waitForIdle()

        compose.onAllNodesWithTag("rename-pet-dialog").assertCountEquals(0)
        assertTrue(true)
    }
}
