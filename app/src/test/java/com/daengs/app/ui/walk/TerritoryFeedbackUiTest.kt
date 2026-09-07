package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.territory.*
import com.daengs.app.map.layers.territory.*
import com.daengs.app.territory.*
import com.daengs.app.ui.theme.DaengsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h844dp")
class TerritoryFeedbackUiTest {
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>(
        effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 1f })

    @Test fun `인증 대기와 거절은 성공 문구와 구별하고 완료만 확정으로 표시한다`() {
        val game = mutableStateOf(TerritoryGameState(photoStatus = ClaimPhotoStatus.PENDING))
        compose.setContent { DaengsTheme { TerritoryFeedbackLine(game.value) } }
        compose.onNodeWithText("사진 확인 중", substring = true).assertIsDisplayed()
        compose.onNodeWithText("사진 인증 완료").assertDoesNotExist()
        compose.runOnIdle { game.value = game.value.copy(photoStatus = ClaimPhotoStatus.RETRY_PENDING) }
        compose.onNodeWithText("사진 보관 중", substring = true).assertIsDisplayed()
        compose.runOnIdle { game.value = game.value.copy(photoStatus = ClaimPhotoStatus.REJECTED) }
        compose.onNodeWithText("인증되지 않았어요", substring = true).assertIsDisplayed()
        compose.runOnIdle { game.value = game.value.copy(photoStatus = ClaimPhotoStatus.VERIFIED) }
        compose.onNodeWithText("사진 인증 완료").assertIsDisplayed()
    }

    @Test fun `성공 문구는 짧게 사라지고 같은 이벤트 재구성으로 다시 시작하지 않는다`() {
        compose.mainClock.autoAdvance = false
        val game = mutableStateOf(TerritoryGameState(guidance = "다음 장소로 산책해요"))
        compose.setContent { DaengsTheme { TerritoryFeedbackLine(game.value, nowNanos = { 0L }) } }
        compose.runOnIdle { game.value = game.value.copy(feedback = TerritoryFeedback(1, "A", TerritoryFeedbackKind.MARKED, 0)) }
        // Android 레이아웃/재구성 프레임을 먼저 반영한 뒤 애니메이션 시계를 진행한다.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(150)
        compose.waitForIdle()
        compose.onNodeWithText("영역표시 완료 · 미인증").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText("다음 장소로 산책해요").assertIsDisplayed()
        compose.runOnIdle { game.value = game.value.copy(radiusMeters = 21.0) }
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText("영역표시 완료 · 미인증").assertDoesNotExist()
    }

    @Test fun `일시정지와 시작 전 카드는 효과 대신 점유 정보만 보여준다`() {
        val site = TerritorySite("A", GeoPoint(37.5, 127.0), 0.0)
        val game = mutableStateOf(TerritoryGameState(enabled = true, phase = TerritoryWalkPhase.PAUSED,
            sites = listOf(TerritoryGameSite(site, TerritoryClaimSite("A"), "", null, 5.0, false)), targetId = "A",
            feedback = TerritoryFeedback(1, "A", TerritoryFeedbackKind.MARKED, System.nanoTime())))
        compose.setContent { DaengsTheme { TerritoryActionCard(game.value, {}) } }
        compose.onNodeWithText("산책을 재개하면 영역표시할 수 있어요").assertIsDisplayed()
        compose.onNodeWithText("영역표시 완료 · 미인증").assertDoesNotExist()
        compose.runOnIdle { game.value = game.value.copy(phase = TerritoryWalkPhase.BROWSING) }
        compose.onNodeWithText("점령 연습 · 점유 정보").assertIsDisplayed()
    }

    @Test fun `실제 벡터와 효과 프레임을 검토용 이미지로 그린다`() {
        compose.setContent { DaengsTheme {
            Column(Modifier.padding(12.dp)) {
                TerritoryFeedbackKind.entries.forEach { kind ->
                    Text(kind.label)
                    Row { TerritoryFeedbackSample(kind, .0f); TerritoryFeedbackSample(kind, .5f) }
                }
            }
        } }
        compose.onAllNodesWithContentDescription("영역표시 성공 발자국").assertCountEquals(2)
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/territory-feedback/frames.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
