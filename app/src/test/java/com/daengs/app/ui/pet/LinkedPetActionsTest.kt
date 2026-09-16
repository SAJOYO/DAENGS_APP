package com.daengs.app.ui.pet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.pet.PetHolder
import com.daengs.app.pet.PetList
import com.daengs.app.ui.my.MyScreen
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * **연결된 아이** — 내가 등록했지만(`isOwner`) 남의 아이와 이어져 그룹 주보호자는 초대한
 * 쪽인 경우(`isGroupOwner = false`).
 *
 * 화면이 `isOwner` 로 버튼을 열고 홀더가 `isGroupOwner` 로 막으면, 사용자는 **폼을 다
 * 채우고 저장을 눌러야** 실패를 안다. 서버도 그 요청을 409 `not_group_owner` 로 막는다.
 * 두 기준이 어긋나지 않는지를 여기서 본다.
 *
 * **가리는 것과 아닌 것이 갈린다.** 공통 정보를 바꾸는 일(삭제·배웅)과 내 계정의 표시
 * 설정(대표 강아지)이 다르다 — `docs/co-care-contract.md` §2.
 *
 * **프로필 진입 자체는 안 가린다.** 카드를 누르면 누구든 그 아이의 프로필이 열리고,
 * 무엇을 고칠 수 있는지는 그 화면이 권한 깃발로 가른다 ([PetProfilePermissionTest]).
 * 예전에는 여기서 막아서 함께 돌보는 아이의 생일·먹는 약을 볼 길이 아예 없었다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkedPetActionsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun pet(
        id: String,
        name: String,
        groupOwner: Boolean,
        primary: Boolean = true,
    ) = Pet(
        id = id, name = name, breed = DogBreed.BEAGLE.id,
        sex = null, neutered = null, weightKg = null,
        birthDate = null, birthDateKind = null,
        isPrimary = primary, isOwner = true, isGroupOwner = groupOwner,
    )

    private fun my(
        pets: List<Pet>,
        onEditPet: (Pet) -> Unit = {},
        onFarewell: (Pet) -> Unit = {},
        farewellOf: (Pet) -> LocalDate? = { null },
    ) {
        compose.setContent {
            MyScreen(
                breed = DogBreed.BEAGLE,
                nickname = "네옹집사",
                pets = pets,
                canAddMore = true,
                onAddPet = {}, onEditPet = onEditPet, onPickPrimary = {}, onDeletePet = {},
                onFarewell = onFarewell,
                farewellOf = farewellOf,
                onAcceptInvite = {},
                deleteBusy = false, deleteError = null, onDismissDelete = {},
                signedIn = true, onSignIn = {}, onSignOut = {}, onWithdraw = {},
                withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
            )
        }
    }

    // -- ① 연결된 아이: 공통 정보를 못 바꾼다 -----------------------------------

    /** 눌러 봐야 실패하는 버튼을 열어 두지 않는다. */
    @Test
    fun `연결된 아이에는 삭제를 내주지 않는다`() {
        my(listOf(pet("linked", "롱롱씨", groupOwner = false)))

        compose.onAllNodesWithText("삭제").assertCountEquals(0)
    }

    /** 카드를 누르면 프로필이 열린다 — 고칠 수 있는 칸이 있는지는 그 화면이 정한다. */
    @Test
    fun `연결된 아이도 카드를 누르면 프로필로 간다`() {
        var edited: Pet? = null
        // **비대표로 둔다.** 대표 강아지는 이름이 상단 프로필에도 떠서 같은 글자가 둘이 된다.
        my(listOf(pet("linked", "롱롱씨", groupOwner = false, primary = false)), onEditPet = { edited = it })

        compose.onNodeWithText("롱롱씨").performScrollTo().performClick()

        assertEquals("linked", edited?.id)
    }

    /** 배웅은 전체 PUT 으로 나간다 — 연결된 아이에서는 그 자리가 안 열리고 프로필로 간다. */
    @Test
    fun `연결된 배웅한 아이는 배웅 자리 대신 프로필로 간다`() {
        var sentOff: Pet? = null
        var edited: Pet? = null
        my(
            listOf(pet("linked", "롱롱씨", groupOwner = false, primary = false)),
            onEditPet = { edited = it },
            onFarewell = { sentOff = it },
            farewellOf = { LocalDate.parse("2026-01-01") },
        )

        compose.onNodeWithText("롱롱씨").performScrollTo().performClick()

        assertNull("배웅은 그룹 주보호자만 한다", sentOff)
        assertEquals("linked", edited?.id)
    }

    /** 그룹 주보호자의 배웅한 아이는 예전 그대로 배웅 자리로 간다. */
    @Test
    fun `배웅한 내 아이는 배웅 자리로 간다`() {
        var sentOff: Pet? = null
        my(
            listOf(pet("mine", "롱이", groupOwner = true, primary = false)),
            onFarewell = { sentOff = it },
            farewellOf = { LocalDate.parse("2026-01-01") },
        )

        compose.onNodeWithText("롱이").performScrollTo().performClick()

        assertEquals("mine", sentOff?.id)
    }

    /**
     * **대표 강아지 고르기는 가리지 않는다.** `docs/co-care-contract.md` §2 가 명시한다 —
     * `is_primary` 는 계정의 표시 기본값이라 보호자 권한과 무관하고, 대표 보호자 전용으로
     * 묶지 않는다.
     */
    @Test
    fun `연결된 아이도 대표 강아지로는 고를 수 있다`() {
        // 대표 강아지가 아닌 줄에만 「대표로」가 뜬다 — 이미 대표인 줄은 배지다.
        my(
            listOf(
                pet("mine", "롱이", groupOwner = true, primary = true),
                pet("linked", "롱롱씨", groupOwner = false, primary = false),
            ),
        )

        compose.onNodeWithText("대표로").performScrollTo().assertIsDisplayed()
    }

    // -- ② 그룹 주보호자: 그대로 된다 --------------------------------------------

    @Test
    fun `그룹 주보호자인 아이에는 삭제가 뜬다`() {
        my(listOf(pet("mine", "롱이", groupOwner = true)))

        compose.onNodeWithText("삭제").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `그룹 주보호자인 아이는 카드를 누르면 프로필로 간다`() {
        var edited: Pet? = null
        my(listOf(pet("mine", "롱이", groupOwner = true, primary = false)), onEditPet = { edited = it })

        compose.onNodeWithText("롱이").performScrollTo().performClick()

        assertNotNull(edited)
    }

    // -- ③ 구 서버 호환 -----------------------------------------------------------

    /**
     * **`is_group_owner` 가 없는 응답에서 관리 기능이 사라지면 안 된다.**
     *
     * 서버 배포 전에는 그 필드가 안 온다. 그때 `false` 로 떨어지면 자기 강아지의 수정·삭제가
     * 통째로 막힌다 — 연결이 없으면 두 값은 언제나 같으므로 `is_owner` 를 따라간다.
     */
    @Test
    fun `구 서버 응답에서도 관리 기능이 남는다`() {
        val old = Pet.parse(
            JSONObject("""{"id":"p1","name":"롱이","breed":"dog_beagle","is_primary":true,"is_owner":true}"""),
        )
        assertTrue("파싱 단계에서 이미 fallback 이 걸려야 한다", old.isGroupOwner)

        my(listOf(old))

        compose.onNodeWithText("삭제").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `구 서버의 돌보미 아이에는 관리 기능이 없다`() {
        val cared = Pet.parse(
            JSONObject("""{"id":"p2","name":"남의집 별이","breed":"dog_beagle","is_primary":true,"is_owner":false}"""),
        )
        assertFalse(cared.isGroupOwner)

        my(listOf(cared))

        compose.onAllNodesWithText("삭제").assertCountEquals(0)
    }

    // -- 화면과 홀더가 같은 기준인가 ----------------------------------------------

    /** 화면이 열어 준 것과 홀더가 허락하는 것이 어긋나면 사용자가 눌러 봐야 안다. */
    @Test
    fun `홀더도 같은 기준으로 막는다`() = runTest {
        val linked = pet("linked", "롱롱씨", groupOwner = false)
        val holder = PetHolder { Result.success(PetList(listOf(linked), 5)) }
        holder.refresh("token")

        assertFalse(holder.edit("token", linked.id, linked.toDraft()))
        assertFalse(holder.remove("token", linked.id))
    }

    @Test
    fun `그룹 주보호자는 게이트에 걸리지 않는다`() = runTest {
        val mine = pet("mine", "롱이", groupOwner = true)
        val holder = PetHolder { Result.success(PetList(listOf(mine), 5)) }
        holder.refresh("token")

        // 요청 자체는 가짜 API 라 실패하지만, **게이트에서 막힌 것이 아니어야** 한다.
        holder.edit("token", mine.id, mine.toDraft())
        assertFalse(
            "게이트에 걸리면 이 문구가 뜬다",
            holder.error == "대표 보호자만 강아지 정보를 바꿀 수 있어요.",
        )
    }
}
