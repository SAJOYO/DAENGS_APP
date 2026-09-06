package com.daengs.app.dogcard

/**
 * 기기와 서버의 카드를 맞출 때 **무엇을 해야 하나.**
 *
 * 이 셈만 따로 꺼내 둔 이유는 앞의 둘과 같다 — 틀리면 **카드가 사라지거나 두 장이
 * 된다.** 도감에서는 "왜 카드가 없어졌지" 로만 보이고 원인이 안 보인다.
 *
 * @param localIds 기기에 있는 카드 id (이 계정 것만)
 * @param remoteIds 서버에 있는 카드 id
 */
data class CardSyncPlan(
    /** 서버에 없다 — 올린다. **첫 동기화가 여기다** (기기에 쌓여 있던 것 전부). */
    val upload: List<String>,
    /** 기기에 없다 — 받아 온다. **새 폰이 여기다.** */
    val download: List<String>,
) {
    val isEmpty: Boolean get() = upload.isEmpty() && download.isEmpty()
}

/**
 * ⚠️ **지운 것을 여기서 다루지 않는다.**
 *
 * "기기에 없고 서버에 있다" 를 무조건 **받아 온다**로 본다. "다른 기기에서 지웠으니
 * 여기서도 지운다" 로 볼 수도 있는데, 그러면 **새 폰에서 복원이 통째로 안 된다** —
 * 새 폰은 기기가 비어 있으니 전부 "지운 것" 이 되어 서버 카드를 다 밀어 버린다.
 *
 * 프로필 사진은 반대로 판단했는데(거기선 지운다), 갈린 이유는 **도장**이다:
 * 사진은 "서버에서 받아 온 적 있다" 는 표시가 파일 옆에 있어서 새 폰과 삭제를
 * 구분할 수 있다. 카드는 Room 이 통째로 비어 있으면 그 구분이 없다.
 *
 * 그래서 **카드 삭제는 지운 그 자리에서 서버에도 알린다** (`CardHolder.remove`).
 * 여기서 뒤늦게 맞추지 않는다.
 */
fun planCardSync(localIds: Collection<String>, remoteIds: Collection<String>): CardSyncPlan {
    val local = localIds.toSet()
    val remote = remoteIds.toSet()
    return CardSyncPlan(
        upload = (local - remote).toList(),
        download = (remote - local).toList(),
    )
}
