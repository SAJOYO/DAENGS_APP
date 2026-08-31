package com.daengs.app.miniroom.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.DogActor
import com.daengs.app.miniroom.FloorQuad
import com.daengs.app.miniroom.MiniRoomState
import com.daengs.app.miniroom.PlacedItem
import com.daengs.app.miniroom.RoomGeometry
import com.daengs.app.miniroom.RoomSpec

// ---------------------------------------------------------------------------
// 개발자 오버레이 — **격자가 그림과 맞는지 보는 자**
//
// 방이 그림 한 장이 되면서 격자가 눈에 안 보이게 됐다. 예전에는 바닥을 코드로
// 그려서 칸이 그대로 보였는데, 이제는 좌표가 어긋나도 소품이 조금 이상한 자리에
// 놓일 뿐이라 **원인을 못 찾는다.**
//
// 그래서 격자를 그림 위에 덧그려 눈으로 맞춘다. 저쪽 목업에도 같은 것이 있고
// (modular-room.js 의 developer overlay), 거기서도 바닥 캘리브레이션에 썼다.
//
// 여기서 확인하는 것:
//   - 격자선이 그림 속 마룻바닥과 나란한가 (어긋나면 [FloorQuad] 를 다시 잰다)
//   - 소품의 발자국이 실제로 덮는 자리와 맞는가
//   - 강아지 반경이 가구를 얼마나 피하는가
// ---------------------------------------------------------------------------

private val GridLine = Color(0x5533AAFF)
private val FloorLine = Color(0xCCFF3366)
private val FootprintFill = Color(0x3300E5FF)
private val FootprintLine = Color(0xAA00E5FF)
private val DogMark = Color(0xCCFFEB3B)
private val LabelBg = Color(0xCC101820)

/**
 * 격자·발자국·강아지를 그림 위에 덧그린다.
 *
 * **격자선은 직선으로 그어도 정확하다.** 바닥이 원근 사각형이라 곡선일 것 같지만,
 * 이중선형 사상은 한 축을 고정하면 나머지 축에 대해 선형이다. 그래서 col 을 고정하고
 * row 를 0 에서 GRID 까지 잇는 선은 두 끝점을 직선으로 이은 것과 정확히 같다.
 */
fun DrawScope.drawDeveloperOverlay(
    g: RoomGeometry,
    state: MiniRoomState,
    catalog: ItemCatalog,
    dogs: List<DogActor>,
    measurer: TextMeasurer,
) {
    val n = RoomSpec.GRID
    for (i in 0..n) {
        val f = i.toFloat()
        drawLine(GridLine, g.toScreenF(f, 0f), g.toScreenF(f, n.toFloat()), strokeWidth = 1f)
        drawLine(GridLine, g.toScreenF(0f, f), g.toScreenF(n.toFloat(), f), strokeWidth = 1f)
    }

    // 칠해진 바닥의 실제 윤곽. 격자(사각형)와 다르다는 걸 눈으로 보게 한다
    val outline = Path().apply {
        FloorQuad.outline.forEachIndexed { i, p ->
            val s = Offset(
                g.stage.left + p.x / 100f * g.stage.width,
                g.stage.top + p.y / 100f * g.stage.height,
            )
            if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
        }
        close()
    }
    drawPath(outline, FloorLine, style = Stroke(2f))

    state.items.forEach { item -> drawItemFootprint(g, item, catalog, measurer) }

    dogs.forEach { dog ->
        val p = g.toScreenF(dog.pos.x, dog.pos.y)
        drawCircle(DogMark, 4f, p)
        // 몸 반경 — 가구를 피하는 실제 크기다
        val edge = g.toScreenF(dog.pos.x + dog.bodyRadius, dog.pos.y)
        drawCircle(DogMark, (edge - p).getDistance(), p, style = Stroke(1f))
    }

    label(measurer, "GRID ${n}x$n · scale ${"%.3f".format(g.scale)}", Offset(g.stage.left + 6f, g.stage.top + 6f))
}

private fun DrawScope.drawItemFootprint(
    g: RoomGeometry,
    item: PlacedItem,
    catalog: ItemCatalog,
    measurer: TextMeasurer,
) {
    val box = catalog[item.itemId]?.box ?: return
    val fp = box.footprintFacing(item.facing)

    for (dc in 0 until fp.width) {
        for (dr in 0 until fp.height) {
            val c = item.col + dc
            val r = item.row + dr
            val quad = Path().apply {
                val a = g.toScreenF(c.toFloat(), r.toFloat())
                val b = g.toScreenF(c + 1f, r.toFloat())
                val d = g.toScreenF(c + 1f, r + 1f)
                val e = g.toScreenF(c.toFloat(), r + 1f)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); lineTo(e.x, e.y); close()
            }
            drawPath(quad, FootprintFill)
            drawPath(quad, FootprintLine, style = Stroke(1f))
        }
    }

    val center = g.footprintCenter(item.col, item.row, fp)
    label(measurer, "${item.itemId} [${item.col},${item.row}] ${fp.width}x${fp.height}", center)
}

/** 작은 라벨. 배경을 깔아야 마룻바닥 위에서도 읽힌다. */
private fun DrawScope.label(measurer: TextMeasurer, text: String, at: Offset) {
    val style = TextStyle(color = Color.White, fontSize = 8.sp)
    val laid = measurer.measure(text, style)
    val w = laid.size.width.toFloat()
    val h = laid.size.height.toFloat()
    drawRect(LabelBg, Offset(at.x - w / 2f - 2f, at.y - h / 2f - 1f), Size(w + 4f, h + 2f))
    drawText(laid, topLeft = Offset(at.x - w / 2f, at.y - h / 2f))
}
