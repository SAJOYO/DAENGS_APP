package com.daengs.app.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.daengs.app.ui.theme.DaengsTheme

internal data class DogBubblePlacement(val offset: IntOffset, val tailX: Float)

/** 머리의 실제 bounds를 기준으로 모든 대화 상태를 같은 자리에 놓는다. */
internal fun placeDogBubble(anchor: IntRect, window: IntSize, popup: IntSize, margin: Int): DogBubblePlacement {
    val x = (anchor.right - popup.width).coerceIn(margin, (window.width - popup.width - margin).coerceAtLeast(margin))
    val y = (anchor.top - popup.height).coerceIn(margin, (window.height - popup.height - margin).coerceAtLeast(margin))
    return DogBubblePlacement(IntOffset(x, y),
        (anchor.center.x - x).toFloat().coerceIn(14f, (popup.width - 14).toFloat().coerceAtLeast(14f)))
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PlaceDogAssistant(
    busy: Boolean,
    replyAvailable: Boolean,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    onUndo: (() -> Unit)? = null,
    searchContext: String? = null,
    details: (@Composable () -> Unit)? = null,
    reply: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    var draft by rememberSaveable { mutableStateOf("") }
    var composing by rememberSaveable { mutableStateOf(false) }
    Box {
        PlaceDogAssistantEntry {
            composing = !replyAvailable
            onOpen(!open)
        }
        if (open) {
            var popupImeVisible by remember { mutableStateOf(false) }
            var popupView by remember { mutableStateOf<android.view.View?>(null) }
            var placement by remember { mutableStateOf(DogBubblePlacement(IntOffset.Zero, 100f)) }
            val margin = with(density) { 8.dp.roundToPx() }
            val position = remember(margin) { object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
                    placeDogBubble(anchorBounds, windowSize, popupContentSize, margin).also { placement = it }.offset
            } }
            fun hideKeyboard() {
                // 입력을 소유한 Popup Window의 IME를 내린다. Activity의 controller는 다른 토큰이다.
                popupView?.let { androidx.core.view.ViewCompat.getWindowInsetsController(it) }
                    ?.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                keyboard?.hide()
            }
            fun close() { hideKeyboard(); onOpen(false) }
            Popup(popupPositionProvider = position,
                onDismissRequest = {
                    // Popup은 별도 Window다. Activity의 가려진 높이가 0이어도 Popup의 IME는 보일 수 있다.
                    val ime = popupView?.let { androidx.core.view.ViewCompat.getRootWindowInsets(it) }
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                    if (popupImeVisible || ime) hideKeyboard() else close()
                },
                properties = PopupProperties(focusable = true, clippingEnabled = true)) {
                val view = LocalView.current
                val imeVisible = WindowInsets.isImeVisible
                SideEffect { popupView = view; popupImeVisible = imeVisible }
                val width = (LocalConfiguration.current.screenWidthDp - 24).coerceIn(120, 324).dp
                PlaceDogDialogue(
                    busy = busy, composing = composing || !replyAvailable,
                    draft = draft, onDraft = { draft = it }, searchContext = searchContext,
                    onClose = ::close, onCancel = { onCancel(); close() }, onUndo = onUndo,
                    onCompose = { composing = true },
                    onSubmit = {
                        if (!busy && draft.isNotBlank()) {
                            hideKeyboard(); composing = false; onSubmit(draft.trim())
                        }
                    },
                    tailX = placement.tailX, modifier = Modifier.width(width),
                    details = details, reply = reply,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 500)
@Composable
private fun DogAssistantPreview() {
    DaengsTheme { PlaceMapControls(false, {}, {}) { PlaceDogAssistant(false, false, true, {}, {}, {}) {} } }
}
