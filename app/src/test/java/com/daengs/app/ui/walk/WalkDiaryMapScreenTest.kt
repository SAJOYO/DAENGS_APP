package com.daengs.app.ui.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.daengs.app.location.GeoPoint
import com.daengs.app.walk.*
import com.daengs.app.walk.diary.DiaryScene
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkDiaryMapScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `one inline editor replaces the complete scene while its source record stays internal`() {
        val scene = DiaryScene("s/n", "s", 0, "자동 제목", "자동 배경. 직접 쓴 원본", null, "",
            content = com.daengs.app.walk.diary.DiarySceneContent("직접 쓴 원본", "note", locationLabel = ""))
        var saved: Pair<String, String>? = null
        // Same workaround as WalkEntryEditorTest: Robolectric cannot idle a dialog with text fields.
        compose.setContent { DiarySceneEditor(scene, false, null, { a, b -> saved = a to b }, {},
            dialog = { title, body, confirm, dismiss -> Column { title(); body(); confirm(); dismiss() } }) }
        compose.onNodeWithText("장면 제목").performTextReplacement("내 제목")
        compose.onNodeWithText("자동 배경. 직접 쓴 원본").assertExists()
        compose.onNodeWithText("장면 내용").performTextReplacement("내가 다시 쓴 장면 전체")
        compose.onNodeWithText("장면 배경").assertDoesNotExist()
        compose.onNodeWithText("직접 남긴 기록").assertDoesNotExist()
        compose.onNodeWithText("직접 쓴 원본").assertDoesNotExist()
        compose.onNodeWithText("저장").performClick()
        assertEquals("내 제목" to "내가 다시 쓴 장면 전체", saved)
        assertEquals("직접 쓴 원본", scene.content!!.recordText)
    }

    @Test fun `generation state prevents repeated taps and shows source notice`() {
        var calls = 0
        compose.setContent {
            var busy by remember { mutableStateOf(false) }
            WalkDiaryMapContent(emptyList(), null, false, null, {}, {}, {}, {}, {}, {},
                map = { Box(Modifier.fillMaxSize()) }, generationNotice = "남긴 기록을 모았어요.",
                generating = busy, onGenerate = { calls++; busy = true })
        }
        compose.onNodeWithText("일기 생성·갱신").assertDoesNotExist()
        compose.onNodeWithContentDescription("일기 메뉴").performClick()
        compose.onNodeWithText("일기 생성·갱신").performClick()
        compose.onNodeWithContentDescription("일기 메뉴").performClick()
        compose.onNodeWithText("준비 중").assertIsNotEnabled()
        compose.onNodeWithText("남긴 기록을 모았어요.").assertExists()
        assertEquals(1, calls)
    }

    @Test fun `reading raises the sheet without recreating the map and dragging it down returns to browsing`() {
        val scene = DiaryScene("s/n", "s", 0, "첫 장면", "공원 옆이었다. 직접 남긴 메모", GeoPoint(37.5, 127.0), "",
            content = com.daengs.app.walk.diary.DiarySceneContent("직접 남긴 메모", "note", locationLabel = "기록한 위치"))
        var mounts = 0
        var inset = 0
        compose.setContent {
            var selected by remember { mutableStateOf<DiaryScene?>(null) }
            WalkDiaryMapContent(listOf(scene), selected, false, null, { selected = it }, { selected = null },
                {}, {}, {}, {}, map = { padding ->
                    DisposableEffect(Unit) { mounts++; onDispose {} }
                    SideEffect { inset = padding }
                    Box(Modifier.fillMaxSize().testTag("diary-map"))
                })
        }
        val peekTop = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        val peekInset = inset
        compose.onNodeWithText("첫 장면").performClick()
        compose.onNodeWithText("공원 옆이었다. 직접 남긴 메모").assertIsDisplayed()
        compose.onNodeWithText("직접 남긴 기록").assertDoesNotExist()
        compose.onNodeWithText("기록한 위치").assertDoesNotExist()
        compose.onNodeWithContentDescription("원본 기록 수정").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("장면 수정").assertCountEquals(1)
        val readingTop = compose.onNodeWithTag("diary-sheet").fetchSemanticsNode().boundsInRoot.top
        assertTrue(readingTop < peekTop - 100)
        assertTrue(readingTop > 100)
        assertTrue(inset > peekInset)
        assertEquals(1, mounts)
        compose.onNodeWithTag("diary-sheet-handle").performTouchInput { swipeDown(startY = 10f, endY = 500f) }
        compose.onNodeWithText("1개 장면 · 시간순").assertIsDisplayed()
        assertEquals(1, mounts)
    }

    @Test fun `next scene starts at its title after reading a long previous scene`() {
        val first = DiaryScene("s/a", "s", 0, "첫 장면", "긴 기록. ".repeat(200), null, "")
        val second = first.copy(id = "s/b", title = "다음 장면", body = "짧은 메모")
        compose.setContent {
            var selected by remember { mutableStateOf<DiaryScene?>(first) }
            WalkDiaryMapContent(listOf(first, second), selected, false, null, { selected = it },
                { selected = null }, {}, {}, {}, {}, map = { Box(Modifier.fillMaxSize()) })
        }
        compose.onNodeWithTag("diary-scene-body").performTouchInput { swipeUp() }
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("다음 장면").assertIsDisplayed()
        compose.onNodeWithText("짧은 메모").assertIsDisplayed()
    }

    @Test fun `scene navigation retains order and edits the source entry`() {
        val a = DiaryScene("s/a", "s", 0, "첫 메모", "내용", null, "직접 남긴 기록", entryId = "a")
        val b = a.copy(id = "s/b", atMillis = 10000, title = "다음 메모", entryId = "b")
        var edited: String? = null
        compose.setContent {
            var selected by remember { mutableStateOf<DiaryScene?>(null) }
            WalkDiaryMapContent(listOf(a,b), selected, false, null, { selected = it }, { selected = null },
                { edited = it.entryId }, {}, {}, {}, map = { Box(Modifier.fillMaxSize().testTag("diary-map")) })
        }
        compose.onNodeWithText("첫 메모").performClick()
        compose.onNodeWithText("이전").assertIsNotEnabled()
        compose.onNodeWithText("장면 1").assertExists()
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("장면 2").assertExists()
        compose.onNodeWithText("스토리보드 검토").assertDoesNotExist()
        compose.onNodeWithContentDescription("장면 수정").performClick()
        assertEquals("b", edited)
        compose.onNodeWithText("‹ 장면 목록").performClick()
        compose.onNodeWithText("2개 장면 · 시간순").assertExists()
    }

    @Test fun `page buttons open a session without a map tab`() {
        val walk = previewDiarySummary()
        var next = false; var opened: String? = null
        compose.setContent { WalkHistoryPageContent(listOf(walk), 2, true, true, {}, { next = true }, { opened = it }, modifier = Modifier.fillMaxSize()) }
        compose.onNodeWithText("지도").assertDoesNotExist()
        compose.onNodeWithContentDescription("산책 동선, 출발·도착 같은 자리").assertExists()
        compose.onNodeWithText("${formatWalkDay(walk.startedAtMillis)} 산책").performClick()
        assertEquals(walk.sessionId, opened)
        compose.onNodeWithText("다음 ›").performClick(); assertTrue(next)
    }

    @Test fun `saved title is shown with date and returns to date title when removed`() {
        val walk = previewDiarySummary()
        val titles = mutableStateOf(mapOf(walk.sessionId to "벤치 옆에서 남긴 기록"))
        compose.setContent { WalkHistoryPageContent(listOf(walk), 1, false, false, {}, {}, {},
            modifier = Modifier.fillMaxSize(), titles = titles.value) }
        compose.onNodeWithText("벤치 옆에서 남긴 기록").assertExists()
        compose.onNodeWithText(formatWalkDay(walk.startedAtMillis)).assertExists()
        compose.runOnIdle { titles.value = emptyMap() }
        compose.onNodeWithText("벤치 옆에서 남긴 기록").assertDoesNotExist()
        compose.onNodeWithText("${formatWalkDay(walk.startedAtMillis)} 산책").assertExists()
    }

    @Test fun `map and navigation remain visible with a long card on a small screen`() {
        var rendered: android.view.View? = null
        val scene = DiaryScene("s/n", "s", 0, "벤치 옆에서 남긴 메모", "함께 걸었던 하루. ".repeat(100), null, "사용자 기록", entryId = "n")
        compose.setContent {
            val view = androidx.compose.ui.platform.LocalView.current
            SideEffect { rendered = view.rootView }
            WalkDiaryMapContent(listOf(scene), scene, false, null, {}, {}, {}, {}, {}, {},
                map = { Box(Modifier.fillMaxSize().background(Color(0xFFE1EBDE)).testTag("diary-map")) })
        }
        compose.onNodeWithTag("diary-map").assertIsDisplayed()
        compose.onNodeWithText("다음").assertIsDisplayed()
        compose.onNodeWithText("‹ 장면 목록").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("diary-map").fetchSemanticsNode().boundsInRoot.height >= 120)
        val file = java.io.File("build/outputs/walk-diary-map-card.png"); file.parentFile.mkdirs()
        compose.runOnIdle {
            val view = requireNotNull(rendered)
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image)); file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }

    @Test fun `pin labels are scene ordinals even when some scenes lack positions`() {
        val a = DiaryScene("s/a","s",0,"a","",null,"")
        val b = a.copy(id="s/b",point=GeoPoint(37.5,127.0))
        val c = b.copy(id="s/c")
        val markers = diarySceneMarkers(listOf(a,b,c), c.id)
        assertEquals("2 · 3",markers.single().label)
        assertEquals(c.id,markers.single().id)
        assertTrue(markers.single().selected)
        assertTrue(markers.single().aboveRouteEndpoints)
        assertEquals("2 · 3", markers.single().sequenceLabel)
    }

    @Test fun `large same-position group keeps complete order and includes the selected ordinal in its badge`() {
        val scenes = (1..20).map { DiaryScene("s/$it", "s", it.toLong(), "장면", "", GeoPoint(37.5, 127.0), "") }
        val marker = diarySceneMarkers(scenes, "s/17").single()
        assertEquals((1..20).joinToString(" · "), marker.label)
        assertEquals("1 · 2 · 17 …", marker.sequenceLabel)
        assertEquals(scenes.first().point, marker.point)
    }

    @Test fun `diary removes only a coincident stay picture and preserves original endpoints and paths`() {
        val point = GeoPoint(37.5, 127.0)
        val far = GeoPoint(37.5002, 127.0)
        val route = com.daengs.app.map.layers.completedroute.CompletedRouteLayerState(paths = listOf(listOf(point, far)),
            start = com.daengs.app.map.layers.completedroute.RouteEndpointMarkerState("start", point, "출발",
                com.daengs.app.map.layers.completedroute.RouteEndpointKind.START))
        val source = com.daengs.app.map.shell.MapScene(completedRoute = route,
            moments = listOf(com.daengs.app.map.layers.moments.MomentMarkerState("n", point, "1")),
            stayStamps = listOf(com.daengs.app.map.layers.stays.StayStampMarkerState(1, point),
                com.daengs.app.map.layers.stays.StayStampMarkerState(2, far)))
        val rendered = diaryDisplayScene(source)
        assertEquals(listOf(2), rendered.stayStamps.map { it.id })
        assertEquals(2, source.stayStamps.size)
        assertEquals(route.paths, rendered.completedRoute.paths)
        assertEquals(point, rendered.completedRoute.start!!.point)
        assertTrue(rendered.completedRoute.start!!.compact)
        assertFalse(source.completedRoute.start!!.compact)
    }

    @Test fun `server GPS anchors select numbered cards and gap navigation clears marker selection`() {
        val (bundle, fixes) = com.daengs.app.walk.diary.sceneAnchorFixture()
        val walk = WalkSummary(bundle.sessionId, emptyList(), fixes.first().atMillis,
            fixes.last().atMillis, null, 0.0, 0, emptyList(), null)
        val scenes = com.daengs.app.walk.diary.diaryWalk(walk, emptyList(), emptyList(),
            com.daengs.app.walk.diary.StoryboardDraft(),
            com.daengs.app.walk.diary.StoryboardAnalysisView(bundle, true, ""), fixes).scenes
        var selected: DiaryScene? by mutableStateOf(null)
        compose.setContent {
            WalkDiaryMapContent(scenes, selected, false, null, { selected = it }, { selected = null },
                {}, {}, {}, {}, map = {
                    Row { diarySceneMarkers(scenes, selected?.id).forEach { marker ->
                        androidx.compose.material3.TextButton(onClick = { selected = scenes.first { it.id == marker.id } }) {
                            androidx.compose.material3.Text(marker.label)
                        }
                    } }
                })
        }
        compose.onNodeWithText("1 · 7").performClick()
        compose.onNodeWithText("장면 1").assertExists()
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("장면 2").assertExists()
        compose.runOnIdle { assertTrue(diarySceneMarkers(scenes, selected?.id).any { it.selected && it.label == "2" }) }
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("장면 3").assertExists()
        compose.runOnIdle {
            assertNull(selected?.point)
            assertTrue(diarySceneMarkers(scenes, selected?.id).none { it.selected })
        }
    }

    @Test fun `new action uses the selected historical observation not current time or location`() {
        val point = previewDiarySummary().toSessionRoute().points[1]
        val entry = point.toDiaryEntry("s", WalkMomentType.SNIFFING, "dog")
        assertEquals(point.point, entry.point)
        assertEquals(point.capturedAtMillis, entry.recordedAtMillis)
        assertEquals(point.capturedAtMillis, entry.locationCapturedAtMillis)
        assertEquals("dog", entry.petId)
        entry.validate()
    }
}
