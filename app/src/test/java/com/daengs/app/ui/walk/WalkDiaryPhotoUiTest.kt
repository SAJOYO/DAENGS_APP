package com.daengs.app.ui.walk

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapPurpose
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.*
import java.io.File
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
class WalkDiaryPhotoUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val photo = WalkPhoto("p", "s", 2000, GeoPoint(37.5, 127.0), File("missing.jpg"))

    @Test fun `저장한 JPEG를 상세에서 읽어 사진을 표시한다`() {
        val file = File(compose.activity.cacheDir, "diary-display-test.jpg")
        val bitmap = android.graphics.Bitmap.createBitmap(180, 120, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(166, 195, 158))
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(232, 214, 185) }
        canvas.drawRect(90f, 0f, 180f, 120f, paint)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        try {
            compose.setContent { DaengsTheme { WalkPhotoDialog(photo.copy(file = file), {}, {}) } }
            compose.waitUntil(10000) {
                compose.onAllNodesWithContentDescription("산책 중 촬영한 사진").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithContentDescription("산책 중 촬영한 사진").assertIsDisplayed()
            compose.onNodeWithText("사진 파일을 읽을 수 없어요.").assertDoesNotExist()
        } finally { file.delete() }
    }

    @Test fun `사진은 점령지 레이어를 숨겨도 같은 위치에 남는다`() {
        for (purpose in listOf(MapPurpose.WALK, MapPurpose.TERRITORY)) {
            val scene = WalkUiState(map = WalkMapUiState(purpose = purpose), diaryPhotos = listOf(photo))
                .toMapPresentation().scene
            assertEquals("photo-p", scene.moments.single().id)
            assertEquals(photo.point, scene.moments.single().point)
            assertEquals(photo.file, scene.moments.single().photoFile)
            assertTrue(scene.territorySites.isEmpty())
        }
    }

    @Test fun `사진만 있는 기록 목록도 사진을 열 수 있다`() {
        var selected: WalkPhoto? = null
        compose.setContent { DaengsTheme {
            WalkEntryEditor(emptyList(), null, emptyList(), null, false, {}, {}, {},
                diaryPhotos = listOf(photo), onOpenPhoto = { selected = it })
        } }
        compose.onNodeWithText("아직 남긴 기록이 없어요.").assertDoesNotExist()
        compose.onNodeWithText("사진 ·", substring = true).performClick()
        assertEquals(photo, selected)
    }

    @Test fun `사진 삭제는 확인 뒤 실행하며 닫기는 사진을 지우지 않는다`() {
        var deleted: String? = null
        var closed = false
        compose.setContent { DaengsTheme { WalkPhotoDialog(photo, { deleted = it }, { closed = true }) } }
        compose.onNodeWithText("사진 삭제").performClick()
        assertNull(deleted)
        compose.onNodeWithText("취소").performClick()
        assertNull(deleted)
        compose.onNodeWithText("사진 삭제").performClick()
        compose.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("p", deleted)
        assertTrue(closed)
    }
}
