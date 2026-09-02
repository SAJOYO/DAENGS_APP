package com.daengs.app.ui.dex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * 폰 기울기를 포일 입력으로 바꾼다.
 *
 * 웹판(`assets/neo-hologram/main.js` 의 `feedOrientation`)이 하던 일을 그대로 옮겼다.
 * 그쪽 규칙 중 옮겨야 했던 것들.
 *
 *  - **절대 각도가 아니라 처음 자세에서 얼마나 움직였는지**를 쓴다. 폰을 눕혀 보든
 *    세워서 보든 처음 자세가 정면이 되므로, 들자마자 카드가 홱 돌아가지 않는다
 *  - [RANGE] 만큼 기울이면 카드가 끝까지 돈다
 *  - **손가락이 올라가 있으면 포인터가 이긴다.** 두 입력이 같은 카드를 두고 매
 *    프레임 싸우면 화면이 떤다
 */
class TiltTracker(
    /** 이 각도(도)만큼 기울이면 끝까지 돈다. */
    private val range: Float = RANGE_CARD,
) {
    var input by mutableStateOf<FoilInput?>(null)
        private set

    private var baseBeta = Float.NaN
    private var baseGamma = Float.NaN

    // 다듬은 값. 센서 눈금이 아니라 이쪽을 화면에 쓴다.
    private var smoothX = Float.NaN
    private var smoothY = Float.NaN

    /** @param beta 앞뒤, @param gamma 좌우. 둘 다 도 단위 (deviceorientation 규약) */
    fun feed(beta: Float, gamma: Float) {
        if (baseBeta.isNaN()) {
            baseBeta = beta
            baseGamma = gamma
        }
        val dx = gamma - baseGamma
        val dy = beta - baseBeta
        val rawX = (0.5f + dx / range / 2f).coerceIn(0f, 1f)
        val rawY = (0.5f + dy / range / 2f).coerceIn(0f, 1f)

        // **눈금을 그대로 쓰면 화면이 저 혼자 떠다닌다.** 폰을 책상에 놓아둔 채로도
        // 1초 사이에 주인공이 46px 밀리는 걸 쟀다. 회전벡터 센서는 가만히 있어도
        // 조금씩 흔들리고, [RANGE_CARD] 가 20도라 그 흔들림이 곧바로 픽셀이 된다.
        //
        // 그래서 지수 이동평균으로 눌러 준다. 손으로 기울이는 동작은 [SMOOTH] 의
        // 시정수(약 130ms)보다 훨씬 길어서 그대로 따라가고, 떨림만 깎인다.
        smoothX = if (smoothX.isNaN()) rawX else smoothX + (rawX - smoothX) * SMOOTH
        smoothY = if (smoothY.isNaN()) rawY else smoothY + (rawY - smoothY) * SMOOTH

        val p = Offset(smoothX, smoothY)
        // 가운데에 가까우면 포일도 잠잠하게. 가만히 든 손이 카드를 번쩍이게 하면
        // 눈이 피로하다.
        val away = maxOf(abs(p.x - 0.5f), abs(p.y - 0.5f)) * 2f
        input = FoilInput.of(p, away.coerceIn(0f, 1f))
    }

    /** 카드를 넘기거나 뷰를 닫을 때. 다음에 들 때 그 자세가 다시 정면이 된다. */
    fun reset() {
        baseBeta = Float.NaN
        baseGamma = Float.NaN
        smoothX = Float.NaN
        smoothY = Float.NaN
        input = null
    }

    companion object {
        /** 카드 한 장을 볼 때. 저쪽 `TILT_RANGE` 와 같은 값. */
        const val RANGE_CARD = 20f

        /**
         * 이머시브에서. **카드보다 둔해야 한다.**
         *
         * 카드는 손바닥만 한 것이 번쩍일 뿐이라 20도면 알맞지만, 이머시브는 화면
         * 전체가 움직인다. 같은 각도에 같은 비율로 움직이면 멀미가 난다.
         */
        const val RANGE_SCENE = 40f

        /**
         * 지수 이동평균 계수. 작을수록 둔하다.
         *
         * 0.12 면 60Hz 에서 시정수가 약 130ms 다 — 일부러 기울이는 동작은 그보다
         * 훨씬 느려서 그대로 따라가고, 손떨림만 깎인다.
         */
        const val SMOOTH = 0.12f
    }
}
