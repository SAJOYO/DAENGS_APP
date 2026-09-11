package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.features.territory.*
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.territory.*
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class WalkTerritoryUiTest {
    @Test fun firstSeasonRenewalButtonsShowZeroRewardAndLease() {
        val base = screen(TerritoryWalkPhase.WALKING).territoryGame
        val occupancy = TerritoryOccupancy("p1", null, null, ClaimCertification.UNVERIFIED, 1000)
        val target = base.target!!.copy(ownerLabel = "보리", isOwnedByMe = true,
            claim = base.target!!.claim.copy(occupancy = occupancy), leaseLabel = "점령 유지 · 2일 3시간 남음")
        val game = mutableStateOf(base.copy(sites = listOf(target), canMark = true, canPhotograph = false,
            onlinePhotos = true, actionLabel = "유지 연장 · 0점", guidance = "현장에서 유지 시간을 연장할 수 있어요 · 연장 보상 0점"))
        var selected: String? = null
        compose.setContent { DaengsTheme { TerritoryActionCard(game.value, { selected = it }) } }
        compose.onNodeWithText("보리 · 미인증").assertIsDisplayed()
        compose.onNodeWithText("미점유").assertDoesNotExist()
        compose.onNodeWithText("점령 유지 · 2일 3시간 남음").assertIsDisplayed()
        compose.onNodeWithText("유지 연장 · 0점").assertIsEnabled().performClick()
        assertEquals(target.site.id, selected)
        screenshot("first-season-gps-renewal")
        compose.runOnIdle { game.value = game.value.copy(canMark = false, canPhotograph = true,
            sites = listOf(target.copy(claim = target.claim.copy(occupancy = occupancy.copy(certification = ClaimCertification.VERIFIED)))),
            photoActionLabel = "사진으로 유지 연장", guidance = "새 사진으로 유지 시간을 연장할 수 있어요 · 연장 보상 0점") }
        compose.onNodeWithText("보리 · 인증").assertIsDisplayed()
        compose.onNodeWithText("사진으로 유지 연장").assertIsEnabled()
        compose.onNodeWithText("유지 연장 · 0점").assertDoesNotExist()
        screenshot("first-season-photo-renewal")
    }

    @Test fun onlineCameraExplainsRealPhotoAndHidesSimulationControls() {
        compose.setContent { DaengsTheme { TerritoryCaptureDialog("internal-site-id", { null },
            { _, _, _ -> kotlinx.coroutines.CompletableDeferred(false) }, {}, online = true) } }
        compose.onNodeWithText("현재 위치에서 강아지가 잘 보이게 찍어 주세요").assertIsDisplayed()
        compose.onNodeWithText("위치는 GPS로, 강아지 여부는 사진으로 확인해요 · 인증 범위 10m").assertIsDisplayed()
        compose.onNodeWithText("테스트 판정 선택").assertDoesNotExist()
        compose.onNodeWithText("internal-site-id", substring = true).assertDoesNotExist()
    }

    @Test fun finalServerPhotoFailureOffersNewCaptureOnTheSiteCard() {
        val base = screen(TerritoryWalkPhase.WALKING)
        val state = base.copy(territoryGame = base.territoryGame.copy(canMark = false, canPhotograph = true,
            petLocked = true, photoStatus = ClaimPhotoStatus.RETRY_PENDING, onlinePhotos = true,
            guidance = "사진 판정을 마치지 못했어요 · 현장에서 새 사진을 찍어 주세요"))
        compose.setContent { DaengsTheme { WalkScreen(state, {}, showMap = false) } }
        compose.onNodeWithText("다시 촬영").assertIsEnabled()
        compose.onNodeWithText("사진 판정을 마치지 못했어요 · 현장에서 새 사진을 찍어 주세요").assertIsDisplayed()
        compose.onNodeWithText("판정 재시도 (페이크 성공)").assertDoesNotExist()
        compose.onNodeWithContentDescription("산책 사진 촬영").assertIsEnabled()
        screenshot("server-photo-recapture")
    }

    @Test fun verifiedPhotoWithSiteConflictKeepsConflictGuidance() {
        val game = TerritoryGameState(onlinePhotos = true, photoStatus = ClaimPhotoStatus.VERIFIED,
            guidance = "점유가 바뀌었어요 · 새 산책에서 다시 방문해 주세요")
        assertEquals(game.guidance, territoryFeedbackLabel(game, null))
    }

    @Test fun pendingServerMarkLocksDogAndKeepsDiaryCameraSeparate() {
        val base = screen(TerritoryWalkPhase.WALKING)
        val pending = base.territoryGame.copy(canMark = false, canPhotograph = false, petLocked = true,
            guidance = "영역표시 확인 중 · 산책을 계속해도 돼요")
        val state = mutableStateOf(base.copy(territoryGame = pending))
        compose.setContent { DaengsTheme { WalkScreen(state.value, {}, showMap = false) } }
        compose.onNodeWithText("영역표시 확인 중 · 산책을 계속해도 돼요").assertIsDisplayed()
        compose.onNodeWithText("보리").assertIsNotEnabled()
        compose.onNodeWithContentDescription("산책 사진 촬영").assertIsEnabled()
        compose.onNodeWithContentDescription("영역표시 인증 촬영").assertDoesNotExist()
        screenshot("server-mark-pending")
        compose.runOnIdle {
            val target = pending.target!!
            state.value = base.copy(territoryGame = pending.copy(
                sites = listOf(target.copy(ownerLabel = "보리", claim = target.claim.copy(version = 1,
                    occupancy = TerritoryOccupancy("p1", null, null, ClaimCertification.UNVERIFIED, 1000)))),
                guidance = "영역표시가 접수됐어요 · 현재 점유는 지도에서 확인해요"))
        }
        compose.onNodeWithText("보리 · 미인증").assertIsDisplayed()
        compose.onNodeWithText("영역표시").assertDoesNotExist()
        screenshot("server-mark-confirmed")
    }

    @Test fun sharedOccupancyShowsOtherDogWithoutGameActionsAndFailureIsNotNeutral() {
        val session = com.daengs.app.auth.Session("user", "token", "refresh", Long.MAX_VALUE, Long.MAX_VALUE)
        var failing = false
        val provider = ServerTerritoryGameProvider(TerritoryOccupancyClient { _, _ ->
            if (failing) throw java.io.IOException("offline")
            listOf(SharedTerritorySite("A", 1,
                SharedTerritoryOccupancy("dog", "두부", false, ClaimCertification.VERIFIED, 1000)))
        }, { session }, { "user" })
        val base = screen(TerritoryWalkPhase.BROWSING)
        fun snapshot() = base.copy(territoryGame = provider.snapshot(base.territory,
            base.tracking, true, emptyMap(), 0))
        val state = mutableStateOf(snapshot())
        compose.setContent { DaengsTheme { WalkScreen(state.value, {}, showMap = false) } }
        compose.onNodeWithText("점유 확인 전").assertExists()
        compose.onNodeWithText("미점유").assertDoesNotExist()
        kotlinx.coroutines.runBlocking { provider.refresh(listOf(site)) }
        compose.runOnIdle { state.value = snapshot() }
        compose.onNodeWithText("두부 · 인증").assertIsDisplayed()
        compose.onNodeWithText("점령 시각 · ${territoryOccupiedAtLabel(1000)}").assertIsDisplayed()
        compose.onNodeWithText("영역표시할 강아지").assertDoesNotExist()
        compose.onNodeWithText("점령 연습 · 점유 정보").assertDoesNotExist()
        screenshot("server-browsing")
        compose.runOnIdle {
            state.value = screen(TerritoryWalkPhase.WALKING).copy(territoryGame = state.value.territoryGame.copy(phase = TerritoryWalkPhase.WALKING))
        }
        compose.onNodeWithContentDescription("영역표시 인증 촬영").assertDoesNotExist()
        compose.onNodeWithText("영역표시할 강아지").assertDoesNotExist()
        compose.onNodeWithContentDescription("산책 사진 촬영").assertIsEnabled()
        failing = true
        kotlinx.coroutines.runBlocking { provider.refresh(listOf(site)) }
        compose.runOnIdle { state.value = snapshot() }
        compose.onNodeWithText("점유 조회 실패").assertIsDisplayed()
        compose.onNodeWithText("미점유").assertDoesNotExist()
        compose.onNodeWithText("두부 · 인증").assertDoesNotExist()
        compose.onNodeWithText("점령 시각", substring = true).assertDoesNotExist()
        compose.onNodeWithText("점유 정보를 불러오지 못했어요 · 잠시 후 다시 확인해요").assertIsDisplayed()
        screenshot("server-error")
    }

    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val site = TerritorySite("A", GeoPoint(37.5,127.0),0.0)
    private fun game(phase: TerritoryWalkPhase) = TerritoryGameState(
        enabled = true, phase = phase, targetId = "A", representativeLabel = "보리",
        guidance = "점령 준비 · 영역표시할 수 있어요",
        claimingPetId = "p1", eligiblePets = linkedMapOf("p1" to "보리", "p2" to "두부"),
        canMark = phase == TerritoryWalkPhase.WALKING, canPhotograph = phase == TerritoryWalkPhase.WALKING,
        sites = listOf(TerritoryGameSite(site, TerritoryClaimSite("A"), "", null, 5.0,false)),
    )
    private fun screen(phase: TerritoryWalkPhase) = WalkUiState(
        map = WalkMapUiState(purpose = MapPurpose.TERRITORY),
        location = WalkLocationUiState(permissionGranted = true, precisePermission = true),
        territory = TerritoryBoardState(sites = listOf(site), selectedSiteId = "A"), territoryGame = game(phase),
        tracking = WalkTrackingState(activeSessionId = if (phase == TerritoryWalkPhase.BROWSING) null else "walk",
            activeDogIds = listOf("p1", "p2"), trail = TrailSnapshot(state = when (phase) {
                TerritoryWalkPhase.BROWSING -> TrackingState.OFF
                TerritoryWalkPhase.WALKING -> TrackingState.RECORDING
                TerritoryWalkPhase.PAUSED -> TrackingState.PAUSED
            })),
    )

    private fun screenshot(name: String) {
        val output = java.io.File("build/reports/walk-territory/$name.png")
        checkNotNull(output.parentFile).mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun browsingThenWalkingThenPausedOnlyExposesEligibleActions() {
        val state = mutableStateOf(screen(TerritoryWalkPhase.BROWSING))
        val actions = mutableListOf<WalkAction>()
        compose.setContent { DaengsTheme { WalkScreen(state.value, actions::add, showMap = false) } }
        compose.onNodeWithText("미점유").assertExists()
        compose.onNodeWithText("영역표시할 강아지").assertDoesNotExist()
        compose.onNodeWithContentDescription("영역표시 인증 촬영").assertDoesNotExist()
        compose.onNodeWithContentDescription("산책 사진 촬영").assertDoesNotExist()
        compose.onNodeWithText("산책 시작").assertExists()
        screenshot("browsing")
        compose.runOnIdle { state.value = screen(TerritoryWalkPhase.WALKING) }
        compose.onNodeWithText("영역표시할 강아지").assertExists()
        compose.onNodeWithContentDescription("산책 사진 촬영").assertIsEnabled().performClick()
        assertEquals(WalkAction.PhotographWalk, actions.last())
        compose.onNodeWithContentDescription("영역표시 인증 촬영").performClick()
        assertEquals(WalkAction.PhotographTerritory("A"), actions.last())
        screenshot("walking")
        compose.onNodeWithText("보리").performClick()
        compose.onNodeWithText("두부").performClick()
        assertEquals(WalkAction.SelectClaimingPet("A","p2"), actions.last())
        // 걷는 중 도크의 `일기` 는 **이 산책에 남긴 것**을 연다. 목록이 아니다 —
        // 걷고 있는 산책은 끝나야 목록에 들어간다.
        compose.onNodeWithContentDescription("이 산책에 남긴 것").performClick()
        assertEquals(WalkAction.OpenEntries, actions.last())
        compose.runOnIdle { state.value = screen(TerritoryWalkPhase.PAUSED) }
        compose.onNodeWithText("지도 둘러보기").performClick()
        compose.onNodeWithText("산책을 재개하면 영역표시할 수 있어요").assertIsDisplayed()
        compose.onNodeWithText("영역표시할 강아지").assertDoesNotExist()
        compose.onNodeWithContentDescription("산책 사진 촬영").assertIsNotEnabled()
        screenshot("paused")
        compose.onNodeWithContentDescription("산책 재개 메뉴").performClick()
        compose.onNodeWithText("지도 둘러보기").assertIsDisplayed()
    }

    @Test fun compactPhotoSummaryOpensExistingRetry() {
        var retried: String? = null
        compose.setContent { DaengsTheme { TerritoryPhotoStatus(listOf(
            TerritoryPhotoJob("attempt", "A", "capture", java.io.File("sample.jpg"), ClaimPhotoStatus.RETRY_PENDING)
        ), { retried = it }) } }
        compose.onNodeWithText("판정 재시도 (페이크 성공)").assertDoesNotExist()
        compose.onNodeWithText("사진 인증 연습 · 결과 보기 ›").performClick()
        compose.onNodeWithText("판정 재시도 (페이크 성공)").performClick()
        assertEquals("attempt", retried)
    }

    @Test fun closingSelectionAndTogglingDisplayHaveSeparateActions() {
        val actions = mutableListOf<WalkAction>()
        compose.setContent { DaengsTheme { WalkScreen(screen(TerritoryWalkPhase.BROWSING), actions::add, showMap = false) } }
        compose.onNodeWithContentDescription("점령지 선택 닫기").performClick()
        assertEquals(WalkAction.ClearTerritory, actions.last())
        compose.onNodeWithContentDescription("점령지 숨기기").performClick()
        assertEquals(WalkAction.ChangeMapPurpose(MapPurpose.WALK), actions.last())
        compose.onNodeWithContentDescription("GPS 불안정").performClick()
        compose.onNodeWithText("정확한 새 위치를 기다리고 있어요").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h844dp")
    fun narrowScreenKeepsClaimAndWalkControlsSeparate() {
        compose.setContent { DaengsTheme { WalkScreen(screen(TerritoryWalkPhase.WALKING), {}, showMap = false) } }
        val claim = compose.onNodeWithText("영역표시").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithContentDescription("행동 기록").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(claim.bottom <= dock.top)
        compose.onNodeWithContentDescription("GPS 불안정").assertIsDisplayed()
        screenshot("narrow")
        compose.onNodeWithContentDescription("영역표시 인증 촬영").assertIsDisplayed()
        compose.onNodeWithContentDescription("내 위치").assertIsDisplayed()
        compose.onNodeWithContentDescription("산책 사진 촬영").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w844dp-h390dp")
    fun landscapeSeparatesCardFromDock() {
        compose.setContent { DaengsTheme { WalkScreen(screen(TerritoryWalkPhase.WALKING), {}, showMap = false) } }
        val card = compose.onNodeWithText("영역표시").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithContentDescription("행동 기록").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(card.right <= dock.left)
        screenshot("landscape")
    }
    @Test fun freshScreenFixShowsGoodGpsBeforeWalkStarts() {
        compose.setContent {
            val sample = androidx.compose.runtime.remember {
                com.daengs.app.location.LocationSample(GeoPoint(37.5,127.0), System.currentTimeMillis(),
                    android.os.SystemClock.elapsedRealtimeNanos(), 3f)
            }
            val state = screen(TerritoryWalkPhase.BROWSING).copy(location = WalkLocationUiState(
                permissionGranted = true, precisePermission = true, currentPosition = sample.point,
                sample = sample, owner = WalkLocationOwner.SCREEN))
            DaengsTheme { WalkScreen(state, {}, showMap = false) }
        }
        compose.onNodeWithContentDescription("GPS 양호").assertIsDisplayed().performClick()
        compose.onNodeWithText("현재 위치를 확인했어요").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("산책 시작").assertIsDisplayed()
        screenshot("browsing-good-gps")
    }


    /**
     * 산책 전 `산책 기록`은 **목록**으로 나간다.
     *
     * 전에는 이 버튼이 걷는 산책에 묶인 편집기를 열어서, 걷기 전에는 묶일 산책이
     * 없어 늘 "아직 남긴 기록이 없어요" 만 떴다. 실기기에서 DB 에 산책이 두 건
     * 있는데도 비어 있는 것을 봤다.
     */
    @Test fun `산책 전 기록 버튼은 편집기가 아니라 목록으로 간다`() {
        val actions = mutableListOf<WalkAction>()
        val state = mutableStateOf(screen(TerritoryWalkPhase.BROWSING))
        compose.setContent { DaengsTheme { WalkScreen(state.value, actions::add, showMap = false) } }

        compose.onNodeWithText("산책 기록").assertIsDisplayed()
        compose.onNodeWithContentDescription("산책 기록").performClick()

        assertEquals(WalkAction.OpenDiaryList, actions.last())
    }

}
