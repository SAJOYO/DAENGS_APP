package com.daengs.app.miniroom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import com.daengs.app.miniroom.art.DogPose
import com.daengs.app.miniroom.art.ItemArt
import com.daengs.app.miniroom.art.drawDogBreed
import com.daengs.app.miniroom.art.footprintFacing
import com.daengs.app.miniroom.sprite.drawSpriteFrame
import com.daengs.app.miniroom.sprite.frameIndexAt
import com.daengs.app.ui.theme.RoomPalette
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** 기본 단위 아트를 화면에 올리는 **유일한** 변환 지점. */
private inline fun DrawScope.inArtSpace(
    art: ItemArt,
    item: PlacedItem,
    g: RoomGeometry,
    dragOffset: Offset,
    lift: Float,
    block: DrawScope.() -> Unit,
) {
    val c = g.footprintCenter(item.col, item.row, art.box.footprintFacing(item.facing))
    withTransform({
        translate(
            c.x - art.box.anchor.x * g.scaleX + dragOffset.x,
            c.y - art.box.anchor.y * g.scale + dragOffset.y - lift,
        )
        // 소품만 가로로 늘어난다. 강아지는 아래에서 g.scale 을 그대로 쓴다.
        scale(g.scaleX, g.scale, pivot = Offset.Zero)
        // 방향 1 = 좌우 반전. 기준점을 축으로 뒤집어야 발밑이 안 움직인다.
        if (item.facing == 1) scale(-1f, 1f, pivot = Offset(art.box.anchor.x, 0f))
    }) {
        block()
    }
}

/**
 * 아이템 하나 그리기.
 *
 * 도형이든 PNG 든 시트든 여기서 갈라진다. 바깥(정렬·터치·스냅)은 어느 쪽인지 모른다.
 */
fun DrawScope.drawItem(
    art: ItemArt,
    item: PlacedItem,
    g: RoomGeometry,
    timeMs: Long,
    dragOffset: Offset = Offset.Zero,
    lift: Float = 0f,
) {
    inArtSpace(art, item, g, dragOffset, lift) {
        when (art) {
            is ItemArt.Shapes -> art.draw(this, frameIndexAt(timeMs, 8, 6))
            is ItemArt.Bitmap -> drawImage(
                image = art.image,
                dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                // dstSize 는 **기본 단위** 기준이다 (바깥 transform 이 이미 scale 을 걸어놨다).
                dstSize = IntSize(art.box.size.width.toInt(), art.box.size.height.toInt()),
                filterQuality = art.filterQuality,
            )

            is ItemArt.Sheet -> {
                val frame = frameIndexAt(timeMs, art.frameCount, art.fps)
                if (art.sheet != null) {
                    drawSpriteFrame(art.sheet, frame, art.box.size)
                } else {
                    art.fallback(this, frame)
                }
            }
        }
    }
}

/** 들어올린 아이템 아래 바닥에 지는 그림자. */
fun DrawScope.drawLiftShadow(g: RoomGeometry, col: Int, row: Int, footprint: IntSize) {
    val c = g.footprintCenter(col, row, footprint)
    val w = g.cell * 0.62f
    val h = w / RoomSpec.TILE_RATIO
    drawOval(
        RoomPalette.Shadow.copy(alpha = 0.28f),
        Offset(c.x - w / 2f, c.y - h / 2f),
        Size(w, h),
    )
}

/**
 * 놓일 칸 표시.
 *
 * 초록이면 그대로 놓이고, 빨강이면 원래 자리로 돌아간다.
 * 격자 밖으로 끌면 이미 가장자리로 붙여둔 좌표가 표시되므로,
 * 손을 떼기 전에 결과가 항상 눈에 보인다.
 */
fun DrawScope.drawCellGhost(
    g: RoomGeometry,
    col: Int,
    row: Int,
    footprint: IntSize,
    valid: Boolean,
) {
    val fw = footprint.width.toFloat()
    val fh = footprint.height.toFloat()
    val p = Path().apply {
        val a = g.toScreenF(col.toFloat(), row.toFloat())
        val b = g.toScreenF(col + fw, row.toFloat())
        val c = g.toScreenF(col + fw, row + fh)
        val d = g.toScreenF(col.toFloat(), row + fh)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    val color = if (valid) RoomPalette.GhostValid else RoomPalette.GhostInvalid
    drawPath(p, color.copy(alpha = 0.32f))
    drawPath(p, color.copy(alpha = 0.85f), style = Stroke(width = 2f * g.scale))
}

/**
 * 돌아다니는 강아지 한 마리.
 *
 * **걷는 그림이 따로 없어도 걷는 것처럼 보이게 하는 부분이다.**
 * 움직임이 두 층으로 나뉜다:
 *
 * | 층 | 무엇 | 어디서 |
 * |---|---|---|
 * | 몸 전체 | 통통 튀기(bob) · 착지 눌림(squash) · 좌우 기울임(lean) | 여기 |
 * | 파츠 | 다리 교차 · 꼬리 · 귀 | [drawDogBreed] |
 *
 * 여기만으로는 **정면 빌보드에서 제자리 뜀뛰기로 읽힌다** — 다리가 안 움직이기 때문이다.
 * 그래서 걸음 상태를 [DogPose] 로 파츠 쪽에 넘긴다.
 *
 * 멈춰 있을 땐 느린 숨쉬기와 꼬리 흔들기만 남는다.
 */
/**
 * 서 있을 때 세워둘 프레임.
 *
 * 0 번이 아니라 1 번이다. 4프레임 워크 시트에서 0 번은 발이 완전히 벌어진 자세라
 * 정지 화면으로 쓰면 어정쩡하다. 1 번이 네 발이 가장 가지런한 자세다.
 */
private const val DOG_IDLE_FRAME = 1

fun DrawScope.drawDog(
    art: ItemArt,
    dog: DogActor,
    g: RoomGeometry,
    timeMs: Long,
    alpha: Float = 1f,
) {
    val s = g.scale * dog.sizeScale
    val foot = g.toScreenF(dog.pos.x, dog.pos.y)

    // 마리마다 시계를 민다. 안 밀면 숨쉬기·꼬리가 전 마리 동기화돼 한 몸처럼 보인다.
    val t = timeMs + dog.animOffsetMs

    val bob: Float
    val lean: Float
    val squash: Float
    if (dog.moving) {
        // abs(sin) 이라 한 걸음에 한 번씩 튄다. 위로만 뜨고 아래로는 안 꺼진다.
        val step = abs(sin(dog.phase))
        bob = -step * 3.4f
        lean = sin(dog.phase * 0.5f) * 4f
        squash = 1f - step * 0.06f
    } else {
        bob = sin(t / 700f) * 0.7f
        lean = 0f
        squash = 1f + sin(t / 700f) * 0.02f
    }

    // 그림자는 몸이 튀어도 바닥에 붙어 있어야 한다. 뜬 만큼 작고 옅어진다.
    val shW = art.box.size.width * 0.62f * s
    val lift = (-bob / 3.4f).coerceIn(0f, 1f)
    drawOval(
        RoomPalette.Shadow.copy(alpha = (0.30f - 0.10f * lift) * alpha),
        Offset(foot.x - shW / 2f, foot.y - shW / (2f * RoomSpec.TILE_RATIO)),
        Size(shW * (1f - 0.12f * lift), shW / RoomSpec.TILE_RATIO * (1f - 0.12f * lift)),
    )

    withTransform({
        translate(
            foot.x - art.box.anchor.x * s,
            foot.y - art.box.anchor.y * s * squash + bob * s,
        )
        scale(s, s * squash, pivot = Offset.Zero)
        // 발밑을 축으로 기울여야 발이 안 미끄러진다
        rotate(lean, pivot = art.box.anchor)
        if (dog.mirrored) scale(-1f, 1f, pivot = Offset(art.box.anchor.x, 0f))
    }) {
        when (art) {
            is ItemArt.Shapes -> art.draw(this, frameIndexAt(t, 8, 6))
            is ItemArt.Bitmap -> drawImage(
                image = art.image,
                dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                dstSize = IntSize(art.box.size.width.toInt(), art.box.size.height.toInt()),
                alpha = alpha,
                filterQuality = art.filterQuality,
            )

            is ItemArt.Sheet -> {
                // 걸을 때만 시트를 돌리고, 멈추면 **1번 프레임에 세운다** — 저쪽 목업
                // 그대로다. 서 있는데 다리가 계속 움직이면 제자리걸음으로 보인다.
                val frame = if (dog.moving) floor(dog.phase).toInt() else DOG_IDLE_FRAME
                if (art.sheet != null) {
                    drawSpriteFrame(art.sheet, frame, art.box.size, alpha = alpha)
                } else {
                    // 도형 폴백은 **걸음과 털색까지** 받는다. 카탈로그의 `fallback` 은
                    // 프레임만 받아서(무드등과 같은 타입이라 시그니처를 못 바꾼다)
                    // 그림 함수는 하나라 미리보기와 방 안 그림이 갈라지지 않는다.
                    drawDogBreed(dog.breed, frame, DogPose(dog.phase, dog.stand))
                }
            }
        }
    }
}

/** 화면 좌표가 이 강아지 위인가. 그리는 순서와 무관하게 판정한다. */
fun DogActor.hitTest(pos: Offset, art: ItemArt, g: RoomGeometry): Boolean {
    val s = g.scale * sizeScale
    val foot = g.toScreenF(this.pos.x, this.pos.y)
    val left = foot.x - art.box.anchor.x * s
    val top = foot.y - art.box.anchor.y * s
    val local = Offset((pos.x - left) / s, (pos.y - top) / s)
    val l = if (mirrored) Offset(2f * art.box.anchor.x - local.x, local.y) else local
    return art.box.touchArea.contains(l)
}
