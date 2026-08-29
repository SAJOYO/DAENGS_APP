package com.daengs.app.miniroom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import com.daengs.app.miniroom.art.ItemCatalog
import com.daengs.app.miniroom.art.footprintFacing
import kotlin.math.floor

/** 화면 좌표를 아이템 아트 상자 안의 로컬 좌표(기본 단위)로 되돌린다. */
fun RoomGeometry.toArtLocal(p: Offset, item: PlacedItem, catalog: ItemCatalog): Offset? {
    val box = catalog[item.itemId]?.box ?: return null
    val c = footprintCenter(item.col, item.row, box.footprintFacing(item.facing))
    // 그리기와 같은 배율을 써야 한다. 그림은 가로로 늘어났는데 판정만 균일하면
    // 손가락이 그림 위에 있는데도 안 잡힌다.
    val left = c.x - box.anchor.x * scaleX
    val top = c.y - box.anchor.y * scale
    val local = Offset((p.x - left) / scaleX, (p.y - top) / scale)
    // 좌우 반전된 아이템은 터치 판정도 같이 뒤집어야 그림과 맞는다.
    return if (item.facing == 1) {
        Offset(2f * box.anchor.x - local.x, local.y)
    } else {
        local
    }
}

/**
 * 손가락 아래에서 **가장 앞에 있는** 아이템을 고른다.
 *
 * [toGrid] 로 발밑 타일만 보면 안 된다 — 화분처럼 키가 큰 아트는 자기 타일보다
 * 위로 삐져나오므로, 그림을 눌렀는데 뒤쪽 빈 칸이 잡힌다.
 * 그리기 순서의 **역순**으로 훑어서, 눈에 위에 있는 것이 먼저 잡히게 한다.
 */
fun List<PlacedItem>.pickTopmost(
    p: Offset,
    g: RoomGeometry,
    catalog: ItemCatalog,
): PlacedItem? = asReversed().firstOrNull { item ->
    val art = catalog[item.itemId] ?: return@firstOrNull false
    if (!art.movable) return@firstOrNull false
    val local = g.toArtLocal(p, item, catalog) ?: return@firstOrNull false
    art.box.touchArea.contains(local)
}

/**
 * 붙박이를 눌렀는지 본다.
 *
 * **[pickTopmost] 로는 못 잡는다.** 붙박이는 `movable = false` 라 거기서 걸러지는데,
 * 그게 드래그·선택·치우기를 한꺼번에 막아 주는 장치라 풀 수도 없다. 그래서 "만질 수
 * 있는가" 와 "누를 수 있는가" 를 갈라, 누르는 쪽만 따로 본다.
 *
 * 판정 자체는 [pickTopmost] 와 같은 것을 쓴다 — 발밑 타일이 아니라 **그림의 터치
 * 영역**이다. 턴테이블은 뚜껑을 연 세로 316 짜리라 제 타일보다 한참 위로 삐져나온다.
 */
fun List<PlacedItem>.pickFixture(
    p: Offset,
    g: RoomGeometry,
    catalog: ItemCatalog,
    itemId: String,
): PlacedItem? = asReversed().firstOrNull { item ->
    if (item.itemId != itemId) return@firstOrNull false
    val art = catalog[item.itemId] ?: return@firstOrNull false
    val local = g.toArtLocal(p, item, catalog) ?: return@firstOrNull false
    art.box.touchArea.contains(local)
}

@Stable

class MiniRoomState internal constructor(initial: List<PlacedItem>) {

    val items: SnapshotStateList<PlacedItem> = initial.toMutableStateList()

    var drag: DragState? by mutableStateOf(null)
        private set

    /** 편집 모드에서 선택된 가구. 이 아이템 옆에 돌리기/치우기 버튼이 뜬다. */
    var selectedId: Long? by mutableStateOf(null)
        private set

    fun select(id: Long?) {
        selectedId = id
    }

    private var nextId: Long = (initial.maxOfOrNull { it.instanceId } ?: 0L) + 1L

    // -- 조회 -----------------------------------------------------------------

    /**
     * 그리기 순서. **반드시 draw 람다 안에서 호출할 것.**
     * 바깥에서 미리 계산해 캡처하면 상태 변화가 draw 를 무효화하지 않는다.
     *
     * 레이어를 먼저 가르고, 그 안에서 CONTEXT.md 4번 섹션대로 col + row 로 정렬한다.
     *
     * | 레이어 | 대상 | 이유 |
     * |---|---|---|
     * | 0 | 바닥 장식 (러그) | 두께가 0 이라 바닥에 선 물건을 가릴 수 없다. 앞칸에 있어도 뒤로 간다. |
     * | 1 | 보통 가구 | 여기서만 col+row 정렬이 의미가 있다. |
     * | 2 | 강아지 | 화면의 주인공. 무엇에도 가리지 않는다. |
     *
     * 레이어 0 을 따로 두지 않으면, 앞칸으로 옮긴 러그가 뒤칸의 공이나 강아지를
     * 덮어버린다 — 깊이 계산은 맞지만 눈에는 명백히 틀려 보인다.
     */
    fun drawOrder(catalog: ItemCatalog): List<PlacedItem> =
        items.sortedWith(
            compareBy(
                { layerOf(it, catalog) },
                { depthOf(it, catalog) },
            )
        )

    /**
     * 앞뒤 정렬 키. 드래그 중에는 **놓일 자리** 기준이라 손을 떼기 전에 앞뒤가 바뀐다.
     *
     * 강아지도 같은 자로 재야 해서 public 이다 ([DogActor.depthCell]).
     * 둘이 다른 자를 쓰면 뒤에 있는 강아지가 가구 위에 그려진다.
     */
    fun depthOf(item: PlacedItem, catalog: ItemCatalog): Int {
        val d = drag
        val fp = catalog[item.itemId]?.box?.footprintFacing(item.facing)
        val col = if (d != null && item.instanceId == d.instanceId) d.targetCol else item.col
        val row = if (d != null && item.instanceId == d.instanceId) d.targetRow else item.row
        return depthKey(col, row, fp?.width ?: 1, fp?.height ?: 1)
    }

    fun layerOf(item: PlacedItem, catalog: ItemCatalog): Int {
        val box = catalog[item.itemId]?.box ?: return LAYER_ITEM
        return when {
            box.alwaysOnTop -> LAYER_TOP
            box.flat -> LAYER_FLOOR
            else -> LAYER_ITEM
        }
    }

    /**
     * 점유된 칸. 바닥에 깔린 아트(러그·강아지침대)는 칸을 막지 않는다.
     *
     * 배치 충돌뿐 아니라 **강아지 통행 판정**도 이 집합을 쓴다 ([DogHerd.update]).
     * 그래서 아이템 하나를 `flat` 으로 바꾸면 "그 위에 물건을 놓을 수 있다"와
     * "강아지가 그 위를 지나갈 수 있다"가 한꺼번에 따라온다.
     */
    fun occupiedCells(exclude: Long?, catalog: ItemCatalog): Set<IntOffset> {
        val out = HashSet<IntOffset>()
        for (item in items) {
            if (item.instanceId == exclude) continue
            val box = catalog[item.itemId]?.box ?: continue
            if (box.flat) continue
            val fp = box.footprintFacing(item.facing)
            for (dc in 0 until fp.width) {
                for (dr in 0 until fp.height) {
                    out += IntOffset(item.col + dc, item.row + dr)
                }
            }
        }
        return out
    }

    fun canPlace(
        itemId: String,
        col: Int,
        row: Int,
        ignore: Long?,
        catalog: ItemCatalog,
        facing: Int = 0,
    ): Boolean {
        val box = catalog[itemId]?.box ?: return false
        val fp = box.footprintFacing(facing)
        if (col < 0 || row < 0) return false
        if (col + fp.width > RoomSpec.GRID) return false
        if (row + fp.height > RoomSpec.GRID) return false
        if (box.flat) return true
        val taken = occupiedCells(ignore, catalog)
        for (dc in 0 until fp.width) {
            for (dr in 0 until fp.height) {
                if (IntOffset(col + dc, row + dr) in taken) return false
            }
        }
        return true
    }

    // -- 드래그 ---------------------------------------------------------------

    fun beginDrag(pointer: Offset, g: RoomGeometry, catalog: ItemCatalog): Boolean {
        val hit = drawOrder(catalog).pickTopmost(pointer, g, catalog) ?: return false
        drag = DragState(
            instanceId = hit.instanceId,
            startCol = hit.col,
            startRow = hit.row,
            startPointer = pointer,
            pointer = pointer,
            targetCol = hit.col,
            targetRow = hit.row,
            valid = true,
        )
        return true
    }

    fun updateDrag(pointer: Offset, g: RoomGeometry, catalog: ItemCatalog) {
        val d = drag ?: return
        val item = items.firstOrNull { it.instanceId == d.instanceId } ?: return
        val box = catalog[item.itemId]?.box ?: return

        // 칸 단위 이동량으로 계산한다. 아이템 중심을 toGrid 에 넣는 방식보다
        // 발자국 크기·기준점에 휘둘리지 않고, 잡은 지점이 그대로 유지된다.
        val (c0, r0) = g.toGridF(d.startPointer)
        val (c1, r1) = g.toGridF(pointer)
        val dCol = floor(c1).toInt() - floor(c0).toInt()
        val dRow = floor(r1).toInt() - floor(r0).toInt()

        // 격자 밖으로 나가면 가장자리에 붙인다 — 유령 표시가 항상 "실제로 놓일 칸"이
        // 되므로, 손을 떼기 전에 결과가 눈에 보인다.
        val fp = box.footprintFacing(item.facing)
        val maxCol = RoomSpec.GRID - fp.width
        val maxRow = RoomSpec.GRID - fp.height
        val targetCol = (d.startCol + dCol).coerceIn(0, maxCol)
        val targetRow = (d.startRow + dRow).coerceIn(0, maxRow)

        drag = d.copy(
            pointer = pointer,
            targetCol = targetCol,
            targetRow = targetRow,
            valid = canPlace(
                item.itemId, targetCol, targetRow, item.instanceId, catalog, item.facing,
            ),
        )
    }

    /** 놓기. 유효하면 스냅, 이미 찬 칸이면 원래 자리로 되돌린다. */
    fun endDrag(catalog: ItemCatalog) {
        val d = drag
        drag = null
        if (d == null) return
        if (!d.valid) return
        val i = items.indexOfFirst { it.instanceId == d.instanceId }
        if (i < 0) return
        // copy 로 통째 교체해야 SnapshotStateList 가 쓰기를 감지한다.
        items[i] = items[i].copy(col = d.targetCol, row = d.targetRow)
    }

    /**
     * 방향 돌리기. 지금은 좌우 반전 2방향뿐이라 누를 때마다 토글된다.
     * 방향별 그림이 늘어나면 여기 나머지 연산만 바꾸면 된다.
     *
     * **다칸 아이템은 발자국까지 같이 돈다** ([footprintFacing]). 그래서 돌린 결과가
     * 격자를 넘거나 옆 물건과 겹칠 수 있다:
     *  - 격자를 넘으면 안쪽으로 밀어 넣는다 (구석의 침대를 못 돌리면 답답하다)
     *  - 그래도 다른 물건과 겹치면 **아무 일도 하지 않는다.** 반쯤 겹친 채로
     *    두는 것보다 안 도는 게 낫다
     *
     * @return 실제로 돌았으면 true
     */
    fun rotate(instanceId: Long, catalog: ItemCatalog): Boolean {
        val i = items.indexOfFirst { it.instanceId == instanceId }
        if (i < 0) return false
        val item = items[i]
        if (item.itemId in RoomDefaults.FIXTURE_IDS) return false
        val box = catalog[item.itemId]?.box ?: return false
        val next = (item.facing + 1) % PlacedItem.FACINGS

        val fp = box.footprintFacing(next)
        val col = item.col.coerceIn(0, (RoomSpec.GRID - fp.width).coerceAtLeast(0))
        val row = item.row.coerceIn(0, (RoomSpec.GRID - fp.height).coerceAtLeast(0))
        if (!canPlace(item.itemId, col, row, item.instanceId, catalog, next)) return false

        items[i] = item.copy(col = col, row = row, facing = next)
        return true
    }

    fun cancelDrag() {
        drag = null
    }

    // -- 인벤토리 -------------------------------------------------------------

    /** 인벤토리에 남은 개수 = 보유 - 방에 놓인 수. 별도 카운터를 두지 않는다. */
    fun availableCount(itemId: String): Int =
        (RoomDefaults.OWNED[itemId] ?: 0) - items.count { it.itemId == itemId }

    /**
     * 새 아이템을 놓을 칸. **방 한가운데에서 가까운 빈 칸**을 고른다.
     *
     * 뒤쪽부터 채우면 (0,0) 이 화면상 강아지 바로 뒤라 새 아이템이 가려지고,
     * 앞쪽부터 채우면 인벤토리 패널에 덮인다. 가운데가 둘 다 피하는 자리다.
     */
    fun firstFreeCell(itemId: String, catalog: ItemCatalog): Pair<Int, Int>? {
        val box = catalog[itemId]?.box ?: return null
        val mid = (RoomSpec.GRID - 1) / 2f
        val cells = ArrayList<Pair<Int, Int>>()
        for (c in 0..(RoomSpec.GRID - box.footprint.width)) {
            for (r in 0..(RoomSpec.GRID - box.footprint.height)) cells += c to r
        }
        return cells
            .sortedWith(
                compareBy(
                    { (it.first - mid) * (it.first - mid) + (it.second - mid) * (it.second - mid) },
                    { it.first + it.second },
                )
            )
            .firstOrNull { canPlace(itemId, it.first, it.second, null, catalog) }
    }

    /** 인벤토리 → 방. 남은 개수가 없거나 빈 칸이 없으면 false. */
    fun placeFromInventory(itemId: String, catalog: ItemCatalog): Boolean {
        if (availableCount(itemId) <= 0) return false
        val (c, r) = firstFreeCell(itemId, catalog) ?: return false
        items += PlacedItem(nextId++, itemId, c, r)
        return true
    }

    /**
     * 방 → 인벤토리. 붙박이는 안 빠진다.
     *
     * `movable = false` 라 [pickTopmost] 부터 안 잡히므로 화면에서는 여기까지
     * 올 길이 없다. 그래도 막아 두는 건, 나중에 붙박이를 고를 수 있게 만들면
     * (예: 개발자 패널) 여기가 조용히 뚫리기 때문이다.
     */
    fun returnToInventory(instanceId: Long) {
        if (isFixture(instanceId)) return
        items.removeAll { it.instanceId == instanceId }
        if (selectedId == instanceId) selectedId = null
    }

    /** 붙박이인가. [RoomDefaults.FIXTURES] 가 유일한 출처다. */
    fun isFixture(instanceId: Long): Boolean =
        items.firstOrNull { it.instanceId == instanceId }?.itemId in RoomDefaults.FIXTURE_IDS

    // -- 편집 -----------------------------------------------------------------

    fun add(itemId: String, col: Int, row: Int, catalog: ItemCatalog): Boolean {
        if (!canPlace(itemId, col, row, null, catalog)) return false
        items += PlacedItem(nextId++, itemId, col, row)
        return true
    }

    fun remove(instanceId: Long) {
        if (isFixture(instanceId)) return
        items.removeAll { it.instanceId == instanceId }
    }

    companion object {
        // 강아지 몸 반경은 여기 없다. 견종마다 다르므로 [DogBreed.bodyRadius] 가 갖는다.

        const val LAYER_FLOOR = 0
        const val LAYER_ITEM = 1
        const val LAYER_TOP = 2

        val Saver = listSaver<MiniRoomState, List<Any>>(
            save = { state ->
                state.items.map { listOf(it.instanceId, it.itemId, it.col, it.row, it.facing) }
            },
            restore = { saved ->
                MiniRoomState(
                    saved.map {
                        PlacedItem(
                            it[0] as Long,
                            it[1] as String,
                            it[2] as Int,
                            it[3] as Int,
                            it[4] as Int,
                        )
                    }
                )
            },
        )
    }
}

/**
 * 저장 모델이 (itemId, col, row) 세 개의 원시값뿐이라 회전 시 복원이 사실상 공짜다.
 * 그래서 remember 가 아니라 rememberSaveable 을 쓴다 — 화면 돌렸다고 방이
 * 초기화되면 배치를 검증하는 데모로서 곤란하다.
 */
@Composable
fun rememberMiniRoomState(
    initial: List<PlacedItem> = RoomDefaults.STARTER_ROOM,
): MiniRoomState = rememberSaveable(saver = MiniRoomState.Saver) { MiniRoomState(initial) }
