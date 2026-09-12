package com.daengs.app.ui.game.owned

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.auth.Session
import com.daengs.app.territory.ClaimCertification
import com.daengs.app.territory.owned.*
import com.daengs.app.ui.game.previewGamePet
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
class OwnedTerritoryRouteTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun `pet filters keep list mode discard late responses and logout clears ownership`() {
        val a = previewOwnedSite()
        val b = previewOwnedSite(a.siteId + "0", "보리").copy(petId = "00000000-0000-0000-0000-000000000002",
            certification = ClaimCertification.UNVERIFIED)
        val pets = listOf(previewGamePet(a.petId), previewGamePet(b.petId, "보리").copy(isPrimary = false))
        val auth = Session("owner", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        val requests = mutableListOf<String?>()
        val late = CompletableDeferred<OwnedTerritoryPage>()
        fun page(pet: String?) = listOf(a, b).filter { pet == null || it.petId == pet }.let {
            OwnedTerritoryPage("first", 1_800_000_000_000, pet, it.size, it, null)
        }
        var owner by mutableStateOf<String?>("owner")
        val repository = OwnedTerritoryRepository(OwnedTerritoryClient { _, pet, _ ->
            requests += pet
            if (pet == a.petId) withContext(NonCancellable) { late.await() } else page(pet)
        }, { auth.takeIf { owner != null } }, { auth.takeIf { owner != null } })
        compose.setContent { DaengsTheme {
            OwnedTerritoryRoute(repository, owner, pets, {}, {}, mapSurface = { _, _, _ -> Text("지도 테스트 표면") })
        } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("조회 기준 2곳 보유").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("owned-list-tab").performClick()
        compose.onNodeWithText("두부의 점령지").assertIsDisplayed()
        compose.onNodeWithText("보리의 점령지").assertIsDisplayed()
        captureList()
        compose.onNodeWithTag("owned-pet-${a.petId}").performClick()
        compose.waitUntil(5_000) { a.petId in requests }
        compose.onNodeWithTag("owned-pet-${b.petId}").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("보리의 점령지").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { late.complete(page(a.petId)) }
        compose.onNodeWithTag("owned-list").assertIsDisplayed()
        compose.onNodeWithText("두부의 점령지").assertDoesNotExist()
        compose.onNodeWithText("조회 기준 1곳 보유").assertIsDisplayed()
        compose.runOnIdle { owner = null }
        compose.onNodeWithText("보리의 점령지").assertDoesNotExist()
        compose.onNodeWithText("로그인하기").assertIsDisplayed()
        assertEquals(listOf(null, a.petId, b.petId), requests)
    }

    private fun captureList() {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val output = java.io.File("build/reports/owned-territories/list.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
