package com.daengs.app.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.daengs.app.miniroom.art.DogBreed
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
internal fun PlaceDogAssistant(
    busy: Boolean,
    replyAvailable: Boolean,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    onUndo: (() -> Unit)? = null,
    avatarBreed: DogBreed? = null,
    avatarPhoto: android.graphics.Bitmap? = null,
    searchContext: String? = null,
    details: (@Composable () -> Unit)? = null,
    reply: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    var draft by rememberSaveable { mutableStateOf("") }
    var composing by rememberSaveable { mutableStateOf(false) }
    Box {
        PlaceDogAssistantEntry(avatarBreed, avatarPhoto) {
            composing = !replyAvailable
            onOpen(!open)
        }
        if (open) {
            var placement by remember { mutableStateOf(DogBubblePlacement(IntOffset.Zero, 100f)) }
            val margin = with(density) { 8.dp.roundToPx() }
            val position = remember(margin) { object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
                    placeDogBubble(anchorBounds, windowSize, popupContentSize, margin).also { placement = it }.offset
            } }
            fun close() { keyboard?.hide(); onOpen(false) }
            Popup(popupPositionProvider = position, onDismissRequest = ::close,
                properties = PopupProperties(focusable = true, clippingEnabled = true)) {
                val width = (LocalConfiguration.current.screenWidthDp - 24).coerceIn(120, 324).dp
                PlaceDogDialogue(
                    busy = busy, composing = composing || !replyAvailable,
                    draft = draft, onDraft = { draft = it }, searchContext = searchContext,
                    onClose = ::close, onCancel = { onCancel(); close() }, onUndo = onUndo,
                    onCompose = { composing = true },
                    onSubmit = {
                        if (!busy && draft.isNotBlank()) {
                            keyboard?.hide(); composing = false; onSubmit(draft.trim())
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
