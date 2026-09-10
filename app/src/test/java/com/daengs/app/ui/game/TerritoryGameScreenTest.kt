package com.daengs.app.ui.game

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.daengs.app.activity.*
import com.daengs.app.ui.home.HomeGameRoute
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class TerritoryGameScreenTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val dog = previewGamePet(GAME_PET_A)

    @Test fun homeEntryOpensFullOverviewAndBackAndMapAreSeparate() {
        val repository = gameTestRepository(GameTestClient())
        var open by mutableStateOf(false)
        var mapCalls = 0
        compose.setContent { DaengsTheme {
            val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            if (open) saved.SaveableStateProvider("game") {
                TerritoryGameRoute(repository, "owner", listOf(dog, previewGamePet(GAME_PET_B, "보리").copy(isPrimary = false)),
                    { open = false }, { mapCalls++ })
            } else HomeGameRoute(repository, "owner", dog.id, dog.name, { open = true })
        } }
        compose.onNodeWithText("현황 ›").performClick()
        compose.onNodeWithText("점령 게임").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("1,240 점").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("두부의 이번 시즌").assertIsDisplayed()
        compose.onNodeWithText("현재 점령").assertIsDisplayed()
        compose.onNodeWithText("점령 지도 보기").assertIsDisplayed().performClick()
        assertEquals(1, mapCalls)
        screenshot("game-overview")
        compose.onNodeWithText("강아지 변경").performClick()
        compose.onNodeWithTag("game-pet-$GAME_PET_B").performClick()
        compose.onNodeWithTag("game-rules-open").performClick()
        compose.onNodeWithTag("game-rules-details-open").performClick()
        compose.onNodeWithText("회원·시즌별 100점", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("game-rules-close").performClick()
        compose.onNodeWithContentDescription("점령 게임 뒤로 가기").performClick()
        compose.onNodeWithText("현황 ›").assertIsDisplayed()
        compose.onNodeWithText("점령 게임").assertDoesNotExist()
        compose.onNodeWithText("현황 ›").performClick()
        compose.onNodeWithText("보리의 이번 시즌").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("현황 ›").assertIsDisplayed()
    }

    @Test fun switchingDogsDiscardsLateScoreAndUsesSelectedProfilePhoto() {
        val late = CompletableDeferred<ActivityTerritorySummary>()
        val requested = mutableListOf<String>()
        val client = object : GameTestClient() {
            override suspend fun territorySummary(token: String, seasonId: String, petId: String): ActivityTerritorySummary {
                requested += petId
                return if (petId == GAME_PET_A) withContext(NonCancellable) { late.await() }
                else super.territorySummary(token, seasonId, petId).let { it.copy(score = it.score!!.copy(bonus = 200,
                    baseBonus = 200, takeoverBonus = 0, holdingUnits = java.math.BigInteger.ZERO)) }
            }
        }
        val repository = gameTestRepository(client)
        val other = previewGamePet(GAME_PET_B, "보리").copy(isPrimary = false)
        val photos = mutableSetOf<String>()
        val suppliedPhoto = ImageBitmap(16, 16)
        var owner by mutableStateOf<String?>("owner")
        compose.setContent { DaengsTheme {
            TerritoryGameRoute(repository, owner, listOf(dog, other), {}, {}, photoOf = { photos += it; suppliedPhoto })
        } }
        compose.waitUntil(5_000) { GAME_PET_A in requested }
        compose.onNodeWithText("강아지 변경").performClick()
        compose.onNodeWithTag("game-pet-$GAME_PET_B").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("200 점").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("보리의 이번 시즌").assertIsDisplayed()
        compose.runOnIdle { late.complete(previewGameOverview().summary!!.copy(petId = GAME_PET_A)) }
        compose.onNodeWithText("200 점").assertIsDisplayed()
        compose.onNodeWithText("1,240 점").assertDoesNotExist()
        assertTrue(GAME_PET_B in photos)
        compose.runOnIdle { owner = null }
        compose.onNodeWithText("로그인하기").assertIsDisplayed()
        compose.onNodeWithText("200 점").assertDoesNotExist()
        compose.onNodeWithText("보리의 이번 시즌").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun narrowScreenKeepsRulesControlsReachableWithLargerText() {
        var opened = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                DaengsTheme { TerritoryGameScreen(previewGameOverview(), dog, listOf(dog), { null }, 0, true,
                    {}, { opened++ }, {}, {}) }
            }
        }
        compose.onNodeWithText("산책 지도로 돌아가기").assertIsDisplayed().performClick()
        assertEquals(1, opened)
        val list = compose.onNodeWithTag("game-overview-list")
        val body = list.fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("game-map-action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(body.bottom <= button.top)
        compose.onNodeWithTag("game-rules-open").performClick()
        compose.onNodeWithTag("game-rules-example-open").assertIsDisplayed()
        compose.onNodeWithTag("game-rules-details-open").assertIsDisplayed()
        compose.onNodeWithText("기본 한도: 회원·장소·시즌당 100점", substring = true)
            .performScrollTo().assertIsDisplayed()
        val summaryBody = compose.onNodeWithTag("game-rules-body").fetchSemanticsNode().boundsInRoot
        val summaryFooter = compose.onNodeWithTag("game-rules-footer").fetchSemanticsNode().boundsInRoot
        assertTrue(summaryBody.bottom <= summaryFooter.top)
        screenshot("game-rules-summary-narrow", dialog = true)
        compose.onNodeWithTag("game-rules-example-open").performClick()
        compose.onNodeWithTag("game-guide-next").assertIsDisplayed().performClick()
        compose.onNodeWithTag("game-guide-pole").performScrollTo().performClick()
        compose.onNodeWithTag("game-guide-next").assertIsDisplayed().performClick()
        compose.onNodeWithText("두부  ·  기본 +20점").performScrollTo().assertIsDisplayed()
        val rulesBody = compose.onNodeWithTag("game-rules-body").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithTag("game-rules-footer").fetchSemanticsNode().boundsInRoot
        assertTrue(rulesBody.bottom <= footer.top)
        compose.onNodeWithTag("game-rules-close").assertIsDisplayed()
        screenshot("game-rules-narrow", dialog = true)
        compose.onNodeWithTag("game-rules-summary-back").performClick()
        compose.onNodeWithTag("game-rules-details-open").performClick()
        compose.onNodeWithText("회원·시즌별 100점", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("game-rules-tab-2").performClick()
        compose.onNodeWithText("인증 직후 10분 보호").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("game-rules-tab-3").performClick()
        compose.onNodeWithText("매월 1일 00:00").assertIsDisplayed()
        compose.onNodeWithTag("game-rules-close").performClick()
        compose.onNodeWithTag("game-rules-dialog").assertDoesNotExist()
        compose.onNodeWithText("산책 지도로 돌아가기").assertIsDisplayed()
    }

    @Test fun preparingErrorAndNoPetStayDistinctAndCanRetryOrRegister() {
        val client = GameTestClient().apply { error = ActivityHttpException(503, "activity_disabled") }
        val repository = gameTestRepository(client)
        var pets by mutableStateOf<List<com.daengs.app.pet.Pet>?>(listOf(dog))
        var registrations = 0
        compose.setContent { DaengsTheme {
            TerritoryGameRoute(repository, "owner", pets, {}, {}, onAddPet = { registrations++ })
        } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("시즌 게임을 준비하고 있어요").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("— 점").assertIsDisplayed()
        compose.onNodeWithText("0 점").assertDoesNotExist()
        screenshot("game-preparing")
        compose.runOnIdle { client.error = java.io.IOException("offline") }
        compose.onNodeWithText("새로고침").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("다시 불러오기").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { client.error = null }
        compose.onNodeWithText("다시 불러오기").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("1,240 점").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { pets = emptyList() }
        compose.onNodeWithText("강아지 등록하기").performClick()
        assertEquals(1, registrations)
        compose.onNodeWithText("1,240 점").assertDoesNotExist()
    }

    private fun screenshot(name: String, dialog: Boolean = false) {
        val output = java.io.File("build/reports/territory-game/$name.png")
        checkNotNull(output.parentFile).mkdirs()
        compose.runOnIdle {
            val view = if (dialog) checkNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog().window).decorView
                else compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
