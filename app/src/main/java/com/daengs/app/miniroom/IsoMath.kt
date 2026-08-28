package com.daengs.app.miniroom

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

// ---------------------------------------------------------------------------
// 바닥 기하 — **방 PNG 에 맞춘 사각형**
//
// 예전에는 화면 한가운데 놓인 순수 아이소메트릭 마름모였다. `(col-row)*tw/2` 로
// 계산되는 규칙적인 격자라 방을 코드로 그릴 때는 맞았지만, 이제 방이 그림 한 장
// (modular_empty_room_v1.png, 1122x1402)이라서 **그림 속 바닥에 격자를 맞춰야** 한다.
//
// 그림의 바닥은 완전한 마름모가 아니다. 원근이 들어가서 네 변의 기울기가 제각각인
// 사각형이고, 양옆에는 짧은 수직 이음매까지 있어 실제 윤곽은 육각형이다.
// 그래서 네 꼭짓점을 **이중선형 보간(bilinear)** 해서 격자를 만든다.
//
// 좌표값은 frankie516c/dog-training-rag 의 room-physics.js 에서 그대로 가져왔다.
// 그쪽이 이 그림에 픽셀 단위로 재서 뽑은 값이고, 우리가 다시 잴 이유가 없다.
// (그 파일 주석에 적혀 있듯 그쪽 격자·슬라이드 로직 자체가 이 저장소에서 간 것이다.)
//
// ## 기본 단위 = 방 PNG 픽셀
//
// 예전 기본 단위는 "타일 64x32" 였다. 아트를 코드로 그리던 시절의 기준이다.
// 이제 아트가 전부 PNG 이므로 **1 기본 단위 = 방 PNG 의 1 픽셀**로 다시 잡는다.
// 소품 PNG 크기를 그대로 ArtBox 에 적어도 비율이 맞는다.
// ---------------------------------------------------------------------------

/** 방 규격. */
object RoomSpec {
    /**
     * 바닥 격자 칸 수.
     *
     * 저쪽 목업은 16 이었다. 16 이면 칸이 잘아서 소품을 세밀히 놓을 수 있지만
     * 우리 소품 footprint(러그 7x7 등)가 방을 거의 덮는다. 6 은 반대로 너무 성겨서
     * 소품 하나가 칸 하나를 통째로 먹는다. 12 는 그 중간이고, 16 대비 정확히
     * 0.75 배라 저쪽 좌표와 footprint 를 나눗셈 한 번으로 환산할 수 있다.
     */
    const val GRID = 12

    /** 방 PNG 원본 크기. 기본 단위가 이 픽셀이다. */
    const val ROOM_PNG_W = 1122f
    const val ROOM_PNG_H = 1402f

    /**
     * 바닥 타원을 얼마나 납작하게 그릴지. 그림자에만 쓴다.
     *
     * 예전에는 이 값이 방 전체 투영을 결정했지만, 이제 투영은 [FloorQuad] 네 꼭짓점이
     * 정한다. 남은 쓰임은 "바닥에 눕는 타원"의 납작한 정도 하나뿐이다.
     * 값은 그림에서 역산했다 — 뒤 모서리에서 오른쪽 모서리까지가 PNG 픽셀로
     * 가로 469 / 세로 224 라 약 2.1 이다.
     */
    const val TILE_RATIO = 2.1f

    /** 방의 가로:세로 비율. */
    const val ASPECT = ROOM_PNG_W / ROOM_PNG_H

    /**
     * 방 그림을 상자에 넣고 **사방에 남길 여백**. 1 이면 짧은 축에 딱 붙는다.
     *
     * 방 그림이 세로로 길어서(1122x1402) 가로로 넓은 상자에서는 세로가 먼저 걸린다.
     * 그래서 이 값이 실제로 여백을 만드는 쪽은 **세로**고, 가로에는 그보다 훨씬 큰
     * 여백이 남는다 — 그림 비율이 정해져 있는 한 피할 수 없다.
     *
     * 0.94 는 세로에 6% 를 내주는 값이다. 390x844 에서 위 8dp / 아래 10dp 로,
     * 방이 상자 모서리에 닿지 않는다는 게 보이는 최소치다. 1.0 으로 두면 위아래가
     * 상자에 딱 붙어서, 잘리지는 않아도 잘린 것처럼 보인다.
     *
     * 예전에는 반대 방향의 상수(OVERSCAN 1.10)가 있었다. 좌우 여백이 아까워 방을
     * 키우고 위아래를 잘라내던 값인데, 잘려 나가는 게 바닥 앞모서리와 울타리라
     * 얻는 것보다 잃는 게 컸다. 좌우 여백을 줄이고 싶으면 이 상수가 아니라 방
     * 상자 자체를 키워야 한다 (HomeScreen 의 CardSlotHeight).
     */
    const val INSET = 0.94f

    /**
     * 방 PNG 안의 **투명 여백** (원본 픽셀 기준).
     *
     * 그림은 1122x1402 지만 실제로 칠해진 영역은 알파 바운딩 박스
     * `(4, 39) - (1121, 1401)` 뿐이다. 위쪽 39px 이 통째로 비어 있다.
     *
     * 이 값을 빼지 않고 공칭 사각형을 가운데 두면, 눈에 보이는 위 여백이
     * 계산값보다 그 39px 만큼 넓어진다. 390x844 에서 계산값 8.2 / 실제 16.1 로
     * 두 배 가까이 벌어졌다 — [V_BIAS] 로 "가운데보다 살짝 위" 를 의도했는데
     * 실제로는 가운데보다 아래에 앉아 있었다.
     *
     * **여섯 테마가 모두 같은 값이다.** 우연이 아니라 만드는 방법이 그렇다 —
     * 한 장의 원본을 `build_room_theme_assets.ps1` 가 색만 바꿔 찍어내고
     * (테마 README: "실루엣과 크기를 그대로 유지한다"),
     * `tools/import_room_assets.py` 가 그대로 반입한다. 그래서 상수로 박는다.
     * 런타임에 1122x1402 알파를 훑는 건 테마를 바꿀 때마다 드는 값비싼 짓이다.
     *
     * 다만 **새 방 아트를 넣으면 반드시 다시 재야 한다.** 안 재면 방이 몇 px
     * 위아래로 밀릴 뿐 터지지는 않아서 알아채기 어렵다.
     * 재는 방법은 `tools/preview` 의 "알파 바운딩 박스" 측정.
     */
    const val ART_PAD_LEFT = 4f
    const val ART_PAD_TOP = 39f
    const val ART_PAD_RIGHT = 0f
    const val ART_PAD_BOTTOM = 0f

    /**
     * 남는 여백 중 **위쪽에 줄 몫**. 0 = 위 붙임, 0.5 = 가운데, 1 = 아래 붙임.
     *
     * 공칭 사각형이 아니라 [ART_PAD_TOP] 등을 뺀 **칠해진 영역** 기준이다.
     * 그래서 이 값이 곧 눈에 보이는 위:아래 여백의 비가 된다 — 0.40 이면 40:60.
     *
     * 아래를 더 주는 이유: 방에서 가장 앞이 바닥 앞모서리·흰 굽도리·울타리라
     * 여기가 카드에 닿으면 잘린 것처럼 보인다. 위쪽은 벽 몰딩뿐이라 좁아도
     * 티가 안 난다. 광학적으로도 물체는 기하학적 중앙보다 살짝 위에 있을 때
     * 가운데로 보인다.
     *
     * 하한이 있다. 이 값이 작아지면 공칭 사각형의 위쪽(투명한 39px 띠)이
     * 상자 밖으로 나가는데, 대략 0.30 아래로 내려가면 그 띠를 다 쓰고
     * 칠해진 부분이 잘리기 시작한다. 0.40 은 그 위로 한 뼘 여유가 있다.
     */
    const val V_BIAS = 0.40f
}

/**
 * 방 PNG 안에서 바닥이 차지하는 자리 — **그림 크기에 대한 백분율**이다.
 *
 * 백분율이라 화면 크기가 변해도 그대로 쓸 수 있다.
 */
object FloorQuad {
    /** 안쪽(뒤) 모서리. 격자 (0,0) 이다. */
    val back = Offset(56.6f, 54.1f)

    /** col 축 끝. 격자 (GRID, 0) */
    val right = Offset(98.4f, 70.1f)

    /** 바깥(앞) 모서리. 격자 (GRID, GRID) */
    val front = Offset(43f, 94.5f)

    /** row 축 끝. 격자 (0, GRID) */
    val left = Offset(0.9f, 68.8f)

    /**
     * 실제로 칠해진 바닥의 윤곽. **배치 격자(사각형)와 다르다.**
     *
     * 그림에서 바닥 양옆이 벽에 닿는 부분에 짧은 수직 이음매가 있어서, 윤곽은
     * 사각형이 아니라 육각형이다. 소품이 바닥 밖으로 삐져나왔는지 볼 때는
     * 배치 격자가 아니라 이쪽을 봐야 한다.
     */
    val outline = listOf(
        back,
        right,
        Offset(95.6f, 72.6f),
        front,
        Offset(2.8f, 72.7f),
        left,
    )
}

/**
 * 화면에 놓인 방의 기하 정보 전부.
 *
 * @param stage 방 PNG 가 그려지는 사각형(px). 모든 백분율 좌표가 이걸 기준으로 푼다
 * @param scale 기본 단위(=방 PNG 픽셀) 1 이 화면 px 로 몇인가
 */
@Immutable
data class RoomGeometry(
    val stage: Rect,
    val scale: Float,
) {
    /**
     * 칸 하나의 대략적인 가로 폭(px).
     *
     * 원근 때문에 칸 폭은 뒤에서 앞으로 갈수록 넓어진다 — 이건 **평균값**이라
     * 그림자처럼 정밀도가 필요 없는 곳에만 쓴다. 위치 계산에는 절대 쓰지 말 것.
     */
    val cell: Float
        get() = stage.width * (FloorQuad.right.x - FloorQuad.back.x) / 100f / RoomSpec.GRID

    /** 백분율 좌표 → 화면 px */
    private fun pct(p: Offset) = Offset(
        stage.left + p.x / 100f * stage.width,
        stage.top + p.y / 100f * stage.height,
    )

    /**
     * 격자 → 화면. 네 꼭짓점의 **이중선형 보간**이다.
     *
     * 마름모였다면 `(col-row)` 한 줄이면 됐지만, 원근이 들어간 사각형은 네 변의
     * 기울기가 달라서 네 꼭짓점을 다 섞어야 한다.
     */
    fun toScreenF(col: Float, row: Float): Offset {
        val u = col / RoomSpec.GRID
        val v = row / RoomSpec.GRID
        val iu = 1f - u
        val iv = 1f - v
        return pct(
            Offset(
                iu * iv * FloorQuad.back.x + u * iv * FloorQuad.right.x +
                    u * v * FloorQuad.front.x + iu * v * FloorQuad.left.x,
                iu * iv * FloorQuad.back.y + u * iv * FloorQuad.right.y +
                    u * v * FloorQuad.front.y + iu * v * FloorQuad.left.y,
            )
        )
    }

    fun toScreen(col: Int, row: Int): Offset = toScreenF(col.toFloat(), row.toFloat())

    /**
     * 화면 → 격자. [toScreenF] 의 역함수.
     *
     * 이중선형 사상은 닫힌 형태의 역함수가 없어서 **뉴턴 반복**으로 푼다.
     * 8 회면 화면 픽셀 오차 아래로 수렴한다.
     *
     * **값을 격자 안으로 자르지 않는다.** 드래그 코드가 "손가락이 바닥 밖으로
     * 나갔다"를 알아야 하기 때문이다 — 예전 `toInt()` 잘림 때문에 격자 밖 아이템이
     * (0,0) 으로 순간이동하던 버그와 같은 이유다.
     */
    fun toGridF(pos: Offset): Pair<Float, Float> {
        val target = Offset(
            (pos.x - stage.left) / stage.width * 100f,
            (pos.y - stage.top) / stage.height * 100f,
        )
        val h = FloorQuad.right - FloorQuad.back
        val vv = FloorQuad.left - FloorQuad.back
        val d = target - FloorQuad.back
        val det = h.x * vv.y - h.y * vv.x
        var u = (d.x * vv.y - d.y * vv.x) / det
        var v = (h.x * d.y - h.y * d.x) / det

        repeat(8) {
            val p = quadAt(u, v)
            val ex = target.x - p.x
            val ey = target.y - p.y
            val du = Offset(
                (1f - v) * (FloorQuad.right.x - FloorQuad.back.x) +
                    v * (FloorQuad.front.x - FloorQuad.left.x),
                (1f - v) * (FloorQuad.right.y - FloorQuad.back.y) +
                    v * (FloorQuad.front.y - FloorQuad.left.y),
            )
            val dv = Offset(
                (1f - u) * (FloorQuad.left.x - FloorQuad.back.x) +
                    u * (FloorQuad.front.x - FloorQuad.right.x),
                (1f - u) * (FloorQuad.left.y - FloorQuad.back.y) +
                    u * (FloorQuad.front.y - FloorQuad.right.y),
            )
            val jac = du.x * dv.y - du.y * dv.x
            if (abs(jac) < 1e-9f) return@repeat
            u += (ex * dv.y - ey * dv.x) / jac
            v += (du.x * ey - du.y * ex) / jac
        }
        return (u * RoomSpec.GRID) to (v * RoomSpec.GRID)
    }

    /** 격자 좌표(정수). 범위 검사에는 쓰지 말 것 — [toGridF] 를 쓴다. */
    fun toGrid(pos: Offset): Pair<Int, Int> {
        val (c, r) = toGridF(pos)
        return kotlin.math.floor(c).toInt() to kotlin.math.floor(r).toInt()
    }

    /** 발자국(footprint)이 차지하는 바닥 영역의 중심. */
    fun footprintCenter(col: Int, row: Int, footprint: IntSize): Offset =
        toScreenF(col + footprint.width / 2f, row + footprint.height / 2f)

    /** 격자 밖으로 나갔는지 — 반드시 실수값으로 검사한다. */
    fun isInside(colF: Float, rowF: Float): Boolean =
        colF >= 0f && rowF >= 0f && colF < RoomSpec.GRID && rowF < RoomSpec.GRID

    /**
     * 화면 높이 [y] 에서 **칠해진 바닥**이 가로로 걸치는 구간(px).
     *
     * 소품의 접지 상자가 바닥 그림 밖으로 나갔는지 볼 때 쓴다. 배치 격자는
     * 사각형이지만 그림 속 바닥은 육각형이라([FloorQuad.outline]) 둘이 다르다.
     * 걸치는 변이 둘 미만이면 그 높이에는 바닥이 없다는 뜻이라 null 이다.
     */
    fun floorRangeAt(y: Float): ClosedFloatingPointRange<Float>? {
        val corners = FloorQuad.outline.map(::pct)
        val hits = ArrayList<Float>(2)
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            if (a.y == b.y) continue
            if (y < min(a.y, b.y) || y > kotlin.math.max(a.y, b.y)) continue
            hits += a.x + (b.x - a.x) * ((y - a.y) / (b.y - a.y))
        }
        if (hits.size < 2) return null
        return hits.min()..hits.max()
    }

    /** 사각형이 칠해진 바닥 안에 온전히 들어가는가. 위·아래 변 둘 다 본다. */
    fun floorContains(box: Rect, gap: Float = 0f): Boolean {
        val top = floorRangeAt(box.top) ?: return false
        val bottom = floorRangeAt(box.bottom) ?: return false
        return box.left >= kotlin.math.max(top.start, bottom.start) + gap &&
            box.right <= min(top.endInclusive, bottom.endInclusive) - gap
    }

    private fun quadAt(u: Float, v: Float): Offset {
        val iu = 1f - u
        val iv = 1f - v
        return Offset(
            iu * iv * FloorQuad.back.x + u * iv * FloorQuad.right.x +
                u * v * FloorQuad.front.x + iu * v * FloorQuad.left.x,
            iu * iv * FloorQuad.back.y + u * iv * FloorQuad.right.y +
                u * v * FloorQuad.front.y + iu * v * FloorQuad.left.y,
        )
    }

    companion object {
        /**
         * 주어진 상자 안에 방 PNG 를 통째로 넣는다. 비율은 그림 비율 그대로.
         *
         * 가로·세로 중 더 빡빡한 쪽으로 맞춘 뒤 [RoomSpec.INSET] 만큼 물러나므로
         * **어느 쪽으로도 넘치지 않는다.** 잘림이 없는 건 값을 잘 골라서가 아니라
         * 이 식의 성질이다 — s 는 contain 을 넘을 수 없다.
         */
        fun of(widthPx: Float, heightPx: Float): RoomGeometry {
            val contain = min(widthPx / RoomSpec.ROOM_PNG_W, heightPx / RoomSpec.ROOM_PNG_H)
            val s = contain * RoomSpec.INSET
            val w = RoomSpec.ROOM_PNG_W * s
            val h = RoomSpec.ROOM_PNG_H * s
            // 놓는 기준은 공칭 사각형이 아니라 **칠해진 부분**이다. 그림 위쪽 39px 이
            // 투명이라(RoomSpec.ART_PAD_TOP) 공칭으로 재면 눈에 보이는 여백이 어긋난다.
            val artW = w - (RoomSpec.ART_PAD_LEFT + RoomSpec.ART_PAD_RIGHT) * s
            val artH = h - (RoomSpec.ART_PAD_TOP + RoomSpec.ART_PAD_BOTTOM) * s
            val left = (widthPx - artW) / 2f - RoomSpec.ART_PAD_LEFT * s
            val top = (heightPx - artH) * RoomSpec.V_BIAS - RoomSpec.ART_PAD_TOP * s
            return RoomGeometry(Rect(left, top, left + w, top + h), s)
        }

        /** 가로만 아는 경우 — 그림 비율대로 세로를 잡는다. */
        fun of(widthPx: Float): RoomGeometry = of(widthPx, widthPx / RoomSpec.ASPECT)
    }
}
