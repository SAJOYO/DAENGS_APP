package com.daengs.app.dogcard.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CardDao {
    /** IGNORE: 같은 카드를 두 번 넣어도 한 줄이다. id 를 앱이 만들므로 재시도가 안전하다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: DrawnCardRow)

    /**
     * 그 계정의 카드. **최근이 앞이다** — 도감 칸의 표지가 가장 최근에 뽑은 것이 된다.
     *
     * 로그인 안 하고 뽑은 카드(`appUserId IS NULL`)도 같이 준다. 아직 누구 것도 아닌
     * 카드라 지금 보는 사람의 것으로 친다 — 로그인하면 [claimOrphans] 가 도장을 찍는다.
     */
    @Query(
        "SELECT * FROM drawn_card WHERE appUserId IS NULL OR appUserId = :appUserId " +
            "ORDER BY drawnAtMillis DESC",
    )
    suspend fun forUser(appUserId: String?): List<DrawnCardRow>

    /** 로그인 전에는 주인 없는 카드만 본다. */
    @Query("SELECT * FROM drawn_card WHERE appUserId IS NULL ORDER BY drawnAtMillis DESC")
    suspend fun orphans(): List<DrawnCardRow>

    /** 로그인하면 주인 없던 카드에 도장을 찍는다. **남의 카드는 안 건드린다.** */
    @Query("UPDATE drawn_card SET appUserId = :appUserId WHERE appUserId IS NULL")
    suspend fun claimOrphans(appUserId: String)

    @Query("DELETE FROM drawn_card WHERE id = :id")
    suspend fun delete(id: String)

    /** 탈퇴할 때. 서버에 사본이 없으므로 여기서 지우면 정말 사라진다. */
    @Query("DELETE FROM drawn_card")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM drawn_card")
    suspend fun count(): Int
}
