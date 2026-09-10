package com.daengs.app.ui.places

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.miniroom.art.DogBreed

internal data class DogBubblePlacement(val offset: IntOffset, val tailX: Float)

/** 고정된 머리 바로 위에서 연다. 원래 머리의 실제 bounds를 쓰므로 밀도/지도 좌표와 무관하다. */
internal fun placeDogBubble(anchor: IntRect, window: IntSize, popup: IntSize, margin: Int): DogBubblePlacement {
    val center = anchor.center
    val x = (anchor.right - popup.width).coerceIn(margin, (window.width - popup.width - margin).coerceAtLeast(margin))
    val y = (anchor.top - popup.height).coerceIn(margin, (window.height - popup.height - margin).coerceAtLeast(margin))
    return DogBubblePlacement(IntOffset(x, y),
        (center.x - x).toFloat().coerceIn(14f, (popup.width - 14).toFloat().coerceAtLeast(14f)))
}

/** 위치 마커와 분리된 검색 도우미. 지도 이동이나 GPS 유무가 입구를 숨기지 않는다. */
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
    reply: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    var draft by rememberSaveable { mutableStateOf("") }
    var composing by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.size(48.dp)) {
        Surface(Modifier.fillMaxSize(), shape = CircleShape, shadowElevation = 4.dp,
            color = DaengsColors.Surface, border = BorderStroke(1.dp, DaengsColors.BrandPrimarySoft)) {
            Box(Modifier.fillMaxSize().clip(CircleShape)
                .clickable(role = Role.Button) { composing = !replyAvailable; onOpen(!open) }
                .semantics { contentDescription = "강아지에게 검색 조건 말하기" }
                .testTag("place-dog-anchor").padding(3.dp)) {
                val portrait = Modifier.fillMaxSize().clip(CircleShape)
                if (avatarPhoto != null) Image(avatarPhoto.asImageBitmap(), contentDescription = null,
                    modifier = portrait, contentScale = ContentScale.Crop)
                else Image(painterResource((avatarBreed ?: DogBreed.BEAGLE).portraitRes), contentDescription = null,
                    modifier = portrait, contentScale = ContentScale.Crop)
            }
        }
        if (open) {
            val position = remember(density, busy) { BubblePositionProvider(with(density) { 8.dp.roundToPx() }, busy) }
            var placement by remember { mutableStateOf(DogBubblePlacement(IntOffset.Zero, 100f)) }
            var maxSpace by remember { mutableIntStateOf(Int.MAX_VALUE) }
            position.onPosition = { placement = it }
            position.onSpace = { maxSpace = it }
            Popup(popupPositionProvider = position,
                onDismissRequest = { keyboard?.hide(); onOpen(false) },
                properties = PopupProperties(focusable = !busy, clippingEnabled = true)) {
                val preferredWidth = if (composing || !replyAvailable) 340 else 280
                val width = (LocalConfiguration.current.screenWidthDp - 24).coerceIn(120, preferredWidth).dp
                val availableHeight = LocalConfiguration.current.screenHeightDp.dp
                if (busy) {
                    // 서버 검색과 설명 생성이 끝날 때까지 실제 네트워크 상태를 따른다.
                    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { onCancel(); onOpen(false) }) { Text("대기 그만", fontSize = 11.sp) }
                        DogThoughts()
                    }
                } else {
                    var appeared by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { appeared = true }
                    val scale by animateFloatAsState(if (appeared) 1f else .88f, spring(dampingRatio = .7f), label = "dog-bubble-pop")
                    Box(Modifier.width(width).graphicsLayer {
                        scaleX = scale; scaleY = scale
                        transformOrigin = TransformOrigin((placement.tailX / size.width).coerceIn(0f, 1f), 1f)
                    }
                        .testTag("place-dog-bubble").padding(vertical = 10.dp)) {
                        Surface(shape = RoundedCornerShape(20.dp), color = DaengsColors.Surface,
                            shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.heightIn(max = (availableHeight * .48f).coerceAtMost(320.dp)
                                .coerceAtMost(with(density) { maxSpace.toDp() } - 20.dp).coerceAtLeast(48.dp))
                                .verticalScroll(rememberScrollState()).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (composing || !replyAvailable) "어디로 가볼까?" else "이렇게 찾아봤어",
                                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { keyboard?.hide(); onOpen(false) },
                                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)) { Text("닫기", fontSize = 11.sp) }
                                }
                                searchContext?.let { Text(it, fontSize = 11.sp, color = DaengsColors.TextSecondary,
                                    modifier = Modifier.testTag("place-dog-search-context")) }
                                if (composing || !replyAvailable) {
                                    fun submit() {
                                        if (draft.isBlank()) return
                                        keyboard?.hide()
                                        composing = false
                                        onSubmit(draft.trim())
                                    }
                                    OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth().testTag("place-dog-input"),
                                        placeholder = { Text("주차 가능한 카페를 찾아줘", fontSize = 13.sp) },
                                        maxLines = 3, textStyle = MaterialTheme.typography.bodyMedium,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = { submit() }),
                                        shape = RoundedCornerShape(12.dp))
                                    TextButton(onClick = { submit() }, enabled = draft.isNotBlank(), modifier = Modifier.align(Alignment.End)) { Text("말해주기") }
                                } else {
                                    reply()
                                    Row {
                                        if (onUndo != null) TextButton(onClick = onUndo) { Text("되돌리기", fontSize = 12.sp) }
                                        TextButton(onClick = { composing = true }) { Text("다시 말하기", fontSize = 12.sp) }
                                    }
                                }
                            }
                        }
                        // 여백 안에서 꼬리를 그려 카드 높이를 바꾸지 않는다.
                        Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp)) {
                            val x = placement.tailX; val h = 10.dp.toPx()
                            drawPath(Path().apply { moveTo(x, h); lineTo(x - h, -1f); lineTo(x + h, -1f); close() }, DaengsColors.Surface)
                        }
                    }
                }
            }
        }
    }
}

private class BubblePositionProvider(private val margin: Int, private val thinking: Boolean) : PopupPositionProvider {
    var onPosition: (DogBubblePlacement) -> Unit = {}
    var onSpace: (Int) -> Unit = {}
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        onSpace(anchorBounds.top - margin)
        if (thinking) return placeDogBubble(anchorBounds, windowSize, popupContentSize, margin).offset
        return placeDogBubble(anchorBounds, windowSize, popupContentSize, margin).also(onPosition).offset
    }
}

@Composable
private fun DogThoughts() {
    val animation = rememberInfiniteTransition(label = "dog-thinking")
    val phase by animation.animateFloat(0f, 4f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "thinking-dots")
    val white = DaengsColors.Surface
    val ink = DaengsColors.TextSecondary
    Canvas(Modifier.size(100.dp, 72.dp).testTag("place-dog-thinking")
        .semantics { contentDescription = "강아지가 검색 조건을 생각하고 있어요"; liveRegion = LiveRegionMode.Polite }) {
        val u = size.width / 100f
        drawCircle(white, 4 * u, Offset(85 * u, 68 * u))
        drawCircle(white, 7 * u, Offset(70 * u, 51 * u))
        drawRoundRect(white, Offset(3 * u, 0f), androidx.compose.ui.geometry.Size(66 * u, 38 * u),
            androidx.compose.ui.geometry.CornerRadius(19 * u))
        repeat(3) { i -> drawCircle(ink.copy(alpha = if (phase >= i + 1) 1f else .2f), 2.6f * u, Offset((22 + i * 14) * u, 19 * u)) }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 500)
@Composable
private fun DogAssistantPreview() {
    DaengsTheme { PlaceMapControls(false, {}, {}) { PlaceDogAssistant(false, false, true, {}, {}, {}) {} } }
}

@Preview(showBackground = true)
@Composable
private fun DogThoughtsPreview() { DaengsTheme { DogThoughts() } }
