package com.daengs.app.dogcard

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntRect
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홀더의 판단만 본다. Room 은 여기서 못 돈다 (이 저장소에 Robolectric 이 없다) —
 * `WalkHistoryTest` 가 페이크 로그로 도는 것과 같은 방식이다. SQL 이 맞는지는
 * 실기기에서 본다.
 */
class CardHolderTest {

    private class FakeCardStore(
        var rows: MutableList<DrawnCard> = mutableListOf(),
        var failOnLoad: Boolean = false,
        var failOnAdd: Boolean = false,
    ) : CardStore {
        var faces = 0
            private set

        override suspend fun all(appUserId: String?): List<DrawnCard> {
            if (failOnLoad) error("네트워크가 아니라 디스크가 안 될 수도 있다")
            return rows.filter { it.appUserId == null || it.appUserId == appUserId }
                .sortedByDescending { it.drawnAtMillis }
        }

        override suspend fun add(card: DrawnCard, face: Bitmap?) {
            if (failOnAdd) error("저장 실패")
            if (face != null) faces++
            rows.add(card)
        }

        override suspend fun remove(id: String) {
            rows.removeAll { it.id == id }
        }

        override suspend fun claimOrphans(appUserId: String) {
            rows = rows.map { if (it.appUserId == null) it.copy(appUserId = appUserId) else it }
                .toMutableList()
        }

        override suspend fun forgetEverything() {
            rows.clear()
        }

        override suspend fun isEmpty(): Boolean = rows.isEmpty()
    }

    private fun card(id: String, owner: String?, at: Long) = DrawnCard(
        id = id,
        appUserId = owner,
        templateId = "cabbage",
        dogId = "dog-1",
        dogName = "네옹",
        drawnAtMillis = at,
        codeText = "NEO-0824",
        core = IntRect(0, 0, 10, 10),
    )

    @Test
    fun `뽑으면 목록 맨 앞에 온다`() = runTest {
        val holder = CardHolder(FakeCardStore())
        val made = holder.draw(
            template = CARD_TEMPLATES.first(),
            face = null,
            core = IntRect(0, 0, 10, 10),
            dogId = "dog-1",
            dogName = "네옹",
            codeText = "NEO-0824",
            appUserId = "user-1",
            now = 1_000L,
        )
        assertNotNull(made)
        assertEquals(1, holder.cards.size)
        assertEquals(made!!.id, holder.cards.first().id)
    }

    /**
     * **실패해도 목록을 안 비운다.** 통째로 사라지면 사용자는 카드가 지워진 줄 안다
     * (`GaitHolder.load` 와 같은 원칙).
     */
    @Test
    fun `못 불러와도 들고 있던 것을 유지한다`() = runTest {
        val store = FakeCardStore(mutableListOf(card("a", "user-1", 10)))
        val holder = CardHolder(store)
        holder.load("user-1")
        assertEquals(1, holder.cards.size)

        store.failOnLoad = true
        holder.load("user-1")
        assertEquals("비우면 안 된다", 1, holder.cards.size)
        assertNotNull(holder.error)

        holder.clearError()
        assertNull(holder.error)
    }

    @Test
    fun `저장이 실패하면 목록에 안 얹는다`() = runTest {
        val holder = CardHolder(FakeCardStore(failOnAdd = true))
        val made = holder.draw(
            template = CARD_TEMPLATES.first(),
            face = null,
            core = IntRect(0, 0, 10, 10),
            dogId = null,
            dogName = "네옹",
            codeText = "NEO-0824",
            appUserId = null,
        )
        assertNull(made)
        assertTrue(holder.cards.isEmpty())
        assertNotNull(holder.error)
    }

    /** 로그인 없이 뽑은 카드만 도장을 찍는다. 남의 카드는 안 건드린다. */
    @Test
    fun `로그인하면 주인 없던 카드만 귀속된다`() = runTest {
        val store = FakeCardStore(
            mutableListOf(card("orphan", null, 20), card("남의것", "user-2", 10)),
        )
        val holder = CardHolder(store)
        holder.claimOrphans("user-1")

        assertEquals("user-1", store.rows.first { it.id == "orphan" }.appUserId)
        assertEquals("user-2", store.rows.first { it.id == "남의것" }.appUserId)
        assertEquals("남의 카드는 안 보인다", listOf("orphan"), holder.cards.map { it.id })
    }

    @Test
    fun `지우면 목록에서 빠진다`() = runTest {
        val store = FakeCardStore(mutableListOf(card("a", "user-1", 10), card("b", "user-1", 20)))
        val holder = CardHolder(store)
        holder.load("user-1")
        holder.remove("a")
        assertEquals(listOf("b"), holder.cards.map { it.id })
    }

    @Test
    fun `탈퇴하면 한 장도 안 남는다`() = runTest {
        val store = FakeCardStore(mutableListOf(card("a", "user-1", 10)))
        val holder = CardHolder(store)
        holder.load("user-1")
        holder.forgetEverything()
        assertTrue(holder.cards.isEmpty())
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `오늘 뽑은 장수로 남은 횟수를 센다`() = runTest {
        val zone = java.time.ZoneId.of("Asia/Seoul")
        fun at(h: Int) = java.time.LocalDateTime.of(2026, 9, 2, h, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val store = FakeCardStore(mutableListOf(card("a", "user-1", at(9)), card("b", "user-1", at(10))))
        val holder = CardHolder(store)
        holder.load("user-1")
        assertEquals(1, holder.drawsLeft(at(11), zone))
    }
}
