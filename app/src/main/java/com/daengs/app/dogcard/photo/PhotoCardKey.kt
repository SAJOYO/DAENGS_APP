package com.daengs.app.dogcard.photo

/**
 * 어느 카드를 만드는가 — 달이면 `"1"`..`"12"`, 종류면 `"strawberry"`·`"lettuce"`.
 *
 * 저쪽 `daengs_cardimage.catalog.card_key()` 와 **같은 글자**다 (`SAJOYO/DAENGS_dev` #593, D-085).
 * 예전에는 앱도 서버도 달 하나(`month: Int`)로 카드를 가리켰는데, 딸기·상추가 열리면서
 * 「달」 로는 가리킬 수 없는 카드가 생겼다.
 *
 * **앱 모델에서 `month` 를 뺐다.** 서버 응답에는 `month`(종류 카드는 `null`)와 `card` 가 함께
 * 오지만, 그 `month` 는 **옛 앱이 읽으라고 남겨 둔 메아리**다 (D-085 전환기 계약). 둘을 다
 * 들고 있으면 「종류 카드의 month 는 뭐였더라」를 부르는 자리마다 되묻게 된다 — 하나로 둔다.
 *
 * 보낼 때도 `card` 하나만 보낸다. `month` 와 함께 보내도 되지만 값이 어긋나면 서버가
 * 400 `card_conflict` 를 주므로, 어긋날 수 있는 길을 아예 안 만든다.
 */
@JvmInline
value class PhotoCardKey(val raw: String) {

    /** 달 카드면 1~12, 종류 카드면 null. */
    val month: Int? get() = raw.toIntOrNull()?.takeIf { it in 1..12 }

    /** 달이 아닌 카드(딸기·상추). */
    val isKind: Boolean get() = month == null

    /**
     * 사람에게 보여 주는 이름 — 달은 「4월」, 종류는 「딸기」.
     *
     * 저쪽 `catalog.label()` 과 같은 글자다. **모르는 종류는 `raw` 를 그대로 보여 준다** —
     * 서버가 앱보다 먼저 새 카드를 열면(카탈로그에 넣는 것이 곧 여는 것이다) 앱에 없는
     * 키가 목록으로 올라올 수 있다. 영문이라도 보여주는 편이 빈칸보다 낫다.
     */
    val label: String get() = month?.let { "${it}월" } ?: PHOTO_KIND_LABELS[raw] ?: raw

    override fun toString(): String = raw

    companion object {
        fun of(month: Int): PhotoCardKey = PhotoCardKey(month.toString())

        val Strawberry = PhotoCardKey("strawberry")
        val Lettuce = PhotoCardKey("lettuce")

        /**
         * 앱이 아는 종류 카드. 저쪽 `catalog.KINDS` 와 같은 순서다.
         *
         * ⚠️ **종류에는 열림/닫힘 설정이 없다** — 저쪽 카탈로그에 있으면 열린 것이다 (D-085).
         * 달의 [OPEN_PHOTO_MONTHS][com.daengs.app.ui.dex.OPEN_PHOTO_MONTHS] 에 해당하는 잠금이
         * 종류에는 없어서, 저쪽이 종류를 늘리면 여기 한 줄을 더하는 것이 곧 앱에서 여는 것이다.
         */
        val KINDS = listOf(Strawberry, Lettuce)

        /**
         * 서버 응답에서 고른다. `card` 를 먼저 보고, 없으면(#593 배포 전 서버) `month` 로 만든다.
         * 둘 다 없으면 null — 부르는 쪽이 그 행을 버린다.
         */
        fun from(card: String?, month: Int?): PhotoCardKey? =
            card?.takeIf { it.isNotBlank() }?.let(::PhotoCardKey)
                ?: month?.let(::of)
    }
}

/**
 * 종류 카드의 한국어 이름. 저쪽 `catalog.KIND_LABELS` 와 **같은 글자여야 한다** — 서버가 409
 * 문장에 「이미 딸기 카드가 있어요」 를 쓰는데, 앱 화면이 「스트로베리」 라고 적고 있으면
 * 같은 카드가 두 이름으로 보인다.
 */
private val PHOTO_KIND_LABELS = mapOf(
    "strawberry" to "딸기",
    "lettuce" to "상추",
)

/** 막힌 카드를 한 줄로 알릴 때 — 「4월·딸기」. 달이 앞, 종류가 뒤다. */
fun Collection<PhotoCardKey>.labelList(): String =
    sortedWith(compareBy({ it.isKind }, { it.month ?: 0 }, { it.raw })).joinToString("·") { it.label }
