package com.daengs.app.dogcard

import android.graphics.Bitmap
import com.daengs.app.dogcard.store.CardDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 뽑은 카드를 어디에 두는가. **화면은 이 뒤가 무엇인지 모른다.**
 *
 * `GaitHolder` 와 같은 결이다 — 지금은 기기 안에만 있고, 서버가 붙으면 이 구현만
 * 갈아 끼우면 된다. 화면은 [CardHolder] 만 보고 출처를 묻지 않는다.
 */
interface CardStore {
    suspend fun all(appUserId: String?): List<DrawnCard>

    /** [face] 가 null 이면 그림 없이 줄만 남는다 — 저쪽이 이미 그린 카드를 쓰는 경우다. */
    suspend fun add(card: DrawnCard, face: Bitmap?)

    suspend fun remove(id: String)

    /** 로그인 없이 뽑아 둔 카드를 그 계정 것으로 만든다. */
    suspend fun claimOrphans(appUserId: String)

    /** 탈퇴. 표와 그림을 같이 지운다. */
    suspend fun forgetEverything()
}

class RoomCardStore(
    private val dao: CardDao,
    private val files: CardFiles,
) : CardStore {

    override suspend fun all(appUserId: String?): List<DrawnCard> = withContext(Dispatchers.IO) {
        dao.forUser(appUserId).map(DrawnCard::of)
    }

    /**
     * **그림을 먼저 쓰고 줄을 넣는다.** 반대로 하면 그림이 없는 줄이 남아서, 도감에
     * 칸은 채워졌는데 얼굴이 안 나오는 카드가 생긴다. 그림만 남는 쪽은 다음 탈퇴나
     * 삭제 때 같이 쓸려서 덜 나쁘다.
     */
    override suspend fun add(card: DrawnCard, face: Bitmap?) {
        if (face != null) files.writeFace(card.id, face)
        withContext(Dispatchers.IO) { dao.insert(card.toRow()) }
    }

    override suspend fun remove(id: String) {
        withContext(Dispatchers.IO) { dao.delete(id) }
        files.deleteFace(id)
    }

    override suspend fun claimOrphans(appUserId: String) =
        withContext(Dispatchers.IO) { dao.claimOrphans(appUserId) }

    override suspend fun forgetEverything() {
        withContext(Dispatchers.IO) { dao.deleteAll() }
        files.deleteAll()
    }
}
