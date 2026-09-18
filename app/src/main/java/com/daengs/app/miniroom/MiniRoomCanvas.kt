package com.daengs.app.miniroom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.rememberTextMeasurer
import com.daengs.app.R
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.miniroom.art.DoorSpec
import com.daengs.app.miniroom.art.FrameSpec
import com.daengs.app.miniroom.art.ItemCatalog
import com.daengs.app.miniroom.art.footprintFacing
import com.daengs.app.miniroom.art.drawDoorHint
import com.daengs.app.miniroom.art.drawWallFrame
import com.daengs.app.miniroom.art.drawDoorOpening
import com.daengs.app.miniroom.art.drawDeveloperOverlay
import com.daengs.app.miniroom.art.drawRoomBackground
import com.daengs.app.miniroom.art.drawWindowOutside
import com.daengs.app.miniroom.sprite.rememberFrameClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.sin

/**
 * 방 전체를 그리는 **하나의** Canvas.
 *
 * 왜 하나여야 하나: 화가 알고리즘(뒤에서 앞으로 덧그리기)으로 앞뒤를 표현하려면
 * 벽·바닥·가구·강아지가 같은 draw 패스에 있어야 한다. 그래서 터치 판정과 드래그를
 * Compose 레이아웃에 맡기지 못하고 직접 짠다.
 *
 * @param frameTimeMs null 이 아니면 애니메이션 프레임을 그 시각으로 고정한다.
 *   @Preview 에서는 무한 애니메이션이 돌지 않아 프레임 0 에 얼어붙으므로 필요하다.
 * @param onItemTap null 이 아니면 **끌지 않고 톡 누른** 아이템을 넘겨준다 (방향 돌리기).
 *   길게 누르기가 아니라 탭으로 구분하는 이유: 드래그가 슬롭 없이 즉시 시작되므로
 *   길게 누르기를 기다리면 끌기가 그만큼 늦게 붙어 손맛이 나빠진다.
 * @param onDoorOpened 문이 **활짝 열린 순간** 불린다. 산책 게임이 생기면 여기서 화면을 넘기면 된다.
 * @param doorOpenOverride null 이 아니면 문 열림 정도를 그 값으로 고정한다 (@Preview 용).
 */
@Composable
fun MiniRoomCanvas(
    state: MiniRoomState,
    catalog: ItemCatalog,
    theme: RoomTheme = RoomTheme.DEFAULT,
    /** 창밖·문밖에 보이는 바깥. 시간 x 날씨 여섯 벌 중 하나다. */
    outside: OutsideView = OutsideView.DEFAULT,
    modifier: Modifier = Modifier,
    herd: DogHerd? = null,
    /** 편집 모드 = 가구를 만지는 중. 강아지는 확 숨고 터치도 가구만 받는다. */
    editing: Boolean = false,
    frameTimeMs: Long? = null,
    onItemTap: ((PlacedItem) -> Unit)? = null,
    onDoorOpened: (() -> Unit)? = null,
    /** 벽에 건 액자를 눌렀을 때. 도감으로 들어가는 문이다. */
    onFrameTap: (() -> Unit)? = null,
    /**
     * 턴테이블을 눌렀을 때. 카드 음악을 트는 곳으로 간다.
     *
     * 붙박이라 [pickTopmost] 가 안 잡으므로 [pickFixture] 로 따로 본다.
     */
    onTurntableTap: (() -> Unit)? = null,
    /**
     * 강아지를 **눌렀을 때** (끌지 않고 뗐을 때). 머리 위에 퀵 메뉴를 여는 데 쓴다.
     *
     * 끌기와 같은 제스처를 나눠 쓴다 — 제스처 블록을 따로 만들면 둘이 서로 먹는다.
     * 자리를 [DogTapTarget] 에 실어 보내는 것은 **밖에서 다시 계산하지 않게** 하려는
     * 것이다. [onSpots] 와 같은 이유다.
     */
    onDogTap: ((DogTapTarget) -> Unit)? = null,
    /**
     * 누를 수 있는 자리가 **화면 어디인지** 알려 준다 (창 기준 좌표).
     *
     * 방 둘러보기가 그 자리를 밝히는 데 쓴다. **자리를 밖에서 다시 계산하지 않는다** —
     * 여기서 주는 값은 터치 판정이 쓰는 것과 같은 셈에서 나오므로, 방 그림이나 배치가
     * 바뀌어도 가리키는 곳과 눌리는 곳이 안 갈라진다.
     */
    onSpots: ((RoomTouchSpots) -> Unit)? = null,
    /**
     * 액자에 걸 그림. null 이면 발자국이 걸린다.
     *
     * 예전에는 여기서 저쪽 카드 한 장을 직접 읽었다. 한 장도 안 뽑은 사람의 방에도
     * 남의 개가 걸려 있었고, 뽑아도 안 바뀌었다. 무엇을 걸지는 방이 정할 일이 아니라
     * 받는 값이다.
     */
    framePicture: ImageBitmap? = null,
    /** 편집 모드에서 빈 곳을 눌렀을 때. 선택 해제용. */
    onEmptyTap: (() -> Unit)? = null,
    doorOpenOverride: Float? = null,
    /**
     * 밖에서 문을 여는 신호. **값이 바뀔 때마다 한 번 연다.**
     *
     * 문 옆 「산책 나가기」 알약이 쓴다. 알약을 문 위에 겹쳐 두면 방 그림을 가리고,
     * 옆에 두면 눌러도 아무 일이 안 일어나므로, 같은 [openDoor] 를 밖에서도 부를
     * 길을 낸다. `Boolean` 이 아니라 세는 수인 것은 두 번 연달아 눌러도 값이 바뀌게
     * 하려는 것이다.
     */
    openDoorSignal: Int = 0,
    /** 개발자 오버레이. 격자·발자국·강아지 반경을 그림 위에 덧그린다. */
    developer: Boolean = false,
    /**
     * 홈 첫 진입 연출. null 이면 평소 그림이다.
     *
     * 값은 **draw 람다 안에서만** 읽는다 ([RoomIntro.frameAt]) — 프레임마다 바뀌는 것을
     * 파라미터로 내리면 60fps 재구성이 된다. 카메라·문 열림·어둠 막을 여기서 덮어쓰고,
     * 불이 켜지는 순간 [herd] 에 마중을 시킨다. 화면을 만지면 건너뛴다.
     */
    intro: RoomIntro? = null,
    /**
     * 방 가로 늘림 ([RoomSpec.H_STRETCH]).
     *
     * **개발자 패널이 실기기에서 값을 고를 때만** 기본값과 다르다. 릴리스에서는 패널이
     * 빈 스텁이라 언제나 기본값이다. "각 폰에서 방을 최대한 크게" 의 손잡이이고,
     * 견딜 만한 값은 눈으로만 알 수 있어서 이렇게 밖에서 넣을 길을 낸다.
     */
    hStretch: Float = RoomSpec.H_STRETCH,
) {
    val clock = rememberFrameClock()

    // 방 그림. 벽·바닥·창문·울타리가 전부 여기 구워져 있다.
    // 문만 예외로 이 그림에서 오려내 다시 그린다 ([drawDoorOpening]).
    // 테마가 바뀌면 그림 전체가 바뀐다 — 색을 덧칠하는 게 아니라 다른 그림이다.
    val roomImage = ImageBitmap.imageResource(theme.room)

    // 바깥. **테마를 안 탄다** — 방 그림 여섯 테마에서 다른 것은 창틀·창살뿐이고
    // 유리 안 풍경은 같아서, 한 벌이 테마 전부를 덮는다.
    val windowOutside = ImageBitmap.imageResource(outside.window)
    val doorOutside = ImageBitmap.imageResource(outside.door)

    val frameCallback by rememberUpdatedState(onFrameTap)
    val turntableCallback by rememberUpdatedState(onTurntableTap)
    val measurer = rememberTextMeasurer()

    // 0 = 닫힘, 1 = 활짝
    val doorOpen = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val doorCallback by rememberUpdatedState(onDoorOpened)
    // 여는 중에 또 눌러도 애니메이션이 겹치지 않게 Job 하나로 관리한다
    val doorJob = remember { arrayOfNulls<Job>(1) }

    // 서 있는 가구가 막은 칸. 강아지가 통과하지 못하는 곳이다.
    // `flat` 아트(러그·강아지침대)는 여기 안 들어오므로 그 위로는 지나다닌다.
    val blocked = state.occupiedCells(exclude = null, catalog = catalog)

    // 배회는 프레임 시계에 맞춰 갱신한다. 그리기와 같은 시각을 쓰므로 어긋나지 않는다.
    if (herd != null && !editing && frameTimeMs == null) {
        herd.update(clock.value, blocked)
    }

    fun openDoor() {
        if (doorJob[0]?.isActive == true) return
        doorJob[0] = scope.launch {
            doorOpen.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            // 산책 게임이 생기면 이 시점에 화면 전환 -> 닫히는 구간은 안 보이게 된다
            doorCallback?.invoke()
            delay(420)
            doorOpen.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        }
    }

    // 밖에서 온 신호. 첫 합성 때는 안 연다 — 화면에 들어오자마자 문이 열리면
    // 사용자가 누르지도 않았는데 산책으로 나간다.
    LaunchedEffect(openDoorSignal) { if (openDoorSignal > 0) openDoor() }

    // 콜백을 pointerInput 키로 직접 쓰면 안 된다. 람다는 재구성마다 새 객체라
    // 제스처 코루틴이 매번 재시작되고, 끌던 중이면 그대로 죽는다.
    // 키는 Boolean 으로 고정하고 콜백은 최신 것을 읽는다.
    val tapHandler by rememberUpdatedState(onItemTap)
    val emptyTapHandler by rememberUpdatedState(onEmptyTap)
    val tapEnabled = onItemTap != null

    // editing 도 **반드시** 여기를 거쳐야 한다. 아래 pointerInput 의 키가 안 바뀌면
    // 제스처 코루틴은 재시작되지 않고, 람다가 캡처한 editing 은 첫 합성 시점 값
    // (= false) 에 얼어붙는다. 그러면 인벤토리를 열어도 터치가 강아지 분기로만 흘러서
    // 가구를 영영 못 잡는다 — 그림은 editing 을 제대로 반영하므로(drawBehind 는 매번
    // 새 람다) 강아지는 사라지는데 가구는 안 잡히는, 원인이 안 보이는 증상이 된다.
    val editingNow by rememberUpdatedState(editing)

    val spotsCallback by rememberUpdatedState(onSpots)

    // 강아지 탭도 같은 이유로 최신 것을 읽는다. 이 값이 키에 들어가면 메뉴를 열고
    // 닫을 때마다 제스처가 재시작된다.
    val dogTapCallback by rememberUpdatedState(onDogTap)

    Spacer(
        modifier
            .onGloballyPositioned { coords ->
                val go = spotsCallback ?: return@onGloballyPositioned
                val w = coords.size.width.toFloat()
                val h = coords.size.height.toFloat()
                if (w <= 0f || h <= 0f) return@onGloballyPositioned
                val g = RoomGeometry.of(w, h, hStretch)
                val origin = coords.positionInWindow()
                fun toWindow(r: Rect) = r.translate(origin.x, origin.y)
                val turntable = state.items
                    .firstOrNull { it.itemId == ItemIds.TURNTABLE }
                    ?.let { g.touchRectOf(it, catalog) }
                go(
                    RoomTouchSpots(
                        door = toWindow(DoorSpec.rectOf(g, DoorSpec.frame)),
                        frame = toWindow(FrameSpec.bounds(g)),
                        turntable = turntable?.let(::toWindow),
                    ),
                )
            }
            // 키는 반드시 Unit. state.items 같은 걸 키로 주면 아이템을 놓는 순간
            // 제스처 코루틴이 재시작되면서 드래그가 도중에 죽는다.
            // 연출 중에는 카메라가 방을 확대해 그린다. 안 자르면 확대된 방이 상단바까지 덮는다.
            .clipToBounds()
            .pointerInput(tapEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    // 연출 중에 무엇이든 만지면 건너뛴다. 터치 판정은 평소 좌표로 이어진다 —
                    // 확대된 그림 위 좌표로 강아지를 잡으려 들면 엉뚱한 아이가 잡히는데,
                    // 건너뛰는 순간 그림이 제자리로 오므로 그대로 둔다.
                    intro?.skip()
                    val g = RoomGeometry.of(size.width.toFloat(), size.height.toFloat(), hStretch)

                    // 편집 모드가 아니면 강아지만 만진다.
                    // **그리는 순서와 무관하게 강아지를 먼저 검사**하므로,
                    // 가구 뒤에 반쯤 가려진 강아지도 그 자리를 누르면 잡힌다.
                    if (!editingNow && herd != null) {
                        val hit = herd.sortedByDepth().asReversed().firstOrNull { d ->
                            val art = catalog[d.breed.id] ?: return@firstOrNull false
                            d.hitTest(down.position, art, g)
                        }
                        if (hit != null) {
                            down.consume()
                            herd.draggingId = hit.id
                            val grab = hit.pos - g.toGridF(down.position).let { Offset(it.first, it.second) }
                            // 끄는 동안엔 편집 모드가 아니라 가구가 안 움직인다 → 한 번만 읽는다
                            val walls = state.occupiedCells(null, catalog)
                            // **끌기로 넘어가기 전까지는 안 옮긴다.** 제자리에서 뗀 것은
                            // 탭이고, 그때 아이를 한 픽셀이라도 밀면 눌렀을 뿐인데
                            // 자리가 바뀐다. 가르는 기준은 아래 턴테이블·액자·문과 같다.
                            var slid = false
                            try {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    if ((ch.position - down.position).getDistance() >
                                        viewConfiguration.touchSlop
                                    ) {
                                        slid = true
                                    }
                                    if (slid) {
                                        val gg = RoomGeometry.of(size.width.toFloat(), size.height.toFloat(), hStretch)
                                        val (cf, rf) = gg.toGridF(ch.position)
                                        // 손가락으로도 책상을 뚫지 못한다 — 자율 이동과 같은 규칙
                                        herd.dragTo(hit, Offset(cf, rf) + grab, walls)
                                        hit.target = hit.pos
                                        hit.restUntil = clock.value + 600L
                                    }
                                    ch.consume()
                                }
                            } finally {
                                herd.draggingId = null
                            }
                            if (!slid) {
                                val gg = RoomGeometry.of(size.width.toFloat(), size.height.toFloat(), hStretch)
                                dogTapCallback?.invoke(hit.tapTarget(catalog, gg))
                            }
                            return@awaitEachGesture
                        }
                        // 강아지가 아니면 벽에 걸린 것과 붙박이만 본다
                        // (가구는 편집 모드에서만). 서로 안 겹치지만 순서를 정해 둔다.
                        if (turntableCallback != null &&
                            state.items.pickFixture(down.position, g, catalog, ItemIds.TURNTABLE) != null
                        ) {
                            var slid = false
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) slid = true
                                if (!ch.pressed) break
                            }
                            if (!slid) turntableCallback?.invoke()
                            return@awaitEachGesture
                        }
                        if (frameCallback != null && FrameSpec.contains(g, down.position)) {
                            var slid = false
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) slid = true
                                if (!ch.pressed) break
                            }
                            if (!slid) frameCallback?.invoke()
                            return@awaitEachGesture
                        }
                        if (DoorSpec.contains(g, down.position)) {
                            var slid = false
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) slid = true
                                if (!ch.pressed) break
                            }
                            if (!slid) openDoor()
                        }
                        return@awaitEachGesture
                    }

                    // --- 여기부터는 편집 모드 ---
                    if (!state.beginDrag(down.position, g, catalog)) {
                        // 가구도 문도 아니면 선택 해제
                        if (!DoorSpec.contains(g, down.position)) emptyTapHandler?.invoke()
                        // 아이템이 아니면 문인지 본다.
                        if (DoorSpec.contains(g, down.position)) {
                            // down 을 소비하지 않는다 — 문 위에서 쓸어내리면
                            // (나중에 스크롤이 생겼을 때) 스크롤이 되게 두려는 것.
                            var slid = false
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    slid = true
                                }
                                if (!ch.pressed) break
                            }
                            if (!slid) openDoor()
                        }
                        // 아이템도 문도 아니면 제스처를 포기한다.
                        return@awaitEachGesture
                    }

                    // 아이템을 정확히 눌렀으므로 슬롭 없이 바로 잡는다.
                    // 여기서 소비해야 부모 스크롤이 가로채지 않는다.
                    down.consume()
                    val grabbed = state.drag?.instanceId
                    var moved = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }
                            state.updateDrag(
                                change.position,
                                RoomGeometry.of(size.width.toFloat(), size.height.toFloat(), hStretch),
                                catalog,
                            )
                            change.consume()
                        }
                        val onTap = tapHandler
                        if (!moved && onTap != null && grabbed != null) {
                            // 제자리 탭 → 이동이 아니라 인벤토리로 보내기
                            state.cancelDrag()
                            state.items.firstOrNull { it.instanceId == grabbed }?.let(onTap)
                        } else {
                            state.endDrag(catalog)
                        }
                    } catch (e: CancellationException) {
                        state.cancelDrag()
                        throw e
                    }
                }
            }
            .drawBehind {
                // 상태는 전부 draw 람다 **안에서** 읽는다. 그래야 recomposition 없이
                // draw 단계만 무효화돼 드래그 중에도 프레임이 안 떨어진다.
                val g = RoomGeometry.of(size.width, size.height, hStretch)
                val t = frameTimeMs ?: clock.value
                val d = state.drag

                // 첫 진입 연출. 끝났거나 없으면 DONE 이라 아래가 전부 평소 값이 된다.
                val cut = intro?.frameAt(t, night = outside.time == OutsideTime.NIGHT) ?: IntroFrame.DONE
                val open = doorOpenOverride ?: if (cut.active) cut.doorOpen else doorOpen.value
                // 누를 수 있다는 은은한 표시. 열리기 시작하면 꺼진다.
                val pulse = ((sin(t / 900f) + 1f) / 2f) * (1f - open)

                // 불이 켜지는 순간 한 번. 연출을 건너뛰어도 나간다.
                if (herd != null && !editing && intro?.takeGreet(cut) == true) {
                    herd.greet(g.doorstep(), t, blocked)
                }

                // 카메라. 문짝이 화면을 채운 상태(pull=1)에서 제자리(pull=0)로 빠진다.
                // 문 한가운데를 축으로 키우고, 그 축이 화면 한가운데로 오도록 민다.
                val door = DoorSpec.rectOf(g, DoorSpec.leaf)
                val zoomIn = minOf(size.width / door.width, size.height / door.height).coerceIn(1.5f, 3.5f)
                val zoom = 1f + (zoomIn - 1f) * cut.pull
                val shift = (center - door.center) * cut.pull

                withTransform({
                    if (cut.pull > 0f) {
                        translate(shift.x, shift.y)
                        scale(zoom, zoom, pivot = door.center)
                    }
                }) {
                drawRoomBackground(g, roomImage)
                // 창밖은 방 그림 **바로 뒤에.** 유리 모양으로 잘려 있어 창틀·창살을
                // 덮지 않는다.
                drawWindowOutside(g, windowOutside, outside.veil)
                drawDoorOpening(g, roomImage, doorOutside, open, outside.veil)
                drawDoorHint(g, pulse)
                drawWallFrame(g, framePicture, pulse)

                if (d != null) {
                    val dragged = state.items.firstOrNull { it.instanceId == d.instanceId }
                    val box = dragged?.let { catalog[it.itemId]?.box }
                    if (box != null) {
                        val fp = box.footprintFacing(dragged.facing)
                        drawCellGhost(g, d.targetCol, d.targetRow, fp, d.valid)
                        drawLiftShadow(g, d.targetCol, d.targetRow, fp)
                    }
                }

                val order = state.drawOrder(catalog)

                fun DrawScope.paint(item: PlacedItem) {
                    val art = catalog[item.itemId] ?: return
                    if (d != null && d.instanceId == item.instanceId) {
                        drawItem(art, item, g, t, dragOffset = d.visualDelta, lift = 7f * g.scale)
                    } else {
                        drawItem(art, item, g, t)
                    }
                }

                // 1) 바닥 장식(러그·강아지침대) — 두께가 0 이라 아무것도 가리지 못한다.
                //    깊이와 무관하게 통째로 맨 뒤. 앞칸 러그가 뒤칸 강아지를 덮으면 안 된다.
                for (item in order) {
                    if (state.layerOf(item, catalog) == MiniRoomState.LAYER_FLOOR) paint(item)
                }

                // 2) 서 있는 가구와 강아지를 **같은 자로** 깊이 정렬해 섞는다.
                //    강아지는 발끝이 딛은 칸으로 잰다 — 자세한 건 DogActor.depthCell.
                //    같은 깊이면 강아지가 앞 (`<` 이므로 동률에서 아이템이 먼저 나간다).
                val dogs = if (herd != null && !editing) herd.sortedByDepth() else emptyList()
                var di = 0

                fun DrawScope.flushDogsUpTo(depth: Int) {
                    while (di < dogs.size && dogs[di].depthCell < depth) {
                        val dog = dogs[di]
                        catalog[dog.breed.id]?.let { drawDog(it, dog, g, t) }
                        di++
                    }
                }

                for (item in order) {
                    if (state.layerOf(item, catalog) == MiniRoomState.LAYER_FLOOR) continue
                    flushDogsUpTo(state.depthOf(item, catalog))
                    paint(item)
                }
                flushDogsUpTo(Int.MAX_VALUE)

                // 오버레이는 **맨 마지막**. 가구 밑에 깔리면 자를 못 댄다
                if (developer) {
                    drawDeveloperOverlay(g, state, catalog, herd?.dogs.orEmpty(), measurer)
                }
                } // withTransform

                // 어둠 막은 카메라 밖에서. 확대와 무관하게 화면 전체를 덮어야 한다.
                if (cut.dark > 0f) drawRect(IntroInk, alpha = cut.dark)
            }
    )
}

/** 불 꺼진 방의 색. 검정이면 화면이 꺼진 것 같고, 살짝 보라가 도는 남색이 밤이다. */
private val IntroInk = Color(0xFF1A1424)

/**
 * 방 안에서 **누를 수 있는 자리**의 화면 사각형 (창 기준).
 *
 * 방 둘러보기가 쓴다. 턴테이블은 배치에 없을 수 있어서 null 이 가능하다 — 그때는
 * 그 단계를 건너뛴다. 빈 화면에 대고 "여기를 누르세요" 라고 하면 안 된다.
 */
data class RoomTouchSpots(
    val door: Rect,
    val frame: Rect,
    val turntable: Rect?,
)

/**
 * 방금 누른 강아지가 **누구이고 화면 어디에 있는지**.
 *
 * 퀵 메뉴가 머리 위에 붙으려면 자리를 알아야 하는데, 그 자리를 밖에서 다시 계산하면
 * **가리키는 곳과 눌리는 곳이 갈라진다.** 방 그림·배치·견종이 바뀌면 조용히 어긋나고,
 * 어긋나도 아무도 모른다. 그래서 히트 판정이 쓴 그 셈에서 나온 값을 그대로 실어 보낸다
 * ([RoomTouchSpots] 와 같은 이유).
 *
 * @param dogId [DogActor.id]. 같은 견종이 여럿이어도 이걸로 갈린다
 * @param breed 화면에 이름·얼굴을 띄울 때 쓴다
 * @param head 머리 꼭대기. **캔버스 왼쪽 위 기준 픽셀**이라 그리는 쪽에서 dp 로 바꾼다
 */
data class DogTapTarget(
    val dogId: Int,
    val breed: DogBreed,
    val head: Offset,
)

/**
 * 이 아이의 [DogTapTarget]. **[hitTest] 와 같은 셈을 쓴다.**
 *
 * 발이 격자 위 [DogActor.pos] 이고, 그림은 거기서 `anchor` 만큼 올려 그린다.
 *
 * ⚠️ **그림 꼭대기가 아니라 `touchArea` 의 꼭대기를 준다.** 스프라이트는 위쪽에 빈
 * 자리를 두고 있어서, 그림 높이로 올리면 메뉴가 머리에서 한참 떠서 벽까지 올라간다
 * (실기기에서 바로 보였다). `touchArea` 는 아이 몸에 붙은 상자이고 [hitTest] 가 누를
 * 수 있다고 판정하는 그 상자라, **눌린 곳 바로 위**에 뜬다.
 *
 * 그림이 없는 견종이면 발 자리를 그대로 준다 — 그런 아이는 애초에 [hitTest] 에 안
 * 걸려서 여기까지 오지 않는다.
 */
private fun DogActor.tapTarget(catalog: ItemCatalog, g: RoomGeometry): DogTapTarget {
    val art = catalog[breed.id]
    val s = g.scale * sizeScale
    val foot = g.toScreenF(pos.x, pos.y)
    val top = if (art == null) {
        foot.y
    } else {
        foot.y - (art.box.anchor.y - art.box.touchArea.top) * s
    }
    return DogTapTarget(dogId = id, breed = breed, head = Offset(foot.x, top))
}
