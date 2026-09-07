package com.daengs.app.dogcard

import android.graphics.BitmapFactory

/**
 * 카드를 서버와 맞춘다. **로그인 직후 한 번 돈다.**
 *
 * 무엇을 할지는 [planCardSync] 가 정하고, 여기는 그것을 실행만 한다.
 *
 * ⚠️ **실패해도 조용하다.** 도감은 기기 것만으로도 온전히 돌아간다 — 서버는 백업과
 *    새 기기 복원을 위한 것이라, 못 맞췄다고 팝업을 띄우면 서버 저장소가 아직 안
 *    켜진 동안(503) 로그인마다 뜬다.
 *
 * ⚠️ **둘러보기 카드는 여기서 안 올린다.** `appUserId` 가 null 인 카드는 서버에 올릴
 *    자리가 없다. 로그인하면 `CardStore.claimOrphans` 가 먼저 그 카드들을 계정 것으로
 *    만들고, 그다음 이 동기화가 돈다 — 순서가 뒤집히면 방금 로그인한 사람의 카드가
 *    안 올라간다.
 */
class CardSyncRunner(
    private val store: CardStore,
    private val files: CardFiles,
    private val accessToken: suspend () -> String?,
) {

    /**
     * 한 바퀴 돈다.
     *
     * @return 무엇이 바뀌었나. 바뀐 게 있으면 부르는 쪽이 목록을 다시 읽는다.
     */
    suspend fun syncOnce(appUserId: String?): Boolean {
        if (appUserId == null || !CardApi.configured) return false
        val token = accessToken() ?: return false

        val local = runCatching { store.all(appUserId) }.getOrNull() ?: return false
        val remote = CardApi.list(token).getOrNull() ?: return false

        val plan = planCardSync(local.map { it.id }, remote.map { it.id })
        if (plan.isEmpty) return false

        val byId = local.associateBy { it.id }
        var changed = false

        for (id in plan.upload) {
            if (upload(token, byId[id] ?: continue)) changed = true
        }
        for (id in plan.download) {
            if (download(token, remote.first { it.id == id }, appUserId)) changed = true
        }
        return changed
    }

    /**
     * 한 장을 올린다. 얼굴이 아직 없으면 그림까지.
     *
     * **409 면 아무것도 안 한다.** 남이 가진 id 라는 뜻인데, 여기서 id 를 새로 만들어
     * 다시 올리면 **같은 카드가 두 장**이 된다 — 기기의 그 카드는 그대로 두고
     * 다음에 사람이 보고 정하는 편이 낫다. 실제로 일어나려면 UUID 가 부딪혀야 해서
     * 사실상 안 생긴다.
     */
    private suspend fun upload(token: String, card: DrawnCard): Boolean {
        val upserted = CardApi.upsert(token, card).getOrNull() ?: return false
        val ticket = upserted.faceUpload ?: return upserted.created

        val png = runCatching { files.faceFile(card.id).takeIf { it.exists() }?.readBytes() }
            .getOrNull()
        // 얼굴 파일이 없는 카드도 있다 (저쪽이 이미 그린 카드). 줄만 올라가면 된다.
            ?: return upserted.created

        if (CardApi.uploadFace(ticket, png).isFailure) return upserted.created
        CardApi.confirmFace(token, card.id)
        return upserted.created
    }

    /**
     * 한 장을 받아 온다. **새 기기 복원이 이 경로다.**
     *
     * 얼굴 주소는 단건 조회에서만 오므로 한 번 더 부른다.
     */
    private suspend fun download(token: String, remote: RemoteCard, appUserId: String): Boolean {
        val detail = CardApi.get(token, remote.id).getOrNull() ?: remote
        val face = detail.faceUrl
            ?.let { CardApi.face(it).getOrNull() }
            ?.let { bytes -> runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }

        // **그림을 먼저 쓰고 줄을 넣는다** — `RoomCardStore.add` 가 같은 순서를 지킨다.
        // 반대로 하면 도감에 칸은 채워졌는데 얼굴이 안 나오는 카드가 생긴다.
        store.add(detail.toDrawnCard(appUserId), face)
        return true
    }
}

/**
 * 서버가 준 것을 Room 의 모양으로.
 *
 * `appUserId` 는 서버가 안 준다 — 토큰의 주인 것만 오기 때문이다. 그래서 받는 쪽이
 * 자기 id 를 채운다.
 */
private fun RemoteCard.toDrawnCard(appUserId: String): DrawnCard = DrawnCard(
    id = id,
    appUserId = appUserId,
    templateId = templateId,
    dogId = dogId,
    dogName = dogName,
    drawnAtMillis = drawnAtMillis,
    codeText = codeText,
    core = androidx.compose.ui.unit.IntRect(coreLeft, coreTop, coreRight, coreBottom),
    userFramed = userFramed,
)
