package com.daengs.app.miniroom

import com.daengs.app.miniroom.art.ItemBoxes
import com.daengs.app.miniroom.art.ItemLabels
import com.daengs.app.miniroom.art.itemSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 붙박이(턴테이블)를 고정한다.
 *
 * #15 는 턴테이블을 [RoomDefaults.STARTER_MOVABLES] 자리에만 넣어서 **첫 설치에만**
 * 걸렸다. 이미 깔려 있던 폰은 저장된 배치를 읽으므로 영영 안 나온다 — 실기기에서
 * 그 상태를 봤고, 그걸 다시는 못 만들게 여기서 잡는다.
 */
class RoomFixtureTest {

    private val turntable = ItemIds.TURNTABLE

    private fun cellsOf(item: PlacedItem): Set<Pair<Int, Int>> {
        val fp = ItemBoxes.getValue(item.itemId).footprint
        return buildSet {
            for (dc in 0 until fp.width) {
                for (dr in 0 until fp.height) add(item.col + dc to item.row + dr)
            }
        }
    }

    @Test
    fun `붙박이는 인벤토리에 안 뜬다`() {
        for (id in RoomDefaults.FIXTURE_IDS) {
            assertFalse("$id 가 인벤토리 목록에 있다", id in RoomDefaults.INVENTORY_ORDER)
            assertFalse("$id 에 보유 개수가 있다", id in RoomDefaults.OWNED)
        }
    }

    @Test
    fun `붙박이는 손으로 못 잡는다`() {
        val specs = itemSpecs(RoomTheme.DEFAULT)
        for (id in RoomDefaults.FIXTURE_IDS) {
            assertFalse("$id 가 movable 이면 드래그·치우기가 다 열린다", specs.getValue(id).movable)
        }
        // 나머지는 그대로 잡혀야 한다 — 붙박이 규칙이 소품 전체를 얼리면 안 된다.
        assertTrue(specs.getValue(ItemIds.CABINET).movable)
    }

    @Test
    fun `첫 설치 배치에 붙박이가 들어 있다`() {
        val placed = RoomDefaults.STARTER_ROOM.filter { it.itemId == turntable }
        assertEquals(1, placed.size)
        assertEquals(5 to 0, placed.single().let { it.col to it.row })
    }

    @Test
    fun `저장본에 붙박이가 없으면 채워 넣는다`() {
        // 15 이전 저장본. 턴테이블이 없다.
        val saved = RoomDefaults.STARTER_MOVABLES
        val out = RoomDefaults.withFixtures(saved)

        val fixture = out.single { it.itemId == turntable }
        assertEquals(5 to 0, fixture.col to fixture.row)
        assertEquals("소품이 사라지면 안 된다", saved.size + 1, out.size)
    }

    @Test
    fun `번호가 이미 쓰이고 있어도 겹치지 않는다`() {
        // 15 이전에는 8번부터 사용자가 놓은 소품이 차지하고 있었다.
        val saved = RoomDefaults.STARTER_MOVABLES + PlacedItem(8L, ItemIds.BALL, 1, 1)
        val out = RoomDefaults.withFixtures(saved)

        assertEquals("번호가 겹치면 드래그가 엉뚱한 것을 집는다", out.size, out.map { it.instanceId }.toSet().size)
        assertTrue(out.any { it.instanceId == 8L && it.itemId == ItemIds.BALL })
    }

    @Test
    fun `사용자가 옮겨 둔 붙박이는 제자리로 돌아온다`() {
        val saved = listOf(PlacedItem(1L, turntable, 9, 9))
        val fixture = RoomDefaults.withFixtures(saved).single { it.itemId == turntable }
        assertEquals(5 to 0, fixture.col to fixture.row)
    }

    @Test
    fun `붙박이 칸을 막고 있던 소품은 인벤토리로 돌아간다`() {
        val fixtureCells = RoomDefaults.FIXTURES.flatMap { cellsOf(it) }.toSet()
        // 서랍장(4x2)을 col 5 로 끌어다 놓아 턴테이블 칸을 덮은 저장본.
        val intruder = PlacedItem(9L, ItemIds.CABINET, 5, 0)
        assertTrue("이 테스트가 의미가 있으려면 실제로 겹쳐야 한다",
            cellsOf(intruder).any { it in fixtureCells })

        val out = RoomDefaults.withFixtures(listOf(intruder))

        assertTrue("붙박이가 소품에 밀리면 안 된다", out.any { it.itemId == turntable })
        assertFalse("겹친 소품은 빠져야 한다", out.any { it.instanceId == 9L })
    }

    @Test
    fun `바닥에 깔리는 러그는 붙박이 밑에 그대로 둔다`() {
        // 러그는 flat 이라 칸을 안 막는다 — 치우면 사용자 입장에서는 그냥 없어진 것이다.
        val rug = PlacedItem(9L, ItemIds.RUG, 4, 0)
        assertTrue(ItemBoxes.getValue(ItemIds.RUG).flat)

        val out = RoomDefaults.withFixtures(listOf(rug))

        assertTrue("바닥 장식까지 걷어내면 안 된다", out.any { it.instanceId == 9L })
    }

    @Test
    fun `여러 번 통과시켜도 하나만 남는다`() {
        // 저장 → 불러오기가 반복되므로 멱등이어야 한다. 아니면 매번 하나씩 늘어난다.
        val once = RoomDefaults.withFixtures(RoomDefaults.STARTER_MOVABLES)
        val twice = RoomDefaults.withFixtures(once)
        assertEquals(once.size, twice.size)
        assertEquals(1, twice.count { it.itemId == turntable })
    }

    /**
     * #15 가 `ItemLabels` 를 안 채워서 인벤토리에 `turntable` 이 그대로 떴다.
     * `ItemLabels[id] ?: id` 라 조용히 넘어간다 — 그 자리를 여기서 잡는다.
     */
    @Test
    fun `인벤토리에 뜨는 소품은 전부 한글 이름이 있다`() {
        val missing = RoomDefaults.INVENTORY_ORDER.filter { it !in ItemLabels }
        assertTrue("ItemLabels 에 이름이 없다: $missing — 화면에는 id 가 그대로 뜬다",
            missing.isEmpty())
    }

    @Test
    fun `붙박이도 이름이 있다`() {
        // 인벤토리에는 안 뜨지만 개발자 패널(MiniRoomPreviews)이 같은 맵을 쓴다.
        for (id in RoomDefaults.FIXTURE_IDS) {
            assertTrue("$id 이름 없음", id in ItemLabels)
        }
    }
}
