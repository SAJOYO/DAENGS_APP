package com.daengs.app.map.layers.territory

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** 유한 Compose animation이라 시스템 애니메이션 배율 0에서도 반복 타이머를 만들지 않는다. */
@Composable
internal fun rememberTerritoryFeedbackAnimationProgress(feedback: TerritoryFeedback?, nowNanos: () -> Long = System::nanoTime): Float {
    val progress = remember(feedback?.id) { Animatable(feedback?.progressAt(nowNanos()) ?: 1f) }
    LaunchedEffect(feedback?.id) {
        if (feedback != null) {
            val initial = feedback.progressAt(nowNanos())
            progress.snapTo(initial)
            progress.animateTo(1f, tween(((1f - initial) * feedback.durationMillis).toInt(), easing = LinearEasing))
        }
    }
    return progress.value
}
