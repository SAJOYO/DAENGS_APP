package com.daengs.app.dogcard.store

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 뽑아 놓은 카드 한 장.
 *
 * **결과를 저장한다. 시드를 저장하지 않는다.** 카드는 "그때 그 사진 + 그때 뽑힌
 * 야채"라 다시 만들 수가 없다 — 사진은 매번 다르고 누끼도 매번 다르게 잘린다.
 * 나중에 파는 물건이기도 해서, 확률표를 고쳤다고 이미 가진 카드가 바뀌면 안 된다.
 */
@Entity(
    tableName = "drawn_card",
    indices = [Index("templateId"), Index("dogId"), Index("drawnAtMillis"), Index("appUserId")],
)
data class DrawnCardRow(
    /**
     * 앱이 만든 UUID. `walk_session.id` 와 같은 방식이다 — 서버가 붙어도 이 id 를
     * 그대로 올려서 **재전송이 멱등해진다.** 얼굴 그림의 파일 이름도 이 값이다.
     */
    @PrimaryKey val id: String,
    /**
     * 누구 것인가. **null 은 "둘러보기로 들어와 로그인 없이 뽑은 카드"** 다 —
     * 랜딩에 그 길이 실제로 있다. 다음 로그인 때 그 계정에 귀속시킨다.
     *
     * 이 칸이 있으면 **로그아웃해도 카드를 안 지우면서 남의 계정 카드는 안 보여주는**
     * 두 가지가 동시에 된다. 지우는 것은 탈퇴뿐이다.
     */
    val appUserId: String?,
    /**
     * 어느 야채인가. **번호가 아니라 문자열이다.**
     *
     * 카드 목록의 원본이 저쪽 저장소의 `cards.mjs` 라, 저쪽이 순서를 바꾸면 번호가
     * 통째로 밀린다. 그러면 **어제 뽑은 배추가 오늘 피망이 된다.** 번호는 필요할 때
     * `DEX_CARDS` 에서 찾아 쓴다.
     */
    val templateId: String,
    /**
     * 어느 아이로 뽑았나. 서버의 pet id 이고 **외래키가 아니다** — 강아지는 서버에
     * 있어서 이 DB 에 걸 대상이 없다. **지워도 이 줄은 남는다.**
     */
    val dogId: String?,
    /**
     * 카드에 **인쇄된** 이름. [dogId] 와 따로 두는 것이 모순처럼 보이지만 성질이 다르다 —
     * 이건 "그 아이의 지금 이름"이 아니라 그때 카드에 찍힌 글자다. 개명했다고 이미
     * 뽑아 놓은 카드의 인쇄가 바뀌면 안 된다. 지금 이름이 필요하면 [dogId] 로 찾는다.
     */
    val dogName: String,
    val drawnAtMillis: Long,
    /** 번호판 글자. 생일에서 만든다 (`birthCode`). */
    val codeText: String,
    /**
     * 또렷한 얼굴만의 자리. `Cutout.faceFor` 가 재어 준 것을 네 칸으로 편다.
     *
     * **이게 없으면 다음에 열 때 얼굴이 밀린다.** 누끼는 목 아래가 서서히 흐려지며
     * 끝나는데, 그 꼬리까지 포함한 사각형을 구멍에 맞추면 머리가 반대쪽으로 밀리고
     * 구멍 한쪽이 통째로 빈다.
     *
     * `IntRect` 컨버터를 두지 않고 네 칸으로 펴 둔 것은, 컨버터 하나 때문에 스키마가
     * 못 읽는 덩어리가 되는 것보다 낫기 때문이다.
     */
    val coreLeft: Int,
    val coreTop: Int,
    val coreRight: Int,
    val coreBottom: Int,
)
