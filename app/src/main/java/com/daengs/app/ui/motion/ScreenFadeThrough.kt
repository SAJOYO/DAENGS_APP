package com.daengs.app.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.daengs.app.miniroom.IntroTimeline

/**
 * 화면 전환 모션의 길이와 세기.
 *
 * ## 왜 fade-through 인가
 *
 * 이 앱에는 **내비게이션 라이브러리가 없다.** `MainActivity` 의 `when (screen)` 하나가
 * 화면 열여덟 개를 갈아 끼우고, 홈 안의 탭도 분기 하나로 갈린다. 그래서 전환 애니메이션이
 * 들어갈 자리가 아예 없었고, 화면이 "팍" 바뀌었다.
 *
 * 그 자리에 **밀어 넣는 슬라이드가 아니라 겹쳐 지나가는 fade 를 쓴다.** 이유가 둘이다.
 *
 * 1. **화면 열다섯 개가 지도·카메라 같은 `AndroidView` 를 안고 있다** (`ui/places` ·
 *    `ui/walk` · `ui/game/owned` · `ui/gait` · `ui/camera`). 슬라이드는 전환이 끝날 때까지
 *    두 화면이 다 살아 있어야 하는데, 지도 두 개를 동시에 띄울 이유가 없다. fade-through 는
 *    나가는 화면을 [EXIT_MS] 만에 놓는다.
 * 2. **방향 개념이 필요 없다.** 슬라이드로 하려면 "앞으로 가나 뒤로 가나" 를 알아야 하는데
 *    이 앱은 백스택이 없고 `BackHandler` 가 아흔세 곳에 흩어져 있다. 그 목적지와 방향 표가
 *    어긋나면 **뒤로가기가 앞으로 가는 것처럼 보인다.** fade 는 그 문제가 없다.
 *
 * 나중에 가벼운 화면에만 방향 있는 슬라이드를 얹는 것은 이 위에 쌓을 수 있다.
 */
object ScreenMotion {
    /** 나가는 화면이 사라지는 데 걸리는 시간. */
    const val EXIT_MS = 90

    /** 들어오는 화면이 떠오르는 데 걸리는 시간. [EXIT_MS] 뒤에 시작한다. */
    const val ENTER_MS = 210

    /**
     * 들어오는 화면이 시작하는 크기. 1 이면 순수 fade 다.
     *
     * 0.92 는 "조금 다가온다" 정도다. 더 작게 잡으면 화면 전체가 확대되는 느낌이 나서
     * 지도가 든 화면에서 어지럽다.
     */
    const val ENTER_SCALE = 0.92f

    /**
     * 전환 전체 길이.
     *
     * **[IntroTimeline.VEIL_END_MS] 와 같은 값이다** — 로딩에서 홈으로 넘어올 때 크림 막을
     * 걷는 시간이다. 앱이 한 박자로 움직이게 하려고 맞췄다. 그 값이 바뀌면 여기도 같이
     * 봐야 한다.
     */
    const val TOTAL_MS = EXIT_MS + ENTER_MS

    /** 칸 크기가 바뀔 때 (인벤토리를 열어 방이 물러나는 것 등). 전환보다 짧다. */
    const val SLOT_MS = 220

    init {
        // 두 값이 갈라지면 로딩 막과 화면 전환이 다른 박자로 움직인다.
        require(TOTAL_MS.toLong() == IntroTimeline.VEIL_END_MS) {
            "전환 $TOTAL_MS ms 가 크림 막 ${IntroTimeline.VEIL_END_MS} ms 와 다르다"
        }
    }
}

/**
 * 이 전환에 모션을 넣나.
 *
 * **로딩 화면이 끼어 있으면 넣지 않는다.** 홈은 로딩에서 넘어올 때 이미 **크림 막**으로
 * 이어진다 — 로딩의 마지막 프레임과 같은 그림으로 시작해 걷어 내는 방식이다
 * (`ui/home/HomeIntroVeil.kt`). 거기에 fade 를 더 얹으면 같은 구간을 두 번 건너는 셈이고,
 * 막이 걷히는 동안 그 밑의 방이 한 번 더 흐려진다. 막이 할 일을 막이 하게 둔다.
 *
 * 순수 함수라 테스트로 잡는다 — 모션은 눈으로 봐야 하지만 **어디에 모션을 넣지 않는가**는
 * 숫자로 잡을 수 있다.
 */
fun screenTransitionAnimates(fromLoading: Boolean, toLoading: Boolean): Boolean =
    !fromLoading && !toLoading

/**
 * 화면 하나를 다른 화면으로 바꿀 때 fade-through 로 잇는다.
 *
 * **레이아웃을 다시 재지 않는다.** alpha 와 scale 은 `graphicsLayer` 단계라 측정·배치가
 * 다시 돌지 않는다 — 지도가 든 화면에 이걸 고른 이유다.
 *
 * @param target 지금 보여야 하는 것. 바뀌면 전환이 돈다
 * @param animates 두 상태 사이에 모션을 넣을지. 기본은 늘 넣는다
 */
@Composable
fun <T> ScreenFadeThrough(
    target: T,
    modifier: Modifier = Modifier,
    animates: (from: T, to: T) -> Boolean = { _, _ -> true },
    label: String = "화면 전환",
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            // ⚠️ `SizeTransform(clip = false)` 을 쓴다. 기본값은 잘라내기라, 크기가 다른
            // 화면 사이를 지날 때 나가는 화면이 새 화면 크기로 잘려서 한쪽이 베어 보인다.
            if (animates(initialState, targetState)) {
                val enter = tween<Float>(
                    durationMillis = ScreenMotion.ENTER_MS,
                    delayMillis = ScreenMotion.EXIT_MS,
                )
                (
                    fadeIn(enter) +
                        scaleIn(initialScale = ScreenMotion.ENTER_SCALE, animationSpec = enter)
                    ) togetherWith
                    fadeOut(tween(ScreenMotion.EXIT_MS)) using SizeTransform(clip = false)
            } else {
                // 즉시 갈아 끼운다. 예전과 같은 동작이다.
                fadeIn(snap()) togetherWith fadeOut(snap()) using SizeTransform(clip = false)
            }
        },
        label = label,
    ) { content(it) }
}
