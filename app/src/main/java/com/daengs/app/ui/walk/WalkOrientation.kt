package com.daengs.app.ui.walk


/** 산책 화면에서 사용자가 고른 화면 방향. 산책 기록 상태와 무관한 UI 설정이다. */
enum class WalkOrientation {
    PORTRAIT,
    LANDSCAPE,
}

/** 실제 창의 비율로 결정한 배치. Android가 방향 요청을 무시해도 이 값은 화면과 맞는다. */
internal enum class WalkLayoutMode {
    PORTRAIT,
    LANDSCAPE,
    ;

    val oppositeOrientation: WalkOrientation
        get() = when (this) {
            PORTRAIT -> WalkOrientation.LANDSCAPE
            LANDSCAPE -> WalkOrientation.PORTRAIT
        }
}

internal fun walkLayoutMode(widthDp: Float, heightDp: Float): WalkLayoutMode =
    if (widthDp >= heightDp) WalkLayoutMode.LANDSCAPE else WalkLayoutMode.PORTRAIT
