package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 원 안에 얼굴을 맞추는 화면이 **손짓을 실제로 받는지.**
 *
 * [FaceFramerTest] 는 셈만 잡는다. 이 화면이 깨졌던 방식은 셈이 아니라 **배선**이었다 —
 * `pointerInput(Unit)` 안에서 `frame` 을 그냥 읽어 첫 조합 때의 값이 박제되는 바람에,
 * 제스처는 붙어 있는데 손을 떼면 제자리였다. 손짓이 **이전 값 위에 쌓이는지**는
 * 화면을 실제로 그려서 밀어 봐야 알 수 있어서 여기서 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FaceFrameStepTest {
    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun face(): Bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)

    @Test
    fun `핀치가 이어서 쌓인다`() {
        var frame by mutableStateOf(FaceFrame(1f, 0.5f, 0.5f))
        val bitmap = face()
        compose.setContent {
            FaceFrameStep(face = bitmap, frame = frame, onChange = { frame = it })
        }

        // 두 손가락을 80px 에서 400px 로 벌린다 = 5배. 쌓이면 한계(3.5)까지 간다.
        compose.onRoot().performTouchInput {
            pinch(
                start0 = center.copy(x = center.x - 40f),
                end0 = center.copy(x = center.x - 200f),
                start1 = center.copy(x = center.x + 40f),
                end1 = center.copy(x = center.x + 200f),
            )
        }
        compose.waitForIdle()

        // 박제된 값을 읽던 시절에는 한 이벤트만큼(1.0x 근처)에 머물렀다.
        assertTrue("핀치가 쌓여야 한다. 실제 배율 ${frame.scale}", frame.scale > 2f)
    }

    @Test
    fun `끌기를 두 번 하면 두 번 다 움직인다`() {
        var frame by mutableStateOf(FaceFrame(1f, 0.5f, 0.5f))
        val bitmap = face()
        compose.setContent {
            FaceFrameStep(face = bitmap, frame = frame, onChange = { frame = it })
        }

        compose.onRoot().performTouchInput {
            swipe(start = center, end = center.copy(x = center.x + 100f))
        }
        compose.waitForIdle()
        val afterFirst = frame.cx

        compose.onRoot().performTouchInput {
            swipe(start = center, end = center.copy(x = center.x + 100f))
        }
        compose.waitForIdle()

        assertTrue("첫 끌기가 자리를 옮겨야 한다. cx=$afterFirst", afterFirst > 0.5f)
        // 박제된 값을 읽으면 두 번째 끌기가 **처음 값에서 다시 계산**해서 같은 자리에 머문다.
        assertTrue(
            "두 번째 끌기가 그 위에 쌓여야 한다. 첫 번째 $afterFirst → 두 번째 ${frame.cx}",
            frame.cx > afterFirst + 0.01f,
        )
    }

    /**
     * **펼친 폴드에서 상자가 화면을 다 먹지 않는다.**
     *
     * 이 상자는 폭만 한 정사각형이라 폭이 넓어지면 키도 그만큼 커진다. 펼친 폴드
     * (`w673dp`)에서는 화면을 덮어서 아래 `이 얼굴로 뽑기` · `다시 자르기` 가 밀려났고,
     * 사용자가 **카드를 못 뽑았다.**
     *
     * ⚠️ 감싼 쪽에 세로 스크롤이 있어도 소용이 없다 — 이 상자가 크롭 손짓으로
     * 세로 끌기를 가져가기 때문이다. 그래서 폭을 묶는 것으로 막는다.
     */
    @Test
    @Config(sdk = [35], qualifiers = "w673dp-h841dp")
    fun `넓은 화면에서도 얼굴 상자가 화면 폭까지 커지지 않는다`() {
        val face = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        var frame by mutableStateOf(FaceFrame(1f, 0.5f, 0.5f))
        compose.setContent { FaceFrameStep(face = face, frame = frame, onChange = { frame = it }) }

        // 눈으로도 볼 수 있게 한 장 남긴다. 폴드가 없는 날 이게 유일한 증거다.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(
                view.width.coerceAtLeast(1), view.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            view.draw(android.graphics.Canvas(bitmap))
            val out = java.io.File("build/reports/card-draw/fold-face-frame.png")
            checkNotNull(out.parentFile).mkdirs()
            out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }

        val bounds = compose.onNodeWithTag(FACE_FRAME_TAG).getBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("상자 폭이 $width 다 — 상한을 넘었다", width <= 300.dp)
    }

}
