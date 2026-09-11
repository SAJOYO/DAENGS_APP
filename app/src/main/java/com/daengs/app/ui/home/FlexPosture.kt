package com.daengs.app.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * 지금 기기가 **반쯤 접혀 있으면** 위쪽 절반의 높이. 아니면 null.
 *
 * 이 파일은 **자세를 읽어 오기만 한다.** 그 값으로 무엇을 할지 정하는 규칙은
 * [flexContentHeight] 에 순수함수로 있다 — 그래야 테스트로 잡힌다. 여기는
 * 테스트가 어려운 바깥세상과 닿는 얇은 껍질이다.
 *
 * @param windowHeight 창 전체 높이. **합성 안 dp** 여야 한다 (바깥
 *   `BoxWithConstraints` 의 `maxHeight`). 힌지 자리도 같은 자로 환산해서
 *   비교하므로 둘의 자가 어긋나면 안 된다.
 */
@Composable
fun rememberFlexTopHeight(windowHeight: Dp): Dp? {
    // @Preview 에는 창도 액티비티도 없다. 접히지 않은 것으로 친다.
    if (LocalInspectionMode.current) return null
    val activity = LocalContext.current.findActivity() ?: return null

    val tracker = remember(activity) { WindowInfoTracker.getOrCreate(activity) }
    // initial = null 이 **접힘을 아직 모른다** 는 뜻이다. 첫 프레임에 "펼쳐져
    // 있다" 고 단정하면 반접힌 채로 켰을 때 화면이 한 번 튄다.
    val layoutInfo by remember(tracker, activity) { tracker.windowLayoutInfo(activity) }
        .collectAsState(initial = null)

    val fold = layoutInfo?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()
        ?: return null

    // **바깥에서 온 px 을 합성 자로 환산한다.** `DaengsTheme` 이 `LocalDensity` 를
    // 갈아끼워 두었으므로 이 환산이 곧 [windowHeight] 와 같은 자가 된다.
    val hingeTop = with(LocalDensity.current) { fold.bounds.top.toDp() }

    return flexContentHeight(
        windowHeight = windowHeight,
        hingeTop = hingeTop,
        halfOpened = fold.state == FoldingFeature.State.HALF_OPENED,
        horizontalHinge = fold.orientation == FoldingFeature.Orientation.HORIZONTAL,
    )
}

/**
 * 합성이 들고 있는 `Context` 에서 액티비티를 찾는다.
 *
 * `LocalContext` 가 늘 액티비티인 것은 아니다 — 테마 래퍼가 끼면
 * `ContextWrapper` 가 와서 바로 캐스팅하면 터진다.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
