package com.daengs.app.ui.dogcard

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
    val compose = createComposeRule()

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
}
