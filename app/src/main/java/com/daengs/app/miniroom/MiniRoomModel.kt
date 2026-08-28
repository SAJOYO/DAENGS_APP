package com.daengs.app.miniroom

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * 방에 놓인 아이템 하나.
 *
 * CONTEXT.md 4번 섹션대로 **격자 좌표만** 저장한다.
 * 화면 좌표는 매번 [RoomGeometry.footprintCenter] 로 계산하고,
 * 앞뒤 순서는 [depth] 로 결정되므로 저장하지 않는다.
 */
@Immutable
data class PlacedItem(
    val instanceId: Long,
    val itemId: String,
    val col: Int,
    val row: Int,
    /**
     * 바라보는 방향. 0 = 기본, 1 = 좌우 반전.
     *
     * 2D 아이소메트릭에서 "회전"은 이미지를 돌리는 게 아니라 **방향별 그림을
     * 따로 두는 것**이다. 화면에서 그냥 돌리면 바닥 평면을 벗어나 기울어 보인다.
     * 지금은 도형 아트라 좌우 반전(2방향)까지만 공짜로 된다.
     * 나중에 3D 프리렌더 PNG 를 쓰면 90도씩 4장을 뽑아 0..3 으로 늘리면 된다.
     */
    val facing: Int = 0,
) {
    /** 앞뒤 정렬 키. 화면 y 좌표가 아니라 col + row 다. */
    val depth: Int get() = col + row

    companion object {
        /** 지금 표현 가능한 방향 수. 도형 아트라 좌우 반전 2방향. */
        const val FACINGS = 2
    }
}

/** 드래그 중인 한 아이템의 임시 상태. 손을 떼면 사라진다. */
@Immutable
data class DragState(
    val instanceId: Long,
    val startCol: Int,
    val startRow: Int,
    val startPointer: Offset,
    val pointer: Offset,
    val targetCol: Int,
    val targetRow: Int,
    val valid: Boolean,
) {
    /** 손가락이 움직인 만큼. 스냅 전 자유 좌표를 그릴 때 쓴다. */
    val visualDelta: Offset get() = pointer - startPointer

    /** 드래그 중에는 "놓일 자리"의 깊이로 정렬해야 앞뒤가 실시간으로 바뀐다. */
    val targetDepth: Int get() = targetCol + targetRow
}

/**
 * 앞뒤 정렬 키. 1x1 이면 CONTEXT.md 4번대로 col + row 다.
 *
 * 다칸 아이템은 **가장 앞 칸** 기준이어야 한다. 2x1 침대를 col+row 로 재면
 * 자기 앞칸에 있는 물건보다 뒤로 밀려서 침대가 그 물건에 가려진다.
 * fw = fh = 1 이면 col + row 로 그대로 줄어들어 기존 동작은 안 바뀐다.
 */
fun depthKey(col: Int, row: Int, fw: Int, fh: Int): Int = (col + fw - 1) + (row + fh - 1)

object RoomDefaults {
    // 강아지 아트 키는 여기 없다 — 털색마다 하나씩이라
    // [com.daengs.app.miniroom.art.DogCoat.ALL] 이 유일한 출처다.

    /**
     * 보유 개수. 인벤토리에 남은 수 = 여기 수 - 방에 놓인 수 로 **계산**한다.
     * 별도 카운터를 들고 있으면 방 상태와 어긋날 여지가 생긴다.
     */
    val OWNED: Map<String, Int> = mapOf(
        ItemIds.RUG to 1,
        ItemIds.RUG_CREAM to 1,
        ItemIds.DOGHOUSE to 1,
        ItemIds.CABINET to 1,
        ItemIds.BASKET to 1,
        ItemIds.BOWLS to 1,
        ItemIds.PLANT to 2,
        ItemIds.BALL to 2,
    )

    /** 인벤토리에 보여줄 순서. 강아지는 아이템이 아니므로 빠진다. */
    val INVENTORY_ORDER: List<String> = ItemIds.ALL

    /**
     * 처음 방에 놓여 있는 소품.
     *
     * 좌표는 저쪽 목업의 `defaultPlacement` 를 12 격자로 환산한 것이다(16 -> 12, x0.75).
     * 러그는 하나만 깔아둔다 — 둘은 같은 자리를 쓰는 교체용이라 같이 깔면 겹친다.
     */
    val STARTER_ROOM: List<PlacedItem> = listOf(
        PlacedItem(1L, ItemIds.RUG, 4, 4),
        PlacedItem(2L, ItemIds.PLANT, 11, 0),
        PlacedItem(3L, ItemIds.DOGHOUSE, 10, 2),
        PlacedItem(4L, ItemIds.CABINET, 0, 0),
        PlacedItem(5L, ItemIds.BASKET, 2, 10),
        PlacedItem(6L, ItemIds.BOWLS, 8, 10),
        PlacedItem(7L, ItemIds.BALL, 11, 9),
        // 턴테이블 자리는 **[5,0] 을 비워 둔다.**
        //
        // 저쪽에서 그림을 그려 주기로 했고, 오면 여기 놓는다. 뒷벽 창문 아래
        // (col 4~9 · row 0~1)가 유일하게 비어 있는 벽면이다 — 왼쪽은 서랍장[0,0],
        // 오른쪽은 화분[11,0]과 개집[10,2]이 이미 잡고 있다.
        //
        // 저쪽에 부탁할 규격 (docs/asset-workflow.md 기준):
        //   발자국 2x2 칸, 아트 상자 대략 200x180 (방 PNG 1122x1402 기준 픽셀)
        //   기준점은 바닥 중심 (anchor 0.50, 0.78 — 개집·바구니와 같은 결)
        //   테마 6종 리컬러, 알파 있는 PNG. 판이 도는 걸 보이려면 스프라이트 시트도 된다
        //
        // 놓을 때 필요한 것은 `ItemIds` 에 id 추가 · `RoomTheme` 에 테마별 그림 ·
        // `ItemBoxes` 에 상자 · `OWNED` 에 개수 한 줄씩이다.
    )

    /**
     * 방에 돌아다니는 강아지 수.
     *
     * 견종이 16종이라 [DogBreed.ALL] 을 차례로 돌려 쓴다 — 이 값만 올리면 그만큼
     * 다른 견종이 나온다. 넘으면 앞에서부터 다시 쓴다.
     */
    const val DOG_COUNT = 4
}
