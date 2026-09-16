package com.daengs.app.ui.dex

import androidx.compose.runtime.Immutable
import com.daengs.app.dogcard.DrawnCard
import com.daengs.app.dogcard.photo.PhotoCard
import com.daengs.app.dogcard.photo.PhotoCardStatus
import java.io.File

/**
 * 도감 칸이 가진 카드 한 장. **누끼 카드이거나 포토 카드다.**
 *
 * 둘은 만드는 길이 다르다 — 누끼는 틀 + 얼굴 + 글자를 조립하고, 포토는 서버가 다 그린
 * PNG 한 장이다. 그래도 **만든 뒤에는 똑같이 작용해야 해서**(사용자 결정 2026-09-15)
 * 칸·확대 뷰·설명·저장·액자·지우기가 이 한 겹만 보고, 그림 얻기에서만 갈린다
 * ([rememberOwnedCardArt]).
 */
@Immutable
sealed interface OwnedCard {
    val id: String
    val dogName: String
    val madeAtMillis: Long

    data class Drawn(val card: DrawnCard) : OwnedCard {
        override val id get() = card.id
        override val dogName get() = card.dogName
        override val madeAtMillis get() = card.drawnAtMillis
    }

    /** @param file 받아 둔 완성 그림. 없으면 아직 만드는 중이거나 받는 중이다 */
    data class Photo(val card: PhotoCard, val file: File?) : OwnedCard {
        override val id get() = card.id
        override val dogName get() = card.dogName
        override val madeAtMillis get() = card.createdAtMillis

        /** 그림이 아직 없다. 포일·설명·저장·액자를 안 연다 */
        val pending: Boolean get() = card.status != PhotoCardStatus.Ready || file == null
    }
}

/**
 * 도감 한 칸.
 *
 * **칸 수는 카탈로그가 정한다.** 같은 종류를 세 번 뽑아도 칸이 세 개가 되지 않고 [owned] 가
 * 세 장이 된다 — 도감은 "무엇을 가졌나" 를 보여 주는 자리이지 뽑은 순서를 늘어놓는
 * 자리가 아니다.
 *
 * 다만 **뽑은 장은 한 장도 안 버린다.** 같은 아이라도 사진마다 표정이 달라서 두 번째
 * 배추가 첫 번째와 다른 카드다. 칸 안에서 넘겨 볼 수 있어야 한다.
 */
@Immutable
data class DexSlot(
    val card: DexCard,
    /** 최근이 앞이다. 비어 있으면 아직 안 뽑은 칸이다 */
    val owned: List<OwnedCard>,
) {
    val locked: Boolean get() = owned.isEmpty()
    val count: Int get() = owned.size

    /**
     * **뽑아서(만들어서) 다 된 장수.** 화면의 `×N` 은 이 값을 쓴다.
     *
     * [count] 와 다르다 — 얼굴 자리가 빈 줄(예전 디버그 시드)과 **아직 만드는 중인 포토**는
     * 안 센다. 시금치를 한 번 뽑았는데 ×2 로 보이면 "두 번 뽑았다" 는 거짓말이 된다.
     */
    val drawnCount: Int get() = owned.count {
        when (it) {
            is OwnedCard.Drawn -> it.card.drawn
            is OwnedCard.Photo -> !it.pending
        }
    }
}

/**
 * 카탈로그에 내가 가진 것을 겹친다. 누끼는 `templateId`, 포토는 달로 칸을 찾는다.
 *
 * 카탈로그에 없는 `templateId` 는 **조용히 버린다.** 저쪽이 카드를 갈아엎으면 생길
 * 수 있는데, 그릴 칸이 없으니 그릴 수가 없다. 다만 [ownedTotal] 에서도 빼서 "내 카드
 * 7장" 이라고 해 놓고 여섯 장만 보이는 화면이 안 나오게 한다.
 *
 * **실패한 포토는 칸에 안 넣는다** — 도감 머리말의 한 줄이 알린다.
 */
fun dexSlots(
    cards: List<DexCard> = DEX_CARDS,
    drawn: List<DrawnCard>,
    photos: List<PhotoCard> = emptyList(),
    photoFiles: Map<String, File> = emptyMap(),
): List<DexSlot> {
    val byTemplate: Map<String, List<OwnedCard>> = drawn
        .map { OwnedCard.Drawn(it) }
        .groupBy { it.card.templateId }
    val byMonth: Map<String, List<OwnedCard>> = photos
        .filter { it.status != PhotoCardStatus.Failed }
        .mapNotNull { p -> photoCardFor(p.month)?.let { it.id to OwnedCard.Photo(p, photoFiles[p.id]) } }
        .groupBy({ it.first }, { it.second })
    return cards.map { card ->
        val mine = (byTemplate[card.id].orEmpty() + byMonth[card.id].orEmpty())
            .sortedByDescending { it.madeAtMillis }
        DexSlot(card, mine)
    }
}

/** 몇 종을 모았나. 머리글의 분자다. */
fun List<DexSlot>.collectedKinds(): Int = count { !it.locked }

/** 모두 몇 장인가. 같은 종류를 여러 장 뽑았으면 그만큼 는다. */
fun List<DexSlot>.ownedTotal(): Int = sumOf { it.count }
