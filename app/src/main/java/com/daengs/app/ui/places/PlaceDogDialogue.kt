package com.daengs.app.ui.places

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import kotlinx.coroutines.delay

/** 지도 안의 도우미견 입력만 키보드가 배경 위를 덮는 배치에 참여한다. */
internal val LocalPlaceAssistantInputFocus = compositionLocalOf<(Boolean) -> Unit> { {} }

/** 246dp 대화판 안에서 대사·조건·행동 영역을 고정한다. 긴 응답은 안쪽만 스크롤한다. */
@Composable
internal fun PlaceDogDialogue(
    busy: Boolean, composing: Boolean, draft: String, onDraft: (String) -> Unit,
    searchContext: String?, onClose: () -> Unit, onCancel: () -> Unit, onUndo: (() -> Unit)?,
    onSubmit: () -> Unit, tailX: Float,
    modifier: Modifier = Modifier, details: (@Composable () -> Unit)? = null,
    reply: @Composable () -> Unit,
) {
    // 글꼴 확대에도 상태에 따른 크기 변화는 없다. 확대분은 대사와 조건 칸에 배분한다.
    val extra = (LocalDensity.current.fontScale - 1f).coerceIn(0f, 1f) * 64
    val speechHeight = (81 + extra * .65f).dp
    val metaTop = (123 + extra * .65f).dp
    val panelHeight = (246 + extra).dp
    val shape = RoundedCornerShape(26.dp)
    Box(modifier.height(panelHeight + 24.dp).testTag("place-dog-bubble")) {
        Surface(Modifier.padding(top = 14.dp).fillMaxWidth().height(panelHeight), shape = shape,
            color = DaengsColors.Surface, shadowElevation = 7.dp,
            border = BorderStroke(1.dp, DaengsColors.BrandPrimary.copy(alpha = .55f))) {
            Box(Modifier.background(Brush.verticalGradient(listOf(DaengsColors.Surface, DaengsColors.AppBackground)))) {
                Box(Modifier.fillMaxSize().padding(4.dp).border(1.dp, DaengsColors.Surface, RoundedCornerShape(22.dp)))
                DogDialoguePaw(Modifier.align(Alignment.TopEnd).padding(top = 79.dp, end = 25.dp).size(20.dp).rotate(15f), .17f)
                DogDialoguePaw(Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 48.dp).size(18.dp).rotate(25f), .14f)
                TextButton(onClick = onClose, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.align(Alignment.TopEnd).size(48.dp).semantics { contentDescription = "닫기" }) {
                    Text("×", fontSize = 22.sp, color = DaengsColors.TextSecondary)
                }
                Box(Modifier.padding(start = 22.dp, end = 22.dp, top = 35.dp).fillMaxWidth()
                    .height(speechHeight).testTag("place-dog-speech").clip(RoundedCornerShape(4.dp))
                    .verticalScroll(rememberScrollState())) {
                    ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium, color = DaengsColors.TextPrimary)) {
                        when {
                            busy -> DogDialogueText("킁킁, 조건에 맞는 곳을\n찾아보고 있어!")
                            composing -> DogDialogueText("어디로 가고 싶어?\n나한테 말해 줘, 멍!")
                            else -> reply()
                        }
                    }
                }
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = metaTop).fillMaxWidth()
                    .height((45 + extra * .35f).dp).testTag("place-dog-metadata")) {
                    HorizontalDivider(color = DaengsColors.BorderNeutral)
                    if (!busy && !composing && details != null) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp)) { details() }
                    } else searchContext?.let {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("검색 범위", fontSize = 10.sp, lineHeight = 14.sp, color = DaengsColors.TextSecondary,
                                modifier = Modifier.testTag("place-dog-context-label"))
                            Text(it, fontSize = 11.sp, lineHeight = 16.sp, color = DaengsColors.TextPrimary,
                                modifier = Modifier.weight(1f).testTag("place-dog-search-context"))
                        }
                    }
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .fillMaxWidth().height(52.dp).testTag("place-dog-footer"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        busy -> {
                            DogSearchSteps(Modifier.weight(1f))
                            TextButton(onClick = onCancel) { Text("대기 그만", fontSize = 12.sp) }
                        }
                        else -> {
                            if (onUndo != null) TextButton(onClick = onUndo) { Text("되돌리기", fontSize = 12.sp) }
                            Box(Modifier.weight(1f)) {
                                DogDialogueInput(draft, onDraft, onSubmit)
                            }
                        }
                    }
                }
            }
        }
        Surface(Modifier.padding(start = 18.dp).height(28.dp),
            color = DaengsColors.BrandPrimarySoft, border = BorderStroke(1.dp, DaengsColors.BrandPrimary.copy(alpha = .55f)),
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 5.dp)) {
            Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DogDialoguePaw(Modifier.size(13.dp).rotate(-12f))
                Text("도우미견", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DaengsColors.TextPrimary)
            }
        }
        Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(10.dp)) {
            val x = tailX.coerceIn(18.dp.toPx(), size.width - 18.dp.toPx())
            drawPath(Path().apply { moveTo(x - 8.dp.toPx(), 0f); lineTo(x, size.height)
                lineTo(x + 8.dp.toPx(), 0f); close() }, DaengsColors.AppBackground)
        }
    }
}

@Composable
private fun DogDialogueInput(draft: String, onDraft: (String) -> Unit, onSubmit: () -> Unit) {
    val onInputFocus = LocalPlaceAssistantInputFocus.current
    DisposableEffect(onInputFocus) { onDispose { onInputFocus(false) } }
    Row(Modifier.fillMaxSize().border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(17.dp))
        .background(DaengsColors.Surface, RoundedCornerShape(17.dp)).padding(start = 12.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(draft, onDraft, modifier = Modifier.weight(1f).testTag("place-dog-input")
            .onFocusChanged { onInputFocus(it.isFocused) }
            .semantics { contentDescription = "강아지에게 말하기" },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = DaengsColors.TextPrimary),
            singleLine = true, cursorBrush = SolidColor(DaengsColors.BrandPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) onSubmit() }),
            decorationBox = { field -> Box {
                if (draft.isEmpty()) Text("도우미견에게 말해 주세요", color = DaengsColors.TextSecondary, fontSize = 12.sp)
                field()
            } })
        IconButton(onClick = onSubmit, enabled = draft.isNotBlank(), modifier = Modifier.size(48.dp)
            .testTag("place-dog-send").semantics { contentDescription = "말해주기" }) {
            Box(Modifier.size(36.dp).background(if (draft.isBlank()) DaengsColors.SurfaceMuted else DaengsColors.BrandPrimarySoft,
                RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("↑", fontSize = 23.sp, color = if (draft.isBlank()) DaengsColors.TextSecondary else DaengsColors.TextPrimary)
            }
        }
    }
}

/** 서버 문장은 그대로 보여 준다. TalkBack은 타이핑 조각 대신 완전한 문장을 한 번 읽는다. */
@Composable
internal fun DogDialogueText(value: String) {
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) }
    var accessible by remember { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(accessibility) {
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { accessible = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var shown by remember(value) { mutableIntStateOf(if (accessible || reducedMotion) value.length else 0) }
    LaunchedEffect(value, accessible, reducedMotion) {
        if (accessible || reducedMotion) shown = value.length
        while (shown < value.length) {
            delay(48)
            if (shown < value.length) shown = value.offsetByCodePoints(shown, 1)
        }
    }
    Text(value.take(shown), Modifier.fillMaxWidth().clickable { shown = value.length }
        .clearAndSetSemantics {
            text = AnnotatedString(value)
            liveRegion = LiveRegionMode.Polite
            if (shown < value.length) onClick("대사 바로 보기") { shown = value.length; true }
        })
}

@Composable
private fun DogSearchSteps(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dog-search")
    val phase by transition.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "paw-steps")
    Row(modifier.testTag("place-dog-thinking").semantics {
        contentDescription = "강아지가 검색 조건을 생각하고 있어요"; liveRegion = LiveRegionMode.Polite
    }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { DogDialoguePaw(Modifier.size(13.dp), if (phase.toInt() == it) 1f else .3f) }
    }
}

@Composable
private fun DogDialoguePaw(modifier: Modifier = Modifier, alpha: Float = 1f) {
    Canvas(modifier) {
        val ink = DaengsColors.BrandPrimary.copy(alpha = alpha)
        drawOval(ink, Offset(size.width * .23f, size.height * .48f), Size(size.width * .54f, size.height * .46f))
        listOf(.08f to .30f, .29f to .07f, .55f to .07f, .76f to .30f).forEach { (x, y) ->
            drawOval(ink, Offset(size.width * x, size.height * y), Size(size.width * .19f, size.height * .27f))
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 360, fontScale = 1.5f)
@Composable
private fun DogDialoguePreview() {
    DaengsTheme { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("input", "busy", "reply").forEach { state ->
            PlaceDogDialogue(state == "busy", state == "input", "", {}, "현재 검색 지역 · 반경 3km",
                {}, {}, {}, {}, 280f, Modifier.fillMaxWidth()) {
                DogDialogueText("주차 가능한 카페를 찾았어.\n아래 카드에서 골라 봐, 멍!")
            }
        }
    } }
}
