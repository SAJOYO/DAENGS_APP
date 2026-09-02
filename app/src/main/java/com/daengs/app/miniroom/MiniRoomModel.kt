package com.daengs.app.miniroom

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.daengs.app.miniroom.art.ItemBoxes
import com.daengs.app.miniroom.art.footprintFacing

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
     * **붙박이.** 사용자가 옮기지도 치우지도 못하고, 인벤토리에도 안 뜬다.
     *
     * 턴테이블이 여기 있는 이유: 뒷벽 창문 아래 (col 4~9 · row 0~1) 가 유일하게
     * 비어 있던 벽면이고 — 왼쪽은 서랍장[0,0], 오른쪽은 화분[11,0]과 개집[10,2]이
     * 잡고 있다 — **저쪽에 그 자리 규격을 불러서 그려 받은 그림**이다. 뚜껑을 연
     * 세로 316 짜리 아트라 다른 데로 옮기면 옆 가구를 파고들고, 그 벽면이 다시 빈다.
     *
     * 붙박이로 만드는 데 새 장치는 필요 없다. 세 군데가 알아서 따라온다:
     *  - [ItemIds.ALL] · [OWNED] 에서 빠지므로 인벤토리에 안 나온다
     *    (남은 개수는 `보유 - 놓인 수` 로 **계산**하는 값이라 분기가 필요 없다)
     *  - `movable = false` 라 [MiniRoomState.pickTopmost] 가 안 잡는다 →
     *    드래그·선택·돌리기/치우기 버튼이 한꺼번에 막힌다 (강아지와 같은 길)
     *  - 칸은 여전히 점유하므로 그 위에 다른 소품을 못 놓고 강아지도 못 지나간다
     *
     * **instanceId 는 여기 적지 않는다.** [withFixtures] 가 저장본에 없는 번호로
     * 매번 새로 붙인다 — #15 이전 저장본에는 8번을 이미 다른 소품이 쓰고 있다.
     */
    val FIXTURES: List<PlacedItem> = listOf(
        PlacedItem(0L, ItemIds.TURNTABLE, 5, 0),
    )

    val FIXTURE_IDS: Set<String> = FIXTURES.mapTo(HashSet()) { it.itemId }

    /**
     * 처음 방에 놓여 있는 소품. 붙박이는 [withFixtures] 가 얹는다.
     *
     * 좌표는 저쪽 목업의 `defaultPlacement` 를 12 격자로 환산한 것이다(16 -> 12, x0.75).
     * 러그는 하나만 깔아둔다 — 둘은 같은 자리를 쓰는 교체용이라 같이 깔면 겹친다.
     */
    val STARTER_MOVABLES: List<PlacedItem> = listOf(
        PlacedItem(1L, ItemIds.RUG, 4, 4),
        PlacedItem(2L, ItemIds.PLANT, 11, 0),
        PlacedItem(3L, ItemIds.DOGHOUSE, 10, 2),
        PlacedItem(4L, ItemIds.CABINET, 0, 0),
        PlacedItem(5L, ItemIds.BASKET, 2, 10),
        PlacedItem(6L, ItemIds.BOWLS, 8, 10),
        PlacedItem(7L, ItemIds.BALL, 11, 9),
    )

    val STARTER_ROOM: List<PlacedItem> = withFixtures(STARTER_MOVABLES)

    /**
     * 불러온 배치에 붙박이를 얹는다. **저장본을 읽을 때마다 통과시킨다.**
     *
     * #15 는 붙박이를 [STARTER_MOVABLES] 에만 넣어서 **첫 설치에만** 걸렸다.
     * 이미 앱이 깔려 있던 폰은 저장된 배치를 불러오므로 턴테이블이 영영 안 나온다
     * (실기기에서 확인했다 — 인벤토리에 소품으로 남아 있었다).
     *
     * [RoomCodec] 버전을 올려 저장본을 버리는 길도 있지만, 그러면 사용자가 꾸며 둔
     * 배치가 통째로 날아간다. 옛 데이터는 **읽는 데 문제가 없고 붙박이만 없는** 것이라
     * 버릴 이유가 없다.
     *
     * 하는 일 셋:
     *  1. 붙박이 종류의 소품은 저장본에서 걷어낸다 (사용자가 옮겨 둔 것을 제자리로)
     *  2. 붙박이 칸을 막고 있는 소품은 인벤토리로 돌려보낸다 (목록에서 빼면 남은
     *     개수가 저절로 하나 늘어난다). 바닥에 깔리는 러그는 칸을 안 막으니 둔다
     *  3. 남은 것 중 가장 큰 번호 다음부터 붙박이에 번호를 새로 붙인다
     */
    fun withFixtures(saved: List<PlacedItem>): List<PlacedItem> {
        val blocked = HashSet<IntOffset>()
        for (f in FIXTURES) blocked += cellsOf(f)

        val kept = saved.filter { item ->
            if (item.itemId in FIXTURE_IDS) return@filter false
            val box = ItemBoxes[item.itemId] ?: return@filter true
            if (box.flat) return@filter true
            cellsOf(item).none { it in blocked }
        }

        var next = (kept.maxOfOrNull { it.instanceId } ?: 0L) + 1L
        return kept + FIXTURES.map { it.copy(instanceId = next++) }
    }

    /** 이 소품이 점유하는 칸들. 바닥에 깔리는 아트도 여기서는 칸을 센다. */
    private fun cellsOf(item: PlacedItem): List<IntOffset> {
        val fp = ItemBoxes[item.itemId]?.footprintFacing(item.facing) ?: IntSize(1, 1)
        return buildList {
            for (dc in 0 until fp.width) {
                for (dr in 0 until fp.height) add(IntOffset(item.col + dc, item.row + dr))
            }
        }
    }

    /**
     * **등록한 강아지를 아직 못 받아 왔을 때** 방을 채우는 마리 수.
     *
     * 예전에는 이게 방의 마리 수였다. 지금은 사용자가 등록한 강아지가 서므로
     * ([DogHerd.setRoster]), 이 값은 목록을 기다리는 동안의 데모다 — 빈 방을 한 번
     * 깜빡이면 눈에 띈다.
     */
    const val DOG_COUNT = 4
}
