package com.daengs.app.miniroom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.rememberTextMeasurer
import com.daengs.app.R
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import com.daengs.app.miniroom.art.DoorSpec
import com.daengs.app.miniroom.art.FrameSpec
import com.daengs.app.miniroom.art.ItemCatalog
import com.daengs.app.miniroom.art.footprintFacing
import com.daengs.app.miniroom.art.drawDoorHint
import com.daengs.app.miniroom.art.rememberAssetImage
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
    /** 편집 모드에서 빈 곳을 눌렀을 때. 선택 해제용. */
    onEmptyTap: (() -> Unit)? = null,
    doorOpenOverride: Float? = null,
    /** 개발자 오버레이. 격자·발자국·강아지 반경을 그림 위에 덧그린다. */
    developer: Boolean = false,
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

    // 액자 속 그림. 도감 웹 데모의 카드를 그대로 쓴다 — 같은 파일을 리소스로 한 벌 더
    // 넣지 않으려는 것이다. 작게 그리므로 절반 크기로 읽는다.
    val framePicture = rememberAssetImage("neo-hologram/art/cabbage-card.webp", sample = 2)
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

    Spacer(
        modifier
            // 키는 반드시 Unit. state.items 같은 걸 키로 주면 아이템을 놓는 순간
            // 제스처 코루틴이 재시작되면서 드래그가 도중에 죽는다.
            .pointerInput(tapEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val g = RoomGeometry.of(size.width.toFloat(), size.height.toFloat())

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
                            try {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    val gg = RoomGeometry.of(size.width.toFloat(), size.height.toFloat())
                                    val (cf, rf) = gg.toGridF(ch.position)
                                    // 손가락으로도 책상을 뚫지 못한다 — 자율 이동과 같은 규칙
                                    herd.dragTo(hit, Offset(cf, rf) + grab, walls)
                                    hit.target = hit.pos
                                    hit.restUntil = clock.value + 600L
                                    ch.consume()
                                }
                            } finally {
                                herd.draggingId = null
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
                                RoomGeometry.of(size.width.toFloat(), size.height.toFloat()),
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
                val g = RoomGeometry.of(size.width, size.height)
                val t = frameTimeMs ?: clock.value
                val d = state.drag
                val open = doorOpenOverride ?: doorOpen.value
                // 누를 수 있다는 은은한 표시. 열리기 시작하면 꺼진다.
                val pulse = ((sin(t / 900f) + 1f) / 2f) * (1f - open)

                drawRoomBackground(g, roomImage)
                // 창밖은 방 그림 **바로 뒤에.** 유리 모양으로 잘려 있어 창틀·창살을
                // 덮지 않는다.
                drawWindowOutside(g, windowOutside)
                drawDoorOpening(g, roomImage, doorOutside, open)
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

            }
    )
}
